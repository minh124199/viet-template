package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.LinkerStatistics;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
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
 * Measures dynamic property access via {@link DynamicCallSite} and {@link DynamicLinker}:
 *
 * <ul>
 *   <li>Workload B09: Monomorphic steady-state property access (single receiver shape)
 *   <li>Polymorphic PIC property access across 2, 3, and 4 distinct receiver types (Workload B10)
 *       scanning the contiguous {@code AccessLink[]} array.
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
public class CallSiteBenchmark {

  public record TypeA(String value) {}

  public record TypeB(String value) {}

  public record TypeC(String value) {}

  public record TypeD(String value) {}

  private DynamicCallSite monoSite;
  private DynamicCallSite poly2Site;
  private DynamicCallSite poly3Site;
  private DynamicCallSite poly4Site;

  private TypeA instanceA;
  private TypeB instanceB;
  private TypeC instanceC;
  private TypeD instanceD;

  private Object[] instances2;
  private Object[] instances3;
  private Object[] instances4;

  @State(Scope.Thread)
  public static class PicState {
    private int index2 = 0;
    private int index3 = 0;
    private int index4 = 0;

    public Object nextPoly2(Object[] instances) {
      Object obj = instances[index2];
      index2 = (index2 + 1) % 2;
      return obj;
    }

    public Object nextPoly3(Object[] instances) {
      Object obj = instances[index3];
      index3 = (index3 + 1) % 3;
      return obj;
    }

    public Object nextPoly4(Object[] instances) {
      Object obj = instances[index4];
      index4 = (index4 + 1) % 4;
      return obj;
    }
  }

  @Setup(Level.Trial)
  public void setUp() throws Throwable {
    DynamicLinker linker = new DynamicLinker(LinkerAccessPolicy.standard());
    MemberKey key = MemberKey.propertyGet("value");

    instanceA = new TypeA("Alpha");
    instanceB = new TypeB("Beta");
    instanceC = new TypeC("Gamma");
    instanceD = new TypeD("Delta");

    instances2 = new Object[] {instanceA, instanceB};
    instances3 = new Object[] {instanceA, instanceB, instanceC};
    instances4 = new Object[] {instanceA, instanceB, instanceC, instanceD};

    // 1. Monomorphic Site (State = MONOMORPHIC)
    monoSite =
        new DynamicCallSite(1, key, LinkerAccessPolicy.standard(), linker, new LinkerStatistics());
    monoSite.invoke(instanceA);

    // 2. Polymorphic Site - 2 shapes (State = POLYMORPHIC)
    poly2Site =
        new DynamicCallSite(2, key, LinkerAccessPolicy.standard(), linker, new LinkerStatistics());
    poly2Site.invoke(instanceA);
    poly2Site.invoke(instanceB);

    // 3. Polymorphic Site - 3 shapes (State = POLYMORPHIC)
    poly3Site =
        new DynamicCallSite(3, key, LinkerAccessPolicy.standard(), linker, new LinkerStatistics());
    poly3Site.invoke(instanceA);
    poly3Site.invoke(instanceB);
    poly3Site.invoke(instanceC);

    // 4. Polymorphic Site - 4 shapes (State = POLYMORPHIC, max PIC depth)
    poly4Site =
        new DynamicCallSite(4, key, LinkerAccessPolicy.standard(), linker, new LinkerStatistics());
    poly4Site.invoke(instanceA);
    poly4Site.invoke(instanceB);
    poly4Site.invoke(instanceC);
    poly4Site.invoke(instanceD);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    monoSite = null;
    poly2Site = null;
    poly3Site = null;
    poly4Site = null;
  }

  @Benchmark
  public Object b09_monomorphicAccess() throws Throwable {
    return monoSite.invoke(instanceA);
  }

  @Benchmark
  public Object polymorphic2Access(PicState state) throws Throwable {
    return poly2Site.invoke(state.nextPoly2(instances2));
  }

  @Benchmark
  public Object polymorphic3Access(PicState state) throws Throwable {
    return poly3Site.invoke(state.nextPoly3(instances3));
  }

  @Benchmark
  public Object b10_polymorphic4Access(PicState state) throws Throwable {
    return poly4Site.invoke(state.nextPoly4(instances4));
  }
}
