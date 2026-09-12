package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.LayoutResolver;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.Escaper;
import io.github.minh124199.viettemplate.runtime.StandardEscapers;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
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
 * End-to-end rendering workloads across execution backends ({@link ExecutionTier#IR} and
 * {@link ExecutionTier#AOT_BYTECODE}):
 * <ul>
 *   <li>Workload B01: Static HTML (20 KB static chunk)
 *   <li>Workload B02: Scalar variables (50 scalar substitutions)
 *   <li>Workload B03: Deep property chains ($order.customer.address.city)
 *   <li>Workload B04: Mixed conditionals (100 branches with truthiness)
 *   <li>Workload B08: Heavy HTML escaping (Escaper streaming)
 *   <li>Workload B11: Macro-heavy rendering
 *   <li>Workload B12: Two-stage layout rendering
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
public class RenderingEndToEndBenchmark {

  // Nested domain records for Workload B03 (Deep Property Chains)
  public record City(String name, String code) {}
  public record Address(String street, City city) {}
  public record Customer(String name, Address address) {}
  public record Billing(String id, String postalCode) {}
  public record Payment(String method, Billing billing) {}
  public record Order(String id, Customer customer, Payment payment) {}

  // Record for Workload B11 (Macros)
  public record Card(String title, String desc, String tag) {}

  @Param({"IR", "AOT_BYTECODE"})
  private String tier;

  private VtlTemplateEngine engine;
  private VtlTemplateEngine layoutEngine;

  private Template b01Template;
  private Template b02Template;
  private Template b03Template;
  private Template b04Template;
  private Template b08Template;
  private Template b11Template;
  private TemplateId b12ScreenId;

  private RenderContext b01Context;
  private RenderContext b02Context;
  private RenderContext b03Context;
  private RenderContext b04Context;
  private RenderContext b08Context;
  private RenderContext b11Context;
  private RenderContext b12Context;

  private Escaper htmlEscaper;
  private String rawHtmlToEscape;

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    ExecutionTier executionTier = ExecutionTier.valueOf(tier);

    // --- Workload B01: 20 KB Static HTML ---
    StringBuilder b01Sb = new StringBuilder(20480);
    b01Sb.append("<!DOCTYPE html><html><head><title>Static Page</title></head><body>\n");
    while (b01Sb.length() < 20480) {
      b01Sb.append(
          "<div class=\"section\"><p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
              + "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p></div>\n");
    }
    b01Sb.append("</body></html>\n");
    repo.put("b01_static.vm", b01Sb.toString());
    b01Context = RenderContext.empty();

    // --- Workload B02: 50 Scalar Variables ---
    StringBuilder b02Tmpl = new StringBuilder("<div class=\"scalars\">\n");
    RenderContext.Builder b02CtxBuilder = RenderContext.builder();
    for (int i = 0; i < 50; i++) {
      b02Tmpl
          .append("  <span id=\"s-")
          .append(i)
          .append("\">Var ")
          .append(i)
          .append(": $var_")
          .append(i)
          .append("</span>\n");
      b02CtxBuilder.put("var_" + i, "ScalarValue_" + i);
    }
    b02Tmpl.append("</div>\n");
    repo.put("b02_scalars.vm", b02Tmpl.toString());
    b02Context = b02CtxBuilder.build();

    // --- Workload B03: Deep Property Chains ---
    String b03Tmpl =
        "<div class=\"order\">\n"
            + "  <h2>Order: $order.id</h2>\n"
            + "  <p>Customer: $order.customer.name</p>\n"
            + "  <p>Street: $order.customer.address.street</p>\n"
            + "  <p>City: $order.customer.address.city.name ($order.customer.address.city.code)</p>\n"
            + "  <p>Payment: $order.payment.method</p>\n"
            + "  <p>Billing: $order.payment.billing.postalCode</p>\n"
            + "</div>\n";
    repo.put("b03_deep.vm", b03Tmpl);
    City city = new City("San Francisco", "CA-94105");
    Address address = new Address("500 Howard Street", city);
    Customer customer = new Customer("Alice Johnson", address);
    Billing billing = new Billing("BILL-9988", "94105");
    Payment payment = new Payment("CREDIT_CARD", billing);
    Order order = new Order("ORD-10029", customer, payment);
    b03Context = RenderContext.builder().put("order", order).build();

    // --- Workload B04: 100 Mixed Conditionals ---
    StringBuilder b04Tmpl = new StringBuilder("<div class=\"conditionals\">\n");
    RenderContext.Builder b04CtxBuilder = RenderContext.builder();
    for (int i = 0; i < 100; i++) {
      if (i % 3 == 0) {
        b04Tmpl
            .append("#if($cond_")
            .append(i)
            .append(")<span>True ")
            .append(i)
            .append("</span>#else<span>False</span>#end\n");
        b04CtxBuilder.put("cond_" + i, i % 2 == 0);
      } else if (i % 3 == 1) {
        b04Tmpl
            .append("#if($str_")
            .append(i)
            .append(")<span>Val: $str_")
            .append(i)
            .append("</span>#else<span>Empty</span>#end\n");
        b04CtxBuilder.put("str_" + i, i % 2 == 0 ? "text-" + i : "");
      } else {
        b04Tmpl
            .append("#if($num_")
            .append(i)
            .append(")<span>Number: $num_")
            .append(i)
            .append("</span>#else<span>Zero</span>#end\n");
        b04CtxBuilder.put("num_" + i, i % 2 == 0 ? i : 0);
      }
    }
    b04Tmpl.append("</div>\n");
    repo.put("b04_conditionals.vm", b04Tmpl.toString());
    b04Context = b04CtxBuilder.build();

    // --- Workload B08: Heavy Escaping ---
    StringBuilder b08Tmpl = new StringBuilder("<div class=\"escaped\">\n");
    RenderContext.Builder b08CtxBuilder = RenderContext.builder();
    for (int i = 0; i < 20; i++) {
      b08Tmpl.append("  <p>Escaped ").append(i).append(": $esc_").append(i).append("</p>\n");
      b08CtxBuilder.put(
          "esc_" + i, "<script>alert('XSS " + i + "');</script> & \"quotes\" 'single' <tag>");
    }
    b08Tmpl.append("</div>\n");
    repo.put("b08_escaping.vm", b08Tmpl.toString());
    b08Context = b08CtxBuilder.build();
    htmlEscaper = StandardEscapers.htmlText();
    rawHtmlToEscape =
        "<script>alert('XSS & payload');</script><div class=\"box\" id='main'>\"Hello & Welcome\"</div>";

    // --- Workload B11: Macros ---
    String b11Tmpl =
        "#macro(renderBadge $label $type)\n"
            + "  <span class=\"badge badge-$type\">$label</span>\n"
            + "#end\n"
            + "#macro(renderCard $title $desc $tag)\n"
            + "  <div class=\"card\">\n"
            + "    <h3>$title</h3>\n"
            + "    <p>$desc</p>\n"
            + "    #renderBadge($tag, 'primary')\n"
            + "  </div>\n"
            + "#end\n"
            + "#foreach($card in $cards)\n"
            + "  #renderCard($card.title, $card.desc, $card.tag)\n"
            + "#end\n";
    repo.put("b11_macros.vm", b11Tmpl);
    List<Card> cards = new ArrayList<>(25);
    for (int i = 0; i < 25; i++) {
      cards.add(new Card("Card Title " + i, "Detailed description for card " + i, "Tag" + i));
    }
    b11Context = RenderContext.builder().put("cards", cards).build();

    // --- Workload B12: Layouts ---
    repo.put(
        "layout.vm",
        "<!DOCTYPE html><html><head><title>$title</title></head><body>"
            + "<header>Nav Header</header><main>$screen_content</main><footer>Footer</footer></body></html>");
    repo.put(
        "screen.vm",
        "<h2>Dashboard for $user</h2><p>Items count: $itemCount</p>"
            + "<ul>#foreach($item in $items)<li>$item</li>#end</ul>");
    b12ScreenId = TemplateId.of("screen.vm");
    b12Context =
        RenderContext.builder()
            .put("title", "Dashboard")
            .put("user", "Alice")
            .put("itemCount", 4)
            .put("items", List.of("Item Alpha", "Item Beta", "Item Gamma", "Item Delta"))
            .build();

    // Initialize engines
    engine = VtlTemplateEngine.builder().repository(repo).executionTier(executionTier).build();

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.constant(TemplateId.of("layout.vm")))
            .build();
    layoutEngine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(executionTier)
            .layoutConfiguration(layoutConfig)
            .build();

    b01Template = engine.get("b01_static.vm");
    b02Template = engine.get("b02_scalars.vm");
    b03Template = engine.get("b03_deep.vm");
    b04Template = engine.get("b04_conditionals.vm");
    b08Template = engine.get("b08_escaping.vm");
    b11Template = engine.get("b11_macros.vm");
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (engine != null) {
      engine.close();
    }
    if (layoutEngine != null) {
      layoutEngine.close();
    }
  }

  @Benchmark
  public void b01_staticHtml(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    b01Template.render(b01Context, output);
    bh.consume(output);
  }

  @Benchmark
  public void b02_scalarVariables(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    b02Template.render(b02Context, output);
    bh.consume(output);
  }

  @Benchmark
  public void b03_deepPropertyChains(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    b03Template.render(b03Context, output);
    bh.consume(output);
  }

  @Benchmark
  public void b04_mixedConditionals(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    b04Template.render(b04Context, output);
    bh.consume(output);
  }

  @Benchmark
  public void b08_heavyEscapingTemplate(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    b08Template.render(b08Context, output);
    bh.consume(output);
  }

  @Benchmark
  public void b08_directEscaper(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    htmlEscaper.escape(rawHtmlToEscape, output);
    bh.consume(output);
  }

  @Benchmark
  public void b11_macros(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    b11Template.render(b11Context, output);
    bh.consume(output);
  }

  @Benchmark
  public void b12_layouts(Blackhole bh) throws IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    layoutEngine.render(b12ScreenId, b12Context, output);
    bh.consume(output);
  }
}
