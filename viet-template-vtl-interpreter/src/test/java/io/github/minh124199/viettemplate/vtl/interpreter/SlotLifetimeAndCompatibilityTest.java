package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.MutableRenderContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Section 19: 18 mandatory semantic regression tests covering variable slot lifetimes, Velocity
 * compatibility invariants, 3-state evaluation semantics, and IR == AOT equivalence.
 */
class SlotLifetimeAndCompatibilityTest {

  public record Item(boolean enabled, String val) {}

  private String render(
      String template,
      RenderContext context,
      ExecutionTier tier,
      Consumer<VtlInterpreterOptions.Builder> customizer) {
    SourceText source = SourceText.of("test.vtl", template);
    VtlParseResult parseResult = VtlParser.parse(source);
    if (parseResult.hasErrors()) {
      throw new AssertionError("Parse errors: " + parseResult.diagnostics());
    }
    VtlTemplate ast = parseResult.template();
    VtlInterpreterOptions.Builder builder =
        VtlInterpreterOptions.builder().executionTier(tier).profile(VtlProfile.VTL_DYNAMIC);
    if (customizer != null) {
      customizer.accept(builder);
    }
    VtlInterpreterOptions options = builder.build();
    VtlInterpreter interpreter = new VtlInterpreter(options);
    StringTemplateOutput output = new StringTemplateOutput();
    try {
      interpreter.render(source, ast, context, output);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return output.toString();
  }

  private String render(String template, Map<String, Object> context, ExecutionTier tier) {
    return render(template, MapRenderContext.of(context), tier, null);
  }

  private String render(
      String template,
      Map<String, Object> context,
      ExecutionTier tier,
      Consumer<VtlInterpreterOptions.Builder> customizer) {
    return render(template, MapRenderContext.of(context), tier, customizer);
  }

  // 1. root x exists + skipped #set(x)
  @Test
  @DisplayName("1. root x exists + skipped #set(x) with setNullAllowed=false preserves original")
  void scenario01_rootExists_skippedSet() {
    String template = "#set($x = $missing)$x";
    Map<String, Object> ctx = Map.of("x", "original");
    Consumer<VtlInterpreterOptions.Builder> opts = b -> b.setNullAllowed(false);

    String irOut = render(template, ctx, ExecutionTier.IR, opts);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE, opts);

    assertThat(irOut).isEqualTo("original");
    assertThat(aotOut).isEqualTo("original");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 2. root x absent + skipped #set(x)
  @Test
  @DisplayName("2. root x absent + skipped #set(x) with setNullAllowed=false renders empty quiet")
  void scenario02_rootAbsent_skippedSet() {
    String template = "#set($x = $missing)[$!x]";
    Map<String, Object> ctx = Map.of();
    Consumer<VtlInterpreterOptions.Builder> opts = b -> b.setNullAllowed(false);

    String irOut = render(template, ctx, ExecutionTier.IR, opts);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE, opts);

    assertThat(irOut).isEqualTo("[]");
    assertThat(aotOut).isEqualTo("[]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 3. root x is DEFINED_NULL
  @Test
  @DisplayName(
      "3. root x is DEFINED_NULL: #set($x = $missing)[$!x] where root context has x = null")
  void scenario03_rootIsDefinedNull() {
    String template = "#set($x = $missing)[$!x]";
    Map<String, Object> map = new HashMap<>();
    map.put("x", null);
    RenderContext ctx = MapRenderContext.of(map);

    String irOut = render(template, ctx, ExecutionTier.IR, null);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE, null);

    assertThat(irOut).isEqualTo("[]");
    assertThat(aotOut).isEqualTo("[]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 4. effective #set overrides inherited value
  @Test
  @DisplayName("4. effective #set overrides inherited value")
  void scenario04_effectiveSetOverridesInheritedValue() {
    String template = "#set($x = 'new')$x";
    Map<String, Object> ctx = Map.of("x", "original");

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("new");
    assertThat(aotOut).isEqualTo("new");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 5. null-RHS #set preserves existing semantics
  @Test
  @DisplayName("5. null-RHS #set preserves existing semantics: setNullAllowed=true and false")
  void scenario05_nullRhsSetPreservesExistingSemantics() {
    Map<String, Object> ctx = Map.of("x", "original");

    // 5a. setNullAllowed = true: overwrites with null, quiet reference renders empty
    String t1 = "#set($x = $missing)[$!x]";
    Consumer<VtlInterpreterOptions.Builder> optTrue = b -> b.setNullAllowed(true);
    String irOut1 = render(t1, ctx, ExecutionTier.IR, optTrue);
    String aotOut1 = render(t1, ctx, ExecutionTier.AOT_BYTECODE, optTrue);

    assertThat(irOut1).isEqualTo("[]");
    assertThat(aotOut1).isEqualTo("[]");
    assertThat(aotOut1).isEqualTo(irOut1);

    // 5b. setNullAllowed = false: skips assignment, leaves original value
    String t2 = "#set($x = $missing)$x";
    Consumer<VtlInterpreterOptions.Builder> optFalse = b -> b.setNullAllowed(false);
    String irOut2 = render(t2, ctx, ExecutionTier.IR, optFalse);
    String aotOut2 = render(t2, ctx, ExecutionTier.AOT_BYTECODE, optFalse);

    assertThat(irOut2).isEqualTo("original");
    assertThat(aotOut2).isEqualTo("original");
    assertThat(aotOut2).isEqualTo(irOut2);
  }

  // 6. repeated foreach iterations do not leak scope-owned values
  @Test
  @DisplayName("6. repeated foreach iterations do not leak scope-owned values")
  void scenario06_repeatedForeachIterationsDoNotLeakScopeOwnedValues() {
    String template =
        "#foreach($item in $items)#if($item.enabled)#set($x = $item.val)#end[$!x]#end";
    Map<String, Object> ctx = Map.of("items", List.of(new Item(true, "A"), new Item(false, "B")));

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("[A][]");
    assertThat(aotOut).isEqualTo("[A][]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 7. nested foreach preserves independent item variables
  @Test
  @DisplayName("7. nested foreach preserves independent item variables")
  void scenario07_nestedForeachPreservesIndependentItemVariables() {
    String template =
        "#foreach($outer in $outerList)#foreach($inner in $innerList)[$outer:$inner]#end#end";
    Map<String, Object> ctx =
        Map.of("outerList", List.of("1", "2"), "innerList", List.of("a", "b"));

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("[1:a][1:b][2:a][2:b]");
    assertThat(aotOut).isEqualTo("[1:a][1:b][2:a][2:b]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 8. nested foreach preserves $foreach.parent
  @Test
  @DisplayName("8. nested foreach preserves $foreach.parent")
  void scenario08_nestedForeachPreservesForeachParent() {
    String template =
        "#foreach($outer in $outerList)#foreach($inner in"
            + " $innerList)($foreach.parent.count:$foreach.count)#end#end";
    Map<String, Object> ctx =
        Map.of("outerList", List.of("a", "b"), "innerList", List.of("1", "2"));

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("(1:1)(1:2)(2:1)(2:2)");
    assertThat(aotOut).isEqualTo("(1:1)(1:2)(2:1)(2:2)");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 9. macro parameters reset between calls
  @Test
  @DisplayName("9. macro parameters reset between calls")
  void scenario09_macroParametersResetBetweenCalls() {
    String template = "#macro(m $param)[$param]#end#m('arg1')#m('arg2')";
    Map<String, Object> ctx = Map.of();

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("[arg1][arg2]");
    assertThat(aotOut).isEqualTo("[arg1][arg2]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 10. macro locals reset between calls
  @Test
  @DisplayName("10. macro locals reset between calls")
  void scenario10_macroLocalsResetBetweenCalls() {
    String template =
        "#macro(testLocal $takeBranch)#if($takeBranch)#set($loc ="
            + " 'defined')#end[$!loc]#end#testLocal(true)#testLocal(false)";
    Map<String, Object> ctx = Map.of();

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("[defined][]");
    assertThat(aotOut).isEqualTo("[defined][]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 11. nested macro calls preserve caller values
  @Test
  @DisplayName("11. nested macro calls preserve caller values")
  void scenario11_nestedMacroCallsPreserveCallerValues() {
    String template =
        "#macro(m2)#set($v = 'inner')[inner:$v]#end#macro(m1)#set($v ="
            + " 'outer')[before:$v]#m2()[after:$v]#end#m1()";
    Map<String, Object> ctx = Map.of();

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("[before:outer][inner:inner][after:outer]");
    assertThat(aotOut).isEqualTo("[before:outer][inner:inner][after:outer]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 12. recursive macro behavior
  @Test
  @DisplayName("12. recursive macro behavior: #rec(3) outputs [3[2[1]2]3]")
  void scenario12_recursiveMacroBehavior() {
    String template = "#macro(rec $d)#if($d > 1)[$d#rec($d - 1)$d]#{else}[1]#end#end#rec(3)";
    Map<String, Object> ctx = Map.of();

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("[3[2[1]2]3]");
    assertThat(aotOut).isEqualTo("[3[2[1]2]3]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 13. static-slot read after dynamic/fallback write
  @Test
  @DisplayName("13. static-slot read after dynamic/fallback write (#evaluate)")
  void scenario13_staticSlotReadAfterDynamicFallbackWrite() {
    String template = "#evaluate('#set($x = \"eval_val\")')$x";
    Map<String, Object> ctx = Map.of();

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("eval_val");
    assertThat(aotOut).isEqualTo("eval_val");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 14. dynamic/fallback read after static-slot write
  @Test
  @DisplayName("14. dynamic/fallback read after static-slot write")
  void scenario14_dynamicFallbackReadAfterStaticSlotWrite() {
    String template = "#set($x = 'val')$map.get($x)|$x.toUpperCase()";
    Map<String, Object> ctx = Map.of("map", new HashMap<>(Map.of("val", "dynamic_success")));

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("dynamic_success|VAL");
    assertThat(aotOut).isEqualTo("dynamic_success|VAL");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 15. mutable-root write-through
  @Test
  @DisplayName(
      "15. mutable-root write-through: #set($x = 'updated') writes through to MutableRenderContext")
  void scenario15_mutableRootWriteThrough() {
    String template = "#set($x = 'updated')";

    // Test IR tier write-through
    MutableRenderContext mrcIr = MutableRenderContext.of(new HashMap<>());
    render(template, mrcIr, ExecutionTier.IR, null);
    assertThat(mrcIr.get("x")).isEqualTo("updated");

    // Test AOT tier write-through
    MutableRenderContext mrcAot = MutableRenderContext.of(new HashMap<>());
    render(template, mrcAot, ExecutionTier.AOT_BYTECODE, null);
    assertThat(mrcAot.get("x")).isEqualTo("updated");
  }

  // 16. strict-reference mode with fresh undefined slot
  @Test
  @DisplayName(
      "16. strict-reference mode with fresh undefined slot throws TemplateRenderException with"
          + " VARIABLE_UNDEFINED")
  void scenario16_strictReferenceModeWithFreshUndefinedSlot() {
    String template = "#if($never)#set($fresh = 'val')#end$fresh";
    Map<String, Object> ctx = Map.of("never", false);
    Consumer<VtlInterpreterOptions.Builder> opts = b -> b.strictReferences(true);

    assertThatThrownBy(() -> render(template, ctx, ExecutionTier.IR, opts))
        .isInstanceOf(TemplateRenderException.class)
        .satisfies(
            ex -> {
              TemplateRenderException tre = (TemplateRenderException) ex;
              assertThat(tre.code()).contains(InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
            });

    assertThatThrownBy(() -> render(template, ctx, ExecutionTier.AOT_BYTECODE, opts))
        .isInstanceOf(TemplateRenderException.class)
        .satisfies(
            ex -> {
              TemplateRenderException tre = (TemplateRenderException) ex;
              assertThat(tre.code()).contains(InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
            });
  }

  // 17. lexical shadowing with same variable name
  @Test
  @DisplayName("17. lexical shadowing with same variable name in loop and macro")
  void scenario17_lexicalShadowingWithSameVariableName() {
    String template =
        "#macro(shadow $x)[$x]#end"
            + "#set($x = 'outer')"
            + "#foreach($x in $items)[$x]#end"
            + "[$x]"
            + "#shadow('inner')"
            + "[$x]";
    Map<String, Object> ctx = Map.of("items", List.of("a", "b"));

    String irOut = render(template, ctx, ExecutionTier.IR);
    String aotOut = render(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(irOut).isEqualTo("[a][b][outer][inner][outer]");
    assertThat(aotOut).isEqualTo("[a][b][outer][inner][outer]");
    assertThat(aotOut).isEqualTo(irOut);
  }

  // 18. IR == AOT output for all above scenarios (automated equivalence assertions)
  record EquivalenceScenario(
      String name,
      String template,
      Map<String, Object> context,
      String expected,
      Consumer<VtlInterpreterOptions.Builder> customizer) {}

  static Stream<Arguments> scenario18EquivalenceCases() {
    return Stream.of(
        Arguments.of(
            new EquivalenceScenario(
                "1. root x exists + skipped #set(x)",
                "#set($x = $missing)$x",
                Map.of("x", "original"),
                "original",
                b -> b.setNullAllowed(false))),
        Arguments.of(
            new EquivalenceScenario(
                "2. root x absent + skipped #set(x)",
                "#set($x = $missing)[$!x]",
                Map.of(),
                "[]",
                b -> b.setNullAllowed(false))),
        Arguments.of(
            new EquivalenceScenario(
                "4. effective #set overrides inherited value",
                "#set($x = 'new')$x",
                Map.of("x", "original"),
                "new",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "5a. null-RHS #set with setNullAllowed=true",
                "#set($x = $missing)[$!x]",
                Map.of("x", "original"),
                "[]",
                b -> b.setNullAllowed(true))),
        Arguments.of(
            new EquivalenceScenario(
                "5b. null-RHS #set with setNullAllowed=false",
                "#set($x = $missing)$x",
                Map.of("x", "original"),
                "original",
                b -> b.setNullAllowed(false))),
        Arguments.of(
            new EquivalenceScenario(
                "6. foreach does not leak scope-owned values",
                "#foreach($item in $items)#if($item.enabled)#set($x = $item.val)#end[$!x]#end",
                Map.of("items", List.of(new Item(true, "A"), new Item(false, "B"))),
                "[A][]",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "7. nested foreach independent items",
                "#foreach($outer in $outerList)#foreach($inner in"
                    + " $innerList)[$outer:$inner]#end#end",
                Map.of("outerList", List.of("1", "2"), "innerList", List.of("a", "b")),
                "[1:a][1:b][2:a][2:b]",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "8. nested foreach preserves $foreach.parent",
                "#foreach($outer in $outerList)#foreach($inner in"
                    + " $innerList)($foreach.parent.count:$foreach.count)#end#end",
                Map.of("outerList", List.of("a", "b"), "innerList", List.of("1", "2")),
                "(1:1)(1:2)(2:1)(2:2)",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "9. macro parameters reset between calls",
                "#macro(m $param)[$param]#end#m('arg1')#m('arg2')",
                Map.of(),
                "[arg1][arg2]",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "10. macro locals reset between calls",
                "#macro(testLocal $takeBranch)#if($takeBranch)#set($loc ="
                    + " 'defined')#end[$!loc]#end#testLocal(true)#testLocal(false)",
                Map.of(),
                "[defined][]",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "11. nested macro calls preserve caller values",
                "#macro(m2)#set($v = 'inner')[inner:$v]#end#macro(m1)#set($v ="
                    + " 'outer')[before:$v]#m2()[after:$v]#end#m1()",
                Map.of(),
                "[before:outer][inner:inner][after:outer]",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "12. recursive macro behavior",
                "#macro(rec $d)#if($d > 1)[$d#rec($d - 1)$d]#{else}[1]#end#end#rec(3)",
                Map.of(),
                "[3[2[1]2]3]",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "13. static-slot read after dynamic/fallback write",
                "#evaluate('#set($x = \"eval_val\")')$x",
                Map.of(),
                "eval_val",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "14. dynamic/fallback read after static-slot write",
                "#set($x = 'val')$map.get($x)|$x.toUpperCase()",
                Map.of("map", new HashMap<>(Map.of("val", "dynamic_success"))),
                "dynamic_success|VAL",
                null)),
        Arguments.of(
            new EquivalenceScenario(
                "17. lexical shadowing with same variable name",
                "#macro(shadow $x)[$x]#end#set($x = 'outer')#foreach($x in"
                    + " $items)[$x]#end[$x]#shadow('inner')[$x]",
                Map.of("items", List.of("a", "b")),
                "[a][b][outer][inner][outer]",
                null)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scenario18EquivalenceCases")
  @DisplayName("18. automated IR == AOT output equivalence assertions")
  void scenario18_irAndAotOutputEquivalenceAllScenarios(EquivalenceScenario tc) {
    String irOut = render(tc.template(), tc.context(), ExecutionTier.IR, tc.customizer());
    String aotOut =
        render(tc.template(), tc.context(), ExecutionTier.AOT_BYTECODE, tc.customizer());

    assertThat(irOut).as("IR output for %s", tc.name()).isEqualTo(tc.expected());
    assertThat(aotOut).as("AOT output for %s", tc.name()).isEqualTo(tc.expected());
    assertThat(aotOut).as("AOT output matches IR for %s", tc.name()).isEqualTo(irOut);
  }
}
