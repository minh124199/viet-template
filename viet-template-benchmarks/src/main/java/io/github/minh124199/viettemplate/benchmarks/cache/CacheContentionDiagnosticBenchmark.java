package io.github.minh124199.viettemplate.benchmarks.cache;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.benchmarks.cache.prototype.BatchedDeferredLruCompileCache;
import io.github.minh124199.viettemplate.benchmarks.cache.prototype.CompileCacheInterface;
import io.github.minh124199.viettemplate.benchmarks.cache.prototype.CurrentTemplateCompileCacheAdapter;
import io.github.minh124199.viettemplate.benchmarks.cache.prototype.LegacyLockedCompileCache;
import io.github.minh124199.viettemplate.benchmarks.cache.prototype.NoLruBookkeepingCache;
import io.github.minh124199.viettemplate.benchmarks.cache.prototype.PreHardeningDeferredRecencyCache;
import io.github.minh124199.viettemplate.benchmarks.cache.prototype.StripedLruCompileCache;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * VT-PERF-M19.3A-QUALIFICATION-1: Contention diagnostic qualification benchmark matrix.
 *
 * <p>Compares the current production compile cache against candidate prototypes across varying key
 * working set sizes (1, 16, 256, 1000) under read-hit, mixed read-heavy, and eviction under
 * pressure workloads.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class CacheContentionDiagnosticBenchmark {

  @Param({"CURRENT", "PRE_HARDENING_M19_3A", "LEGACY_LOCKED", "NO_LRU", "STRIPED_A", "BATCHED_B"})
  private String cacheType;

  @Param({"1", "16", "256", "1000"})
  private int keyCount;

  private CompileCacheInterface cache;
  private List<CompileCacheKey> keys;
  private List<CompiledTemplateHandle> handles;

  private CompileCacheInterface evictionCache;
  private List<CompileCacheKey> evictionKeys;
  private List<CompiledTemplateHandle> evictionHandles;

  @State(Scope.Thread)
  public static class ThreadState {
    private int counter = 0;
    public final RenderContext renderContext =
        RenderContext.of(Map.of("user", "Alice", "item", "Item-12345"));

    public CompileCacheKey nextKey(List<CompileCacheKey> allKeys) {
      counter = (counter + 1) & 0x7FFFFFFF;
      return allKeys.get(counter % allKeys.size());
    }

    public void executeMixed(
        CompileCacheInterface targetCache,
        List<CompileCacheKey> allKeys,
        List<CompiledTemplateHandle> allHandles,
        Blackhole bh) {
      counter = (counter + 1) & 0x7FFFFFFF;
      int idx = counter % allKeys.size();
      if (counter % 100 < 95) {
        // 95% Reads
        bh.consume(targetCache.get(allKeys.get(idx)));
      } else {
        // 5% Writes
        targetCache.put(allKeys.get(idx), allHandles.get(idx));
      }
    }

    public void executeEviction(
        CompileCacheInterface targetCache,
        List<CompileCacheKey> allKeys,
        List<CompiledTemplateHandle> allHandles,
        Blackhole bh) {
      counter = (counter + 1) & 0x7FFFFFFF;
      int idx = counter % allKeys.size();
      targetCache.put(allKeys.get(idx), allHandles.get(idx));
      bh.consume(targetCache.get(allKeys.get(idx)));
    }
  }

  @Setup(Level.Trial)
  public void setUp() {
    int workingCapacity = Math.max(2000, keyCount * 2);
    cache = createCache(cacheType, workingCapacity);
    keys = new ArrayList<>(keyCount);
    handles = new ArrayList<>(keyCount);

    CompiledTemplate tmpl =
        new CompiledTemplate() {
          @Override
          public TemplateDescriptor descriptor() {
            return TemplateDescriptor.of(TemplateId.of("diagnostic"), "BYTECODE");
          }

          @Override
          public void render(RenderContext ctx, TemplateOutput out) throws IOException {
            out.write("Hello ");
            out.write(String.valueOf(ctx.get("user")));
            out.write(", your order ");
            out.write(String.valueOf(ctx.get("item")));
            out.write(" is confirmed.\n");
          }
        };

    for (int i = 0; i < keyCount; i++) {
      TemplateId id = TemplateId.of("diagnostic-tmpl-" + i + ".vm");
      CompileCacheKey key = createKey(id, "hash-" + i);
      CompiledTemplateHandle handle =
          CompiledTemplateHandle.ofBytecode(id, 1L, key, tmpl, null, null);
      keys.add(key);
      handles.add(handle);
      cache.put(key, handle);
    }

    // Capacity 100 cache cycling through 200 keys to force continuous eviction
    evictionCache = createCache(cacheType, 100);
    evictionKeys = new ArrayList<>(200);
    evictionHandles = new ArrayList<>(200);

    for (int i = 0; i < 200; i++) {
      TemplateId id = TemplateId.of("evict-pressure-tmpl-" + i + ".vm");
      CompileCacheKey key = createKey(id, "hash-" + i);
      CompiledTemplateHandle handle = CompiledTemplateHandle.ofIr(id, 1L, key, null);
      evictionKeys.add(key);
      evictionHandles.add(handle);
      if (i < 100) {
        evictionCache.put(key, handle);
      }
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
  public Optional<CompiledTemplateHandle> readHit(ThreadState state) {
    return cache.get(state.nextKey(keys));
  }

  @Benchmark
  public void readHeavy(ThreadState state, Blackhole bh) {
    state.executeMixed(cache, keys, handles, bh);
  }

  @Benchmark
  public void evictionUnderPressure(ThreadState state, Blackhole bh) {
    state.executeEviction(evictionCache, evictionKeys, evictionHandles, bh);
  }

  @Benchmark
  public void concurrentRender(ThreadState state, Blackhole bh) throws IOException {
    Optional<CompiledTemplateHandle> handle = cache.get(state.nextKey(keys));
    if (handle.isPresent()) {
      StringTemplateOutput output = new StringTemplateOutput();
      handle.get().compiledTemplate().get().render(state.renderContext, output);
      bh.consume(output);
    }
  }

  private static CompileCacheInterface createCache(String type, int capacity) {
    return switch (type) {
      case "CURRENT" -> new CurrentTemplateCompileCacheAdapter(capacity, 60000L, 500);
      case "PRE_HARDENING_M19_3A" -> new PreHardeningDeferredRecencyCache(capacity, 60000L, 500);
      case "LEGACY_LOCKED" -> new LegacyLockedCompileCache(capacity, 60000L, 500);
      case "NO_LRU" -> new NoLruBookkeepingCache(capacity, 60000L, 500);
      case "STRIPED_A" -> new StripedLruCompileCache(capacity, 60000L, 500);
      case "BATCHED_B" -> new BatchedDeferredLruCompileCache(capacity, 60000L, 500);
      default -> throw new IllegalArgumentException("Unknown cache type: " + type);
    };
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
}
