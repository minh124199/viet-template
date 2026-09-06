package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlParserDirectiveTest {

  @Test
  void parsesSetDirectives() {
    SourceText source =
        SourceText.from(
            "#set($x = 10)\n#set($user.name = 'Viet')\n#set($arr[0] = $other[1])",
            TemplateId.of("set_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> children = result.template().children();
    // Filter out text nodes (newlines)
    List<VtlSetDirectiveNode> setNodes =
        children.stream()
            .filter(n -> n instanceof VtlSetDirectiveNode)
            .map(n -> (VtlSetDirectiveNode) n)
            .toList();

    assertEquals(3, setNodes.size());

    // #set($x = 10)
    assertTrue(setNodes.get(0).target() instanceof VtlAssignmentTarget.ReferenceTarget);
    VtlReference ref0 =
        ((VtlAssignmentTarget.ReferenceTarget) setNodes.get(0).target()).reference();
    assertEquals("x", ref0.rootName());
    assertTrue(setNodes.get(0).value() instanceof VtlIntegerLiteralExpression);

    // #set($user.name = 'Viet')
    VtlReference ref1 =
        ((VtlAssignmentTarget.ReferenceTarget) setNodes.get(1).target()).reference();
    assertEquals("user", ref1.rootName());
    assertEquals(1, ref1.accessSteps().size());
    assertTrue(setNodes.get(1).value() instanceof VtlStringLiteralExpression);

    // #set($arr[0] = $other[1])
    VtlReference ref2 =
        ((VtlAssignmentTarget.ReferenceTarget) setNodes.get(2).target()).reference();
    assertEquals("arr", ref2.rootName());
    assertTrue(ref2.accessSteps().get(0) instanceof VtlAccessStep.IndexAccess);
    assertTrue(setNodes.get(2).value() instanceof VtlReferenceExpression);
  }

  @Test
  void parsesIfElseIfElseChain() {
    String content =
        "#if($score >= 90)\n"
            + "  A\n"
            + "#elseif($score >= 80)\n"
            + "  B\n"
            + "#elseif($score >= 70)\n"
            + "  C\n"
            + "#else\n"
            + "  F\n"
            + "#end";
    SourceText source = SourceText.from(content, TemplateId.of("if_chain"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlIfDirectiveNode ifNode = (VtlIfDirectiveNode) result.template().children().get(0);
    assertEquals(3, ifNode.branches().size());
    assertTrue(ifNode.elseBody().isPresent());

    // Branch 1: score >= 90
    assertTrue(ifNode.branches().get(0).condition() instanceof VtlBinaryExpression);
    // Branch 2: score >= 80
    assertTrue(ifNode.branches().get(1).condition() instanceof VtlBinaryExpression);
    // Branch 3: score >= 70
    assertTrue(ifNode.branches().get(2).condition() instanceof VtlBinaryExpression);
    // Else body: F
    assertEquals(1, ifNode.elseBody().get().size());
  }

  @Test
  void parsesBracedDirectives() {
    String content = "#{if}($a) Alpha #{elseif}($b) Beta #{else} Gamma #{end}";
    SourceText source = SourceText.from(content, TemplateId.of("braced_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlIfDirectiveNode ifNode = (VtlIfDirectiveNode) result.template().children().get(0);
    assertEquals(2, ifNode.branches().size());
    assertTrue(ifNode.elseBody().isPresent());
  }

  @Test
  void parsesForeachWithAndWithoutElse() {
    String contentWithElse =
        "#foreach($item in $items)\n" + "  * $item\n" + "#else\n" + "  No items.\n" + "#end";
    SourceText source1 = SourceText.from(contentWithElse, TemplateId.of("fe_else"));
    VtlParseResult res1 = VtlParser.parse(source1);
    assertFalse(res1.hasErrors(), () -> "Diagnostics: " + res1.diagnostics());
    VtlAstInvariantWalker.assertInvariants(res1.template(), source1);

    VtlForeachDirectiveNode fe1 = (VtlForeachDirectiveNode) res1.template().children().get(0);
    assertEquals("item", fe1.loopVariable().rootName());
    assertTrue(fe1.iterable() instanceof VtlReferenceExpression);
    assertTrue(fe1.elseBody().isPresent());

    String contentWithoutElse = "#foreach($u in $users)$u#end";
    SourceText source2 = SourceText.from(contentWithoutElse, TemplateId.of("fe_no_else"));
    VtlParseResult res2 = VtlParser.parse(source2);
    assertFalse(res2.hasErrors(), () -> "Diagnostics: " + res2.diagnostics());
    VtlAstInvariantWalker.assertInvariants(res2.template(), source2);

    VtlForeachDirectiveNode fe2 = (VtlForeachDirectiveNode) res2.template().children().get(0);
    assertEquals("u", fe2.loopVariable().rootName());
    assertTrue(fe2.elseBody().isEmpty());
  }

  @Test
  void parsesIncludeAndParseDirectives() {
    String content = "#include('header.vtl', 'menu.vtl')\n#parse('body.vtl')";
    SourceText source = SourceText.from(content, TemplateId.of("inc_parse"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> directives =
        result.template().children().stream().filter(n -> n instanceof VtlDirectiveNode).toList();

    assertEquals(2, directives.size());

    VtlIncludeDirectiveNode inc = (VtlIncludeDirectiveNode) directives.get(0);
    assertEquals(2, inc.arguments().size());

    VtlParseDirectiveNode parse = (VtlParseDirectiveNode) directives.get(1);
    assertTrue(parse.templateExpression() instanceof VtlStringLiteralExpression);
  }

  @Test
  void parsesBreakAndStopDirectives() {
    String content = "#break #break($foreach) #stop #stop('Critical error')";
    SourceText source = SourceText.from(content, TemplateId.of("break_stop"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> directives =
        result.template().children().stream().filter(n -> n instanceof VtlDirectiveNode).toList();

    assertEquals(4, directives.size());

    VtlBreakDirectiveNode brk1 = (VtlBreakDirectiveNode) directives.get(0);
    assertTrue(brk1.scopeExpression().isEmpty());

    VtlBreakDirectiveNode brk2 = (VtlBreakDirectiveNode) directives.get(1);
    assertTrue(brk2.scopeExpression().isPresent());

    VtlStopDirectiveNode stop1 = (VtlStopDirectiveNode) directives.get(2);
    assertTrue(stop1.messageExpression().isEmpty());

    VtlStopDirectiveNode stop2 = (VtlStopDirectiveNode) directives.get(3);
    assertTrue(stop2.messageExpression().isPresent());
    assertTrue(stop2.messageExpression().get() instanceof VtlStringLiteralExpression);
  }

  @Test
  void parsesEvaluateAndDefineDirectives() {
    String content = "#evaluate('1 + 2')\n" + "#define($block)\n" + "  Hello $name!\n" + "#end";
    SourceText source = SourceText.from(content, TemplateId.of("eval_def"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> directives =
        result.template().children().stream().filter(n -> n instanceof VtlDirectiveNode).toList();

    assertEquals(2, directives.size());

    VtlEvaluateDirectiveNode eval = (VtlEvaluateDirectiveNode) directives.get(0);
    assertTrue(eval.expression() instanceof VtlStringLiteralExpression);

    VtlDefineDirectiveNode def = (VtlDefineDirectiveNode) directives.get(1);
    assertEquals("block", def.targetReference().rootName());
    assertFalse(def.body().isEmpty());
  }

  @Test
  void parsesMacroDefinitionWithDefaults() {
    String content = "#macro(greet $user $greeting = 'Hello')\n" + "  $greeting, $user!\n" + "#end";
    SourceText source = SourceText.from(content, TemplateId.of("macro_def"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlMacroDefinitionNode macro = (VtlMacroDefinitionNode) result.template().children().get(0);
    assertEquals("greet", macro.name());
    assertEquals(2, macro.parameters().size());

    VtlMacroParameter p1 = macro.parameters().get(0);
    assertEquals("user", p1.name());
    assertTrue(p1.defaultValue().isEmpty());

    VtlMacroParameter p2 = macro.parameters().get(1);
    assertEquals("greeting", p2.name());
    assertTrue(p2.defaultValue().isPresent());
    assertTrue(p2.defaultValue().get() instanceof VtlStringLiteralExpression);
  }

  @Test
  void parsesBlockMacroAndCustomDirectiveCalls() {
    String content =
        "#@panel('My Title')\n"
            + "  This is inside panel.\n"
            + "#end\n"
            + "#myButton('Submit', 'btn-primary')\n"
            + "#standaloneDirective()";
    SourceText source = SourceText.from(content, TemplateId.of("block_macro"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> directives =
        result.template().children().stream().filter(n -> n instanceof VtlDirectiveNode).toList();

    assertEquals(3, directives.size());

    // #@panel
    assertTrue(directives.get(0) instanceof VtlBlockDirectiveCallNode);
    VtlBlockDirectiveCallNode bcall = (VtlBlockDirectiveCallNode) directives.get(0);
    assertEquals("panel", bcall.name());
    assertEquals(1, bcall.arguments().size());
    assertFalse(bcall.body().isEmpty());

    // #myButton
    assertTrue(directives.get(1) instanceof VtlDirectiveCallNode);
    VtlDirectiveCallNode call1 = (VtlDirectiveCallNode) directives.get(1);
    assertEquals("mybutton", call1.name().toLowerCase());
    assertEquals(2, call1.arguments().size());

    // #standaloneDirective
    assertTrue(directives.get(2) instanceof VtlDirectiveCallNode);
    VtlDirectiveCallNode call2 = (VtlDirectiveCallNode) directives.get(2);
    assertEquals("standalonedirective", call2.name().toLowerCase());
    assertTrue(call2.arguments().isEmpty());
  }
}
