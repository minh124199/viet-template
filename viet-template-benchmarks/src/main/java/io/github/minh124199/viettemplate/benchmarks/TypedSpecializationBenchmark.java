package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import java.io.IOException;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Milestone M25 Dedicated JMH Benchmark Suite: Measures throughput, latency, and allocation across
 * specialized multi-argument method invocations, overload resolutions, guards, single-evaluation
 * semantics, and root-slot ABI vs RenderContext.
 *
 * <p>Workloads: - M25_01: Zero-argument method invocation ($svc.name()) - M25_02: One-argument
 * method invocation ($svc.greet('World')) - M25_03: Two-argument method invocation ($svc.add(10,
 * 20)) - M25_04: Four-argument method invocation ($svc.format('Prefix', 42, true, 3.14)) - M25_05:
 * Eight-argument method invocation ($svc.sum8(1, 2, 3, 4, 5, 6, 7, 8)) - M25_06: Primitive widening
 * method invocation ($svc.compute(10, 2.5)) - M25_07: Overload resolution ($svc.compute(10, 20)) -
 * M25_08: Guard fallback execution (alternating receiver types) - M25_09: Single-evaluation side
 * effect execution - M25_10: Root-slot binding vs RenderContext baseline lookup
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class TypedSpecializationBenchmark {

  public static class BenchmarkService {
    public String name() {
      return "service-0";
    }

    public String greet(String target) {
      return "Hello, " + target + "!";
    }

    public int add(int a, int b) {
      return a + b;
    }

    public String format(String prefix, int count, boolean active, double ratio) {
      return prefix + ":" + count + ":" + active + ":" + ratio;
    }

    public long sum8(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
      return (long) a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8;
    }

    public String compute(int a, double b) {
      return "int,double:" + (a + b);
    }

    public String compute(int a, int b) {
      return "int,int:" + (a + b);
    }

    public String compute(double a, double b) {
      return "double,double:" + (a + b);
    }
  }

  public static class AlternateBenchmarkService {
    public int add(int a, int b) {
      return (a + b) * 100;
    }
  }

  public static class BenchmarkTracker {
    int counter = 0;

    public int nextVal() {
      return ++counter;
    }
  }

  public static final class BlackholeOutput implements TemplateOutput {
    long count = 0;

    @Override
    public void write(CharSequence value) {
      if (value != null) count += value.length();
    }

    @Override
    public void write(char value) {
      count++;
    }

    @Override
    public void writeUtf8(byte[] bytes) {
      if (bytes != null) count += bytes.length;
    }

    @Override
    public void writeUtf8(byte[] bytes, int offset, int length) {
      count += length;
    }

    @Override
    public void writeInt(int value) {
      count += 4;
    }

    @Override
    public void writeLong(long value) {
      count += 8;
    }

    @Override
    public void writeDouble(double value) {
      count += 8;
    }

    @Override
    public void writeBoolean(boolean value) {
      count += 1;
    }
  }

  private BenchmarkService svc;
  private AlternateBenchmarkService alternateSvc;
  private BenchmarkTracker tracker;
  private BlackholeOutput output;

  private CompiledTemplate t01_typed;
  private CompiledTemplate t01_dynamic;
  private RenderContext ctx01;

  private CompiledTemplate t02_typed;
  private CompiledTemplate t02_dynamic;
  private RenderContext ctx02;

  private CompiledTemplate t03_typed;
  private CompiledTemplate t03_dynamic;
  private RenderContext ctx03;

  private CompiledTemplate t04_typed;
  private CompiledTemplate t04_dynamic;
  private RenderContext ctx04;

  private CompiledTemplate t05_typed;
  private CompiledTemplate t05_dynamic;
  private RenderContext ctx05;

  private CompiledTemplate t06_typed;
  private CompiledTemplate t06_dynamic;
  private RenderContext ctx06;

  private CompiledTemplate t07_typed;
  private CompiledTemplate t07_dynamic;
  private RenderContext ctx07;

  private CompiledTemplate t08_typed;
  private RenderContext ctx08_orig;
  private RenderContext ctx08_alt;

  private CompiledTemplate t09_typed;
  private RenderContext ctx09;

  private RenderContext ctx10;

  @Setup(Level.Trial)
  public void setUp() {
    svc = new BenchmarkService();
    alternateSvc = new AlternateBenchmarkService();
    tracker = new BenchmarkTracker();
    output = new BlackholeOutput();

    TemplateContract svcContract =
        TemplateContract.builder(TemplateId.of("dummy.vtl"))
            .parameter("svc", BenchmarkService.class)
            .build();

    // M25_01
    t01_typed = compile("m01_t.vtl", "$svc.name()", svcContract);
    t01_dynamic = compile("m01_d.vtl", "$svc.name()", null);
    ctx01 = RenderContext.of("svc", svc);

    // M25_02
    t02_typed = compile("m02_t.vtl", "$svc.greet('World')", svcContract);
    t02_dynamic = compile("m02_d.vtl", "$svc.greet('World')", null);
    ctx02 = RenderContext.of("svc", svc);

    // M25_03
    t03_typed = compile("m03_t.vtl", "$svc.add(10, 20)", svcContract);
    t03_dynamic = compile("m03_d.vtl", "$svc.add(10, 20)", null);
    ctx03 = RenderContext.of("svc", svc);

    // M25_04
    t04_typed = compile("m04_t.vtl", "$svc.format('Prefix', 42, true, 3.14)", svcContract);
    t04_dynamic = compile("m04_d.vtl", "$svc.format('Prefix', 42, true, 3.14)", null);
    ctx04 = RenderContext.of("svc", svc);

    // M25_05
    t05_typed = compile("m05_t.vtl", "$svc.sum8(1, 2, 3, 4, 5, 6, 7, 8)", svcContract);
    t05_dynamic = compile("m05_d.vtl", "$svc.sum8(1, 2, 3, 4, 5, 6, 7, 8)", null);
    ctx05 = RenderContext.of("svc", svc);

    // M25_06
    t06_typed = compile("m06_t.vtl", "$svc.compute(10, 2.5)", svcContract);
    t06_dynamic = compile("m06_d.vtl", "$svc.compute(10, 2.5)", null);
    ctx06 = RenderContext.of("svc", svc);

    // M25_07
    t07_typed = compile("m07_t.vtl", "$svc.compute(10, 20)", svcContract);
    t07_dynamic = compile("m07_d.vtl", "$svc.compute(10, 20)", null);
    ctx07 = RenderContext.of("svc", svc);

    // M25_08
    t08_typed = compile("m08_t.vtl", "$svc.add(10, 20)", svcContract);
    ctx08_orig = RenderContext.of("svc", svc);
    ctx08_alt = RenderContext.of("svc", alternateSvc);

    // M25_09
    TemplateContract trackerContract =
        TemplateContract.builder(TemplateId.of("dummy_tracker.vtl"))
            .parameter("svc", BenchmarkService.class)
            .parameter("tracker", BenchmarkTracker.class)
            .build();
    t09_typed = compile("m09_t.vtl", "$svc.add($tracker.nextVal(), 10)", trackerContract);
    ctx09 = RenderContext.of("svc", svc, "tracker", tracker);

    // M25_10
    ctx10 = RenderContext.of("svc", svc, "a", 10, "b", 20);
  }

  private static CompiledTemplate compile(
      String idStr, String templateText, TemplateContract contract) {
    SourceText source = SourceText.of(idStr, templateText);
    var parseResult = VtlParser.parse(source);
    VtlSemanticOptions.Builder semBuilder =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).allowArbitraryMethods(true);
    if (contract != null) {
      semBuilder.modelSchema(ModelSchema.fromContract(contract));
    }
    VtlSemanticOptions semanticOptions = semBuilder.build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, BackendOptions.defaults());
    if (!result.isSuccess() || result.templateInstance() == null) {
      throw new IllegalStateException("Compilation failed: " + result.diagnostics());
    }
    return result.templateInstance();
  }

  // --- Workload M25_01: 0-arg method call ---

  @Benchmark
  public void m25_01_zeroArg_typed(Blackhole bh) throws IOException {
    t01_typed.render(ctx01, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_01_zeroArg_dynamic(Blackhole bh) throws IOException {
    t01_dynamic.render(ctx01, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_01_zeroArg_baseline(Blackhole bh) {
    bh.consume(svc.name());
  }

  // --- Workload M25_02: 1-arg method call ---

  @Benchmark
  public void m25_02_oneArg_typed(Blackhole bh) throws IOException {
    t02_typed.render(ctx02, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_02_oneArg_dynamic(Blackhole bh) throws IOException {
    t02_dynamic.render(ctx02, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_02_oneArg_baseline(Blackhole bh) {
    bh.consume(svc.greet("World"));
  }

  // --- Workload M25_03: 2-arg method call ---

  @Benchmark
  public void m25_03_twoArg_typed(Blackhole bh) throws IOException {
    t03_typed.render(ctx03, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_03_twoArg_dynamic(Blackhole bh) throws IOException {
    t03_dynamic.render(ctx03, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_03_twoArg_baseline(Blackhole bh) {
    bh.consume(svc.add(10, 20));
  }

  // --- Workload M25_04: 4-arg method call ---

  @Benchmark
  public void m25_04_fourArg_typed(Blackhole bh) throws IOException {
    t04_typed.render(ctx04, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_04_fourArg_dynamic(Blackhole bh) throws IOException {
    t04_dynamic.render(ctx04, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_04_fourArg_baseline(Blackhole bh) {
    bh.consume(svc.format("Prefix", 42, true, 3.14));
  }

  // --- Workload M25_05: 8-arg method call ---

  @Benchmark
  public void m25_05_eightArg_typed(Blackhole bh) throws IOException {
    t05_typed.render(ctx05, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_05_eightArg_dynamic(Blackhole bh) throws IOException {
    t05_dynamic.render(ctx05, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_05_eightArg_baseline(Blackhole bh) {
    bh.consume(svc.sum8(1, 2, 3, 4, 5, 6, 7, 8));
  }

  // --- Workload M25_06: primitive widening ---

  @Benchmark
  public void m25_06_primitiveWidening_typed(Blackhole bh) throws IOException {
    t06_typed.render(ctx06, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_06_primitiveWidening_dynamic(Blackhole bh) throws IOException {
    t06_dynamic.render(ctx06, output);
    bh.consume(output.count);
  }

  // --- Workload M25_07: overload resolution ---

  @Benchmark
  public void m25_07_overloadResolution_typed(Blackhole bh) throws IOException {
    t07_typed.render(ctx07, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_07_overloadResolution_dynamic(Blackhole bh) throws IOException {
    t07_dynamic.render(ctx07, output);
    bh.consume(output.count);
  }

  // --- Workload M25_08: guard fallback execution ---

  private boolean toggle = false;

  @Benchmark
  public void m25_08_guardFallback(Blackhole bh) throws IOException {
    RenderContext ctx = (toggle = !toggle) ? ctx08_orig : ctx08_alt;
    t08_typed.render(ctx, output);
    bh.consume(output.count);
  }

  // --- Workload M25_09: single-evaluation side effects ---

  @Benchmark
  public void m25_09_singleEvaluationSideEffect(Blackhole bh) throws IOException {
    t09_typed.render(ctx09, output);
    bh.consume(tracker.counter);
  }

  // --- Workload M25_10: Root-slot binding vs RenderContext lookup overhead ---

  @Benchmark
  public void m25_10_renderContext_overhead(Blackhole bh) throws IOException {
    RenderContext dynamicCtx = RenderContext.of("svc", svc, "a", 10, "b", 20);
    t03_typed.render(dynamicCtx, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_10_retainedContext_overhead(Blackhole bh) throws IOException {
    t03_typed.render(ctx10, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void m25_10_directSlot_baseline(Blackhole bh) {
    // Direct slot baseline: simulating invocation where parameters are already in local slots
    int result = svc.add(10, 20);
    bh.consume(result);
  }
}
