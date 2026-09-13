package io.github.minh124199.viettemplate.benchmarks.output;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.DiagnosticDirectByteOutput;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.DiagnosticDirectHtmlTextEscaper;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.DiagnosticFastNumberFormatting;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.DiagnosticPooledUtf8Output;
import io.github.minh124199.viettemplate.runtime.HtmlTextEscaper;
import io.github.minh124199.viettemplate.runtime.NumberFormatting;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
 * Qualification benchmark suite for Milestone M19.3b (Streaming Output Allocation & UTF-8
 * Formatting Qualification).
 *
 * <p>Methodology: VT-PERF-M19.3B-QUALIFICATION-1
 *
 * <p>Covers Taxonomy Workloads A through P across production and diagnostic output targets.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class StreamingOutputQualificationBenchmark {

  public record TableRow(String col1, int col2, String col3, double col4, boolean col5) {}

  public record Item(String name, int price, String category) {}

  @Param({"STRING", "UTF8_STREAM_NEW", "UTF8_STREAM_REUSED", "UTF8_POOLED", "UTF8_DIRECT_BYTE"})
  private String outputType;

  private VtlTemplateEngine engine;

  // Templates
  private Template tplStaticAscii;
  private Template tplStaticUnicode;
  private Template tplScalarString;
  private Template tplManyTinyStrings;
  private Template tplLargeString;
  private Template tplHtmlEscapingAscii;
  private Template tplHtmlEscapingUnicode;
  private Template tplPrimitiveInt;
  private Template tplPrimitiveLong;
  private Template tplPrimitiveDouble;
  private Template tplPrimitiveBoolean;
  private Template tplForeachTable;
  private Template tplMacroCards;
  private Template tplLargeDoc;

  // Contexts
  private RenderContext ctxStaticAscii;
  private RenderContext ctxStaticUnicode;
  private RenderContext ctxScalarString;
  private RenderContext ctxManyTinyStrings;
  private RenderContext ctxLargeString;
  private RenderContext ctxHtmlEscapingAscii;
  private RenderContext ctxHtmlEscapingUnicode;
  private RenderContext ctxPrimitiveInt;
  private RenderContext ctxPrimitiveLong;
  private RenderContext ctxPrimitiveDouble;
  private RenderContext ctxPrimitiveBoolean;
  private RenderContext ctxForeachTable;
  private RenderContext ctxMacroCards;
  private RenderContext ctxLargeDoc;

  // Reusable thread-local buffers to isolate write-path from setup allocation
  private static final ThreadLocal<ReusedStreamState> REUSED_STATE =
      ThreadLocal.withInitial(ReusedStreamState::new);

  private static final class ReusedStreamState {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
    final Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(baos);
  }

  private static final class NullOutputStream extends OutputStream {
    static final NullOutputStream INSTANCE = new NullOutputStream();

    @Override
    public void write(int b) {}

    @Override
    public void write(byte[] b, int off, int len) {}
  }

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();

    // A. Static ASCII
    repo.put(
        "static_ascii.vm",
        """
        <!DOCTYPE html>
        <html>
        <head><title>Static ASCII Page</title></head>
        <body>
          <div class="container">
            <header><h1>Welcome to Viet Template Engine</h1></header>
            <nav><ul><li><a href="/home">Home</a></li><li><a href="/about">About</a></li></ul></nav>
            <main><p>This is a completely static document designed to evaluate literal write throughput.</p></main>
            <footer><p>&copy; 2026 Viet Template. All rights reserved.</p></footer>
          </div>
        </body>
        </html>
        """);

    // B. Static non-ASCII UTF-8 (Vietnamese & CJK)
    repo.put(
        "static_unicode.vm",
        """
        <!DOCTYPE html>
        <html>
        <head><title>Trang Web Tiếng Việt & 日本語</title></head>
        <body>
          <div class="noi-dung">
            <h1>Chào mừng bạn đến với Viet Template Engine!</h1>
            <p>Viet Template là công cụ kết xuất mẫu VTL hiệu năng cao dành cho Java 17, 21 và 25.</p>
            <p>Đặc sản: Cà phê sữa đá ☕, phở bò Hà Nội 🍜, bánh mì Sài Gòn 🥖.</p>
            <p>日本語テスト: こんにちは世界！高速テンプレートエンジンのテストです。</p>
          </div>
        </body>
        </html>
        """);

    // C. Scalar String
    repo.put("scalar_string.vm", "<div>Hello, $userName! Welcome back to $siteName.</div>");

    // D. Many Tiny Strings (20 strings)
    StringBuilder sbTiny = new StringBuilder("<ul>\n");
    for (int i = 0; i < 20; i++) {
      sbTiny.append("  <li>$tag").append(i).append("</li>\n");
    }
    sbTiny.append("</ul>");
    repo.put("many_tiny_strings.vm", sbTiny.toString());

    // E. Large String (8 KB)
    repo.put("large_string.vm", "<div class=\"article\"><article>$articleBody</article></div>");

    // F. HTML Escaping ASCII
    repo.put(
        "escaping_ascii.vm", "<div>Escaped: $field1 | $field2 | $field3 | $field4 | $field5</div>");

    // G. HTML Escaping Unicode
    repo.put("escaping_unicode.vm", "<div>Vietnamese Escaped: $vn1 | $vn2 | $vn3 | $vn4</div>");

    // H. Primitive Int
    repo.put(
        "primitive_int.vm", "<div>Counts: $i1, $i2, $i3, $i4, $i5, $i6, $i7, $i8, $i9, $i10</div>");

    // I. Primitive Long
    repo.put("primitive_long.vm", "<div>Timestamps: $l1, $l2, $l3, $l4, $l5</div>");

    // J. Primitive Double
    repo.put("primitive_double.vm", "<div>Metrics: $d1, $d2, $d3, $d4, $d5</div>");

    // K. Primitive Boolean
    repo.put("primitive_bool.vm", "<div>Flags: $b1, $b2, $b3, $b4, $b5, $b6, $b7, $b8</div>");

    // L. Foreach Table (50 rows)
    repo.put(
        "foreach_table.vm",
        """
        <table>
        #foreach($r in $rows)
          <tr><td>$r.col1</td><td>$r.col2</td><td>$r.col3</td><td>$r.col4</td><td>$r.col5</td></tr>
        #end
        </table>
        """);

    // M. Macro Cards
    repo.put(
        "macro_cards.vm",
        """
        #macro(renderCard $title $val $badge)
          <div class="card"><h3>$title</h3><span>$val</span><em>$badge</em></div>
        #end
        <section>
        #foreach($item in $items)
          #renderCard($item.name, $item.price, $item.category)
        #end
        </section>
        """);

    // P. Large Document (repeated sections reaching ~64 KB)
    StringBuilder sbLarge = new StringBuilder("<!DOCTYPE html><html><body>\n");
    for (int s = 0; s < 50; s++) {
      sbLarge.append("<section id=\"sec-").append(s).append("\">\n");
      sbLarge.append("  <h2>Section Header ").append(s).append(": $secTitle</h2>\n");
      sbLarge
          .append("  <p>Detailed description of section ")
          .append(s)
          .append(" with $secDesc.</p>\n");
      sbLarge.append(
          "  <p>Static paragraphs filling out document volume to simulate enterprise"
              + " reports.</p>\n");
      sbLarge.append("</section>\n");
    }
    sbLarge.append("</body></html>");
    repo.put("large_doc.vm", sbLarge.toString());

    engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    // Compile templates
    tplStaticAscii = engine.get("static_ascii.vm");
    tplStaticUnicode = engine.get("static_unicode.vm");
    tplScalarString = engine.get("scalar_string.vm");
    tplManyTinyStrings = engine.get("many_tiny_strings.vm");
    tplLargeString = engine.get("large_string.vm");
    tplHtmlEscapingAscii = engine.get("escaping_ascii.vm");
    tplHtmlEscapingUnicode = engine.get("escaping_unicode.vm");
    tplPrimitiveInt = engine.get("primitive_int.vm");
    tplPrimitiveLong = engine.get("primitive_long.vm");
    tplPrimitiveDouble = engine.get("primitive_double.vm");
    tplPrimitiveBoolean = engine.get("primitive_bool.vm");
    tplForeachTable = engine.get("foreach_table.vm");
    tplMacroCards = engine.get("macro_cards.vm");
    tplLargeDoc = engine.get("large_doc.vm");

    // Build Contexts
    ctxStaticAscii = RenderContext.empty();
    ctxStaticUnicode = RenderContext.empty();
    ctxScalarString =
        RenderContext.builder()
            .put("userName", "NguyenVanA")
            .put("siteName", "VietCommerce")
            .build();

    var tinyBuilder = RenderContext.builder();
    for (int i = 0; i < 20; i++) {
      tinyBuilder.put("tag" + i, "tag-" + i);
    }
    ctxManyTinyStrings = tinyBuilder.build();

    ctxLargeString = RenderContext.builder().put("articleBody", "A".repeat(8192)).build();

    ctxHtmlEscapingAscii =
        RenderContext.builder()
            .put("field1", "<script>alert('xss')</script>")
            .put("field2", "Tom & Jerry & \"Friends\"")
            .put("field3", "Cost is > $100 & < $500")
            .put("field4", "\"Double\" and 'Single' quotes")
            .put("field5", "Clean String without special chars")
            .build();

    ctxHtmlEscapingUnicode =
        RenderContext.builder()
            .put("vn1", "Cà phê sữa đá & <Trứng> ngon tuyệt!")
            .put("vn2", "Phở bò & Bún chả \"Hà Nội\"")
            .put("vn3", "Trời mưa > 50mm tại TP. Hồ Chí Minh")
            .put("vn4", "Chúc mừng năm mới: An khang & Thịnh vượng!")
            .build();

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

    ctxPrimitiveDouble =
        RenderContext.builder()
            .put("d1", 3.14159)
            .put("d2", 0.0)
            .put("d3", -273.15)
            .put("d4", 100.5)
            .put("d5", 99999.99)
            .build();

    ctxPrimitiveBoolean =
        RenderContext.builder()
            .put("b1", true)
            .put("b2", false)
            .put("b3", true)
            .put("b4", true)
            .put("b5", false)
            .put("b6", false)
            .put("b7", true)
            .put("b8", false)
            .build();

    List<TableRow> rows = new ArrayList<>(50);
    for (int i = 0; i < 50; i++) {
      rows.add(new TableRow("Product-" + i, i * 10, "SKU-" + (i * 100), i * 1.5, i % 2 == 0));
    }
    ctxForeachTable = RenderContext.builder().put("rows", rows).build();

    List<Item> items = new ArrayList<>(20);
    for (int i = 0; i < 20; i++) {
      items.add(new Item("Item " + i, i * 50, "Electronics"));
    }
    ctxMacroCards = RenderContext.builder().put("items", items).build();

    ctxLargeDoc =
        RenderContext.builder()
            .put("secTitle", "Annual Financial Report")
            .put("secDesc", "Audited figures for Q1-Q4 across all regional operating branches")
            .build();
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  private void render(Template tpl, RenderContext ctx, Blackhole bh) throws IOException {
    switch (outputType) {
      case "STRING" -> {
        StringTemplateOutput out = new StringTemplateOutput();
        tpl.render(ctx, out);
        bh.consume(out);
      }
      case "UTF8_STREAM_NEW" -> {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
          tpl.render(ctx, out);
        }
        bh.consume(baos);
      }
      case "UTF8_STREAM_REUSED" -> {
        ReusedStreamState state = REUSED_STATE.get();
        state.baos.reset();
        tpl.render(ctx, state.output);
        state.output.flush();
        bh.consume(state.baos);
      }
      case "UTF8_POOLED" -> {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        try (DiagnosticPooledUtf8Output out = new DiagnosticPooledUtf8Output(baos)) {
          tpl.render(ctx, out);
        }
        bh.consume(baos);
      }
      case "UTF8_DIRECT_BYTE" -> {
        DiagnosticDirectByteOutput out = new DiagnosticDirectByteOutput(1024);
        tpl.render(ctx, out);
        bh.consume(out);
      }
      default -> throw new IllegalArgumentException("Unknown outputType: " + outputType);
    }
  }

  // Workload A: Static ASCII
  @Benchmark
  public void workloadA_staticAscii(Blackhole bh) throws IOException {
    render(tplStaticAscii, ctxStaticAscii, bh);
  }

  // Workload B: Static non-ASCII UTF-8
  @Benchmark
  public void workloadB_staticUnicode(Blackhole bh) throws IOException {
    render(tplStaticUnicode, ctxStaticUnicode, bh);
  }

  // Workload C: Scalar String Variable
  @Benchmark
  public void workloadC_scalarString(Blackhole bh) throws IOException {
    render(tplScalarString, ctxScalarString, bh);
  }

  // Workload D: Many Tiny String Variables (20 variables)
  @Benchmark
  public void workloadD_manyTinyStrings(Blackhole bh) throws IOException {
    render(tplManyTinyStrings, ctxManyTinyStrings, bh);
  }

  // Workload E: One Large String Variable (8 KB)
  @Benchmark
  public void workloadE_largeString(Blackhole bh) throws IOException {
    render(tplLargeString, ctxLargeString, bh);
  }

  // Workload F: HTML Escaping ASCII
  @Benchmark
  public void workloadF_htmlEscapingAscii(Blackhole bh) throws IOException {
    render(tplHtmlEscapingAscii, ctxHtmlEscapingAscii, bh);
  }

  // Workload G: HTML Escaping Unicode
  @Benchmark
  public void workloadG_htmlEscapingUnicode(Blackhole bh) throws IOException {
    render(tplHtmlEscapingUnicode, ctxHtmlEscapingUnicode, bh);
  }

  // Workload H: Primitive Int
  @Benchmark
  public void workloadH_primitiveInt(Blackhole bh) throws IOException {
    render(tplPrimitiveInt, ctxPrimitiveInt, bh);
  }

  // Workload I: Primitive Long
  @Benchmark
  public void workloadI_primitiveLong(Blackhole bh) throws IOException {
    render(tplPrimitiveLong, ctxPrimitiveLong, bh);
  }

  // Workload J: Primitive Double
  @Benchmark
  public void workloadJ_primitiveDouble(Blackhole bh) throws IOException {
    render(tplPrimitiveDouble, ctxPrimitiveDouble, bh);
  }

  // Workload K: Primitive Boolean
  @Benchmark
  public void workloadK_primitiveBoolean(Blackhole bh) throws IOException {
    render(tplPrimitiveBoolean, ctxPrimitiveBoolean, bh);
  }

  // Workload L: Foreach Table (50 rows)
  @Benchmark
  public void workloadL_foreachTable(Blackhole bh) throws IOException {
    render(tplForeachTable, ctxForeachTable, bh);
  }

  // Workload M: Macro Cards
  @Benchmark
  public void workloadM_macroCards(Blackhole bh) throws IOException {
    render(tplMacroCards, ctxMacroCards, bh);
  }

  // Workload P: Large Document (~64 KB)
  @Benchmark
  public void workloadP_largeDocument(Blackhole bh) throws IOException {
    render(tplLargeDoc, ctxLargeDoc, bh);
  }

  // --- Micro-attributions ---

  // H3: Primitive Formatting Microbenchmark (Production vs Diagnostic Direct)
  @Benchmark
  public void microH3_formatInt_production(Blackhole bh) {
    byte[] target = new byte[32];
    int len = NumberFormatting.formatInt(123456, target, 0);
    bh.consume(len);
    bh.consume(target);
  }

  @Benchmark
  public void microH3_formatInt_direct(Blackhole bh) {
    byte[] target = new byte[32];
    int len = DiagnosticFastNumberFormatting.formatInt(123456, target, 0);
    bh.consume(len);
    bh.consume(target);
  }

  // H4: HTML Escaping Microbenchmark (Production vs Diagnostic Direct)
  @Benchmark
  public void microH4_htmlEscaping_production(Blackhole bh) throws IOException {
    StringTemplateOutput out = new StringTemplateOutput(128);
    HtmlTextEscaper.INSTANCE.escape("<script>alert('xss')</script> & \"more\"", out);
    bh.consume(out);
  }

  @Benchmark
  public void microH4_htmlEscaping_direct(Blackhole bh) throws IOException {
    StringTemplateOutput out = new StringTemplateOutput(128);
    DiagnosticDirectHtmlTextEscaper.escape("<script>alert('xss')</script> & \"more\"", out);
    bh.consume(out);
  }
}
