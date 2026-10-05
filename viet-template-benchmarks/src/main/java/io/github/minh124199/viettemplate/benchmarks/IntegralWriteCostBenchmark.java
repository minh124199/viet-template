package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class IntegralWriteCostBenchmark {

  private static final String TEMPLATE_ID_STR = "template.vtl";
  private static final int RAW_ORDINAL = IrEscapeMode.RAW.ordinal();
  private static final int NULL_MODE_ORDINAL = NullRenderMode.EMPTY_STRING.ordinal();
  private static final LinkerAccessPolicy POLICY = LinkerAccessPolicy.standard();

  @Param({"0", "1", "42", "-1", "1000", "123456789", "MIN_VALUE", "MAX_VALUE"})
  private String intScenario;

  private int primitiveIntValue;
  private Integer preboxedIntValue;

  private CountingTemplateOutput countingOutput;
  private StringTemplateOutput stringOutput;

  @Setup(Level.Trial)
  public void setUp() {
    countingOutput = new CountingTemplateOutput();
    stringOutput = new StringTemplateOutput(8192);

    primitiveIntValue =
        switch (intScenario) {
          case "0" -> 0;
          case "1" -> 1;
          case "42" -> 42;
          case "-1" -> -1;
          case "1000" -> 1000;
          case "123456789" -> 123456789;
          case "MIN_VALUE" -> Integer.MIN_VALUE;
          case "MAX_VALUE" -> Integer.MAX_VALUE;
          default -> throw new IllegalArgumentException(intScenario);
        };
    preboxedIntValue = Integer.valueOf(primitiveIntValue);
  }

  @Benchmark
  public void direct_writeInt(Blackhole bh) throws IOException {
    countingOutput.writeInt(primitiveIntValue);
    bh.consume(countingOutput.count);
  }

  @Benchmark
  public void boxing_plus_generic_writeValue(Blackhole bh) throws IOException {
    // Exactly what current AOT bytecode generates for $user.age
    Integer boxed = Integer.valueOf(primitiveIntValue);
    BytecodeRuntimeBridge.writeValue(
        boxed,
        countingOutput,
        RAW_ORDINAL,
        NULL_MODE_ORDINAL,
        "$age",
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
  public void preboxed_generic_writeValue(Blackhole bh) throws IOException {
    // What current AOT bytecode generates for root parameter $age when already an Object
    BytecodeRuntimeBridge.writeValue(
        preboxedIntValue,
        countingOutput,
        RAW_ORDINAL,
        NULL_MODE_ORDINAL,
        "$age",
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
  public void writeInteger_candidate(Blackhole bh) throws IOException {
    Integer boxed = Integer.valueOf(primitiveIntValue);
    if (boxed != null) {
      countingOutput.writeInt(boxed.intValue());
    }
    bh.consume(countingOutput.count);
  }
}
