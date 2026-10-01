package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
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
 * Phase 15: Focused microbenchmark isolating TemplateId.of() and SourceSpan creation costs on the
 * AOT BytecodeRuntimeBridge.writeValue hot path.
 *
 * <p>Tests four variants:
 *
 * <ul>
 *   <li>A (current): TemplateId.of() + makeSpan() on every call
 *   <li>B (precomputed TemplateId): Reuse pre-created TemplateId
 *   <li>C (lazy SourceSpan): Create TemplateId but defer SourceSpan
 *   <li>D (fully precomputed): Reuse both TemplateId and SourceSpan
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
public class OutputMetadataHotPathBenchmark {

  // Template ID string matching typical template identifiers
  private static final String TEMPLATE_ID_STR = "pages/user-profile.vtl";

  // Precomputed TemplateId for variant B/D
  private static final TemplateId PRECOMPUTED_ID = TemplateId.of(TEMPLATE_ID_STR);

  // Precomputed SourceSpan for variant D
  private static final SourceSpan PRECOMPUTED_SPAN = SourceSpan.of(0, 0, 3, 5, 3, 15);

  @Param({"1", "4", "16", "64"})
  private int writeCount;

  private CountingTemplateOutput output;
  private String[] values;
  private int[][] spans; // [startLine, startCol, endLine, endCol] per write

  @Setup(Level.Trial)
  public void setUp() {
    output = new CountingTemplateOutput();
    values = new String[writeCount];
    spans = new int[writeCount][4];
    for (int i = 0; i < writeCount; i++) {
      values[i] = "value_" + i;
      spans[i] = new int[] {i + 1, 5, i + 1, 15 + i};
    }
  }

  // === Variant A: Current implementation (TemplateId.of + makeSpan every time) ===

  @Benchmark
  public void a_current_baseline(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      TemplateId templateId = TemplateId.of(TEMPLATE_ID_STR);
      SourceSpan span = SourceSpan.of(0, 0, spans[i][0], spans[i][1], spans[i][2], spans[i][3]);
      output.write(values[i]);
      bh.consume(templateId);
      bh.consume(span);
    }
    bh.consume(output.count);
  }

  // === Variant B: Precomputed TemplateId, fresh SourceSpan ===

  @Benchmark
  public void b_precomputed_templateId(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      SourceSpan span = SourceSpan.of(0, 0, spans[i][0], spans[i][1], spans[i][2], spans[i][3]);
      output.write(values[i]);
      bh.consume(PRECOMPUTED_ID);
      bh.consume(span);
    }
    bh.consume(output.count);
  }

  // === Variant C: Fresh TemplateId, lazy SourceSpan (deferred) ===

  @Benchmark
  public void c_lazy_sourceSpan(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      TemplateId templateId = TemplateId.of(TEMPLATE_ID_STR);
      // Simulate lazy: pass primitives, no SourceSpan allocation on success
      output.write(values[i]);
      bh.consume(templateId);
      bh.consume(spans[i][0]); // line info stays as primitives
    }
    bh.consume(output.count);
  }

  // === Variant D: Both precomputed (best case) ===

  @Benchmark
  public void d_fully_precomputed(Blackhole bh) throws IOException {
    for (int i = 0; i < writeCount; i++) {
      output.write(values[i]);
      bh.consume(PRECOMPUTED_ID);
      bh.consume(PRECOMPUTED_SPAN);
    }
    bh.consume(output.count);
  }

  // === Isolated TemplateId.of() cost ===

  @Benchmark
  public TemplateId templateId_of_fresh() {
    return TemplateId.of(TEMPLATE_ID_STR);
  }

  @Benchmark
  public TemplateId templateId_precomputed() {
    return PRECOMPUTED_ID;
  }

  // === Isolated SourceSpan cost ===

  @Benchmark
  public SourceSpan sourceSpan_fresh() {
    return SourceSpan.of(0, 0, 3, 5, 3, 15);
  }

  @Benchmark
  public SourceSpan sourceSpan_cached() {
    return PRECOMPUTED_SPAN;
  }

  // === Isolated makeSpan pattern (matching BytecodeRuntimeBridge.makeSpan) ===

  @Benchmark
  public SourceSpan makeSpan_withValidation() {
    int startLine = 3, startCol = 5, endLine = 3, endCol = 15;
    if (startLine < 1 || startCol < 1 || endLine < 1 || endCol < 1) {
      return SourceSpan.UNKNOWN;
    }
    if (startLine > endLine || (startLine == endLine && startCol > endCol)) {
      return SourceSpan.UNKNOWN;
    }
    return SourceSpan.of(0, 0, startLine, startCol, endLine, endCol);
  }
}
