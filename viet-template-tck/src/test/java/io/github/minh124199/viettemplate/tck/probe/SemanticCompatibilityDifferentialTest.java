package io.github.minh124199.viettemplate.tck.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Side-by-side differential test suite executing identical templates against Apache Velocity Engine
 * 2.4.1 and Viet Template reference interpreter.
 *
 * <p>Covers all 7 audited areas for Milestone M3.1: 1. Numeric truthiness 2. #set null-RHS behavior
 * 3. Bare null syntax rejection 4. Defined-null vs undefined reference rendering & escaping 5.
 * directive.if.empty_check configuration 6. Strict-reference interactions with null and
 * conditionals 7. Alternate-value behavior with zero and empty values
 */
class SemanticCompatibilityDifferentialTest {

  public static class ProbeService {
    public Object returnNull() {
      return null;
    }

    public Object getNullProperty() {
      return null;
    }
  }

  private String renderViet(
      String template, Map<String, Object> context, VtlInterpreterOptions options) {
    SourceText source = SourceText.of("test.vm", template);
    VtlParseResult parseResult = VtlParser.parse(source);
    if (parseResult.hasErrors()) {
      throw new IllegalArgumentException("Parse errors: " + parseResult.diagnostics());
    }
    VtlInterpreter interpreter = new VtlInterpreter(options);
    StringTemplateOutput output = new StringTemplateOutput();
    RenderContext renderContext = MapRenderContext.of(context);
    try {
      interpreter.interpret(parseResult.template(), source, renderContext, output);
    } catch (Exception e) {
      if (e instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException(e);
    }
    return output.toString();
  }

  private String renderViet(String template, Map<String, Object> context) {
    return renderViet(template, context, VtlInterpreterOptions.DEFAULT);
  }

  // =========================================================================
  // 1. NUMERIC TRUTHINESS & DUCKTYPE DISPATCH ORDER
  // =========================================================================

  @Test
  @DisplayName("Area 1: Numeric truthiness under default empty_check=true matches Velocity 2.4.1")
  void testNumericTruthinessWithDefaultEmptyCheck() {
    Map<String, Object> nums = new HashMap<>();
    nums.put("int0", 0);
    nums.put("int1", 1);
    nums.put("intNeg1", -1);
    nums.put("long0", 0L);
    nums.put("long1", 1L);
    nums.put("float0", 0.0f);
    nums.put("float1", 1.0f);
    nums.put("double0", 0.0);
    nums.put("double1", 1.0);
    nums.put("bigInt0", BigInteger.ZERO);
    nums.put("bigInt1", BigInteger.ONE);
    nums.put("bigDec0", BigDecimal.ZERO);
    nums.put("bigDec1", BigDecimal.ONE);

    for (String key : nums.keySet()) {
      String template = "#if($" + key + ")TRUE#{else}FALSE#end";
      String velocityOut = Velocity241Probe.render(template, nums).output();
      String vietOut = renderViet(template, nums);
      assertThat(vietOut)
          .as("Numeric truthiness for %s (%s)", key, nums.get(key))
          .isEqualTo(velocityOut);
    }

    // Literals 0 and 0.0
    for (String lit : List.of("0", "0.0", "1", "-1")) {
      String template = "#if(" + lit + ")TRUE#{else}FALSE#end";
      String velocityOut = Velocity241Probe.render(template).output();
      String vietOut = renderViet(template, Map.of());
      assertThat(vietOut).as("Literal truthiness for %s", lit).isEqualTo(velocityOut);
    }
  }

  @Test
  @DisplayName("Area 1 & 5: Numeric truthiness under empty_check=false matches Velocity 2.4.1")
  void testNumericTruthinessWithEmptyCheckDisabled() {
    Properties props = new Properties();
    props.setProperty("directive.if.empty_check", "false");

    VtlInterpreterOptions vietOptions = VtlInterpreterOptions.builder().emptyCheck(false).build();

    Map<String, Object> nums = new HashMap<>();
    nums.put("int0", 0);
    nums.put("double0", 0.0);
    nums.put("bigInt0", BigInteger.ZERO);
    nums.put("bigDec0", BigDecimal.ZERO);
    nums.put("emptyStr", "");
    nums.put("emptyList", Collections.emptyList());

    for (String key : nums.keySet()) {
      String template = "#if($" + key + ")TRUE#{else}FALSE#end";
      String velocityOut = Velocity241Probe.render(template, nums, props).output();
      String vietOut = renderViet(template, nums, vietOptions);
      assertThat(vietOut)
          .as("Truthiness with empty_check=false for %s (%s)", key, nums.get(key))
          .isEqualTo(velocityOut)
          .isEqualTo("TRUE");
    }
  }

  @Test
  @DisplayName(
      "Area 1: Custom getAsBoolean, isEmpty, size dispatch precedence matches Velocity 2.4.1")
  void testCustomMethodPrecedence() {
    SemanticCompatibilityProbeTest.CustomBooleanEmpty obj1 =
        new SemanticCompatibilityProbeTest.CustomBooleanEmpty(false, false);
    SemanticCompatibilityProbeTest.CustomBooleanEmpty obj2 =
        new SemanticCompatibilityProbeTest.CustomBooleanEmpty(true, true);

    Map<String, Object> ctx = Map.of("obj1", obj1, "obj2", obj2);
    String tpl1 = "#if($obj1)T#{else}F#end";
    String tpl2 = "#if($obj2)T#{else}F#end";

    assertThat(renderViet(tpl1, ctx))
        .isEqualTo(Velocity241Probe.render(tpl1, ctx).output())
        .isEqualTo("F");
    assertThat(renderViet(tpl2, ctx))
        .isEqualTo(Velocity241Probe.render(tpl2, ctx).output())
        .isEqualTo("T");
  }

  // =========================================================================
  // 2. #SET NULL-RHS BEHAVIOR
  // =========================================================================

  @Test
  @DisplayName("Area 2: #set null-RHS overwrites variable to null matching Velocity 2.x default")
  void testSetNullRhsBehavior() {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("probe", new ProbeService());

    // 1. Missing reference RHS
    String tpl1 = "#set($x = 'before')\n#set($x = $missing)\n[$x]";
    assertThat(renderViet(tpl1, ctx))
        .isEqualTo(Velocity241Probe.render(tpl1, ctx).output())
        .isEqualTo("[$x]");

    // 2. Method returning null RHS
    String tpl2 = "#set($x = 'before')\n#set($x = $probe.returnNull())\n[$x]";
    assertThat(renderViet(tpl2, ctx))
        .isEqualTo(Velocity241Probe.render(tpl2, ctx).output())
        .isEqualTo("[$x]");

    // 3. Property returning null RHS
    String tpl3 = "#set($x = 'before')\n#set($x = $probe.nullProperty)\n[$x]";
    assertThat(renderViet(tpl3, ctx))
        .isEqualTo(Velocity241Probe.render(tpl3, ctx).output())
        .isEqualTo("[$x]");

    // 4. Map property assignment to null
    Map<String, Object> map = new HashMap<>();
    map.put("k", "initial");
    Map<String, Object> ctxMap = new HashMap<>();
    ctxMap.put("map", map);
    ctxMap.put("probe", new ProbeService());
    String tplMap = "#set($map.k = $probe.returnNull())\n[$map.k]";
    assertThat(renderViet(tplMap, ctxMap))
        .isEqualTo(Velocity241Probe.render(tplMap, ctxMap).output())
        .isEqualTo("[$map.k]");
    assertThat(map.get("k")).isNull();

    // 5. List element assignment to null
    List<Object> list = new ArrayList<>();
    list.add("initial");
    Map<String, Object> ctxList = new HashMap<>();
    ctxList.put("list", list);
    ctxList.put("probe", new ProbeService());
    String tplList = "#set($list[0] = $probe.returnNull())\n[$list[0]]";
    assertThat(renderViet(tplList, ctxList))
        .isEqualTo(Velocity241Probe.render(tplList, ctxList).output())
        .isEqualTo("[$list[0]]");
    assertThat(list.get(0)).isNull();
  }

  // =========================================================================
  // 3. BARE NULL SYNTAX REJECTION
  // =========================================================================

  @Test
  @DisplayName("Area 3: Bare null syntax is rejected by default in both engines")
  void testBareNullSyntaxRejection() {
    String tpl = "#set($x = null)\n[$x]";

    // Velocity 2.4.1 rejects with ParseErrorException
    Velocity241Probe.Result velRes = Velocity241Probe.render(tpl);
    assertThat(velRes.isSuccess()).isFalse();

    // Viet Template rejects with parse errors
    SourceText source = SourceText.of("test.vm", tpl);
    VtlParseResult vietRes = VtlParser.parse(source);
    assertThat(vietRes.hasErrors()).isTrue();
    assertThat(vietRes.diagnostics().stream().anyMatch(d -> d.message().contains("null"))).isTrue();
  }

  // =========================================================================
  // 4 & 5. DEFINED-NULL VS UNDEFINED REFERENCE OUTPUT & ESCAPING
  // =========================================================================

  @Test
  @DisplayName("Area 4 & 5: Reference output in non-strict mode matches Velocity 2.4.1")
  void testReferenceOutputNonStrict() {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("probe", new ProbeService());
    ctx.put("myNull", null);
    ctx.put("str", "hello");

    String[] templates = {
      "[$missing]",
      "[$!missing]",
      "[${missing}]",
      "[$!{missing}]",
      "[$probe.nullProperty]",
      "[$!probe.nullProperty]",
      "[$probe.returnNull()]",
      "[$!probe.returnNull()]",
      "[$myNull]",
      "[$!myNull]",
      "[$str]",
      "[$!str]"
    };

    for (String tpl : templates) {
      String velocityOut = Velocity241Probe.render(tpl, ctx).output();
      String vietOut = renderViet(tpl, ctx);
      assertThat(vietOut).as("Rendering template: %s", tpl).isEqualTo(velocityOut);
    }
  }

  @Test
  @DisplayName("Area 4: Escaping with defined-null vs undefined matches Velocity 2.4.1")
  void testEscapingWithDefinedNullVsUndefined() {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("myNull", null);
    ctx.put("str", "hello");

    String[] templates = {
      "\\$missing",
      "\\\\$missing",
      "\\\\\\$missing",
      "\\$myNull",
      "\\\\$myNull",
      "\\\\\\$myNull",
      "\\$str",
      "\\\\$str",
      "\\\\\\$str"
    };

    for (String tpl : templates) {
      String velocityOut = Velocity241Probe.render(tpl, ctx).output();
      String vietOut = renderViet(tpl, ctx);
      assertThat(vietOut).as("Escaping template: %s", tpl).isEqualTo(velocityOut);
    }
  }

  // =========================================================================
  // 6. STRICT-REFERENCE INTERACTIONS WITH NULL & CONDITIONALS
  // =========================================================================

  @Test
  @DisplayName("Area 6: Strict mode conditionals and quiet null suppression match Velocity 2.4.1")
  void testStrictModeInteractions() {
    Properties strictProps = new Properties();
    strictProps.setProperty("runtime.strict_mode.enable", "true");

    VtlInterpreterOptions strictOptions =
        VtlInterpreterOptions.builder().strictReferences(true).build();

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("probe", new ProbeService());
    ctx.put("myNull", null);

    // 1. Conditionals evaluate cleanly to false without throwing
    String[] condTemplates = {
      "#if($missing)YES#{else}NO#end",
      "#if($!missing)YES#{else}NO#end",
      "#if($probe.nullProperty)YES#{else}NO#end",
      "#if($myNull)YES#{else}NO#end"
    };
    for (String tpl : condTemplates) {
      String velocityOut = Velocity241Probe.render(tpl, ctx, strictProps).output();
      String vietOut = renderViet(tpl, ctx, strictOptions);
      assertThat(vietOut)
          .as("Conditional in strict mode: %s", tpl)
          .isEqualTo(velocityOut)
          .isEqualTo("NO");
    }

    // 2. Quiet defined-null evaluates cleanly to empty string
    String[] quietNullTemplates = {
      "[$!probe.nullProperty]", "[$!probe.returnNull()]", "[$!myNull]"
    };
    for (String tpl : quietNullTemplates) {
      String velocityOut = Velocity241Probe.render(tpl, ctx, strictProps).output();
      String vietOut = renderViet(tpl, ctx, strictOptions);
      assertThat(vietOut)
          .as("Quiet defined null in strict mode: %s", tpl)
          .isEqualTo(velocityOut)
          .isEqualTo("[]");
    }

    // 3. Alternate value fallback evaluates cleanly without throwing
    String altTpl = "[${missing|'fallback'}]";
    String velocityAlt = Velocity241Probe.render(altTpl, ctx, strictProps).output();
    String vietAlt = renderViet(altTpl, ctx, strictOptions);
    assertThat(vietAlt).isEqualTo(velocityAlt).isEqualTo("[fallback]");

    // 4. Output rendering of undefined or non-quiet null throws in both
    assertThatThrownBy(() -> renderViet("[$missing]", ctx, strictOptions))
        .isInstanceOf(TemplateRenderException.class);
    assertThatThrownBy(() -> renderViet("[$!missing]", ctx, strictOptions))
        .isInstanceOf(TemplateRenderException.class);
    assertThatThrownBy(() -> renderViet("[$probe.nullProperty]", ctx, strictOptions))
        .isInstanceOf(TemplateRenderException.class);
    assertThatThrownBy(() -> renderViet("[$myNull]", ctx, strictOptions))
        .isInstanceOf(TemplateRenderException.class);
  }

  // =========================================================================
  // 7. ALTERNATE-VALUE BEHAVIOR WITH ZERO AND EMPTY VALUES
  // =========================================================================

  @Test
  @DisplayName("Area 7: Alternate-value behavior with zero and empty values matches Velocity 2.4.1")
  void testAlternateValueBehavior() {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("zero", 0);
    ctx.put("one", 1);
    ctx.put("emptyStr", "");
    ctx.put("str", "hello");
    ctx.put("probe", new ProbeService());

    // Under default empty_check=true
    String[] vars = {"missing", "zero", "one", "emptyStr", "str", "probe.nullProperty"};
    for (String v : vars) {
      String tpl = "[${" + v + "|'fallback'}]";
      String velocityOut = Velocity241Probe.render(tpl, ctx).output();
      String vietOut = renderViet(tpl, ctx);
      assertThat(vietOut)
          .as("Alternate value for %s with empty_check=true", v)
          .isEqualTo(velocityOut);
    }

    // In Velocity, alternate value fallback always checks emptiness regardless of
    // directive.if.empty_check
    Properties noEmptyProps = new Properties();
    noEmptyProps.setProperty("directive.if.empty_check", "false");
    VtlInterpreterOptions noEmptyOptions =
        VtlInterpreterOptions.builder().emptyCheck(false).build();

    String tplZero = "[${zero|'fallback'}]";
    String velZero = Velocity241Probe.render(tplZero, ctx, noEmptyProps).output();
    String vietZero = renderViet(tplZero, ctx, noEmptyOptions);
    assertThat(vietZero)
        .as("Alternate value for zero with empty_check=false")
        .isEqualTo(velZero)
        .isEqualTo("[fallback]");
  }
}
