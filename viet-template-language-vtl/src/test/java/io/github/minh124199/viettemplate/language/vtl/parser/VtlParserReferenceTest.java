package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlParserReferenceTest {

  @Test
  void parsesSimpleReference() {
    SourceText source = SourceText.from("Hello $name!", TemplateId.of("ref1"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> children = result.template().children();
    assertEquals(3, children.size());

    assertTrue(children.get(0) instanceof VtlTextNode);
    assertEquals("Hello ", ((VtlTextNode) children.get(0)).text(source));

    assertTrue(children.get(1) instanceof VtlReferenceOutputNode);
    VtlReference ref = ((VtlReferenceOutputNode) children.get(1)).reference();
    assertEquals("name", ref.rootName());
    assertFalse(ref.notation().quiet());
    assertFalse(ref.notation().formal());
    assertTrue(ref.accessSteps().isEmpty());
    assertTrue(ref.alternateValue().isEmpty());

    assertTrue(children.get(2) instanceof VtlTextNode);
    assertEquals("!", ((VtlTextNode) children.get(2)).text(source));
  }

  @Test
  void parsesQuietAndFormalReferences() {
    SourceText source = SourceText.from("$!foo ${bar} $!{baz}", TemplateId.of("ref2"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> children = result.template().children();
    assertEquals(5, children.size());

    // $!foo
    VtlReference ref1 = ((VtlReferenceOutputNode) children.get(0)).reference();
    assertEquals("foo", ref1.rootName());
    assertTrue(ref1.notation().quiet());
    assertFalse(ref1.notation().formal());

    // ${bar}
    VtlReference ref2 = ((VtlReferenceOutputNode) children.get(2)).reference();
    assertEquals("bar", ref2.rootName());
    assertFalse(ref2.notation().quiet());
    assertTrue(ref2.notation().formal());

    // $!{baz}
    VtlReference ref3 = ((VtlReferenceOutputNode) children.get(4)).reference();
    assertEquals("baz", ref3.rootName());
    assertTrue(ref3.notation().quiet());
    assertTrue(ref3.notation().formal());
  }

  @Test
  void parsesChainedPropertiesAndMethods() {
    SourceText source = SourceText.from("$user.profile.getName().trim()", TemplateId.of("ref3"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlNode> children = result.template().children();
    assertEquals(1, children.size());

    VtlReference ref = ((VtlReferenceOutputNode) children.get(0)).reference();
    assertEquals("user", ref.rootName());
    assertEquals(3, ref.accessSteps().size());

    assertTrue(ref.accessSteps().get(0) instanceof VtlAccessStep.PropertyAccess);
    assertEquals(
        "profile", ((VtlAccessStep.PropertyAccess) ref.accessSteps().get(0)).propertyName());

    assertTrue(ref.accessSteps().get(1) instanceof VtlAccessStep.MethodCall);
    assertEquals("getName", ((VtlAccessStep.MethodCall) ref.accessSteps().get(1)).methodName());
    assertTrue(((VtlAccessStep.MethodCall) ref.accessSteps().get(1)).arguments().isEmpty());

    assertTrue(ref.accessSteps().get(2) instanceof VtlAccessStep.MethodCall);
    assertEquals("trim", ((VtlAccessStep.MethodCall) ref.accessSteps().get(2)).methodName());
    assertTrue(((VtlAccessStep.MethodCall) ref.accessSteps().get(2)).arguments().isEmpty());
  }

  @Test
  void parsesMethodCallWithArguments() {
    SourceText source =
        SourceText.from("$service.process($req, 1 + 2, 'fast')", TemplateId.of("ref4"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlReference ref = ((VtlReferenceOutputNode) result.template().children().get(0)).reference();
    assertEquals("service", ref.rootName());
    assertEquals(1, ref.accessSteps().size());

    assertTrue(ref.accessSteps().get(0) instanceof VtlAccessStep.MethodCall);
    VtlAccessStep.MethodCall call = (VtlAccessStep.MethodCall) ref.accessSteps().get(0);
    assertEquals("process", call.methodName());
    assertEquals(3, call.arguments().size());

    assertTrue(call.arguments().get(0) instanceof VtlReferenceExpression);
    assertTrue(call.arguments().get(1) instanceof VtlBinaryExpression);
    assertTrue(call.arguments().get(2) instanceof VtlStringLiteralExpression);
  }

  @Test
  void parsesIndexAccess() {
    SourceText source = SourceText.from("$list[0] $map[$key] $matrix[1][2]", TemplateId.of("ref5"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlReference ref1 = ((VtlReferenceOutputNode) result.template().children().get(0)).reference();
    assertEquals("list", ref1.rootName());
    assertEquals(1, ref1.accessSteps().size());
    assertTrue(ref1.accessSteps().get(0) instanceof VtlAccessStep.IndexAccess);

    VtlReference ref2 = ((VtlReferenceOutputNode) result.template().children().get(2)).reference();
    assertEquals("map", ref2.rootName());
    assertTrue(ref2.accessSteps().get(0) instanceof VtlAccessStep.IndexAccess);

    VtlReference ref3 = ((VtlReferenceOutputNode) result.template().children().get(4)).reference();
    assertEquals("matrix", ref3.rootName());
    assertEquals(2, ref3.accessSteps().size());
  }

  @Test
  void parsesFormalReferenceWithAlternateValue() {
    SourceText source =
        SourceText.from(
            "${user.name|'Guest'} ${count|0} ${other|$fallback}", TemplateId.of("ref6"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlReference ref1 = ((VtlReferenceOutputNode) result.template().children().get(0)).reference();
    assertEquals("user", ref1.rootName());
    assertTrue(ref1.alternateValue().isPresent());
    assertTrue(ref1.alternateValue().get() instanceof VtlStringLiteralExpression);
    assertEquals("Guest", ((VtlStringLiteralExpression) ref1.alternateValue().get()).value());

    VtlReference ref2 = ((VtlReferenceOutputNode) result.template().children().get(2)).reference();
    assertEquals("count", ref2.rootName());
    assertTrue(ref2.alternateValue().isPresent());
    assertTrue(ref2.alternateValue().get() instanceof VtlIntegerLiteralExpression);

    VtlReference ref3 = ((VtlReferenceOutputNode) result.template().children().get(4)).reference();
    assertEquals("other", ref3.rootName());
    assertTrue(ref3.alternateValue().isPresent());
    assertTrue(ref3.alternateValue().get() instanceof VtlReferenceExpression);
  }
}
