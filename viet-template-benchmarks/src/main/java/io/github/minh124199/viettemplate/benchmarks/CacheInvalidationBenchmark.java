package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyKind;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures indexed cache invalidation, parameterized by total cache entries (N) and entries per
 * template (K). Fixed-K comparisons across N expose sensitivity to total cache size; fixed-N
 * comparisons across K expose affected-entry bookkeeping. Also measures transitive invalidation via
 * {@link TemplateCompileCache#invalidateWithDependents}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class CacheInvalidationBenchmark {

  @Param({"100", "1000", "10000"})
  private int totalEntries;

  @Param({"1", "5", "20"})
  private int entriesPerTemplate;

  private TemplateCompileCache cache;
  private TemplateId targetId;
  private List<CompileCacheKey> targetKeys;
  private List<CompiledTemplateHandle> targetHandles;

  private TemplateId nonExistentId;

  private DefaultTemplateDependencyGraph dependencyGraph;
  private TemplateId leafDependencyId;
  private List<TemplateId> dependentTemplateIds;
  private List<CompileCacheKey> dependentKeys;
  private List<CompiledTemplateHandle> dependentHandles;

  @State(Scope.Thread)
  public static class RestoreTargetState {
    @Setup(Level.Invocation)
    public void setUp(CacheInvalidationBenchmark benchmark) {
      for (int k = 0; k < benchmark.entriesPerTemplate; k++) {
        benchmark.cache.put(benchmark.targetKeys.get(k), benchmark.targetHandles.get(k));
      }
    }
  }

  @State(Scope.Thread)
  public static class RestoreDependentState {
    @Setup(Level.Invocation)
    public void setUp(CacheInvalidationBenchmark benchmark) {
      for (int i = 0; i < benchmark.dependentKeys.size(); i++) {
        benchmark.cache.put(benchmark.dependentKeys.get(i), benchmark.dependentHandles.get(i));
      }
    }
  }

  @Setup(Level.Trial)
  public void setUp() {
    int maxCapacity = Math.max(2000, totalEntries * 2);
    cache = new TemplateCompileCache(maxCapacity, 60000L, 500);

    int templateCount = Math.max(1, totalEntries / entriesPerTemplate);
    for (int t = 0; t < templateCount; t++) {
      TemplateId tId = TemplateId.of("tmpl-" + t + ".vm");
      for (int k = 0; k < entriesPerTemplate; k++) {
        CompileCacheKey key =
            CompileCacheKey.of(
                tId,
                "src-" + t + "-v" + k,
                "0.2.0",
                OptimizationLevel.O2,
                ExecutionTier.IR,
                "policy-" + k,
                "model-" + k,
                "opts-" + k);
        CompiledTemplateHandle handle = CompiledTemplateHandle.ofIr(tId, 1L, key, null);
        cache.put(key, handle);
      }
    }

    // Target template for single invalidation
    targetId = TemplateId.of("tmpl-0.vm");
    targetKeys = new ArrayList<>(entriesPerTemplate);
    targetHandles = new ArrayList<>(entriesPerTemplate);
    for (int k = 0; k < entriesPerTemplate; k++) {
      CompileCacheKey key =
          CompileCacheKey.of(
              targetId,
              "src-0-v" + k,
              "0.2.0",
              OptimizationLevel.O2,
              ExecutionTier.IR,
              "policy-" + k,
              "model-" + k,
              "opts-" + k);
      targetKeys.add(key);
      targetHandles.add(CompiledTemplateHandle.ofIr(targetId, 1L, key, null));
    }

    nonExistentId = TemplateId.of("never-cached-template.vm");

    // Transitive dependency chain setup:
    // app.vm -> widget.vm -> leaf.vm
    // Invalidation of leaf.vm must invalidate leaf.vm, widget.vm, and app.vm
    dependencyGraph = new DefaultTemplateDependencyGraph();
    leafDependencyId = TemplateId.of("dep-leaf.vm");
    TemplateId midId = TemplateId.of("dep-mid.vm");
    TemplateId rootId = TemplateId.of("dep-root.vm");

    dependencyGraph.replaceDependencies(
        midId,
        Set.of(
            TemplateDependency.of(midId, leafDependencyId, TemplateDependencyKind.STATIC_PARSE)));
    dependencyGraph.replaceDependencies(
        rootId, Set.of(TemplateDependency.of(rootId, midId, TemplateDependencyKind.STATIC_PARSE)));

    dependentTemplateIds = List.of(leafDependencyId, midId, rootId);
    dependentKeys = new ArrayList<>();
    dependentHandles = new ArrayList<>();

    for (TemplateId dId : dependentTemplateIds) {
      for (int k = 0; k < entriesPerTemplate; k++) {
        CompileCacheKey key =
            CompileCacheKey.of(
                dId,
                "dep-src-" + dId.value() + "-v" + k,
                "0.2.0",
                OptimizationLevel.O2,
                ExecutionTier.IR,
                "policy-" + k,
                "model-" + k,
                "opts-" + k);
        CompiledTemplateHandle handle = CompiledTemplateHandle.ofIr(dId, 1L, key, null);
        dependentKeys.add(key);
        dependentHandles.add(handle);
        cache.put(key, handle);
      }
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (cache != null) {
      cache.invalidateAll();
      cache = null;
    }
  }

  @Benchmark
  public void invalidateSingleTemplate(RestoreTargetState state) {
    cache.invalidate(targetId);
  }

  @Benchmark
  public void invalidateIndexedMiss() {
    cache.invalidate(nonExistentId);
  }

  @Benchmark
  public void invalidateWithDependents(RestoreDependentState state, Blackhole bh) {
    Set<TemplateId> invalidated = cache.invalidateWithDependents(leafDependencyId, dependencyGraph);
    bh.consume(invalidated);
  }
}
