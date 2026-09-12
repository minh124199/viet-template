package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.ByteArrayOutputStream;
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

/**
 * End-to-end rendering benchmarks exercising {@code #foreach} loop constructs:
 *
 * <ul>
 *   <li>Workload B05: Small Table Loop (10 rows x 5 columns)
 *   <li>Workload B06: Large Table Loop (1,000 rows x 5 columns)
 *   <li>Workload B07: Nested Loops (100 outer x 10 inner rows)
 * </ul>
 *
 * Evaluated across {@link StringTemplateOutput} and {@link Utf8OutputStreamTemplateOutput}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class ForeachRenderingBenchmark {

  public record TableRow(String col1, int col2, String col3, double col4, boolean col5) {}

  public record NestedItem(String name, int score) {}

  public record MatrixRow(int id, String category, List<NestedItem> items) {}

  @Param({"IR", "AOT_BYTECODE"})
  private String tier;

  private VtlTemplateEngine engine;
  private Template tableTemplate;
  private Template nestedTemplate;

  private RenderContext smallTableContext;
  private RenderContext largeTableContext;
  private RenderContext nestedLoopContext;

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();

    String tableVm =
        "<table>\n"
            + "#foreach($row in $table)\n"
            + "  <tr><td>$row.col1</td><td>$row.col2</td><td>$row.col3</td><td>$row.col4</td><td>$row.col5</td></tr>\n"
            + "#end\n"
            + "</table>\n";
    repo.put("table.vm", tableVm);

    String nestedVm =
        "<div class=\"matrix\">\n"
            + "#foreach($row in $matrix)\n"
            + "  <div class=\"group\" id=\"grp-$row.id\">\n"
            + "    <h3>$row.category</h3>\n"
            + "    <ul>\n"
            + "    #foreach($item in $row.items)\n"
            + "      <li>[$foreach.count] $item.name - $item.score (outerCount:"
            + " $foreach.parent.count)</li>\n"
            + "    #end\n"
            + "    </ul>\n"
            + "  </div>\n"
            + "#end\n"
            + "</div>\n";
    repo.put("nested.vm", nestedVm);

    ExecutionTier executionTier = ExecutionTier.valueOf(tier);
    engine = VtlTemplateEngine.builder().repository(repo).executionTier(executionTier).build();

    tableTemplate = engine.get("table.vm");
    nestedTemplate = engine.get("nested.vm");

    // 1. Small Table Context (10 rows)
    List<TableRow> smallRows = new ArrayList<>(10);
    for (int i = 0; i < 10; i++) {
      smallRows.add(new TableRow("Item-" + i, i * 10, "Description " + i, i * 1.5, i % 2 == 0));
    }
    smallTableContext = RenderContext.builder().put("table", smallRows).build();

    // 2. Large Table Context (1,000 rows)
    List<TableRow> largeRows = new ArrayList<>(1000);
    for (int i = 0; i < 1000; i++) {
      largeRows.add(
          new TableRow("Product-" + i, i * 100, "Specs for product " + i, i * 2.5, i % 2 == 0));
    }
    largeTableContext = RenderContext.builder().put("table", largeRows).build();

    // 3. Nested Loop Context (100 outer x 10 inner = 1,000 inner rows)
    List<MatrixRow> matrix = new ArrayList<>(100);
    for (int o = 0; o < 100; o++) {
      List<NestedItem> items = new ArrayList<>(10);
      for (int in = 0; in < 10; in++) {
        items.add(new NestedItem("SubItem-" + o + "-" + in, o * 10 + in));
      }
      matrix.add(new MatrixRow(o, "Category-" + o, items));
    }
    nestedLoopContext = RenderContext.builder().put("matrix", matrix).build();
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  @Benchmark
  public void b05_smallTable_StringOutput(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    tableTemplate.render(smallTableContext, output);
    bh.consume(output);
  }

  @Benchmark
  public void b05_smallTable_Utf8StreamOutput(Blackhole bh) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(baos);
    tableTemplate.render(smallTableContext, output);
    output.flush();
    bh.consume(baos);
  }

  @Benchmark
  public void b06_largeTable_StringOutput(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    tableTemplate.render(largeTableContext, output);
    bh.consume(output);
  }

  @Benchmark
  public void b06_largeTable_Utf8StreamOutput(Blackhole bh) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
    Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(baos);
    tableTemplate.render(largeTableContext, output);
    output.flush();
    bh.consume(baos);
  }

  @Benchmark
  public void b07_nestedLoop_StringOutput(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    nestedTemplate.render(nestedLoopContext, output);
    bh.consume(output);
  }

  @Benchmark
  public void b07_nestedLoop_Utf8StreamOutput(Blackhole bh) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
    Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(baos);
    nestedTemplate.render(nestedLoopContext, output);
    output.flush();
    bh.consume(baos);
  }
}
