package io.github.minh124199.viettemplate.vtl.engine.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    cache.drainMaintenance();

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

  @Test
  void enforcesBoundedApproximateLruEviction() {
    TemplateCompileCache cache = new TemplateCompileCache(3, 5000L, 5);

    TemplateId id1 = TemplateId.of("approx1.vm");
    TemplateId id2 = TemplateId.of("approx2.vm");
    TemplateId id3 = TemplateId.of("approx3.vm");
    TemplateId id4 = TemplateId.of("approx4.vm");

    CompileCacheKey k1 = key(id1, 1);
    CompileCacheKey k2 = key(id2, 2);
    CompileCacheKey k3 = key(id3, 3);
    CompileCacheKey k4 = key(id4, 4);

    cache.put(k1, handle(k1));
    cache.put(k2, handle(k2));
    cache.put(k3, handle(k3));
    assertThat(cache.size()).isEqualTo(3);

    // Repeatedly hit hot key k1 to record and drain recency
    for (int i = 0; i < 70; i++) {
      assertThat(cache.get(k1)).isPresent();
    }
    cache.drainMaintenance();

    // Insert k4 under capacity pressure (capacity 3)
    cache.put(k4, handle(k4));

    assertThat(cache.size()).isEqualTo(3);
    assertThat(cache.get(k1)).isPresent(); // hot key survives
    assertThat(cache.get(k3)).isPresent();
    assertThat(cache.get(k4)).isPresent();
    assertThat(cache.get(k2)).isEmpty(); // eldest cold key evicted
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void adversarialProducerCollisionStress() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(32, 5000L, 20);
    int targetStripe = 0;
    int workerCount = 32;

    List<CompileCacheKey> keys = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      keys.add(key(TemplateId.of("adversarial-" + i + ".vm"), i));
    }
    for (int i = 0; i < 32; i++) {
      cache.put(keys.get(i), handle(keys.get(i)));
    }

    List<Thread> workers = new ArrayList<>();
    CountDownLatch ready = new CountDownLatch(workerCount);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(workerCount);
    AtomicInteger errorCount = new AtomicInteger();

    while (workers.size() < workerCount) {
      Thread candidate =
          new Thread(
              () -> {
                ready.countDown();
                try {
                  start.await();
                  for (int iter = 0; iter < 1000; iter++) {
                    CompileCacheKey k = keys.get(iter % keys.size());
                    if ((iter & 1) == 0) {
                      cache.get(k);
                    } else {
                      cache.put(k, handle(k));
                    }
                  }
                } catch (Throwable ex) {
                  errorCount.incrementAndGet();
                } finally {
                  done.countDown();
                }
              });
      if ((candidate.getId() & 15) == targetStripe) {
        workers.add(candidate);
      }
    }

    for (Thread t : workers) {
      t.start();
    }
    ready.await();
    start.countDown();
    assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
    assertThat(errorCount.get()).isZero();
    assertThat(cache.size()).isLessThanOrEqualTo(32);
    cache.drainMaintenance();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void recencyBufferSaturationStress() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(16, 5000L, 10);
    int workerCount = 8;
    int itemsPerWorker = 1000;
    ExecutorService executor = Executors.newFixedThreadPool(workerCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    try {
      for (int w = 0; w < workerCount; w++) {
        int workerIndex = w;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  for (int i = 0; i < itemsPerWorker; i++) {
                    int id = workerIndex * itemsPerWorker + i;
                    CompileCacheKey key = key(TemplateId.of("sat-" + id + ".vm"), id);
                    cache.put(key, handle(key));
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(15, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(cache.size()).isLessThanOrEqualTo(16);
    cache.drainMaintenance();
    assertThat(cache.size()).isLessThanOrEqualTo(16);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void duplicateHotKeyStress() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(100, 5000L, 20);
    CompileCacheKey hotKey = key(TemplateId.of("hot.vm"), 1);
    cache.put(hotKey, handle(hotKey));

    int workerCount = 32;
    int readsPerWorker = 50_000;
    ExecutorService executor = Executors.newFixedThreadPool(workerCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    try {
      for (int w = 0; w < workerCount; w++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  for (int i = 0; i < readsPerWorker; i++) {
                    if (cache.get(hotKey).isEmpty()) {
                      throw new IllegalStateException("Hot key lookup failed");
                    }
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(15, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    cache.drainMaintenance();
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void broadWorkingSetStress() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(200, 5000L, 50);
    List<CompileCacheKey> workingSet = new ArrayList<>(1000);
    for (int i = 0; i < 1000; i++) {
      workingSet.add(key(TemplateId.of("broad-" + (i % 250) + ".vm"), i));
    }

    int workerCount = 12;
    ExecutorService executor = Executors.newFixedThreadPool(workerCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    try {
      for (int w = 0; w < workerCount; w++) {
        int workerIndex = w;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  ThreadLocalRandom random = ThreadLocalRandom.current();
                  for (int i = 0; i < 2000; i++) {
                    int keyIdx = random.nextInt(workingSet.size());
                    CompileCacheKey key = workingSet.get(keyIdx);
                    if (workerIndex % 3 == 0) {
                      cache.invalidate(key.templateId());
                    } else if (workerIndex % 3 == 1) {
                      cache.put(key, handle(key));
                    } else {
                      cache.get(key);
                      cache.getActive(key.templateId());
                    }
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(15, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(cache.size()).isLessThanOrEqualTo(200);
    for (int i = 0; i < 250; i++) {
      cache.invalidate(TemplateId.of("broad-" + i + ".vm"));
    }
    cache.drainMaintenance();
    assertThat(cache.size()).isZero();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void staleEventAfterInvalidateIsIgnored() {
    TemplateCompileCache cache = new TemplateCompileCache(10, 5000L, 5);
    TemplateId id = TemplateId.of("stale-inv.vm");
    CompileCacheKey k = key(id, 1);

    cache.put(k, handle(k));
    assertThat(cache.get(k)).isPresent();

    // Invalidate clears positive mapping, but recency buffer still holds the key event
    cache.invalidate(id);
    assertThat(cache.get(k)).isEmpty();
    assertThat(cache.size()).isZero();

    // Maintenance drain must discard the stale key event without resurrecting it
    cache.drainMaintenance();
    assertThat(cache.get(k)).isEmpty();
    assertThat(cache.size()).isZero();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void staleEventAfterEvictionIsIgnored() {
    TemplateCompileCache cache = new TemplateCompileCache(1, 5000L, 5);
    CompileCacheKey k1 = key(TemplateId.of("e1.vm"), 1);
    CompileCacheKey k2 = key(TemplateId.of("e2.vm"), 2);

    cache.put(k1, handle(k1));
    assertThat(cache.get(k1)).isPresent();

    // Capacity pressure evicts k1
    cache.put(k2, handle(k2));
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.get(k1)).isEmpty();
    assertThat(cache.get(k2)).isPresent();

    // Maintenance drain must not resurrect evicted k1
    cache.drainMaintenance();
    assertThat(cache.get(k1)).isEmpty();
    assertThat(cache.get(k2)).isPresent();
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void highThreadCollisionStress16Threads() throws Exception {
    runAdversarialCollisionTest(16, 1);
    runAdversarialCollisionTest(16, 16);
  }

  @Test
  void highThreadCollisionStress32Threads() throws Exception {
    runAdversarialCollisionTest(32, 16);
    runAdversarialCollisionTest(32, 256);
  }

  @Test
  void highThreadCollisionStress64Threads() throws Exception {
    runAdversarialCollisionTest(64, 16);
    runAdversarialCollisionTest(64, 256);
  }

  @Test
  void highThreadCollisionStress128And256Threads() throws Exception {
    runAdversarialCollisionTest(128, 16);
    runAdversarialCollisionTest(256, 64);
  }

  private void runAdversarialCollisionTest(int threadCount, int keyCount) throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(100, 5000L, 20);
    List<CompileCacheKey> keys = new ArrayList<>(keyCount);
    for (int i = 0; i < keyCount; i++) {
      CompileCacheKey k = key(TemplateId.of("col-" + i + ".vm"), i);
      keys.add(k);
      cache.put(k, handle(k));
    }

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    AtomicInteger errorCount = new AtomicInteger(0);

    try {
      for (int t = 0; t < threadCount; t++) {
        int threadIdx = t;
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  ThreadLocalRandom random = ThreadLocalRandom.current();
                  for (int op = 0; op < 2000; op++) {
                    CompileCacheKey k = keys.get(random.nextInt(keys.size()));
                    if (threadIdx % 5 == 0 && op % 50 == 0) {
                      cache.invalidate(k.templateId());
                    } else if (threadIdx % 5 == 1 && op % 50 == 0) {
                      cache.put(k, handle(k));
                    } else {
                      cache.get(k);
                    }
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(20, TimeUnit.SECONDS);
      }
    } catch (Throwable e) {
      errorCount.incrementAndGet();
      throw e;
    } finally {
      executor.shutdownNow();
    }

    assertThat(errorCount.get()).isZero();
    assertThat(cache.size()).isLessThanOrEqualTo(100);
    cache.drainMaintenance();
    assertThat(cache.size()).isLessThanOrEqualTo(100);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void recorderSaturationWithDelayedMaintenance() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(50, 5000L, 20);
    List<CompileCacheKey> keys = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      CompileCacheKey k = key(TemplateId.of("sat-hold-" + i + ".vm"), i);
      keys.add(k);
      cache.put(k, handle(k));
    }

    CountDownLatch lockHeld = new CountDownLatch(1);
    CountDownLatch releaseLock = new CountDownLatch(1);
    ExecutorService lockHolder = Executors.newSingleThreadExecutor();
    lockHolder.submit(
        () -> {
          cache.maintenanceLock.lock();
          try {
            lockHeld.countDown();
            releaseLock.await();
          } finally {
            cache.maintenanceLock.unlock();
          }
          return null;
        });

    assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

    int workerCount = 16;
    ExecutorService workers = Executors.newFixedThreadPool(workerCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int w = 0; w < workerCount; w++) {
        futures.add(
            workers.submit(
                () -> {
                  start.await();
                  ThreadLocalRandom random = ThreadLocalRandom.current();
                  for (int i = 0; i < 3000; i++) {
                    CompileCacheKey k = keys.get(random.nextInt(keys.size()));
                    assertThat(cache.get(k)).isPresent();
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(10, TimeUnit.SECONDS);
      }
    } finally {
      releaseLock.countDown();
      workers.shutdownNow();
      lockHolder.shutdownNow();
    }

    cache.drainMaintenance();
    assertThat(cache.size()).isEqualTo(50);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void sequenceWraparoundHarmlessness() {
    TemplateCompileCache cache = new TemplateCompileCache(10, 5000L, 5);
    CompileCacheKey k = key(TemplateId.of("wrap.vm"), 1);
    cache.put(k, handle(k));

    long[] testPositions = {
      0L,
      1L,
      63L,
      64L,
      65L,
      127L,
      128L,
      Integer.MAX_VALUE,
      (long) Integer.MAX_VALUE + 1,
      -1L,
      -2L,
      -63L,
      -64L,
      -65L,
      Long.MAX_VALUE,
      Long.MIN_VALUE
    };

    for (long pos : testPositions) {
      int index = (int) (pos & 63);
      assertThat(index).isBetween(0, 63);
      boolean trigger = (pos & 63) == 0;
      if (pos == 0L || pos == 64L || pos == 128L || pos == -64L || pos == Long.MIN_VALUE) {
        assertThat(trigger).isTrue();
      }
    }

    for (int i = 0; i < 100_000; i++) {
      assertThat(cache.get(k)).isPresent();
    }
    cache.drainMaintenance();
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void consumerProducerConcurrentSlotRace() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(20, 5000L, 10);
    List<CompileCacheKey> keys = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      CompileCacheKey k = key(TemplateId.of("slot-race-" + i + ".vm"), i);
      keys.add(k);
      cache.put(k, handle(k));
    }

    int threadCount = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    List<Future<?>> futures = new ArrayList<>();

    try {
      for (int t = 0; t < threadCount; t++) {
        int threadId = t;
        futures.add(
            executor.submit(
                () -> {
                  for (int round = 0; round < 2000; round++) {
                    barrier.await();
                    if (threadId == 0) {
                      cache.drainMaintenance();
                    } else {
                      CompileCacheKey k = keys.get(round % keys.size());
                      cache.get(k);
                    }
                  }
                  return null;
                }));
      }
      for (Future<?> f : futures) {
        f.get(15, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    cache.drainMaintenance();
    assertThat(cache.size()).isEqualTo(10);
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void invalidateAllRaceUnderContinuousReadHits() throws Exception {
    TemplateCompileCache cache = new TemplateCompileCache(100, 5000L, 20);
    TemplateId id = TemplateId.of("inv-race.vm");
    CompileCacheKey k = key(id, 1);
    cache.put(k, handle(k));

    int readerCount = 16;
    ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch stopReaders = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    try {
      for (int r = 0; r < readerCount; r++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  while (stopReaders.getCount() > 0) {
                    cache.get(k);
                    Thread.onSpinWait();
                  }
                  return null;
                }));
      }

      futures.add(
          executor.submit(
              () -> {
                start.await();
                for (int i = 0; i < 100; i++) {
                  cache.invalidateAll();
                  if (i % 2 == 0) {
                    cache.put(k, handle(k));
                  }
                }
                cache.invalidateAll();
                stopReaders.countDown();
                return null;
              }));

      start.countDown();
      for (Future<?> f : futures) {
        f.get(15, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    cache.drainMaintenance();
    assertThat(cache.size()).isZero();
    assertThat(cache.getActive(id)).isEmpty();
    assertThat(cache.get(k)).isEmpty();
    assertThat(cache.isInternallyConsistent()).isTrue();
  }

  @Test
  void extremeEventLossUnderSaturationFavorsHotEntry() {
    TemplateCompileCache cache = new TemplateCompileCache(10, 5000L, 5);
    CompileCacheKey hotKey = key(TemplateId.of("fav-hot.vm"), 0);
    cache.put(hotKey, handle(hotKey));

    for (int i = 1; i < 10; i++) {
      CompileCacheKey k = key(TemplateId.of("cold-" + i + ".vm"), i);
      cache.put(k, handle(k));
    }
    assertThat(cache.size()).isEqualTo(10);

    for (int i = 0; i < 1000; i++) {
      cache.get(hotKey);
    }

    for (int i = 10; i < 15; i++) {
      CompileCacheKey k = key(TemplateId.of("cold-" + i + ".vm"), i);
      cache.put(k, handle(k));
    }

    assertThat(cache.size()).isLessThanOrEqualTo(10);
    assertThat(cache.get(hotKey)).isPresent();
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
