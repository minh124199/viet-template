package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import org.junit.jupiter.api.Test;

class VtlParserGoldenTest {

  @Test
  void goldenDumpForEmailTemplate() {
    String email =
        "Hello $customer.name,\n"
            + "#if($hasDiscount)\n"
            + "Your order total is $$order.discountedTotal.\n"
            + "#else\n"
            + "Your order total is $$order.total.\n"
            + "#end";

    SourceText source = SourceText.from(email, TemplateId.of("golden_email"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Errors: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    String dump = result.dumpAst();
    assertNotNull(dump);
    assertTrue(dump.contains("Template [0.."), "Must start with Template");
    assertTrue(
        dump.contains("ReferenceOutput [6..20]"), "Must have customer.name reference output");
    assertTrue(dump.contains("root=customer"), "Must have customer root");
    assertTrue(dump.contains("Property: name"), "Must have name property");
    assertTrue(dump.contains("IfDirective [22.."), "Must contain IfDirective");
    assertTrue(dump.contains("root=order"), "Must contain order reference");
    assertTrue(dump.contains("Property: discountedTotal"), "Must contain discountedTotal property");
    assertTrue(dump.contains("Property: total"), "Must contain total property");
  }

  @Test
  void goldenDumpForForeachLoopWithAlternateValue() {
    String template =
        "#foreach($item in $cart.items)\n"
            + "  - ${item.name|'Item'}: $$item.price\n"
            + "#else\n"
            + "  Your cart is empty.\n"
            + "#end";

    SourceText source = SourceText.from(template, TemplateId.of("golden_foreach"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Errors: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    String dump = result.dumpAst();
    assertNotNull(dump);
    assertTrue(dump.contains("ForeachDirective [0.."), "Must contain ForeachDirective");
    assertTrue(dump.contains("item=$item"), "Must have loop variable $item");
    assertTrue(dump.contains("ReferenceOutput"), "Must have ReferenceOutput inside loop");
    assertTrue(dump.contains("ElseBody:"), "Must have ElseBody");
  }

  @Test
  void goldenDumpForMacroAndBlockMacro() {
    String template =
        "#macro(badge $label $color = 'blue')\n"
            + "<span class=\"badge badge-$color\">$label</span>\n"
            + "#end\n"
            + "#@panel('Alert')\n"
            + "Warning: System update at midnight.\n"
            + "#end";

    SourceText source = SourceText.from(template, TemplateId.of("golden_macro"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Errors: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    String dump = result.dumpAst();
    assertNotNull(dump);
    assertTrue(dump.contains("MacroDefinition [0.."), "Must contain MacroDefinition");
    assertTrue(dump.contains("name=badge"), "Macro name must be badge");
    assertTrue(dump.contains("Param $label"), "Param $label");
    assertTrue(dump.contains("Param $color"), "Param $color");
    assertTrue(dump.contains("BlockDirectiveCall"), "Must contain BlockDirectiveCall");
    assertTrue(dump.contains("name=panel"), "Block macro name must be panel");
  }

  @Test
  void goldenDumpForComplexExpressions() {
    String template = "#set($result = ($a + $b * 3) > 10 && !($c == null))";

    SourceText source = SourceText.from(template, TemplateId.of("golden_expr"));
    VtlParseResult result =
        VtlParser.parse(source, VtlParserOptions.ofDefaults().withAllowBareNullLiteral(true));

    assertFalse(result.hasErrors(), () -> "Errors: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    String dump = result.dumpAst();
    assertNotNull(dump);
    assertTrue(dump.contains("SetDirective [0.."), "Must contain SetDirective");
    assertTrue(dump.contains("root=result"), "Target must be result");
    assertTrue(dump.contains("Binary ["), "Must contain Binary");
    assertTrue(dump.contains("op=LOGICAL_AND"), "Top op must be LOGICAL_AND");
    assertTrue(dump.contains("Unary [") && dump.contains("op=NOT"), "Right must have Unary NOT");
  }
}
