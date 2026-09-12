package io.github.minh124199.viettemplate.vtl.engine.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TemplateCompileCacheTest {

  @Test
  void cacheHitAndMissAcrossDimensions() {
    TemplateCompileCache cache = new TemplateCompileCache(10, 5000L, 10);
    TemplateId id = TemplateId.of("test.vm");

    CompileCacheKey key1 =
        CompileCacheKey.of(
            id,
            "hash1",
            "0.2.0",
            OptimizationLevel.O2,
            ExecutionTier.AOT_BYTECODE,
            "policy1",
            "model1",
            "v1");

    CompiledTemplateHandle handle1 = CompiledTemplateHandle.ofIr(id, 1L, key1, null);
    cache.put(key1, handle1);

    assertThat(cache.get(key1)).isPresent();
    assertThat(cache.getActive(id)).isPresent();
    assertThat(cache.getActive(id).get().generation()).isEqualTo(1L);

    // Different fingerprint -> miss
    CompileCacheKey key2 =
        CompileCacheKey.of(
            id,
            "hash2",
            "0.2.0",
            OptimizationLevel.O2,
            ExecutionTier.AOT_BYTECODE,
            "policy1",
            "model1",
            "v1");
    assertThat(cache.get(key2)).isEmpty();

    // Different policy -> miss
    CompileCacheKey key3 =
        CompileCacheKey.of(
            id,
            "hash1",
            "0.2.0",
            OptimizationLevel.O2,
            ExecutionTier.AOT_BYTECODE,
            "policy2",
            "model1",
            "v1");
    assertThat(cache.get(key3)).isEmpty();

    // Different model signature -> miss
    CompileCacheKey key4 =
        CompileCacheKey.of(
            id,
            "hash1",
            "0.2.0",
            OptimizationLevel.O2,
            ExecutionTier.AOT_BYTECODE,
            "policy1",
            "model2",
            "v1");
    assertThat(cache.get(key4)).isEmpty();

    // Different optimization level -> miss
    CompileCacheKey key5 =
        CompileCacheKey.of(
            id,
            "hash1",
            "0.2.0",
            OptimizationLevel.O0,
            ExecutionTier.AOT_BYTECODE,
            "policy1",
            "model1",
            "v1");
    assertThat(cache.get(key5)).isEmpty();
  }

  @Test
  void enforcesBoundedLruEviction() {
    TemplateCompileCache cache = new TemplateCompileCache(2, 5000L, 5);

    TemplateId id1 = TemplateId.of("t1.vm");
    TemplateId id2 = TemplateId.of("t2.vm");
    TemplateId id3 = TemplateId.of("t3.vm");

    CompileCacheKey k1 =
        CompileCacheKey.of(
            id1, "h1", "0.2.0", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");
    CompileCacheKey k2 =
        CompileCacheKey.of(
            id2, "h2", "0.2.0", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");
    CompileCacheKey k3 =
        CompileCacheKey.of(
            id3, "h3", "0.2.0", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");

    cache.put(k1, CompiledTemplateHandle.ofIr(id1, 1L, k1, null));
    cache.put(k2, CompiledTemplateHandle.ofIr(id2, 2L, k2, null));
    assertThat(cache.size()).isEqualTo(2);

    // Access k1 to make k2 the eldest
    cache.get(k1);

    // Insert k3 -> should evict k2
    cache.put(k3, CompiledTemplateHandle.ofIr(id3, 3L, k3, null));
    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.get(k1)).isPresent();
    assertThat(cache.get(k3)).isPresent();
    assertThat(cache.get(k2)).isEmpty();
  }

  @Test
  void negativeCachingWithTtlAndInvalidation() throws InterruptedException {
    TemplateCompileCache cache = new TemplateCompileCache(10, 50L, 5); // 50ms TTL
    TemplateId missing = TemplateId.of("missing.vm");

    assertThat(cache.isNegativelyCached(missing)).isFalse();

    cache.recordNegative(missing, "File not found");
    assertThat(cache.isNegativelyCached(missing)).isTrue();

    // Wait for TTL expiration
    Thread.sleep(80L);
    assertThat(cache.isNegativelyCached(missing)).isFalse();

    // Re-record and test explicit invalidation
    cache.recordNegative(missing, "File not found");
    assertThat(cache.isNegativelyCached(missing)).isTrue();
    cache.invalidate(missing);
    assertThat(cache.isNegativelyCached(missing)).isFalse();
  }

  @Test
  void atomicReplacementUpdatesGeneration() {
    TemplateCompileCache cache = new TemplateCompileCache();
    TemplateId id = TemplateId.of("dynamic.vm");

    CompileCacheKey k1 =
        CompileCacheKey.of(
            id, "h1", "0.2.0", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");
    CompileCacheKey k2 =
        CompileCacheKey.of(
            id, "h2", "0.2.0", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");

    long gen1 = cache.nextGeneration();
    cache.put(k1, CompiledTemplateHandle.ofIr(id, gen1, k1, null));
    assertThat(cache.currentGeneration(id)).isEqualTo(gen1);

    long gen2 = cache.nextGeneration();
    cache.put(k2, CompiledTemplateHandle.ofIr(id, gen2, k2, null));
    assertThat(cache.currentGeneration(id)).isEqualTo(gen2);
    assertThat(gen2).isGreaterThan(gen1);
    assertThat(cache.get(k1)).isPresent();
    assertThat(cache.indexedKeyCount(id)).isEqualTo(2);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void indexedInvalidationRemovesAllAndOnlyTargetTemplateVariants() {
    TemplateCompileCache cache = new TemplateCompileCache(20, 5000L, 5);
    TemplateId x = TemplateId.of("x.vm");
    TemplateId y = TemplateId.of("y.vm");
    List<CompileCacheKey> xKeys = List.of(key(x, 1), key(x, 2), key(x, 3));
    List<CompileCacheKey> yKeys = List.of(key(y, 1), key(y, 2));
    xKeys.forEach(key -> cache.put(key, handle(key)));
    yKeys.forEach(key -> cache.put(key, handle(key)));

    cache.invalidate(x);
    cache.invalidate(x);

    assertThat(xKeys).allSatisfy(key -> assertThat(cache.get(key)).isEmpty());
    assertThat(yKeys).allSatisfy(key -> assertThat(cache.get(key)).isPresent());
    assertThat(cache.indexedKeyCount(x)).isZero();
    assertThat(cache.indexedKeyCount(y)).isEqualTo(2);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void evictionAndClearRemoveReverseIndexMembership() {
    TemplateCompileCache cache = new TemplateCompileCache(2, 5000L, 5);
    TemplateId x = TemplateId.of("x.vm");
    TemplateId y = TemplateId.of("y.vm");
    CompileCacheKey x1 = key(x, 1);
    CompileCacheKey x2 = key(x, 2);
    CompileCacheKey y1 = key(y, 1);
    cache.put(x1, handle(x1));
    cache.put(x2, handle(x2));
    cache.put(y1, handle(y1));

    assertThat(cache.get(x1)).isEmpty();
    assertThat(cache.indexedKeyCount(x)).isEqualTo(1);
    assertThat(cache.isInternallyConsistent()).isTrue();

    cache.invalidateAll();
    assertThat(cache.size()).isZero();
    assertThat(cache.indexedKeyCount(x)).isZero();
    assertThat(cache.indexedKeyCount(y)).isZero();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void concurrentMutationPreservesAllIndexes() throws Exception {
    // Capacity below the variant count makes insertion race with eviction as well as invalidation.
    TemplateCompileCache cache = new TemplateCompileCache(16, 5000L, 10);
    TemplateId target = TemplateId.of("race.vm");
    List<CompileCacheKey> keys = new ArrayList<>();
    for (int i = 0; i < 32; i++) {
      keys.add(key(target, i));
    }
    int workers = 6;
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int worker = 0; worker < workers; worker++) {
        int workerIndex = worker;
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  for (int i = 0; i < 1000; i++) {
                    CompileCacheKey key = keys.get((i + workerIndex) % keys.size());
                    if (workerIndex % 3 == 0) {
                      cache.invalidate(target);
                    } else if (workerIndex % 3 == 1) {
                      cache.put(key, handle(key));
                    } else {
                      cache.get(key);
                      cache.getActive(target);
                    }
                  }
                  return null;
                }));
      }
      ready.await();
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(cache.isInternallyConsistent()).isTrue();
    cache.invalidate(target);
    assertThat(cache.size()).isZero();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void concurrentClearInsertionAndCrossTemplateEvictionPreserveIndexes() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(8, 5000L, 10);
    List<CompileCacheKey> keys = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      keys.add(key(TemplateId.of("clear-race-" + i + ".vm"), i));
    }
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(4);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int worker = 0; worker < 3; worker++) {
        int offset = worker;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  for (int i = 0; i < 2000; i++) {
                    CompileCacheKey key = keys.get((i + offset) % keys.size());
                    cache.put(key, handle(key));
                  }
                  return null;
                }));
      }
      futures.add(
          executor.submit(
              () -> {
                start.await();
                for (int i = 0; i < 250; i++) {
                  cache.invalidateAll();
                }
                return null;
              }));
      start.countDown();
      for (Future<?> future : futures) {
        future.get(15, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(cache.isInternallyConsistent()).isTrue();
    for (CompileCacheKey key : keys) {
      cache.invalidate(key.templateId());
    }
    assertThat(cache.size()).isZero();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  private static CompileCacheKey key(TemplateId id, int variant) {
    return CompileCacheKey.of(
        id,
        "h" + variant,
        "0.2.0",
        OptimizationLevel.O2,
        ExecutionTier.IR,
        "p" + variant,
        "m",
        "v");
  }

  private static CompiledTemplateHandle handle(CompileCacheKey key) {
    return CompiledTemplateHandle.ofIr(key.templateId(), 1L, key, null);
  }
}
