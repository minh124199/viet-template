package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SlottedRenderContext;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures the throughput difference between name-based RenderContext and slot-indexed
 * SlottedRenderContext for AOT-compiled templates under varying parameter counts.
 *
 * <p>This benchmark isolates the seeding prologue overhead: the cost of extracting parameter values
 * from the context and loading them into JVM local slots at the beginning of each render() call.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class SlottedSeedingBenchmark {

  @Param({"1", "2", "4", "8", "16", "32", "64"})
  private int paramCount;

  private CompiledTemplate compiled;
  private RenderContext nameBasedCtx;
  private SlottedRenderContext slottedCtx;
  private String[] keys;
  private Object[] values;
  private BlackholeOutput output;
  private long invocationCounter;

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

  @Setup(Level.Trial)
  public void setUp() {
    output = new BlackholeOutput();
    invocationCounter = 0;

    // Build template with paramCount parameters
    StringBuilder templateSb = new StringBuilder();
    TemplateContract.Builder contractBuilder =
        TemplateContract.builder(TemplateId.of("bench_slotted_" + paramCount + ".vtl"));
    keys = new String[paramCount];
    values = new Object[paramCount];

    for (int i = 0; i < paramCount; i++) {
      String name = "p" + i;
      keys[i] = name;
      values[i] = "value" + i;
      contractBuilder.parameter(name, String.class);
      if (i > 0) templateSb.append(' ');
      templateSb.append('$').append(name);
    }

    TemplateContract contract = contractBuilder.build();
    String templateText = templateSb.toString();

    compiled = compile("bench_slotted_" + paramCount + ".vtl", templateText, contract);
    nameBasedCtx = RenderContext.of(keys, values);
    slottedCtx = RenderContext.slotted(keys, values);

    // Verify semantic equivalence
    try {
      io.github.minh124199.viettemplate.runtime.StringTemplateOutput outName =
          new io.github.minh124199.viettemplate.runtime.StringTemplateOutput();
      io.github.minh124199.viettemplate.runtime.StringTemplateOutput outSlot =
          new io.github.minh124199.viettemplate.runtime.StringTemplateOutput();
      compiled.render(nameBasedCtx, outName);
      compiled.render(slottedCtx, outSlot);
      if (!outName.toString().equals(outSlot.toString())) {
        throw new IllegalStateException(
            "Slotted/name-based divergence: '" + outName + "' vs '" + outSlot + "'");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // --- Scenario A: Render Only (context pre-created) ---

  @Benchmark
  public void renderNameBased(Blackhole bh) throws IOException {
    compiled.render(nameBasedCtx, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void renderSlotted(Blackhole bh) throws IOException {
    compiled.render(slottedCtx, output);
    bh.consume(output.count);
  }

  // --- Scenario B: Context Creation + Parameter Seeding + Render ---

  @Benchmark
  public void createAndRenderNameBased(Blackhole bh) throws IOException {
    RenderContext ctx = RenderContext.of(keys, values);
    compiled.render(ctx, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void createAndRenderSlotted(Blackhole bh) throws IOException {
    RenderContext ctx = RenderContext.slotted(keys, values);
    compiled.render(ctx, output);
    bh.consume(output.count);
  }

  // --- Phase 6: instanceof Dual-Path Mixed Workloads ---

  @Benchmark
  public void renderMixed50_50(Blackhole bh) throws IOException {
    long idx = invocationCounter++;
    RenderContext ctx = ((idx & 1) == 0) ? slottedCtx : nameBasedCtx;
    compiled.render(ctx, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void renderMixed90_10(Blackhole bh) throws IOException {
    long idx = invocationCounter++;
    RenderContext ctx = ((idx % 10) != 0) ? slottedCtx : nameBasedCtx;
    compiled.render(ctx, output);
    bh.consume(output.count);
  }

  @Benchmark
  public void renderMixed10_90(Blackhole bh) throws IOException {
    long idx = invocationCounter++;
    RenderContext ctx = ((idx % 10) == 0) ? slottedCtx : nameBasedCtx;
    compiled.render(ctx, output);
    bh.consume(output.count);
  }

  private static CompiledTemplate compile(
      String idStr, String templateText, TemplateContract contract) {
    SourceText source = SourceText.of(idStr, templateText);
    var parseResult = VtlParser.parse(source);
    VtlSemanticOptions semOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(ModelSchema.fromContract(contract))
            .build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, BackendOptions.defaults());
    if (!result.isSuccess() || result.templateInstance() == null) {
      throw new IllegalStateException("Compilation failed: " + result.diagnostics());
    }
    return result.templateInstance();
  }
}
