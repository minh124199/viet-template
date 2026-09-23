package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
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
 * Separates retained-template execution, warmed engine lookup, and full request rendering costs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class EngineSteadyStateBenchmark {

  private static final TemplateId TEMPLATE_ID = TemplateId.of("engine/steady-state.vm");
  private static final TemplateId PRECOMPILED_ID = TemplateId.of("benchmark/precompiled.vm");

  @State(Scope.Benchmark)
  public static class EngineState {
    @Param({"IR", "AOT_BYTECODE", "AST"})
    String tier;

    VtlTemplateEngine engine;
    Template retainedTemplate;
    RenderContext context;
    RenderRequest request;

    @Setup(Level.Trial)
    public void setUp() {
      InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
      repository.put(TEMPLATE_ID.value(), "Hello $user, you have $count messages.");
      engine =
          VtlTemplateEngine.builder()
              .repository(repository)
              .executionTier(ExecutionTier.valueOf(tier))
              .build();
      context = RenderContext.builder().put("user", "Viet").put("count", 7).build();
      request = RenderRequest.of(TEMPLATE_ID, context);
      retainedTemplate = engine.get(TEMPLATE_ID);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      engine.close();
    }
  }

  @State(Scope.Benchmark)
  public static class PureAotState {
    VtlTemplateEngine engine;
    RenderRequest request;

    @Setup(Level.Trial)
    public void setUp() {
      engine =
          VtlTemplateEngine.builder()
              .repository(InMemoryTemplateRepository.create())
              .rejectRuntimeCompilation(true)
              .build();
      request = RenderRequest.of(PRECOMPILED_ID, RenderContext.empty());
      engine.get(PRECOMPILED_ID);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      engine.close();
    }
  }

  @State(Scope.Thread)
  public static class OutputState {
    @Param({"REALISTIC", "RETAINED"})
    String mode;

    private StringTemplateOutput retained;

    @Setup(Level.Trial)
    public void setUp() {
      retained = new StringTemplateOutput(128);
    }

    StringTemplateOutput acquire() {
      return "RETAINED".equals(mode) ? retained : new StringTemplateOutput(128);
    }

    void consume(StringTemplateOutput output, Blackhole blackhole) {
      blackhole.consume(output.length());
      if (output == retained) {
        retained.reset();
      }
    }
  }

  @Benchmark
  public void warmedEngineGet(EngineState state, Blackhole blackhole) {
    blackhole.consume(state.engine.get(TEMPLATE_ID));
  }

  @Benchmark
  public void pureAotWarmedEngineGet(PureAotState state, Blackhole blackhole) {
    blackhole.consume(state.engine.get(PRECOMPILED_ID));
  }

  @Benchmark
  public void retainedTemplateRender(EngineState state, OutputState outputs, Blackhole blackhole)
      throws IOException {
    StringTemplateOutput output = outputs.acquire();
    state.retainedTemplate.render(state.context, output);
    outputs.consume(output, blackhole);
  }

  @Benchmark
  public void engineGetThenRender(EngineState state, OutputState outputs, Blackhole blackhole)
      throws IOException {
    StringTemplateOutput output = outputs.acquire();
    state.engine.get(TEMPLATE_ID).render(state.context, output);
    outputs.consume(output, blackhole);
  }

  @Benchmark
  public void engineRenderRequest(EngineState state, OutputState outputs, Blackhole blackhole)
      throws IOException {
    StringTemplateOutput output = outputs.acquire();
    state.engine.render(state.request, output);
    outputs.consume(output, blackhole);
  }

  @Benchmark
  public void pureAotEngineRender(PureAotState state, OutputState outputs, Blackhole blackhole)
      throws IOException {
    StringTemplateOutput output = outputs.acquire();
    state.engine.render(state.request, output);
    outputs.consume(output, blackhole);
  }

  /** Stateless fixture discovered through the same classpath AOT index used by applications. */
  public static final class PrecompiledFixture implements CompiledTemplate {
    public PrecompiledFixture() {}

    @Override
    public TemplateDescriptor descriptor() {
      return TemplateDescriptor.of(PRECOMPILED_ID, ExecutionTier.AOT_BYTECODE.name());
    }

    @Override
    public void render(RenderContext context, TemplateOutput output) throws IOException {
      output.write("Precompiled benchmark output");
    }
  }
}
