package io.github.minh124199.viettemplate.benchmarks.output;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.NumberFormatting;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
 * Benchmark suite for Milestone M19.3b.1 (Zero-Allocation Direct Primitive Number Formatting).
 *
 * <p>Protocol: VT-PERF-M19.3B1-DIRECT-NUMBER-FORMATTING-1
 *
 * <p>Measures pure formatter throughput, pure formatter allocation, and end-to-end primitive
 * rendering impact across supported value classes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class PrimitiveFormattingBenchmark {

  public record TableRow(String col1, int col2, String col3, double col4, boolean col5) {}

  @Param({
    "SINGLE_DIGIT",
    "MEDIUM_POS",
    "MEDIUM_NEG",
    "MAX_MAGNITUDE",
    "MIN_VALUE",
    "MAX_VALUE",
    "RANDOM"
  })
  private String valueClass;

  // Pre-allocated target buffer to isolate formatter-only allocation from caller allocation
  private byte[] targetBuffer;
  private int intValue;
  private long longValue;
  private int[] randomInts;
  private long[] randomLongs;
  private int randomIndex;

  // End-to-end engine and templates
  private VtlTemplateEngine engine;
  private Template tplPrimitiveInt;
  private Template tplPrimitiveLong;
  private Template tplForeachTable;
  private RenderContext ctxPrimitiveInt;
  private RenderContext ctxPrimitiveLong;
  private RenderContext ctxForeachTable;

  private static final byte[] INT_MIN_BYTES = "-2147483648".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] LONG_MIN_BYTES =
      "-9223372036854775808".getBytes(StandardCharsets.US_ASCII);

  @Setup(Level.Trial)
  public void setUpTrial() throws IOException {
    targetBuffer = new byte[64];
    Random rng = new Random(42L);
    randomInts = new int[1024];
    randomLongs = new long[1024];
    for (int i = 0; i < 1024; i++) {
      randomInts[i] = rng.nextInt();
      randomLongs[i] = rng.nextLong();
    }

    switch (valueClass) {
      case "SINGLE_DIGIT" -> {
        intValue = 7;
        longValue = 7L;
      }
      case "MEDIUM_POS" -> {
        intValue = 123456;
        longValue = 123456789L;
      }
      case "MEDIUM_NEG" -> {
        intValue = -123456;
        longValue = -123456789L;
      }
      case "MAX_MAGNITUDE" -> {
        intValue = 1987654321;
        longValue = 9123456789012345678L;
      }
      case "MIN_VALUE" -> {
        intValue = Integer.MIN_VALUE;
        longValue = Long.MIN_VALUE;
      }
      case "MAX_VALUE" -> {
        intValue = Integer.MAX_VALUE;
        longValue = Long.MAX_VALUE;
      }
      case "RANDOM" -> {
        intValue = 42;
        longValue = 42L;
      }
      default -> throw new IllegalArgumentException("Unknown valueClass: " + valueClass);
    }

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "primitive_int.vm", "<div>Counts: $i1, $i2, $i3, $i4, $i5, $i6, $i7, $i8, $i9, $i10</div>");
    repo.put("primitive_long.vm", "<div>Timestamps: $l1, $l2, $l3, $l4, $l5</div>");
    repo.put(
        "foreach_table.vm",
        """
        <table>
        #foreach($r in $rows)
          <tr><td>$r.col1</td><td>$r.col2</td><td>$r.col3</td><td>$r.col4</td><td>$r.col5</td></tr>
        #end
        </table>
        """);

    engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    tplPrimitiveInt = engine.get("primitive_int.vm");
    tplPrimitiveLong = engine.get("primitive_long.vm");
    tplForeachTable = engine.get("foreach_table.vm");

    ctxPrimitiveInt =
        RenderContext.builder()
            .put("i1", 0)
            .put("i2", 42)
            .put("i3", -100)
            .put("i4", 12345)
            .put("i5", 999999)
            .put("i6", -888888)
            .put("i7", 100)
            .put("i8", 7)
            .put("i9", 2026)
            .put("i10", Integer.MAX_VALUE)
            .build();

    ctxPrimitiveLong =
        RenderContext.builder()
            .put("l1", 0L)
            .put("l2", 1700000000000L)
            .put("l3", 987654321098L)
            .put("l4", -5555555555L)
            .put("l5", Long.MAX_VALUE)
            .build();

    List<TableRow> rows = new ArrayList<>(50);
    for (int i = 0; i < 50; i++) {
      rows.add(new TableRow("Product-" + i, i * 10, "SKU-" + (i * 100), i * 1.5, i % 2 == 0));
    }
    ctxForeachTable = RenderContext.builder().put("rows", rows).build();
  }

  @TearDown(Level.Trial)
  public void tearDownTrial() {
    if (engine != null) {
      engine.close();
    }
  }

  private int nextInt() {
    if ("RANDOM".equals(valueClass)) {
      randomIndex = (randomIndex + 1) & 1023;
      return randomInts[randomIndex];
    }
    return intValue;
  }

  private long nextLong() {
    if ("RANDOM".equals(valueClass)) {
      randomIndex = (randomIndex + 1) & 1023;
      return randomLongs[randomIndex];
    }
    return longValue;
  }

  // --- Historical Legacy Implementation (Allocates Temporary Array) ---

  private static int legacyFormatInt(int value, byte[] target, int offset) {
    if (value == Integer.MIN_VALUE) {
      System.arraycopy(INT_MIN_BYTES, 0, target, offset, INT_MIN_BYTES.length);
      return INT_MIN_BYTES.length;
    }
    if (value == 0) {
      target[offset] = '0';
      return 1;
    }
    byte[] temp = new byte[11];
    int pos = 11;
    boolean negative = value < 0;
    int v = negative ? -value : value;
    while (v > 0) {
      temp[--pos] = (byte) ('0' + (v % 10));
      v /= 10;
    }
    if (negative) {
      temp[--pos] = '-';
    }
    int len = 11 - pos;
    System.arraycopy(temp, pos, target, offset, len);
    return len;
  }

  private static int legacyFormatLong(long value, byte[] target, int offset) {
    if (value == Long.MIN_VALUE) {
      System.arraycopy(LONG_MIN_BYTES, 0, target, offset, LONG_MIN_BYTES.length);
      return LONG_MIN_BYTES.length;
    }
    if (value == 0) {
      target[offset] = '0';
      return 1;
    }
    byte[] temp = new byte[20];
    int pos = 20;
    boolean negative = value < 0;
    long v = negative ? -value : value;
    while (v > 0) {
      temp[--pos] = (byte) ('0' + (v % 10));
      v /= 10;
    }
    if (negative) {
      temp[--pos] = '-';
    }
    int len = 20 - pos;
    System.arraycopy(temp, pos, target, offset, len);
    return len;
  }

  // --- Pure Formatter Microbenchmarks (Target Pre-allocated) ---

  @Benchmark
  public void pureFormatter_int_legacy(Blackhole bh) {
    int v = nextInt();
    int len = legacyFormatInt(v, targetBuffer, 0);
    bh.consume(len);
    bh.consume(targetBuffer[0]);
  }

  @Benchmark
  public void pureFormatter_int_productionDirect(Blackhole bh) {
    int v = nextInt();
    int len = NumberFormatting.formatInt(v, targetBuffer, 0);
    bh.consume(len);
    bh.consume(targetBuffer[0]);
  }

  @Benchmark
  public void pureFormatter_long_legacy(Blackhole bh) {
    long v = nextLong();
    int len = legacyFormatLong(v, targetBuffer, 0);
    bh.consume(len);
    bh.consume(targetBuffer[0]);
  }

  @Benchmark
  public void pureFormatter_long_productionDirect(Blackhole bh) {
    long v = nextLong();
    int len = NumberFormatting.formatLong(v, targetBuffer, 0);
    bh.consume(len);
    bh.consume(targetBuffer[0]);
  }

  // --- End-to-End Primitive Rendering ---

  @Benchmark
  public void endToEnd_primitiveInt(Blackhole bh) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      tplPrimitiveInt.render(ctxPrimitiveInt, out);
    }
    bh.consume(baos);
  }

  @Benchmark
  public void endToEnd_primitiveLong(Blackhole bh) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      tplPrimitiveLong.render(ctxPrimitiveLong, out);
    }
    bh.consume(baos);
  }

  @Benchmark
  public void endToEnd_foreachTable(Blackhole bh) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      tplForeachTable.render(ctxForeachTable, out);
    }
    bh.consume(baos);
  }
}
