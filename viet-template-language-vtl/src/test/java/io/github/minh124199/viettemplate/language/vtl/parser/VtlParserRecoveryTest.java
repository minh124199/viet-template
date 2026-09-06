package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlParserRecoveryTest {

  @Test
  void recoversFromUnclosedIfBlock() {
    String content = "#if($condition)\n  Hello unclosed\n";
    SourceText source = SourceText.from(content, TemplateId.of("unclosed_if"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream().anyMatch(d -> d.code().id().equals("UNCLOSED_DIRECTIVE")));

    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> children = result.template().children();
    assertEquals(1, children.size());
    assertTrue(children.get(0) instanceof VtlIfDirectiveNode);
    VtlIfDirectiveNode ifNode = (VtlIfDirectiveNode) children.get(0);
    assertFalse(ifNode.branches().get(0).body().isEmpty());
  }

  @Test
  void recoversFromUnmatchedEndDirective() {
    String content = "Hello world\n#end\nMore text $name";
    SourceText source = SourceText.from(content, TemplateId.of("orphan_end"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream().anyMatch(d -> d.code().id().equals("UNMATCHED_DIRECTIVE")));

    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> children = result.template().children();
    // Hello world, #end (VtlErrorNode), More text, $name
    assertTrue(children.stream().anyMatch(n -> n instanceof VtlErrorNode));
    assertTrue(children.stream().anyMatch(n -> n instanceof VtlReferenceOutputNode));
  }

  @Test
  void recoversFromInvalidAssignmentTargetAndPreservesSubsequentNodes() {
    String content = "#set(123 = 456)\nHello $validUser!";
    SourceText source = SourceText.from(content, TemplateId.of("invalid_set"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(d -> d.code().id().equals("INVALID_ASSIGNMENT_TARGET")));

    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    // Verify subsequent nodes are still successfully parsed
    boolean foundUserRef =
        result.template().children().stream()
            .anyMatch(
                n ->
                    n instanceof VtlReferenceOutputNode ro
                        && ro.reference().rootName().equals("validUser"));
    assertTrue(foundUserRef, "Subsequent valid reference must be preserved after recovery");
  }

  @Test
  void recoversFromUnclosedFormalReference() {
    String content = "${unclosedReference";
    SourceText source = SourceText.from(content, TemplateId.of("unclosed_formal"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> children = result.template().children();
    assertEquals(1, children.size());
    assertTrue(children.get(0) instanceof VtlReferenceOutputNode);
    VtlReference ref = ((VtlReferenceOutputNode) children.get(0)).reference();
    assertEquals("unclosedReference", ref.rootName());
  }

  @Test
  void recoversFromMissingDirectiveConditionParen() {
    String content = "#if $badCondition\n  Inside\n#end\n$after";
    SourceText source = SourceText.from(content, TemplateId.of("missing_paren"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);
  }
}
