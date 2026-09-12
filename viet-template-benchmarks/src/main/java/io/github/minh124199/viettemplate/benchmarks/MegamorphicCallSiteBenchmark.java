package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.runtime.linker.AccessLink;
import io.github.minh124199.viettemplate.runtime.linker.BoundedWeakClassCache;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.LinkerStatistics;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import java.util.ArrayList;
import java.util.List;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Forces {@link DynamicCallSite} beyond {@link DynamicCallSite#MAX_PIC_DEPTH} (4) using 16
 * distinct receiver classes, transitioning it to the {@link DynamicCallSite.State#MEGAMORPHIC} state.
 * Measures:
 * <ul>
 *   <li>Megamorphic call site invocation hits in {@link BoundedWeakClassCache}
 *   <li>Direct {@link BoundedWeakClassCache#get(Class)} hits
 *   <li>Direct {@link BoundedWeakClassCache#get(Class)} misses
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
public class MegamorphicCallSiteBenchmark {

  // 16 distinct receiver types to exceed PIC depth 4
  public record Type00(String value) {}
  public record Type01(String value) {}
  public record Type02(String value) {}
  public record Type03(String value) {}
  public record Type04(String value) {}
  public record Type05(String value) {}
  public record Type06(String value) {}
  public record Type07(String value) {}
  public record Type08(String value) {}
  public record Type09(String value) {}
  public record Type10(String value) {}
  public record Type11(String value) {}
  public record Type12(String value) {}
  public record Type13(String value) {}
  public record Type14(String value) {}
  public record Type15(String value) {}

  // Extra types that are never registered in the cache, to measure misses
  public record UncachedTypeA(String value) {}
  public record UncachedTypeB(String value) {}

  private DynamicCallSite megamorphicCallSite;
  private BoundedWeakClassCache<AccessLink> standaloneCache;

  private List<Object> cachedInstances;
  private List<Class<?>> cachedClasses;
  private Class<?> uncachedClass;

  @State(Scope.Thread)
  public static class MegaState {
    private int counter = 0;

    public Object nextInstance(List<Object> instances) {
      counter = (counter + 1) & 0x7FFFFFFF;
      return instances.get(counter % instances.size());
    }

    public Class<?> nextClass(List<Class<?>> classes) {
      counter = (counter + 1) & 0x7FFFFFFF;
      return classes.get(counter % classes.size());
    }
  }

  @Setup(Level.Trial)
  public void setUp() throws Throwable {
    DynamicLinker linker = new DynamicLinker(LinkerAccessPolicy.standard());
    MemberKey key = MemberKey.propertyGet("value");

    megamorphicCallSite =
        new DynamicCallSite(10, key, LinkerAccessPolicy.standard(), linker, new LinkerStatistics());

    cachedInstances = new ArrayList<>();
    cachedClasses = new ArrayList<>();

    cachedInstances.add(new Type00("v00"));
    cachedInstances.add(new Type01("v01"));
    cachedInstances.add(new Type02("v02"));
    cachedInstances.add(new Type03("v03"));
    cachedInstances.add(new Type04("v04"));
    cachedInstances.add(new Type05("v05"));
    cachedInstances.add(new Type06("v06"));
    cachedInstances.add(new Type07("v07"));
    cachedInstances.add(new Type08("v08"));
    cachedInstances.add(new Type09("v09"));
    cachedInstances.add(new Type10("v10"));
    cachedInstances.add(new Type11("v11"));
    cachedInstances.add(new Type12("v12"));
    cachedInstances.add(new Type13("v13"));
    cachedInstances.add(new Type14("v14"));
    cachedInstances.add(new Type15("v15"));

    for (Object obj : cachedInstances) {
      cachedClasses.add(obj.getClass());
    }

    // Force call site through UNLINKED -> MONOMORPHIC -> POLYMORPHIC (depth 4) -> MEGAMORPHIC
    for (Object instance : cachedInstances) {
      megamorphicCallSite.invoke(instance);
    }

    // Standalone BoundedWeakClassCache with the same 16 classes populated
    standaloneCache = new BoundedWeakClassCache<>(1024);
    for (Class<?> clazz : cachedClasses) {
      AccessLink link = linker.link(clazz, key);
      standaloneCache.put(clazz, link);
    }

    uncachedClass = UncachedTypeA.class;
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    megamorphicCallSite = null;
    standaloneCache = null;
  }

  @Benchmark
  public Object megamorphicCallSiteHit(MegaState state) throws Throwable {
    return megamorphicCallSite.invoke(state.nextInstance(cachedInstances));
  }

  @Benchmark
  public AccessLink standaloneCacheHit(MegaState state) {
    return standaloneCache.get(state.nextClass(cachedClasses));
  }

  @Benchmark
  public AccessLink standaloneCacheMiss() {
    return standaloneCache.get(uncachedClass);
  }
}
