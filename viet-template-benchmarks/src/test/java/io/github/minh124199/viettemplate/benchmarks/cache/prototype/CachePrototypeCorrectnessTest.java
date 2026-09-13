package io.github.minh124199.viettemplate.benchmarks.cache.prototype;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyKind;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.benchmarks.stress.VirtualThreadSupport;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Comprehensive correctness and race stress test suite for compile cache implementations:
 *
 * <ul>
 *   <li>{@link CurrentTemplateCompileCacheAdapter}
 *   <li>{@link LegacyLockedCompileCache}
 *   <li>{@link NoLruBookkeepingCache}
 *   <li>{@link StripedLruCompileCache}
 *   <li>{@link BatchedDeferredLruCompileCache}
 * </ul>
 */
class CachePrototypeCorrectnessTest {

  @FunctionalInterface
  interface CacheFactory extends Function<Integer, CompileCacheInterface> {}

  static Stream<Arguments> allCacheFactories() {
    return Stream.of(
        Arguments.of(
            "CurrentTemplateCompileCacheAdapter",
            (CacheFactory) cap -> new CurrentTemplateCompileCacheAdapter(cap, 5000L, 50)),
        Arguments.of(
            "LegacyLockedCompileCache",
            (CacheFactory) cap -> new LegacyLockedCompileCache(cap, 5000L, 50)),
        Arguments.of(
            "NoLruBookkeepingCache",
            (CacheFactory) cap -> new NoLruBookkeepingCache(cap, 5000L, 50)),
        Arguments.of(
            "StripedLruCompileCache",
            (CacheFactory) cap -> new StripedLruCompileCache(cap, 5000L, 50, 16)),
        Arguments.of(
            "BatchedDeferredLruCompileCache",
            (CacheFactory) cap -> new BatchedDeferredLruCompileCache(cap, 5000L, 50)),
        Arguments.of(
            "PreHardeningDeferredRecencyCache",
            (CacheFactory) cap -> new PreHardeningDeferredRecencyCache(cap, 5000L, 50)),
        Arguments.of(
            "SampledDeferredRecency_1_2",
            (CacheFactory) cap -> new SampledDeferredRecencyCompileCache(cap, 5000L, 50, 1, 128)),
        Arguments.of(
            "SampledDeferredRecency_1_4",
            (CacheFactory) cap -> new SampledDeferredRecencyCompileCache(cap, 5000L, 50, 2, 256)),
        Arguments.of(
            "SampledDeferredRecency_1_8",
            (CacheFactory) cap -> new SampledDeferredRecencyCompileCache(cap, 5000L, 50, 3, 512)));
  }

  static Stream<Arguments> lruCacheFactories() {
    return Stream.of(
        Arguments.of(
            "CurrentTemplateCompileCacheAdapter",
            (CacheFactory) cap -> new CurrentTemplateCompileCacheAdapter(cap, 5000L, 50)),
        Arguments.of(
            "LegacyLockedCompileCache",
            (CacheFactory) cap -> new LegacyLockedCompileCache(cap, 5000L, 50)),
        Arguments.of(
            "StripedLruCompileCache_SingleStripe",
            (CacheFactory) cap -> new StripedLruCompileCache(cap, 5000L, 50, 1)),
        Arguments.of(
            "BatchedDeferredLruCompileCache",
            (CacheFactory) cap -> new BatchedDeferredLruCompileCache(cap, 5000L, 50)),
        Arguments.of(
            "PreHardeningDeferredRecencyCache",
            (CacheFactory) cap -> new PreHardeningDeferredRecencyCache(cap, 5000L, 50)),
        Arguments.of(
            "Drain_128",
            (CacheFactory) cap -> new SampledDeferredRecencyCompileCache(cap, 5000L, 50, 0, 128)),
        Arguments.of(
            "Drain_256",
            (CacheFactory) cap -> new SampledDeferredRecencyCompileCache(cap, 5000L, 50, 0, 256)));
  }

  static Stream<Arguments> sampledCacheFactories() {
    return Stream.of(
        Arguments.of(
            "SampledDeferredRecency_1_2",
            (CacheFactory) cap -> new SampledDeferredRecencyCompileCache(cap, 5000L, 50, 1, 128)),
        Arguments.of(
            "SampledDeferredRecency_1_4",
            (CacheFactory) cap -> new SampledDeferredRecencyCompileCache(cap, 5000L, 50, 2, 256)),
        Arguments.of(
            "SampledDeferredRecency_1_8",
            (CacheFactory) cap -> new SampledDeferredRecencyCompileCache(cap, 5000L, 50, 3, 512)));
  }

  // --- a. Basic put and get hit ---

  @ParameterizedTest(name = "{0}: basic put and get hit")
  @MethodSource("allCacheFactories")
  @DisplayName("a. Basic put and get hit retrieves cached handle and maintains consistency")
  void testBasicPutAndGetHit(String name, CacheFactory factory) {
    CompileCacheInterface cache = factory.apply(100);
    TemplateId templateId = TemplateId.of("basic-tmpl.vm");
    CompileCacheKey key = createKey(templateId, "hash-basic");
    CompiledTemplateHandle handle = createHandle(templateId, key);

    assertThat(cache.get(key)).isEmpty();
    assertThat(cache.getActive(templateId)).isEmpty();
    assertThat(cache.size()).isZero();

    cache.put(key, handle);

    assertThat(cache.size()).isEqualTo(1);
    Optional<CompiledTemplateHandle> fetched = cache.get(key);
    assertThat(fetched).isPresent();
    assertThat(fetched.get()).isSameAs(handle);

    Optional<CompiledTemplateHandle> active = cache.getActive(templateId);
    assertThat(active).isPresent();
    assertThat(active.get()).isSameAs(handle);

    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  // --- b. Put-before-invalidate ---

  @ParameterizedTest(name = "{0}: put before invalidate")
  @MethodSource("allCacheFactories")
  @DisplayName("b. Put before invalidate ensures invalidated entry is absent")
  void testPutBeforeInvalidate(String name, CacheFactory factory) throws Exception {
    CompileCacheInterface cache = factory.apply(100);
    TemplateId templateId = TemplateId.of("linear-put-before.vm");
    CompileCacheKey key = createKey(templateId, "hash-put-before");
    CompiledTemplateHandle handle = createHandle(templateId, key);

    CountDownLatch putDone = new CountDownLatch(1);
    CountDownLatch invalidateDone = new CountDownLatch(1);

    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();
    try {
      executor.submit(
          () -> {
            cache.put(key, handle);
            putDone.countDown();
          });

      executor.submit(
          () -> {
            try {
              if (putDone.await(5, TimeUnit.SECONDS)) {
                cache.invalidate(templateId);
                invalidateDone.countDown();
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      assertThat(invalidateDone.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(cache.get(key)).isEmpty();
      assertThat(cache.getActive(templateId)).isEmpty();
      assertThat(cache.size()).isZero();
      assertThat(cache.isInternallyConsistent()).isTrue();
    } finally {
      executor.shutdown();
    }
  }

  // --- c. Put-after-invalidate ---

  @ParameterizedTest(name = "{0}: put after invalidate")
  @MethodSource("allCacheFactories")
  @DisplayName("c. Put after invalidate ensures newly inserted entry remains present")
  void testPutAfterInvalidate(String name, CacheFactory factory) throws Exception {
    CompileCacheInterface cache = factory.apply(100);
    TemplateId templateId = TemplateId.of("linear-put-after.vm");
    CompileCacheKey key = createKey(templateId, "hash-put-after");
    CompiledTemplateHandle handle = createHandle(templateId, key);

    CountDownLatch invalidateDone = new CountDownLatch(1);
    CountDownLatch putDone = new CountDownLatch(1);

    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();
    try {
      executor.submit(
          () -> {
            cache.invalidate(templateId);
            invalidateDone.countDown();
          });

      executor.submit(
          () -> {
            try {
              if (invalidateDone.await(5, TimeUnit.SECONDS)) {
                cache.put(key, handle);
                putDone.countDown();
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      assertThat(putDone.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(cache.get(key)).isPresent();
      assertThat(cache.getActive(templateId)).isPresent();
      assertThat(cache.size()).isEqualTo(1);
      assertThat(cache.isInternallyConsistent()).isTrue();
    } finally {
      executor.shutdown();
    }
  }

  // --- d. Multi-key template invalidation ---

  @ParameterizedTest(name = "{0}: multi-key invalidation")
  @MethodSource("allCacheFactories")
  @DisplayName("d. Multi-key template invalidation removes all variants and reverse index")
  void testMultiKeyTemplateInvalidation(String name, CacheFactory factory) {
    CompileCacheInterface cache = factory.apply(100);
    TemplateId targetId = TemplateId.of("multi-target.vm");
    TemplateId otherId = TemplateId.of("multi-other.vm");

    List<CompileCacheKey> targetKeys = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      CompileCacheKey key = createKey(targetId, "variant-" + i);
      targetKeys.add(key);
      cache.put(key, createHandle(targetId, key));
    }

    CompileCacheKey otherKey1 = createKey(otherId, "other-1");
    CompileCacheKey otherKey2 = createKey(otherId, "other-2");
    cache.put(otherKey1, createHandle(otherId, otherKey1));
    cache.put(otherKey2, createHandle(otherId, otherKey2));

    assertThat(cache.size()).isEqualTo(7);

    // Invalidate target template
    cache.invalidate(targetId);

    // All 5 target keys must be evicted
    for (CompileCacheKey key : targetKeys) {
      assertThat(cache.get(key)).isEmpty();
    }
    assertThat(cache.getActive(targetId)).isEmpty();

    // Other template must remain completely intact
    assertThat(cache.get(otherKey1)).isPresent();
    assertThat(cache.get(otherKey2)).isPresent();
    assertThat(cache.getActive(otherId)).isPresent();
    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  // --- e. Concurrent invalidation idempotency ---

  @ParameterizedTest(name = "{0}: concurrent invalidation idempotency")
  @MethodSource("allCacheFactories")
  @DisplayName("e. Concurrent invalidation idempotency with 32 threads")
  void testConcurrentInvalidationIdempotency(String name, CacheFactory factory) throws Exception {
    CompileCacheInterface cache = factory.apply(200);
    int templateCount = 10;
    List<TemplateId> templates = new ArrayList<>();

    for (int t = 0; t < templateCount; t++) {
      TemplateId tId = TemplateId.of("concurrent-inval-" + t + ".vm");
      templates.add(tId);
      for (int k = 0; k < 3; k++) {
        CompileCacheKey key = createKey(tId, "hash-" + t + "-" + k);
        cache.put(key, createHandle(tId, key));
      }
    }

    assertThat(cache.size()).isEqualTo(templateCount * 3);

    int threadCount = 32;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();

    try {
      List<Callable<Void>> tasks = new ArrayList<>(threadCount);
      for (int i = 0; i < threadCount; i++) {
        final int threadIdx = i;
        tasks.add(
            () -> {
              barrier.await();
              // Each thread invalidates a template (some overlap, some different)
              TemplateId target = templates.get(threadIdx % templateCount);
              cache.invalidate(target);
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }

      // Verify all invalidated templates are cleared
      for (TemplateId tId : templates) {
        assertThat(cache.getActive(tId)).isEmpty();
      }
      assertThat(cache.size()).isZero();
      assertThat(cache.isInternallyConsistent()).isTrue();
    } finally {
      executor.shutdown();
    }
  }

  // --- f. Capacity bounding under concurrency ---

  @ParameterizedTest(name = "{0}: capacity bounding under concurrency")
  @MethodSource("allCacheFactories")
  @DisplayName("f. Capacity bounding under concurrency: 500 keys inserted across 32 threads")
  void testCapacityBoundingUnderConcurrency(String name, CacheFactory factory) throws Exception {
    int maxCapacity = 100;
    CompileCacheInterface cache = factory.apply(maxCapacity);

    int threadCount = 32;
    int keysPerThread = 16;
    int totalInserts = threadCount * keysPerThread; // 512 keys

    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();

    try {
      List<Callable<Void>> tasks = new ArrayList<>(threadCount);
      for (int t = 0; t < threadCount; t++) {
        final int threadId = t;
        tasks.add(
            () -> {
              barrier.await();
              for (int k = 0; k < keysPerThread; k++) {
                int id = threadId * keysPerThread + k;
                TemplateId tId = TemplateId.of("bounded-evict-" + id + ".vm");
                CompileCacheKey key = createKey(tId, "hash-" + id);
                cache.put(key, createHandle(tId, key));
              }
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> future : futures) {
        future.get(15, TimeUnit.SECONDS);
      }

      // Verify capacity is bounded within configured per-stripe/batch tolerance
      int size = cache.size();
      assertThat(size).isGreaterThan(0);

      if (cache instanceof CurrentTemplateCompileCacheAdapter
          || cache instanceof LegacyLockedCompileCache) {
        assertThat(size).isLessThanOrEqualTo(maxCapacity);
      } else if (cache instanceof BatchedDeferredLruCompileCache) {
        assertThat(size).isLessThanOrEqualTo(maxCapacity);
      } else if (cache instanceof StripedLruCompileCache) {
        // 16 stripes * ceil(100 / 16) = 16 * 7 = 112
        int expectedMax = 16 * ((maxCapacity + 15) / 16);
        assertThat(size).isLessThanOrEqualTo(expectedMax);
      } else if (cache instanceof NoLruBookkeepingCache) {
        // Best-effort concurrent eviction without a central lock; verifies eviction occurred
        assertThat(size).isLessThan(totalInserts);
      }

      assertThat(cache.isInternallyConsistent()).isTrue();
    } finally {
      executor.shutdown();
    }
  }

  // --- g. Eviction recency test (for LRU implementations) ---

  @ParameterizedTest(name = "{0}: eviction recency")
  @MethodSource("lruCacheFactories")
  @DisplayName("g. Eviction recency: key accessed frequently is retained while LRU is evicted")
  void testEvictionRecency(String name, CacheFactory factory) {
    int capacity = 5;
    CompileCacheInterface cache = factory.apply(capacity);

    List<CompileCacheKey> keys = new ArrayList<>();
    for (int i = 0; i < capacity; i++) {
      TemplateId id = TemplateId.of("recency-" + i + ".vm");
      CompileCacheKey key = createKey(id, "hash-" + i);
      keys.add(key);
      cache.put(key, createHandle(id, key));
    }

    assertThat(cache.size()).isEqualTo(capacity);

    // Frequently access key 0
    CompileCacheKey key0 = keys.get(0);
    for (int a = 0; a < 5; a++) {
      assertThat(cache.get(key0)).isPresent();
    }

    // Insert key capacity (key 5), triggering eviction
    TemplateId newId = TemplateId.of("recency-new.vm");
    CompileCacheKey newKey = createKey(newId, "hash-new");
    cache.put(newKey, createHandle(newId, newKey));

    // Key 0 must be retained as it was recently accessed
    assertThat(cache.get(key0)).as("Recently accessed key 0 should be retained").isPresent();

    // The newly inserted key must be present
    assertThat(cache.get(newKey)).as("Newly inserted key should be present").isPresent();

    // Key 1 (the least recently used) should have been evicted
    CompileCacheKey key1 = keys.get(1);
    assertThat(cache.get(key1)).as("Least recently used key 1 should be evicted").isEmpty();

    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  // --- Additional coverage: Invalidate with dependents ---

  @ParameterizedTest(name = "{0}: invalidate with dependents")
  @MethodSource("allCacheFactories")
  @DisplayName("Invalidate with dependents clears transitive dependent templates")
  void testInvalidateWithDependents(String name, CacheFactory factory) {
    CompileCacheInterface cache = factory.apply(100);
    DefaultTemplateDependencyGraph graph = new DefaultTemplateDependencyGraph();

    TemplateId root = TemplateId.of("root.vm");
    TemplateId child = TemplateId.of("child.vm");
    graph.replaceDependencies(
        root, Set.of(TemplateDependency.of(root, child, TemplateDependencyKind.STATIC_PARSE)));

    CompileCacheKey rootKey = createKey(root, "hash-root");
    CompileCacheKey childKey = createKey(child, "hash-child");
    cache.put(rootKey, createHandle(root, rootKey));
    cache.put(childKey, createHandle(child, childKey));

    assertThat(cache.size()).isEqualTo(2);

    Set<TemplateId> invalidated = cache.invalidateWithDependents(child, graph);
    assertThat(invalidated).containsExactlyInAnyOrder(child, root);

    assertThat(cache.get(rootKey)).isEmpty();
    assertThat(cache.get(childKey)).isEmpty();
    assertThat(cache.size()).isZero();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  // --- Additional coverage: Invalidate all ---

  @ParameterizedTest(name = "{0}: invalidate all")
  @MethodSource("allCacheFactories")
  @DisplayName("Invalidate all clears entire cache and resets state")
  void testInvalidateAll(String name, CacheFactory factory) {
    CompileCacheInterface cache = factory.apply(100);
    for (int i = 0; i < 20; i++) {
      TemplateId id = TemplateId.of("inv-all-" + i + ".vm");
      CompileCacheKey key = createKey(id, "hash-" + i);
      cache.put(key, createHandle(id, key));
    }
    assertThat(cache.size()).isEqualTo(20);

    cache.invalidateAll();
    assertThat(cache.size()).isZero();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  // --- Additional coverage: Negative caching ---

  @ParameterizedTest(name = "{0}: negative caching")
  @MethodSource("allCacheFactories")
  @DisplayName("Negative caching records and invalidates negative entries")
  void testNegativeCaching(String name, CacheFactory factory) {
    CompileCacheInterface cache = factory.apply(100);
    TemplateId missing = TemplateId.of("missing.vm");

    assertThat(cache.isNegativelyCached(missing)).isFalse();
    cache.recordNegative(missing, "not found");
    assertThat(cache.isNegativelyCached(missing)).isTrue();

    // Putting a compiled handle should clear negative cache
    CompileCacheKey key = createKey(missing, "found-hash");
    cache.put(key, createHandle(missing, key));
    assertThat(cache.isNegativelyCached(missing)).isFalse();
    assertThat(cache.get(key)).isPresent();
  }

  // --- Eviction Quality under capacity pressure & sampling ---

  @ParameterizedTest(name = "{0}: eviction quality under capacity pressure")
  @MethodSource("lruCacheFactories")
  @DisplayName(
      "Eviction quality: hot entry and warm entries favored over cold churn under capacity"
          + " pressure")
  void testEvictionQualityUnderSampling(String name, CacheFactory factory) {
    CompileCacheInterface cache = factory.apply(10);
    TemplateId hotId = TemplateId.of("hot.vm");
    CompileCacheKey hotKey = createKey(hotId, "h1");
    cache.put(hotKey, createHandle(hotId, hotKey));

    TemplateId warmId = TemplateId.of("warm.vm");
    CompileCacheKey warmKey = createKey(warmId, "w1");
    cache.put(warmKey, createHandle(warmId, warmKey));

    // Fill remaining 8 slots
    for (int i = 0; i < 8; i++) {
      TemplateId id = TemplateId.of("init-" + i + ".vm");
      CompileCacheKey k = createKey(id, "init-" + i);
      cache.put(k, createHandle(id, k));
    }
    assertThat(cache.size()).isEqualTo(10);

    // Continuous workload: 500 operations
    // Every operation: access hotKey
    // Every 4th operation: access warmKey
    // Every 10th operation: insert a new cold key (churn)
    for (int i = 0; i < 500; i++) {
      cache.get(hotKey);
      if (i % 4 == 0) {
        cache.get(warmKey);
      }
      if (i % 10 == 0) {
        TemplateId coldId = TemplateId.of("cold-" + i + ".vm");
        CompileCacheKey coldKey = createKey(coldId, "cold-" + i);
        cache.put(coldKey, createHandle(coldId, coldKey));
      }
    }

    // Capacity must remain exact: <= 10
    assertThat(cache.size()).isLessThanOrEqualTo(10);
    // Hot key must be 100% retained!
    assertThat(cache.get(hotKey)).isPresent();
    // Warm key should also be retained (accessed 125 times vs 1 time for cold keys)!
    assertThat(cache.get(warmKey)).isPresent();
  }

  @ParameterizedTest(name = "{0}: eviction quality degradation under read sampling")
  @MethodSource("sampledCacheFactories")
  @DisplayName("Eviction quality degradation: read sampling drops warm keys under cold churn")
  void testSampledRecencyEvictionQualityDegradation(String name, CacheFactory factory) {
    CompileCacheInterface cache = factory.apply(10);
    TemplateId hotId = TemplateId.of("hot.vm");
    CompileCacheKey hotKey = createKey(hotId, "h1");
    cache.put(hotKey, createHandle(hotId, hotKey));

    TemplateId warmId = TemplateId.of("warm.vm");
    CompileCacheKey warmKey = createKey(warmId, "w1");
    cache.put(warmKey, createHandle(warmId, warmKey));

    // Fill remaining 8 slots
    for (int i = 0; i < 8; i++) {
      TemplateId id = TemplateId.of("init-" + i + ".vm");
      CompileCacheKey k = createKey(id, "init-" + i);
      cache.put(k, createHandle(id, k));
    }
    assertThat(cache.size()).isEqualTo(10);

    for (int i = 0; i < 500; i++) {
      cache.get(hotKey);
      if (i % 4 == 0) {
        cache.get(warmKey);
      }
      if (i % 10 == 0) {
        TemplateId coldId = TemplateId.of("cold-" + i + ".vm");
        CompileCacheKey coldKey = createKey(coldId, "cold-" + i);
        cache.put(coldKey, createHandle(coldId, coldKey));
      }
    }

    assertThat(cache.size()).isLessThanOrEqualTo(10);
    // Hot key accessed every iteration is retained
    assertThat(cache.get(hotKey)).isPresent();
    // Warm key accessed 125 times is evicted due to read sampling lossiness!
    assertThat(cache.get(warmKey)).isEmpty();
  }

  private static CompileCacheKey createKey(TemplateId id, String hash) {
    return CompileCacheKey.of(
        id,
        hash,
        "0.2.1-SNAPSHOT",
        OptimizationLevel.O2,
        ExecutionTier.IR,
        "standard",
        "model",
        "opts");
  }

  private static CompiledTemplateHandle createHandle(TemplateId id, CompileCacheKey key) {
    return CompiledTemplateHandle.ofIr(id, 1L, key, null);
  }
}
