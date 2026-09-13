package io.github.minh124199.viettemplate.benchmarks.output;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.runtime.HtmlTextEscaper;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark suite for Milestone M19.3b.2 (Zero-Allocation HTML Escaping).
 *
 * <p>Protocol: VT-PERF-M19.3B2-HTML-ESCAPING-1
 *
 * <p>Compares legacy substring/subSequence materialization against direct range forwarding across
 * multiple escape densities, destination output types, and end-to-end template workloads.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class HtmlEscapingBenchmark {

  public record TableRow(String id, String name, String email, String role, String notes) {}

  @Param({"NO_ESCAPE", "SPARSE_ESCAPE", "MEDIUM_ESCAPE", "DENSE_ESCAPE", "UNICODE_MIXED"})
  private String workload;

  @Param({"STRING", "UTF8_STREAM"})
  private String outputType;

  private String testInput;

  // Reusable outputs for isolated microbenchmarks
  private StringTemplateOutput stringOutput;
  private ResettableByteArrayOutputStream baos;
  private Utf8OutputStreamTemplateOutput utf8Output;

  // End-to-end engines and templates
  private VtlTemplateEngine irEngine;
  private VtlTemplateEngine aotEngine;
  private Template irRealisticTpl;
  private Template aotRealisticTpl;
  private Template irEscapeHeavyTpl;
  private Template aotEscapeHeavyTpl;
  private RenderContext ctxRealistic;
  private RenderContext ctxEscapeHeavy;

  @Setup(Level.Trial)
  public void setUpTrial() throws IOException {
    switch (workload) {
      case "NO_ESCAPE" ->
          testInput =
              "The quick brown fox jumps over the lazy dog and runs through the peaceful forest"
                  + " seamlessly.";
      case "SPARSE_ESCAPE" ->
          testInput =
              "User profile for Jane Doe & Associates: active status confirmed in database"
                  + " transaction log.";
      case "MEDIUM_ESCAPE" ->
          testInput =
              "Check condition if 5 < 10 && 10 > 5 for 'active' user state with \"strict\" mode"
                  + " enabled.";
      case "DENSE_ESCAPE" ->
          testInput =
              "&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"&<>'\"";
      case "UNICODE_MIXED" ->
          testInput =
              "Xin chào <Việt Nam> & 'thế giới'! 日本語 <テスト> & \"双引号\" 🍵 🙂 <emoji> 🚀 &" + " done.";
      default -> throw new IllegalArgumentException("Unknown workload: " + workload);
    }

    stringOutput = new StringTemplateOutput(512);
    baos = new ResettableByteArrayOutputStream(1024);
    utf8Output = new Utf8OutputStreamTemplateOutput(baos, 8192);

    // End-to-end templates
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "realistic.vm",
        """
        <!DOCTYPE html>
        <html>
        <head><title>$pageTitle</title></head>
        <body>
          <h1>$header</h1>
          <p>$description</p>
          <div class="user-info">
            <span>Name: $userName</span>
            <span>Role: $userRole</span>
            <span>Notice: $notice</span>
          </div>
          <footer>$footerText</footer>
        </body>
        </html>
        """);

    repo.put(
        "escape_heavy.vm",
        """
        <table>
        #foreach($r in $rows)
          <tr>
            <td>$r.id</td>
            <td>$r.name</td>
            <td>$r.email</td>
            <td>$r.role</td>
            <td>$r.notes</td>
          </tr>
        #end
        </table>
        """);

    irEngine = VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
    aotEngine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    irRealisticTpl = irEngine.get("realistic.vm");
    aotRealisticTpl = aotEngine.get("realistic.vm");
    irEscapeHeavyTpl = irEngine.get("escape_heavy.vm");
    aotEscapeHeavyTpl = aotEngine.get("escape_heavy.vm");

    ctxRealistic =
        RenderContext.builder()
            .put("pageTitle", "Dashboard & Reports <Production>")
            .put("header", "Welcome to 'Admin' Console & Settings")
            .put("description", "Status: 100% operational with 0 < errors && 0 > warnings.")
            .put("userName", "Alice & Bob <Engineers>")
            .put("userRole", "\"Senior Architect\" & Lead")
            .put("notice", "Important: Keep <credentials> & 'tokens' secure!")
            .put("footerText", "© 2026 Viet Template & Contributors. All rights reserved.")
            .build();

    List<TableRow> rows = new ArrayList<>(25);
    for (int i = 0; i < 25; i++) {
      rows.add(
          new TableRow(
              "ID-" + i + " <#>",
              "User & " + i,
              "user." + i + "+test@example.com",
              i % 2 == 0 ? "Admin & \"Owner\"" : "Guest 'Reader'",
              "Notes: <tag> & special 'content' #" + i));
    }
    ctxEscapeHeavy = RenderContext.builder().put("rows", rows).build();
  }

  @Setup(Level.Invocation)
  public void setUpInvocation() {
    stringOutput.reset();
    baos.reset();
  }

  /**
   * Reference implementation of legacy substring/subSequence-based HTML escaper replicating
   * pre-M19.3b.2 behavior.
   */
  public static void legacyEscape(CharSequence input, TemplateOutput output) throws IOException {
    if (input == null) {
      return;
    }
    int len = input.length();
    if (len == 0) {
      return;
    }

    int firstSpecial = -1;
    for (int i = 0; i < len; i++) {
      char c = input.charAt(i);
      if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'') {
        firstSpecial = i;
        break;
      }
    }

    if (firstSpecial == -1) {
      output.write(input);
      return;
    }

    if (firstSpecial > 0) {
      output.write(input.subSequence(0, firstSpecial));
    }

    int last = firstSpecial;
    for (int i = firstSpecial; i < len; i++) {
      char c = input.charAt(i);
      String entity =
          switch (c) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            case '"' -> "&quot;";
            case '\'' -> "&#39;";
            default -> null;
          };
      if (entity != null) {
        if (i > last) {
          output.write(input.subSequence(last, i));
        }
        output.write(entity);
        last = i + 1;
      }
    }

    if (last < len) {
      output.write(input.subSequence(last, len));
    }
  }

  @Benchmark
  public void pureEscaper_legacySubsequence(Blackhole bh) throws IOException {
    if ("STRING".equals(outputType)) {
      legacyEscape(testInput, stringOutput);
      bh.consume(stringOutput.length());
    } else {
      legacyEscape(testInput, utf8Output);
      utf8Output.flush();
      bh.consume(baos.size());
    }
  }

  @Benchmark
  public void pureEscaper_productionRange(Blackhole bh) throws IOException {
    if ("STRING".equals(outputType)) {
      HtmlTextEscaper.INSTANCE.escape(testInput, stringOutput);
      bh.consume(stringOutput.length());
    } else {
      HtmlTextEscaper.INSTANCE.escape(testInput, utf8Output);
      utf8Output.flush();
      bh.consume(baos.size());
    }
  }

  @Benchmark
  public void endToEnd_realisticHtml_ir(Blackhole bh) throws IOException {
    if ("STRING".equals(outputType)) {
      StringTemplateOutput out = new StringTemplateOutput(2048);
      irRealisticTpl.render(ctxRealistic, out);
      bh.consume(out.length());
    } else {
      ByteArrayOutputStream b = new ByteArrayOutputStream(2048);
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(b, 8192)) {
        irRealisticTpl.render(ctxRealistic, out);
      }
      bh.consume(b.size());
    }
  }

  @Benchmark
  public void endToEnd_realisticHtml_aot(Blackhole bh) throws IOException {
    if ("STRING".equals(outputType)) {
      StringTemplateOutput out = new StringTemplateOutput(2048);
      aotRealisticTpl.render(ctxRealistic, out);
      bh.consume(out.length());
    } else {
      ByteArrayOutputStream b = new ByteArrayOutputStream(2048);
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(b, 8192)) {
        aotRealisticTpl.render(ctxRealistic, out);
      }
      bh.consume(b.size());
    }
  }

  @Benchmark
  public void endToEnd_escapeHeavy_ir(Blackhole bh) throws IOException {
    if ("STRING".equals(outputType)) {
      StringTemplateOutput out = new StringTemplateOutput(4096);
      irEscapeHeavyTpl.render(ctxEscapeHeavy, out);
      bh.consume(out.length());
    } else {
      ByteArrayOutputStream b = new ByteArrayOutputStream(4096);
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(b, 8192)) {
        irEscapeHeavyTpl.render(ctxEscapeHeavy, out);
      }
      bh.consume(b.size());
    }
  }

  @Benchmark
  public void endToEnd_escapeHeavy_aot(Blackhole bh) throws IOException {
    if ("STRING".equals(outputType)) {
      StringTemplateOutput out = new StringTemplateOutput(4096);
      aotEscapeHeavyTpl.render(ctxEscapeHeavy, out);
      bh.consume(out.length());
    } else {
      ByteArrayOutputStream b = new ByteArrayOutputStream(4096);
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(b, 8192)) {
        aotEscapeHeavyTpl.render(ctxEscapeHeavy, out);
      }
      bh.consume(b.size());
    }
  }

  static class ResettableByteArrayOutputStream extends OutputStream {
    private byte[] buf;
    private int count;

    ResettableByteArrayOutputStream(int size) {
      this.buf = new byte[size];
    }

    @Override
    public void write(int b) {
      ensureCapacity(1);
      buf[count++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) {
      ensureCapacity(len);
      System.arraycopy(b, off, buf, count, len);
      count += len;
    }

    private void ensureCapacity(int needed) {
      if (count + needed > buf.length) {
        int newCap = Math.max(buf.length << 1, count + needed);
        byte[] newBuf = new byte[newCap];
        System.arraycopy(buf, 0, newBuf, 0, count);
        buf = newBuf;
      }
    }

    public void reset() {
      count = 0;
    }

    public int size() {
      return count;
    }
  }
}
