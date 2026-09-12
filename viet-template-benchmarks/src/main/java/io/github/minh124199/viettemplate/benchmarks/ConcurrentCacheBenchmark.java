package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures multi-threaded scaling and contention on {@link TemplateCompileCache#lruLock}.
 * Evaluates read-heavy workloads and mixed read/write workloads (95% reads, 5% writes)
 * across 1, 4, and 8 threads.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class ConcurrentCacheBenchmark {

  private static final int CACHE_CAPACITY = 2000;
  private static final int TOTAL_KEYS = 1000;

  private TemplateCompileCache cache;
  private List<CompileCacheKey> keys;
  private List<CompiledTemplateHandle> handles;

  @State(Scope.Thread)
  public static class ThreadState {
    private int counter = 0;

    public CompileCacheKey nextKey(List<CompileCacheKey> allKeys) {
      counter = (counter + 1) & 0x7FFFFFFF;
      return allKeys.get(counter % allKeys.size());
    }

    public void executeMixed(
        TemplateCompileCache cache,
        List<CompileCacheKey> allKeys,
        List<CompiledTemplateHandle> allHandles,
        Blackhole bh) {
      counter = (counter + 1) & 0x7FFFFFFF;
      int idx = counter % allKeys.size();
      if (counter % 100 < 95) {
        // 95% Reads
        bh.consume(cache.get(allKeys.get(idx)));
      } else {
        // 5% Writes
        cache.put(allKeys.get(idx), allHandles.get(idx));
      }
    }
  }

  @Setup(Level.Trial)
  public void setUp() {
    cache = new TemplateCompileCache(CACHE_CAPACITY, 60000L, 500);
    keys = new ArrayList<>(TOTAL_KEYS);
    handles = new ArrayList<>(TOTAL_KEYS);

    for (int i = 0; i < TOTAL_KEYS; i++) {
      TemplateId tId = TemplateId.of("concurrent-tmpl-" + i + ".vm");
      CompileCacheKey key =
          CompileCacheKey.of(
              tId,
              "hash-" + i,
              "0.1.1-SNAPSHOT",
              OptimizationLevel.O2,
              ExecutionTier.IR,
              "standard",
              "model-sig-" + i,
              "opts");
      CompiledTemplateHandle handle = CompiledTemplateHandle.ofIr(tId, 1L, key, null);
      keys.add(key);
      handles.add(handle);
      cache.put(key, handle);
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (cache != null) {
      cache.invalidateAll();
      cache = null;
    }
  }

  // --- Read-Heavy Workloads ---

  @Benchmark
  @Threads(1)
  public Optional<CompiledTemplateHandle> readHeavy_1Thread(ThreadState state) {
    return cache.get(state.nextKey(keys));
  }

  @Benchmark
  @Threads(4)
  public Optional<CompiledTemplateHandle> readHeavy_4Threads(ThreadState state) {
    return cache.get(state.nextKey(keys));
  }

  @Benchmark
  @Threads(8)
  public Optional<CompiledTemplateHandle> readHeavy_8Threads(ThreadState state) {
    return cache.get(state.nextKey(keys));
  }

  // --- Mixed Read/Write (95% read, 5% write) Workloads ---

  @Benchmark
  @Threads(1)
  public void mixedReadWrite_1Thread(ThreadState state, Blackhole bh) {
    state.executeMixed(cache, keys, handles, bh);
  }

  @Benchmark
  @Threads(4)
  public void mixedReadWrite_4Threads(ThreadState state, Blackhole bh) {
    state.executeMixed(cache, keys, handles, bh);
  }

  @Benchmark
  @Threads(8)
  public void mixedReadWrite_8Threads(ThreadState state, Blackhole bh) {
    state.executeMixed(cache, keys, handles, bh);
  }
}
