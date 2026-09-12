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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures isolated operations on {@link TemplateCompileCache}:
 *
 * <ul>
 *   <li>Active template lookup {@code getActive(TemplateId)} (cache hit)
 *   <li>Exact multi-dimensional key lookup {@code get(CompileCacheKey)} (cache hit)
 *   <li>Insertion {@code put(CompileCacheKey, CompiledTemplateHandle)}
 *   <li>Replacement of active template key
 *   <li>Negative cache hit and miss via {@code isNegativelyCached(TemplateId)}
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class CompileCacheBenchmark {

  @State(Scope.Thread)
  public static class FreshInsertionState {
    private int cursor;
    private CompileCacheKey key;
    private CompiledTemplateHandle handle;

    @Setup(Level.Invocation)
    public void prepare(CompileCacheBenchmark benchmark) {
      int variant = cursor++ & 1023;
      TemplateId id = TemplateId.of("fresh-insertion-" + variant + ".vm");
      key =
          CompileCacheKey.of(
              id,
              "fresh-" + variant,
              "0.1.1-SNAPSHOT",
              OptimizationLevel.O2,
              ExecutionTier.IR,
              "standard",
              "model",
              "opts");
      handle = CompiledTemplateHandle.ofIr(id, 1L, key, null);
      benchmark.cache.invalidate(id);
    }
  }

  @State(Scope.Thread)
  public static class ActiveVariantState {
    private CompileCacheKey replacement;
    private CompiledTemplateHandle replacementHandle;

    @Setup(Level.Invocation)
    public void prepare(CompileCacheBenchmark benchmark) {
      benchmark.cache.invalidate(benchmark.targetId);
      benchmark.cache.put(benchmark.targetKey, benchmark.targetHandle);
      replacement = benchmark.replacementKey;
      replacementHandle = benchmark.replacementHandle;
    }
  }

  private TemplateCompileCache cache;
  private TemplateCompileCache evictionCache;

  private TemplateId targetId;
  private CompileCacheKey targetKey;
  private CompiledTemplateHandle targetHandle;

  private CompileCacheKey replacementKey;
  private CompiledTemplateHandle replacementHandle;

  private TemplateId negativeCachedId;
  private TemplateId negativeMissingId;
  private List<CompileCacheKey> evictionKeys;
  private List<CompiledTemplateHandle> evictionHandles;
  private int evictionCursor;

  @Setup(Level.Trial)
  public void setUp() {
    cache = new TemplateCompileCache(2000, 60000L, 500);

    for (int i = 0; i < 500; i++) {
      TemplateId id = TemplateId.of("template-" + i + ".vm");
      CompileCacheKey key =
          CompileCacheKey.of(
              id,
              "hash-" + i,
              "0.1.1-SNAPSHOT",
              OptimizationLevel.O2,
              ExecutionTier.IR,
              "standard",
              "model-sig-" + i,
              "opts");
      CompiledTemplateHandle handle = CompiledTemplateHandle.ofIr(id, 1L, key, null);
      cache.put(key, handle);
    }

    targetId = TemplateId.of("template-250.vm");
    targetKey =
        CompileCacheKey.of(
            targetId,
            "hash-250",
            "0.1.1-SNAPSHOT",
            OptimizationLevel.O2,
            ExecutionTier.IR,
            "standard",
            "model-sig-250",
            "opts");
    targetHandle = CompiledTemplateHandle.ofIr(targetId, 1L, targetKey, null);

    replacementKey =
        CompileCacheKey.of(
            targetId,
            "hash-250-v2",
            "0.1.1-SNAPSHOT",
            OptimizationLevel.O2,
            ExecutionTier.IR,
            "standard",
            "model-sig-250",
            "opts");
    replacementHandle = CompiledTemplateHandle.ofIr(targetId, 2L, replacementKey, null);

    negativeCachedId = TemplateId.of("missing-cached.vm");
    cache.recordNegative(negativeCachedId, "File not found");

    negativeMissingId = TemplateId.of("not-in-negative-cache.vm");

    evictionCache = new TemplateCompileCache(500, 60000L, 50);
    evictionKeys = new ArrayList<>(1024);
    evictionHandles = new ArrayList<>(1024);
    for (int i = 0; i < 1024; i++) {
      TemplateId id = TemplateId.of("eviction-" + i + ".vm");
      CompileCacheKey key =
          CompileCacheKey.of(
              id,
              "eviction-hash-" + i,
              "0.1.1-SNAPSHOT",
              OptimizationLevel.O2,
              ExecutionTier.IR,
              "standard",
              "model",
              "opts");
      evictionKeys.add(key);
      evictionHandles.add(CompiledTemplateHandle.ofIr(id, 1L, key, null));
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (cache != null) {
      cache.invalidateAll();
      cache = null;
    }
    if (evictionCache != null) {
      evictionCache.invalidateAll();
      evictionCache = null;
    }
  }

  @Benchmark
  public Optional<CompiledTemplateHandle> getActiveHit() {
    return cache.getActive(targetId);
  }

  @Benchmark
  public Optional<CompiledTemplateHandle> getExactKeyHit() {
    return cache.get(targetKey);
  }

  @Benchmark
  public void putInsertion() {
    // Historical M19.1 name retained: this is repeated same-key replacement.
    cache.put(targetKey, targetHandle);
  }

  @Benchmark
  public void putFreshInsertion(FreshInsertionState state) {
    cache.put(state.key, state.handle);
  }

  @Benchmark
  public void replaceActiveKey() {
    cache.put(replacementKey, replacementHandle);
  }

  @Benchmark
  public void replaceActiveWithNewVariant(ActiveVariantState state) {
    cache.put(state.replacement, state.replacementHandle);
  }

  @Benchmark
  public void putWithEviction() {
    int index = evictionCursor++ & 1023;
    evictionCache.put(evictionKeys.get(index), evictionHandles.get(index));
  }

  @Benchmark
  public boolean negativeCacheHit() {
    return cache.isNegativelyCached(negativeCachedId);
  }

  @Benchmark
  public boolean negativeCacheMiss() {
    return cache.isNegativelyCached(negativeMissingId);
  }
}
