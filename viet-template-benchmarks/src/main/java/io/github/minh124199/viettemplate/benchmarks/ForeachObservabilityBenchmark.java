package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Isolates the allocation cost of observable versus unobservable {@code $foreach} state. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class ForeachObservabilityBenchmark {

  @Param({"IR", "AOT_BYTECODE"})
  private String tier;

  @Param({"NONE", "INDEX"})
  private String metadata;

  @Param({"1", "10", "100", "1000"})
  private int size;

  private VtlTemplateEngine engine;
  private Template template;
  private RenderContext context;
  private StringTemplateOutput output;

  @Setup(Level.Trial)
  public void setUp() {
    String body = metadata.equals("NONE") ? "$item" : "$item:$foreach.index";
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    repository.put("foreach-observability.vm", "#foreach($item in $items)" + body + "#end");
    engine =
        VtlTemplateEngine.builder()
            .repository(repository)
            .executionTier(ExecutionTier.valueOf(tier))
            .build();
    template = engine.get("foreach-observability.vm");
    List<Integer> values = new ArrayList<>(size);
    for (int index = 0; index < size; index++) {
      values.add(index);
    }
    context = RenderContext.builder().put("items", values).build();
    output = new StringTemplateOutput(Math.max(16, size * 8));
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    engine.close();
  }

  @Benchmark
  public void retainedTemplateRender(Blackhole blackhole) throws IOException {
    template.render(context, output);
    blackhole.consume(output.length());
    output.reset();
  }
}
