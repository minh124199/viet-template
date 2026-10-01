package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.UndefinedReferencePolicy;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.runtime.EscapeMode;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.EvaluationValue;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
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
 * 3-Way Paired benchmark comparing:
 *
 * <ul>
 *   <li>Variant A (Pre-Phase 15): Eager metadata (TemplateId + SourceSpan) + repeated .values()
 *       calls (~120 B/write)
 *   <li>Variant B (Phase 15): Lazy metadata, but repeated NullRenderMode.values() and
 *       IrEscapeMode.values() calls (~64 B/write)
 *   <li>Variant C (Phase 16): Optimized with cached enum arrays (0 B/write)
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class WriteValuePairedBenchmark {

  private static final String TEMPLATE_ID_STR = "templates/user-card.vtl";
  private static final int ESCAPE_MODE_ORDINAL = IrEscapeMode.HTML_TEXT.ordinal();
  private static final int NULL_MODE_ORDINAL = NullRenderMode.EMPTY_STRING.ordinal();
  private static final String LITERAL = "$user.name";
  private static final UndefinedReferencePolicy POLICY = UndefinedReferencePolicy.SILENT;
  private static final LinkerAccessPolicy SECURITY_POLICY = LinkerAccessPolicy.standard();

  @Param({"1", "4", "16", "64", "256", "1024"})
  private int writeCount;

  private CountingTemplateOutput countingOutput;
  private StringTemplateOutput stringOutput;
  private String[] values;
  private int[][] spans;

  @Setup(Level.Trial)
  public void setUp() {
    countingOutput = new CountingTemplateOutput();
    stringOutput = new StringTemplateOutput(8192);
    values = new String[writeCount];
    spans = new int[writeCount][4];
    for (int i = 0; i < writeCount; i++) {
      values[i] = "User Name " + i;
      spans[i] = new int[] {i + 1, 1, i + 1, 20};
    }
  }

  @Setup(Level.Invocation)
  public void resetOutput() {
    stringOutput.reset();
  }

  // Pre-Phase 15 implementation: Eagerly builds TemplateId and SourceSpan at start of writeValue +
  // repeated .values()
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private static void writeValueVariantA(
      Object val,
      TemplateOutput output,
      int escapeModeOrdinal,
      int nullModeOrdinal,
      String literal,
      UndefinedReferencePolicy policy,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol,
      LinkerAccessPolicy securityPolicy)
      throws IOException {
    TemplateId templateId = TemplateId.of(templateIdStr);
    SourceSpan span = makeSpan(startLine, startCol, endLine, endCol);
    NullRenderMode nullMode = NullRenderMode.values()[nullModeOrdinal];
    IrEscapeMode irMode = IrEscapeMode.values()[escapeModeOrdinal];
    EscapeMode escapeMode =
        switch (irMode) {
          case RAW -> EscapeMode.RAW;
          case HTML_TEXT -> EscapeMode.HTML_TEXT;
          case HTML_ATTRIBUTE_QUOTED -> EscapeMode.HTML_ATTRIBUTE_QUOTED;
          case URL_COMPONENT -> EscapeMode.URL_COMPONENT;
        };

    Object unwrapped = (val instanceof EvaluationValue ev) ? ev.value() : val;
    boolean isNullOrUndef =
        (val == null) || (val instanceof EvaluationValue ev && (ev.isNull() || ev.isUndefined()));

    if (nullMode == NullRenderMode.EMPTY_STRING) {
      if (!isNullOrUndef) {
        BytecodeRuntimeBridge.renderEscaped(
            unwrapped, output, escapeMode, securityPolicy, templateId, span);
      }
      return;
    }

    if (isNullOrUndef) {
      if (literal != null && !literal.isEmpty()) {
        output.write(literal);
      }
    } else {
      BytecodeRuntimeBridge.renderEscaped(
          unwrapped, output, escapeMode, securityPolicy, templateId, span);
    }
  }

  // Phase 15 implementation: Lazy metadata, but repeated NullRenderMode.values() and
  // IrEscapeMode.values()
  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private static void writeValueVariantB(
      Object val,
      TemplateOutput output,
      int escapeModeOrdinal,
      int nullModeOrdinal,
      String literal,
      UndefinedReferencePolicy policy,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol,
      LinkerAccessPolicy securityPolicy)
      throws IOException {
    NullRenderMode nullMode = NullRenderMode.values()[nullModeOrdinal];
    IrEscapeMode irMode = IrEscapeMode.values()[escapeModeOrdinal];
    EscapeMode escapeMode =
        switch (irMode) {
          case RAW -> EscapeMode.RAW;
          case HTML_TEXT -> EscapeMode.HTML_TEXT;
          case HTML_ATTRIBUTE_QUOTED -> EscapeMode.HTML_ATTRIBUTE_QUOTED;
          case URL_COMPONENT -> EscapeMode.URL_COMPONENT;
        };

    Object unwrapped = (val instanceof EvaluationValue ev) ? ev.value() : val;
    boolean isNullOrUndef =
        (val == null) || (val instanceof EvaluationValue ev && (ev.isNull() || ev.isUndefined()));

    if (nullMode == NullRenderMode.EMPTY_STRING) {
      if (!isNullOrUndef) {
        BytecodeRuntimeBridge.renderEscaped(
            unwrapped, output, escapeMode, securityPolicy, null, null);
      }
      return;
    }

    if (isNullOrUndef) {
      if (literal != null && !literal.isEmpty()) {
        output.write(literal);
      }
    } else {
      BytecodeRuntimeBridge.renderEscaped(
          unwrapped, output, escapeMode, securityPolicy, null, null);
    }
  }

  private static SourceSpan makeSpan(int startLine, int startCol, int endLine, int endCol) {
    if (startLine < 1 || startCol < 1 || endLine < 1 || endCol < 1) {
      return SourceSpan.UNKNOWN;
    }
    if (startLine > endLine || (startLine == endLine && startCol > endCol)) {
      return SourceSpan.UNKNOWN;
    }
    return SourceSpan.of(0, 0, startLine, startCol, endLine, endCol);
  }

  // === CountingOutput Benchmarks ===

  @Benchmark
  public void variantA_prePhase15_eager_countingOutput(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      writeValueVariantA(
          values[i],
          countingOutput,
          ESCAPE_MODE_ORDINAL,
          NULL_MODE_ORDINAL,
          LITERAL,
          POLICY,
          TEMPLATE_ID_STR,
          spans[i][0],
          spans[i][1],
          spans[i][2],
          spans[i][3],
          SECURITY_POLICY);
    }
    bh.consume(countingOutput);
  }

  @Benchmark
  public void variantB_phase15_reconstructed_countingOutput(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      writeValueVariantB(
          values[i],
          countingOutput,
          ESCAPE_MODE_ORDINAL,
          NULL_MODE_ORDINAL,
          LITERAL,
          POLICY,
          TEMPLATE_ID_STR,
          spans[i][0],
          spans[i][1],
          spans[i][2],
          spans[i][3],
          SECURITY_POLICY);
    }
    bh.consume(countingOutput);
  }

  @Benchmark
  public void variantC_phase16_optimized_countingOutput(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      BytecodeRuntimeBridge.writeValue(
          values[i],
          countingOutput,
          ESCAPE_MODE_ORDINAL,
          NULL_MODE_ORDINAL,
          LITERAL,
          POLICY,
          TEMPLATE_ID_STR,
          spans[i][0],
          spans[i][1],
          spans[i][2],
          spans[i][3],
          SECURITY_POLICY);
    }
    bh.consume(countingOutput);
  }

  // === StringTemplateOutput Benchmarks (sink isolation) ===

  @Benchmark
  public void variantA_prePhase15_eager_stringOutput(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      writeValueVariantA(
          values[i],
          stringOutput,
          ESCAPE_MODE_ORDINAL,
          NULL_MODE_ORDINAL,
          LITERAL,
          POLICY,
          TEMPLATE_ID_STR,
          spans[i][0],
          spans[i][1],
          spans[i][2],
          spans[i][3],
          SECURITY_POLICY);
    }
    bh.consume(stringOutput.length());
  }

  @Benchmark
  public void variantB_phase15_reconstructed_stringOutput(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      writeValueVariantB(
          values[i],
          stringOutput,
          ESCAPE_MODE_ORDINAL,
          NULL_MODE_ORDINAL,
          LITERAL,
          POLICY,
          TEMPLATE_ID_STR,
          spans[i][0],
          spans[i][1],
          spans[i][2],
          spans[i][3],
          SECURITY_POLICY);
    }
    bh.consume(stringOutput.length());
  }

  @Benchmark
  public void variantC_phase16_optimized_stringOutput(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      BytecodeRuntimeBridge.writeValue(
          values[i],
          stringOutput,
          ESCAPE_MODE_ORDINAL,
          NULL_MODE_ORDINAL,
          LITERAL,
          POLICY,
          TEMPLATE_ID_STR,
          spans[i][0],
          spans[i][1],
          spans[i][2],
          spans[i][3],
          SECURITY_POLICY);
    }
    bh.consume(stringOutput.length());
  }
}
