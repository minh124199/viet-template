package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.StandardEscapers;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
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

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 2,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class WriteValueCostLadderBenchmark {

  private static final String TEMPLATE_ID_STR = "template.vtl";
  private static final int RAW_ORDINAL = IrEscapeMode.RAW.ordinal();
  private static final int HTML_ORDINAL = IrEscapeMode.HTML_TEXT.ordinal();
  private static final int NULL_MODE_ORDINAL = NullRenderMode.EMPTY_STRING.ordinal();
  private static final LinkerAccessPolicy POLICY = LinkerAccessPolicy.standard();

  private CountingTemplateOutput countingOutput;
  private StringTemplateOutput stringOutput;

  private String shortCleanString;
  private String escapedString;
  private SafeHtml safeHtml;

  private int primitiveInt;
  private Integer boxedInt;

  private long primitiveLong;
  private Long boxedLong;

  private double primitiveDouble;
  private Double boxedDouble;

  private boolean primitiveBoolean;
  private Boolean boxedBoolean;

  @Setup(Level.Trial)
  public void setUp() {
    countingOutput = new CountingTemplateOutput();
    stringOutput = new StringTemplateOutput(8192);

    shortCleanString = "VietTemplate";
    escapedString = "Hello & <world> \"quoted\" 'single'";
    safeHtml = SafeHtml.of("<b>Safe HTML</b>");

    primitiveInt = 42;
    boxedInt = 42;

    primitiveLong = 1234567890123L;
    boxedLong = 1234567890123L;

    primitiveDouble = 3.14159;
    boxedDouble = 3.14159;

    primitiveBoolean = true;
    boxedBoolean = true;
  }

  @Setup(Level.Invocation)
  public void resetOutput() {
    stringOutput.reset();
  }

  // --- String (Clean, RAW) ---
  @Benchmark
  public void string_clean_direct(Blackhole bh) throws IOException {
    countingOutput.write(shortCleanString);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void string_clean_generic(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeValue(
        shortCleanString,
        countingOutput,
        RAW_ORDINAL,
        NULL_MODE_ORDINAL,
        "$s",
        false,
        TEMPLATE_ID_STR,
        1,
        1,
        1,
        2,
        POLICY);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void string_clean_specialized(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeString(
        shortCleanString, countingOutput, RAW_ORDINAL, NULL_MODE_ORDINAL, "$s");
    bh.consume(countingOutput.count);
  }

  // --- String (Clean, HTML_TEXT) ---
  @Benchmark
  public void string_htmlClean_direct(Blackhole bh) throws IOException {
    StandardEscapers.htmlText().escape(shortCleanString, countingOutput);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void string_htmlClean_generic(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeValue(
        shortCleanString,
        countingOutput,
        HTML_ORDINAL,
        NULL_MODE_ORDINAL,
        "$s",
        false,
        TEMPLATE_ID_STR,
        1,
        1,
        1,
        2,
        POLICY);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void string_htmlClean_specialized(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeString(
        shortCleanString, countingOutput, HTML_ORDINAL, NULL_MODE_ORDINAL, "$s");
    bh.consume(countingOutput.count);
  }

  // --- String (Escaped, HTML_TEXT) ---
  @Benchmark
  public void string_escaped_direct(Blackhole bh) throws IOException {
    StandardEscapers.htmlText().escape(escapedString, countingOutput);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void string_escaped_generic(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeValue(
        escapedString,
        countingOutput,
        HTML_ORDINAL,
        NULL_MODE_ORDINAL,
        "$s",
        false,
        TEMPLATE_ID_STR,
        1,
        1,
        1,
        2,
        POLICY);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void string_escaped_specialized(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeString(
        escapedString, countingOutput, HTML_ORDINAL, NULL_MODE_ORDINAL, "$s");
    bh.consume(countingOutput.count);
  }

  // --- Primitive int ---
  @Benchmark
  public void int_direct(Blackhole bh) throws IOException {
    countingOutput.writeInt(primitiveInt);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void int_generic(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeValue(
        boxedInt,
        countingOutput,
        RAW_ORDINAL,
        NULL_MODE_ORDINAL,
        "$i",
        false,
        TEMPLATE_ID_STR,
        1,
        1,
        1,
        2,
        POLICY);
    bh.consume(countingOutput.count);
  }

  // --- Primitive long ---
  @Benchmark
  public void long_direct(Blackhole bh) throws IOException {
    countingOutput.writeLong(primitiveLong);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void long_generic(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeValue(
        boxedLong,
        countingOutput,
        RAW_ORDINAL,
        NULL_MODE_ORDINAL,
        "$l",
        false,
        TEMPLATE_ID_STR,
        1,
        1,
        1,
        2,
        POLICY);
    bh.consume(countingOutput.count);
  }

  // --- Primitive double ---
  @Benchmark
  public void double_direct(Blackhole bh) throws IOException {
    countingOutput.writeDouble(primitiveDouble);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void double_generic(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeValue(
        boxedDouble,
        countingOutput,
        RAW_ORDINAL,
        NULL_MODE_ORDINAL,
        "$d",
        false,
        TEMPLATE_ID_STR,
        1,
        1,
        1,
        2,
        POLICY);
    bh.consume(countingOutput.count);
  }

  // --- Primitive boolean ---
  @Benchmark
  public void boolean_direct(Blackhole bh) throws IOException {
    countingOutput.writeBoolean(primitiveBoolean);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void boolean_generic(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeValue(
        boxedBoolean,
        countingOutput,
        RAW_ORDINAL,
        NULL_MODE_ORDINAL,
        "$b",
        false,
        TEMPLATE_ID_STR,
        1,
        1,
        1,
        2,
        POLICY);
    bh.consume(countingOutput.count);
  }

  // --- SafeHtml ---
  @Benchmark
  public void safeHtml_direct(Blackhole bh) throws IOException {
    countingOutput.write(safeHtml.content());
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void safeHtml_generic(Blackhole bh) throws IOException {
    BytecodeRuntimeBridge.writeValue(
        safeHtml,
        countingOutput,
        HTML_ORDINAL,
        NULL_MODE_ORDINAL,
        "$h",
        false,
        TEMPLATE_ID_STR,
        1,
        1,
        1,
        2,
        POLICY);
    bh.consume(countingOutput.count);
  }
}
