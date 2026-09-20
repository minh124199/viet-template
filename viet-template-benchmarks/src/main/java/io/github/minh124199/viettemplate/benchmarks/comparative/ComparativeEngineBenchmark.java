package io.github.minh124199.viettemplate.benchmarks.comparative;

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
 * Standard JMH benchmark suite comparing Viet Template against leading Java template engines
 * (Apache Velocity 2.4.1, Quarkus Qute 3.39.4, jte 3.2.4, Thymeleaf 3.1.5.RELEASE) across canonical
 * workloads C01–C08.
 *
 * <p>Evaluation Tracks:
 *
 * <ul>
 *   <li>Track A (Dynamic / Interpreted): {@code Viet-IR}, {@code Velocity}, {@code Thymeleaf}
 *   <li>Track B (Compiled / Bytecode): {@code Viet-AOT}, {@code jte}, {@code Qute}
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
public class ComparativeEngineBenchmark {

  @Param({"Viet-IR", "Viet-AOT", "Velocity", "Qute", "jte", "Thymeleaf"})
  private String engine;

  private BenchmarkEngineAdapter adapter;
  private Object c01Model;
  private Object c02Model;
  private Object c03Model;
  private Object c04Model;
  private Object c05Model;
  private Object c06Model;
  private Object c07Model;
  private Object c08Model;

  @Setup(Level.Trial)
  public void setUp() {
    adapter =
        switch (engine) {
          case "Viet-IR" -> new VietIrAdapter();
          case "Viet-AOT" -> new VietAotAdapter();
          case "Velocity" -> new VelocityAdapter();
          case "Qute" -> new QuteAdapter();
          case "jte" -> new JteAdapter();
          case "Thymeleaf" -> new ThymeleafAdapter();
          default -> throw new IllegalArgumentException("Unknown engine: " + engine);
        };
    adapter.setup();

    c01Model = ComparativeWorkloads.getModel(ComparativeWorkloads.C01);
    c02Model = ComparativeWorkloads.getModel(ComparativeWorkloads.C02);
    c03Model = ComparativeWorkloads.getModel(ComparativeWorkloads.C03);
    c04Model = ComparativeWorkloads.getModel(ComparativeWorkloads.C04);
    c05Model = ComparativeWorkloads.getModel(ComparativeWorkloads.C05);
    c06Model = ComparativeWorkloads.getModel(ComparativeWorkloads.C06);
    c07Model = ComparativeWorkloads.getModel(ComparativeWorkloads.C07);
    c08Model = ComparativeWorkloads.getModel(ComparativeWorkloads.C08);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (adapter != null) {
      adapter.close();
    }
  }

  @Benchmark
  public void c01_staticHtml(Blackhole bh) {
    bh.consume(adapter.render(ComparativeWorkloads.C01, c01Model));
  }

  @Benchmark
  public void c02_scalarVariables(Blackhole bh) {
    bh.consume(adapter.render(ComparativeWorkloads.C02, c02Model));
  }

  @Benchmark
  public void c03_deepPropertyChains(Blackhole bh) {
    bh.consume(adapter.render(ComparativeWorkloads.C03, c03Model));
  }

  @Benchmark
  public void c04_conditionals(Blackhole bh) {
    bh.consume(adapter.render(ComparativeWorkloads.C04, c04Model));
  }

  @Benchmark
  public void c05_smallTableForeach(Blackhole bh) {
    bh.consume(adapter.render(ComparativeWorkloads.C05, c05Model));
  }

  @Benchmark
  public void c06_largeTableForeach(Blackhole bh) {
    bh.consume(adapter.render(ComparativeWorkloads.C06, c06Model));
  }

  @Benchmark
  public void c07_nestedForeach(Blackhole bh) {
    bh.consume(adapter.render(ComparativeWorkloads.C07, c07Model));
  }

  @Benchmark
  public void c08_htmlEscaping(Blackhole bh) {
    bh.consume(adapter.render(ComparativeWorkloads.C08, c08Model));
  }
}
