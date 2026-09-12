package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrency stress test verifying M19.2a linearization invariants for {@link
 * TemplateCompileCache}: - Put-before-invalidate ordering - Put-after-invalidate ordering -
 * Concurrent invalidation idempotency - LRU bounded capacity eviction coupled with reverse-index
 * consistency
 */
class CompileCacheConcurrencyStressTest {

  @Test
  @DisplayName("Verify linearization contract: Put serialized before Invalidate removes the entry")
  void testPutBeforeInvalidateLinearization() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(100, 5000L, 50);
    TemplateId templateId = TemplateId.of("linear-put-before.vm");
    CompileCacheKey key = createKey(templateId, "hash1");
    CompiledTemplateHandle handle = CompiledTemplateHandle.ofIr(templateId, 1L, key, null);

    CountDownLatch putStarted = new CountDownLatch(1);
    CountDownLatch putDone = new CountDownLatch(1);
    CountDownLatch invalidateDone = new CountDownLatch(1);

    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();
    try {
      executor.submit(
          () -> {
            putStarted.countDown();
            cache.put(key, handle);
            putDone.countDown();
          });

      executor.submit(
          () -> {
            try {
              putDone.await(5, TimeUnit.SECONDS);
              cache.invalidate(templateId);
              invalidateDone.countDown();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      assertThat(invalidateDone.await(5, TimeUnit.SECONDS)).isTrue();

      // Because put linearized before invalidate, the key must be absent
      assertThat(cache.get(key)).isEmpty();
      assertThat(cache.getActive(templateId)).isEmpty();
    } finally {
      executor.shutdown();
    }
  }

  @Test
  @DisplayName("Verify linearization contract: Put serialized after Invalidate remains present")
  void testPutAfterInvalidateLinearization() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(100, 5000L, 50);
    TemplateId templateId = TemplateId.of("linear-put-after.vm");
    CompileCacheKey key = createKey(templateId, "hash2");
    CompiledTemplateHandle handle = CompiledTemplateHandle.ofIr(templateId, 1L, key, null);

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
              invalidateDone.await(5, TimeUnit.SECONDS);
              cache.put(key, handle);
              putDone.countDown();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      assertThat(putDone.await(5, TimeUnit.SECONDS)).isTrue();

      // Because put linearized after invalidate, the entry must remain present
      assertThat(cache.get(key)).isPresent();
      assertThat(cache.getActive(templateId)).isPresent();
    } finally {
      executor.shutdown();
    }
  }

  @Test
  @DisplayName("Stress test concurrent invalidation idempotency with 64 threads")
  void testConcurrentInvalidationIdempotency() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(200, 5000L, 50);
    TemplateId templateId = TemplateId.of("idempotent.vm");
    CompileCacheKey key = createKey(templateId, "hash3");
    cache.put(key, CompiledTemplateHandle.ofIr(templateId, 1L, key, null));

    int threadCount = 64;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();

    try {
      List<Callable<Void>> tasks = new ArrayList<>(threadCount);
      for (int i = 0; i < threadCount; i++) {
        tasks.add(
            () -> {
              barrier.await();
              cache.invalidate(templateId);
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get();
      }

      assertThat(cache.get(key)).isEmpty();
      assertThat(cache.getActive(templateId)).isEmpty();
    } finally {
      executor.shutdown();
    }
  }

  @Test
  @DisplayName("Stress test LRU bounded eviction with concurrent reverse index cleanup")
  void testLruEvictionAndReverseIndexCleanup() throws Exception {
    int maxCapacity = 50;
    TemplateCompileCache cache = new TemplateCompileCache(maxCapacity, 5000L, 50);

    int totalInserts = 300;
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();

    try {
      List<Callable<Void>> tasks = new ArrayList<>(totalInserts);
      for (int i = 0; i < totalInserts; i++) {
        final int id = i;
        tasks.add(
            () -> {
              TemplateId tId = TemplateId.of("evict-" + id + ".vm");
              CompileCacheKey k = createKey(tId, "hash-" + id);
              cache.put(k, CompiledTemplateHandle.ofIr(tId, (long) id, k, null));
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get();
      }

      // Verify that the cache maintained bounded entry limits and did not throw or deadlock
      // Any remaining entry in active cache should be retrievable
      int foundActive = 0;
      int evicted = 0;
      for (int i = 0; i < totalInserts; i++) {
        TemplateId tId = TemplateId.of("evict-" + i + ".vm");
        if (cache.getActive(tId).isPresent()) {
          foundActive++;
        } else {
          evicted++;
          assertThat(indexedKeyCount(cache, tId)).isZero();
        }
      }
      assertThat(foundActive).isLessThanOrEqualTo(maxCapacity);
      assertThat(evicted).isGreaterThan(0);
      assertThat(isInternallyConsistent(cache)).isTrue();
    } finally {
      executor.shutdown();
    }
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

  private static int indexedKeyCount(TemplateCompileCache cache, TemplateId id) throws Exception {
    Method method =
        TemplateCompileCache.class.getDeclaredMethod("indexedKeyCount", TemplateId.class);
    method.setAccessible(true);
    return (int) method.invoke(cache, id);
  }

  private static boolean isInternallyConsistent(TemplateCompileCache cache) throws Exception {
    Method method = TemplateCompileCache.class.getDeclaredMethod("isInternallyConsistent");
    method.setAccessible(true);
    return (boolean) method.invoke(cache);
  }
}
