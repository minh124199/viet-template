package io.github.minh124199.viettemplate.benchmarks.output;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.AtomicSlotPool;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.PrototypePooledUtf8Output;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.SynchronizedArrayStackPool;
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
 * End-to-end rendering benchmark evaluating allocation and throughput deltas between unpooled
 * {@link Utf8OutputStreamTemplateOutput} and bounded buffer pool prototypes ({@link
 * SynchronizedArrayStackPool}, {@link AtomicSlotPool}).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 3,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class StreamingOutputPoolBenchmark {

  public record TableRow(int id, String name, double price, String status) {}

  @Param({"CURRENT_NEW", "POOLED_PRODUCTION"})
  private String outputMode;

  private VtlTemplateEngine engine;
  private SynchronizedArrayStackPool syncStackPool;
  private AtomicSlotPool atomicSlotPool;

  private Template tplStaticAsciiTiny;
  private Template tplStaticUnicodeTiny;
  private Template tplDynamicStringTiny;
  private Template tplHtmlEscapeTiny;
  private Template tplPrimitiveTiny;
  private Template tplMediumTemplate;
  private Template tplForeachTable;
  private Template tplLargeDocument;

  private RenderContext ctxStaticAsciiTiny;
  private RenderContext ctxStaticUnicodeTiny;
  private RenderContext ctxDynamicStringTiny;
  private RenderContext ctxHtmlEscapeTiny;
  private RenderContext ctxPrimitiveTiny;
  private RenderContext ctxMediumTemplate;
  private RenderContext ctxForeachTable;
  private RenderContext ctxLargeDocument;

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    syncStackPool = new SynchronizedArrayStackPool(64, true);
    atomicSlotPool = new AtomicSlotPool(64, true);

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();

    // 1. STATIC_ASCII_TINY (~100 bytes)
    repo.put(
        "static_ascii_tiny.vm",
        "<div class=\"banner\"><p>Viet Template high-performance streaming output qualification"
            + " benchmark.</p></div>");

    // 2. STATIC_UNICODE_TINY (~100 bytes UTF-8)
    repo.put(
        "static_unicode_tiny.vm",
        "<div class=\"vn\"><p>Chào mừng đến với Viet Template! Cà phê sữa đá ☕ và phở"
            + " Hà Nội 🍜.</p></div>");

    // 3. DYNAMIC_STRING_TINY (tiny template with 2-3 variables)
    repo.put(
        "dynamic_string_tiny.vm",
        "<div class=\"user-badge\"><span>$userName</span><span"
            + " class=\"role\">$role</span><em>$dept</em></div>");

    // 4. HTML_ESCAPE_TINY (tiny HTML with escaped variables)
    repo.put(
        "html_escape_tiny.vm", "<div class=\"alert\"><span>$alert</span><em>$source</em></div>");

    // 5. PRIMITIVE_TINY (tiny template with numbers)
    repo.put(
        "primitive_tiny.vm",
        "<div>Count: $count, Total: $total, Rating: $rating, Active: $active</div>");

    // 6. MEDIUM_TEMPLATE (500-1000 bytes)
    repo.put(
        "medium_template.vm",
        """
        <article class=\"profile-card\">
          <header>
            <h2>User Profile: $user</h2>
            <p class=\"subtitle\">Department: $dept | Location: $location</p>
          </header>
          <section class=\"bio\">
            <p>$bio</p>
          </section>
          <section class=\"stats\">
            <dl>
              <dt>Score:</dt><dd>$score</dd>
              <dt>Rank:</dt><dd>$rank</dd>
              <dt>Status:</dt><dd>$status</dd>
            </dl>
          </section>
          <footer>
            <p>&copy; 2026 Viet Template. All rights reserved.</p>
          </footer>
        </article>
        """);

    // 7. FOREACH_TABLE (10-row table)
    repo.put(
        "foreach_table.vm",
        """
        <table>
          <thead><tr><th>ID</th><th>Name</th><th>Price</th><th>Status</th></tr></thead>
          <tbody>
          #foreach($r in $rows)
            <tr><td>$r.id</td><td>$r.name</td><td>$r.price</td><td>$r.status</td></tr>
          #end
          </tbody>
        </table>
        """);

    // 8. LARGE_DOCUMENT (10-20 KB document)
    StringBuilder sbLarge = new StringBuilder("<!DOCTYPE html><html><body>\n");
    for (int i = 0; i < 20; i++) {
      sbLarge
          .append("<section id=\"sec-")
          .append(i)
          .append("\">\n")
          .append("  <h3>Section ")
          .append(i)
          .append(": $docTitle</h3>\n")
          .append("  <p>Detailed overview of operational metrics and performance targets.</p>\n")
          .append(
              "  <p>Paragraph body content verifying sustained streaming output capacity across"
                  + " multi-kilobyte buffers without thrashing.</p>\n")
          .append("  <div class=\"meta\"><span>Index: ")
          .append(i)
          .append("</span> | <span>Author: $docAuthor</span></div>\n")
          .append("</section>\n");
    }
    sbLarge.append("</body></html>");
    repo.put("large_document.vm", sbLarge.toString());

    engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    tplStaticAsciiTiny = engine.get("static_ascii_tiny.vm");
    tplStaticUnicodeTiny = engine.get("static_unicode_tiny.vm");
    tplDynamicStringTiny = engine.get("dynamic_string_tiny.vm");
    tplHtmlEscapeTiny = engine.get("html_escape_tiny.vm");
    tplPrimitiveTiny = engine.get("primitive_tiny.vm");
    tplMediumTemplate = engine.get("medium_template.vm");
    tplForeachTable = engine.get("foreach_table.vm");
    tplLargeDocument = engine.get("large_document.vm");

    ctxStaticAsciiTiny = RenderContext.empty();
    ctxStaticUnicodeTiny = RenderContext.empty();
    ctxDynamicStringTiny =
        RenderContext.builder()
            .put("userName", "Alice")
            .put("role", "Administrator")
            .put("dept", "Engineering")
            .build();
    ctxHtmlEscapeTiny =
        RenderContext.builder()
            .put("alert", "<script>alert('xss')</script>")
            .put("source", "Admin & Co <info>")
            .build();
    ctxPrimitiveTiny =
        RenderContext.builder()
            .put("count", 42)
            .put("total", 1000000L)
            .put("rating", 4.95)
            .put("active", true)
            .build();
    ctxMediumTemplate =
        RenderContext.builder()
            .put("user", "NguyenVanA")
            .put("dept", "Platform Infrastructure")
            .put("location", "Ho Chi Minh City")
            .put(
                "bio",
                "Senior systems engineer focusing on high-performance template compilation and"
                    + " zero-allocation streaming architectures.")
            .put("score", 9850)
            .put("rank", 1)
            .put("status", "Active")
            .build();

    List<TableRow> rows = new ArrayList<>(10);
    for (int i = 0; i < 10; i++) {
      rows.add(new TableRow(i + 1, "Product-" + (i + 1), (i + 1) * 19.99, "AVAILABLE"));
    }
    ctxForeachTable = RenderContext.builder().put("rows", rows).build();

    ctxLargeDocument =
        RenderContext.builder()
            .put("docTitle", "Quarterly System Performance Qualification Report")
            .put("docAuthor", "Viet Template Performance Core")
            .build();
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  private void render(Template tpl, RenderContext ctx, int preSizedBuffer, Blackhole bh)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(preSizedBuffer);
    switch (outputMode) {
      case "CURRENT_NEW" -> {
        // Unpooled 8 KiB buffer allocated on every render, identical encoding and formatting paths
        try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos, 8191)) {
          tpl.render(ctx, out);
        }
        bh.consume(baos);
      }
      case "POOLED_PRODUCTION" -> {
        // Production merged pool (capacity 16 AtomicReferenceArray)
        try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
          tpl.render(ctx, out);
        }
        bh.consume(baos);
      }
      case "POOLED_SYNC_STACK" -> {
        try (PrototypePooledUtf8Output out = new PrototypePooledUtf8Output(baos, syncStackPool)) {
          tpl.render(ctx, out);
        }
        bh.consume(baos);
      }
      case "POOLED_ATOMIC_SLOT" -> {
        try (PrototypePooledUtf8Output out = new PrototypePooledUtf8Output(baos, atomicSlotPool)) {
          tpl.render(ctx, out);
        }
        bh.consume(baos);
      }
      default -> throw new IllegalArgumentException("Unknown outputMode: " + outputMode);
    }
  }

  @Benchmark
  public void workload_staticAsciiTiny(Blackhole bh) throws IOException {
    render(tplStaticAsciiTiny, ctxStaticAsciiTiny, 128, bh);
  }

  @Benchmark
  public void workload_staticUnicodeTiny(Blackhole bh) throws IOException {
    render(tplStaticUnicodeTiny, ctxStaticUnicodeTiny, 128, bh);
  }

  @Benchmark
  public void workload_dynamicStringTiny(Blackhole bh) throws IOException {
    render(tplDynamicStringTiny, ctxDynamicStringTiny, 128, bh);
  }

  @Benchmark
  public void workload_htmlEscapeTiny(Blackhole bh) throws IOException {
    render(tplHtmlEscapeTiny, ctxHtmlEscapeTiny, 128, bh);
  }

  @Benchmark
  public void workload_primitiveTiny(Blackhole bh) throws IOException {
    render(tplPrimitiveTiny, ctxPrimitiveTiny, 128, bh);
  }

  @Benchmark
  public void workload_mediumTemplate(Blackhole bh) throws IOException {
    render(tplMediumTemplate, ctxMediumTemplate, 1024, bh);
  }

  @Benchmark
  public void workload_foreachTable(Blackhole bh) throws IOException {
    render(tplForeachTable, ctxForeachTable, 2048, bh);
  }

  @Benchmark
  public void workload_largeDocument(Blackhole bh) throws IOException {
    render(tplLargeDocument, ctxLargeDocument, 32768, bh);
  }
}
