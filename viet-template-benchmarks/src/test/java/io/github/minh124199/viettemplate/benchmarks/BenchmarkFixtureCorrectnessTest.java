package io.github.minh124199.viettemplate.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.LayoutResolver;
import io.github.minh124199.viettemplate.api.MutableRenderContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyKind;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.runtime.Escaper;
import io.github.minh124199.viettemplate.runtime.StandardEscapers;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.LinkerStatistics;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.interpreter.EvaluationValue;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionContext;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.ForeachMetadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Correctness verification test ensuring every template, context, and fixture used across all JMH
 * benchmarks renders deterministically and maintains 100% byte-for-byte output equivalence between
 * the reference interpreter (IR) and ahead-of-time bytecode (AOT_BYTECODE) execution backends.
 */
class BenchmarkFixtureCorrectnessTest {

  // --- End-to-End Rendering Parity Tests ---

  @Test
  @DisplayName("Verify Workloads B05, B06, B07 (Foreach Loops) render identically in IR and AOT")
  void verifyForeachRenderingParity() throws IOException {
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

    try (VtlTemplateEngine irEngine =
            VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
        VtlTemplateEngine aotEngine =
            VtlTemplateEngine.builder()
                .repository(repo)
                .executionTier(ExecutionTier.AOT_BYTECODE)
                .build()) {

      // 1. Small Table Context (B05)
      List<ForeachRenderingBenchmark.TableRow> smallRows = new ArrayList<>(10);
      for (int i = 0; i < 10; i++) {
        smallRows.add(
            new ForeachRenderingBenchmark.TableRow(
                "Item-" + i, i * 10, "Description " + i, i * 1.5, i % 2 == 0));
      }
      RenderContext smallContext = RenderContext.builder().put("table", smallRows).build();

      String irSmall = renderToString(irEngine.get("table.vm"), smallContext);
      String aotSmall = renderToString(aotEngine.get("table.vm"), smallContext);
      assertThat(aotSmall).isNotEmpty().isEqualTo(irSmall);

      // Verify Utf8OutputStreamTemplateOutput parity between IR and AOT
      ByteArrayOutputStream irBaos = new ByteArrayOutputStream();
      try (Utf8OutputStreamTemplateOutput irUtf8Out = new Utf8OutputStreamTemplateOutput(irBaos)) {
        irEngine.get("table.vm").render(smallContext, irUtf8Out);
        irUtf8Out.flush();
      }
      ByteArrayOutputStream aotBaos = new ByteArrayOutputStream();
      try (Utf8OutputStreamTemplateOutput aotUtf8Out =
          new Utf8OutputStreamTemplateOutput(aotBaos)) {
        aotEngine.get("table.vm").render(smallContext, aotUtf8Out);
        aotUtf8Out.flush();
      }
      assertThat(aotBaos.toString(StandardCharsets.UTF_8))
          .isNotEmpty()
          .isEqualTo(irBaos.toString(StandardCharsets.UTF_8));

      // 2. Large Table Context (B06 - verify smaller slice for fast assertion)
      List<ForeachRenderingBenchmark.TableRow> largeRows = new ArrayList<>(50);
      for (int i = 0; i < 50; i++) {
        largeRows.add(
            new ForeachRenderingBenchmark.TableRow(
                "Prod-" + i, i * 100, "Specs " + i, i * 2.5, i % 2 == 0));
      }
      RenderContext largeContext = RenderContext.builder().put("table", largeRows).build();
      String irLarge = renderToString(irEngine.get("table.vm"), largeContext);
      String aotLarge = renderToString(aotEngine.get("table.vm"), largeContext);
      assertThat(aotLarge).isNotEmpty().isEqualTo(irLarge);

      // 3. Nested Loop Context (B07)
      List<ForeachRenderingBenchmark.MatrixRow> matrix = new ArrayList<>(5);
      for (int o = 0; o < 5; o++) {
        List<ForeachRenderingBenchmark.NestedItem> items = new ArrayList<>(3);
        for (int in = 0; in < 3; in++) {
          items.add(new ForeachRenderingBenchmark.NestedItem("Sub-" + o + "-" + in, o * 10 + in));
        }
        matrix.add(new ForeachRenderingBenchmark.MatrixRow(o, "Cat-" + o, items));
      }
      RenderContext nestedContext = RenderContext.builder().put("matrix", matrix).build();
      String irNested = renderToString(irEngine.get("nested.vm"), nestedContext);
      String aotNested = renderToString(aotEngine.get("nested.vm"), nestedContext);
      assertThat(aotNested).isNotEmpty().isEqualTo(irNested);
    }
  }

  @Test
  @DisplayName(
      "Verify Workloads B01, B02, B03, B04, B08, B11, B12 render identically in IR and AOT")
  void verifyEndToEndWorkloadsParity() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();

    // Workload B01: Static HTML
    StringBuilder b01Sb = new StringBuilder(20480);
    b01Sb.append("<!DOCTYPE html><html><head><title>Static Page</title></head><body>\n");
    while (b01Sb.length() < 20480) {
      b01Sb.append(
          "<div class=\"section\"><p>Lorem ipsum dolor sit amet, consectetur adipiscing"
              + " elit.</p></div>\n");
    }
    b01Sb.append("</body></html>\n");
    repo.put("b01.vm", b01Sb.toString());

    // Workload B02: 50 Scalars (at method split threshold 50)
    StringBuilder b02Tmpl = new StringBuilder("<div class=\"scalars\">\n");
    RenderContext.Builder b02Ctx = RenderContext.builder();
    for (int i = 0; i < 50; i++) {
      b02Tmpl.append("  <span>Val ").append(i).append(": $var_").append(i).append("</span>\n");
      b02Ctx.put("var_" + i, "ScalarVal_" + i);
    }
    b02Tmpl.append("</div>\n");
    repo.put("b02.vm", b02Tmpl.toString());

    // Workload B03: Deep Property Chains
    String b03Tmpl =
        "<div class=\"order\">\n"
            + "  <h2>Order: $order.id</h2>\n"
            + "  <p>Customer: $order.customer.name</p>\n"
            + "  <p>City: $order.customer.address.city.name"
            + " ($order.customer.address.city.code)</p>\n"
            + "  <p>Postal: $order.payment.billing.postalCode</p>\n"
            + "</div>\n";
    repo.put("b03.vm", b03Tmpl);
    RenderingEndToEndBenchmark.City city =
        new RenderingEndToEndBenchmark.City("San Francisco", "CA-94105");
    RenderingEndToEndBenchmark.Address address =
        new RenderingEndToEndBenchmark.Address("500 Howard Street", city);
    RenderingEndToEndBenchmark.Customer customer =
        new RenderingEndToEndBenchmark.Customer("Alice", address);
    RenderingEndToEndBenchmark.Billing billing =
        new RenderingEndToEndBenchmark.Billing("B1", "94105");
    RenderingEndToEndBenchmark.Payment payment =
        new RenderingEndToEndBenchmark.Payment("CARD", billing);
    RenderingEndToEndBenchmark.Order order =
        new RenderingEndToEndBenchmark.Order("ORD-1", customer, payment);
    RenderContext b03Ctx = RenderContext.builder().put("order", order).build();

    // Workload B04: Mixed Conditionals
    StringBuilder b04Tmpl = new StringBuilder("<div class=\"conditionals\">\n");
    RenderContext.Builder b04Ctx = RenderContext.builder();
    for (int i = 0; i < 100; i++) {
      b04Tmpl
          .append("#if($cond_")
          .append(i)
          .append(")<span>True ")
          .append(i)
          .append("</span>#else<span>False</span>#end\n");
      b04Ctx.put("cond_" + i, i % 2 == 0);
    }
    b04Tmpl.append("</div>\n");
    repo.put("b04.vm", b04Tmpl.toString());

    // Workload B08: Heavy Escaping
    StringBuilder b08Tmpl = new StringBuilder("<div class=\"escaped\">\n");
    RenderContext.Builder b08Ctx = RenderContext.builder();
    for (int i = 0; i < 5; i++) {
      b08Tmpl.append("<p>$esc_").append(i).append("</p>\n");
      b08Ctx.put("esc_" + i, "<script>alert('XSS');</script> & 'quotes'");
    }
    b08Tmpl.append("</div>\n");
    repo.put("b08.vm", b08Tmpl.toString());

    // Workload B11: Macros
    String b11Tmpl =
        "#macro(renderTag $tag)\n"
            + "  <span class=\"tag\">$tag</span>\n"
            + "#end\n"
            + "#macro(renderItem $title $tag)\n"
            + "  <div class=\"item\"><h3>$title</h3>#renderTag($tag)</div>\n"
            + "#end\n"
            + "#foreach($c in $cards)\n"
            + "  #renderItem($c.title, $c.tag)\n"
            + "#end\n";
    repo.put("b11.vm", b11Tmpl);
    List<RenderingEndToEndBenchmark.Card> cards =
        List.of(
            new RenderingEndToEndBenchmark.Card("Card1", "Desc1", "TagA"),
            new RenderingEndToEndBenchmark.Card("Card2", "Desc2", "TagB"));
    RenderContext b11Ctx = RenderContext.builder().put("cards", cards).build();

    // Workload B12: Layouts
    repo.put(
        "layout.vm", "<html><head><title>$title</title></head><body>$screen_content</body></html>");
    repo.put("screen.vm", "<h2>User: $user</h2><p>Items: $itemCount</p>");
    TemplateId screenId = TemplateId.of("screen.vm");
    RenderContext b12Ctx =
        RenderContext.builder()
            .put("title", "Dashboard")
            .put("user", "Alice")
            .put("itemCount", 3)
            .put("items", List.of("A", "B", "C"))
            .build();

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.constant(TemplateId.of("layout.vm")))
            .build();

    try (VtlTemplateEngine irEngine =
            VtlTemplateEngine.builder()
                .repository(repo)
                .executionTier(ExecutionTier.IR)
                .layoutConfiguration(layoutConfig)
                .build();
        VtlTemplateEngine aotEngine =
            VtlTemplateEngine.builder()
                .repository(repo)
                .executionTier(ExecutionTier.AOT_BYTECODE)
                .layoutConfiguration(layoutConfig)
                .build()) {

      // Test B01
      assertThat(renderToString(aotEngine.get("b01.vm"), RenderContext.empty()))
          .isEqualTo(renderToString(irEngine.get("b01.vm"), RenderContext.empty()));

      // Test B02
      assertThat(renderToString(aotEngine.get("b02.vm"), b02Ctx.build()))
          .isEqualTo(renderToString(irEngine.get("b02.vm"), b02Ctx.build()));

      // Test B03
      assertThat(renderToString(aotEngine.get("b03.vm"), b03Ctx))
          .isEqualTo(renderToString(irEngine.get("b03.vm"), b03Ctx));

      // Test B04
      assertThat(renderToString(aotEngine.get("b04.vm"), b04Ctx.build()))
          .isEqualTo(renderToString(irEngine.get("b04.vm"), b04Ctx.build()));

      // Test B08
      assertThat(renderToString(aotEngine.get("b08.vm"), b08Ctx.build()))
          .isEqualTo(renderToString(irEngine.get("b08.vm"), b08Ctx.build()));

      // Test B11
      assertThat(renderToString(aotEngine.get("b11.vm"), b11Ctx))
          .isEqualTo(renderToString(irEngine.get("b11.vm"), b11Ctx));

      // Test B12
      StringTemplateOutput irLayoutOut = new StringTemplateOutput();
      irEngine.render(screenId, b12Ctx, irLayoutOut);

      StringTemplateOutput aotLayoutOut = new StringTemplateOutput();
      aotEngine.render(screenId, b12Ctx, aotLayoutOut);

      assertThat(aotLayoutOut.toString()).isNotEmpty().isEqualTo(irLayoutOut.toString());
    }
  }

  // --- Isolated Microbenchmark Component Correctness Tests ---

  @Test
  @DisplayName("Verify VariableLookupBenchmark lookup mechanics")
  void verifyVariableLookup() {
    RenderContext root = RenderContext.builder().put("rootVar", "rootValue").build();
    ExecutionContext ctx = new ExecutionContext(root);
    ctx.set("templateVar", EvaluationValue.of("templateValue"));

    for (int i = 1; i <= 4; i++) {
      Map<String, EvaluationValue> bindings = new HashMap<>();
      if (i == 1) {
        bindings.put("outerVar", EvaluationValue.of("outerValue"));
      }
      bindings.put("level" + i, EvaluationValue.of(i));
      if (i == 4) {
        bindings.put("innerVar", EvaluationValue.of("innerValue"));
      }
      ctx.pushScope(bindings, false);
    }

    assertThat(ctx.lookup("innerVar").asObjectOrNull()).isEqualTo("innerValue");
    assertThat(ctx.lookup("outerVar").asObjectOrNull()).isEqualTo("outerValue");
    assertThat(ctx.lookup("templateVar").asObjectOrNull()).isEqualTo("templateValue");
    assertThat(ctx.lookup("rootVar").asObjectOrNull()).isEqualTo("rootValue");
    assertThat(ctx.lookup("missing").isDefined()).isFalse();
  }

  @Test
  @DisplayName("Verify NestedScopeBenchmark push/pop and traversal")
  void verifyNestedScopes() {
    RenderContext root = RenderContext.builder().put("root", "val").build();
    ExecutionContext ctx = new ExecutionContext(root);

    ForeachMetadata parentMeta = new ForeachMetadata(0, 1, true, false, true, null);
    ctx.pushForeachScope("outer", EvaluationValue.of("outVal"), parentMeta);

    ForeachMetadata innerMeta = new ForeachMetadata(0, 1, true, false, true, parentMeta);
    ctx.pushForeachScope("inner", EvaluationValue.of("inVal"), innerMeta);

    Map<String, EvaluationValue> macroBindings = Map.of("param", EvaluationValue.of("paramVal"));
    ctx.pushScope(macroBindings, false);

    assertThat(ctx.lookup("param").asObjectOrNull()).isEqualTo("paramVal");
    assertThat(ctx.lookup("inner").asObjectOrNull()).isEqualTo("inVal");
    assertThat(ctx.lookup("outer").asObjectOrNull()).isEqualTo("outVal");

    ctx.popScope();
    assertThat(ctx.lookup("param").isDefined()).isFalse();
    assertThat(ctx.lookup("inner").asObjectOrNull()).isEqualTo("inVal");

    ctx.popScope();
    assertThat(ctx.lookup("inner").isDefined()).isFalse();
    assertThat(ctx.lookup("outer").asObjectOrNull()).isEqualTo("outVal");

    ctx.popScope();
    assertThat(ctx.lookup("outer").isDefined()).isFalse();
  }

  @Test
  @DisplayName("Verify VariableAssignmentBenchmark assignment paths")
  void verifyVariableAssignment() {
    MutableRenderContext mutableRoot = MutableRenderContext.of();
    ExecutionContext ctx = new ExecutionContext(mutableRoot);

    // Template-local
    ctx.set("temp", EvaluationValue.of("tempVal"));
    assertThat(ctx.lookup("temp").asObjectOrNull()).isEqualTo("tempVal");
    assertThat(mutableRoot.get("temp")).isEqualTo("tempVal"); // write-through

    // Local scope update
    Map<String, EvaluationValue> scope = new HashMap<>();
    scope.put("localKey", EvaluationValue.of("initial"));
    ctx.pushScope(scope, false);

    ctx.set("localKey", EvaluationValue.of("updated"));
    assertThat(ctx.lookup("localKey").asObjectOrNull()).isEqualTo("updated");
    ctx.popScope();
  }

  @Test
  @DisplayName("Verify CompileCacheBenchmark and CacheInvalidationBenchmark mechanics")
  void verifyCacheAndInvalidation() {
    TemplateCompileCache cache = new TemplateCompileCache(100, 5000L, 50);

    TemplateId id1 = TemplateId.of("t1.vm");
    CompileCacheKey key1 =
        CompileCacheKey.of(
            id1, "hash1", "0.2.0", OptimizationLevel.O2, ExecutionTier.IR, "std", "model", "opts");
    CompiledTemplateHandle handle1 = CompiledTemplateHandle.ofIr(id1, 1L, key1, null);

    cache.put(key1, handle1);
    assertThat(cache.get(key1)).isPresent();
    assertThat(cache.getActive(id1)).isPresent();

    // Invalidation
    cache.invalidate(id1);
    assertThat(cache.get(key1)).isEmpty();
    assertThat(cache.getActive(id1)).isEmpty();

    // Invalidate with dependents
    TemplateId rootId = TemplateId.of("root.vm");
    TemplateId depId = TemplateId.of("dep.vm");
    DefaultTemplateDependencyGraph graph = new DefaultTemplateDependencyGraph();
    graph.replaceDependencies(
        rootId, Set.of(TemplateDependency.of(rootId, depId, TemplateDependencyKind.STATIC_PARSE)));

    CompileCacheKey keyRoot =
        CompileCacheKey.of(
            rootId,
            "hashR",
            "0.2.0",
            OptimizationLevel.O2,
            ExecutionTier.IR,
            "std",
            "model",
            "opts");
    CompileCacheKey keyDep =
        CompileCacheKey.of(
            depId,
            "hashD",
            "0.2.0",
            OptimizationLevel.O2,
            ExecutionTier.IR,
            "std",
            "model",
            "opts");
    cache.put(keyRoot, CompiledTemplateHandle.ofIr(rootId, 1L, keyRoot, null));
    cache.put(keyDep, CompiledTemplateHandle.ofIr(depId, 1L, keyDep, null));

    Set<TemplateId> invalidated = cache.invalidateWithDependents(depId, graph);
    assertThat(invalidated).contains(depId, rootId);
    assertThat(cache.getActive(depId)).isEmpty();
    assertThat(cache.getActive(rootId)).isEmpty();
  }

  @Test
  @DisplayName("Verify CallSiteBenchmark and MegamorphicCallSiteBenchmark states")
  void verifyCallSites() throws Throwable {
    DynamicLinker linker = new DynamicLinker(LinkerAccessPolicy.standard());
    MemberKey key = MemberKey.propertyGet("value");

    DynamicCallSite site =
        new DynamicCallSite(1, key, LinkerAccessPolicy.standard(), linker, new LinkerStatistics());
    assertThat(site.state()).isEqualTo(DynamicCallSite.State.UNLINKED);

    CallSiteBenchmark.TypeA a = new CallSiteBenchmark.TypeA("Alpha");
    CallSiteBenchmark.TypeB b = new CallSiteBenchmark.TypeB("Beta");
    CallSiteBenchmark.TypeC c = new CallSiteBenchmark.TypeC("Gamma");
    CallSiteBenchmark.TypeD d = new CallSiteBenchmark.TypeD("Delta");

    assertThat(site.invoke(a)).isEqualTo("Alpha");
    assertThat(site.state()).isEqualTo(DynamicCallSite.State.MONOMORPHIC);

    assertThat(site.invoke(b)).isEqualTo("Beta");
    assertThat(site.state()).isEqualTo(DynamicCallSite.State.POLYMORPHIC);

    site.invoke(c);
    site.invoke(d);
    assertThat(site.state()).isEqualTo(DynamicCallSite.State.POLYMORPHIC);

    // 5th shape forces MEGAMORPHIC
    MegamorphicCallSiteBenchmark.Type04 type04 = new MegamorphicCallSiteBenchmark.Type04("Val04");
    assertThat(site.invoke(type04)).isEqualTo("Val04");
    assertThat(site.state()).isEqualTo(DynamicCallSite.State.MEGAMORPHIC);

    // Verify Megamorphic cache hit for previously linked type
    assertThat(site.invoke(a)).isEqualTo("Alpha");
  }

  @Test
  @DisplayName("Verify StandardEscapers.htmlText escapes unsafe characters properly")
  void verifyEscapers() throws IOException {
    Escaper escaper = StandardEscapers.htmlText();
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("<script>alert('XSS & \"test\"');</script>", out);

    assertThat(out.toString())
        .isEqualTo("&lt;script&gt;alert(&#39;XSS &amp; &quot;test&quot;&#39;);&lt;/script&gt;");
  }

  private static String renderToString(Template template, RenderContext context)
      throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(context, out);
    return out.toString();
  }
}
