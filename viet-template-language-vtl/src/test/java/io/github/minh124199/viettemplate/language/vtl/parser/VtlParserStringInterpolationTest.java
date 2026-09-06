package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlParserStringInterpolationTest {

  @Test
  void verifiesSingleQuotedStringRemainsUninterpolated() {
    SourceText source =
        SourceText.from("#set($msg = 'Hello $user.name! Price: $100')", TemplateId.of("sq"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlSetDirectiveNode set = (VtlSetDirectiveNode) result.template().children().get(0);
    assertTrue(set.value() instanceof VtlStringLiteralExpression);
    VtlStringLiteralExpression str = (VtlStringLiteralExpression) set.value();
    assertEquals("Hello $user.name! Price: $100", str.value());
  }

  @Test
  void parsesInterpolatedReferencesInDoubleQuotedString() {
    SourceText source =
        SourceText.from("#set($msg = \"Hello $user.name, welcome!\")", TemplateId.of("dq"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlSetDirectiveNode set = (VtlSetDirectiveNode) result.template().children().get(0);
    assertTrue(set.value() instanceof VtlInterpolatedStringExpression);
    VtlInterpolatedStringExpression dq = (VtlInterpolatedStringExpression) set.value();

    List<VtlInterpolatedStringExpression.VtlInterpolatedStringPart> parts = dq.parts();
    assertEquals(3, parts.size());

    // Text "Hello "
    assertTrue(
        parts.get(0) instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart);
    assertEquals(
        "Hello ",
        ((VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart) parts.get(0)).text());

    // Reference $user.name
    assertTrue(
        parts.get(1)
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart);
    VtlReference ref =
        ((VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart) parts.get(1))
            .reference();
    assertEquals("user", ref.rootName());
    assertEquals(1, ref.accessSteps().size());

    // Text ", welcome!"
    assertTrue(
        parts.get(2) instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart);
    assertEquals(
        ", welcome!",
        ((VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart) parts.get(2)).text());
  }

  @Test
  void handlesBackslashEscapesInInterpolatedString() {
    // Odd backslashes: \$ is escaped, becomes literal $
    SourceText source1 =
        SourceText.from("#set($msg = \"Price: \\$100\")", TemplateId.of("dq_esc1"));
    VtlParseResult result1 = VtlParser.parse(source1);
    assertFalse(result1.hasErrors(), () -> "Diagnostics: " + result1.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result1.template(), source1);

    VtlSetDirectiveNode set1 = (VtlSetDirectiveNode) result1.template().children().get(0);
    VtlInterpolatedStringExpression dq1 = (VtlInterpolatedStringExpression) set1.value();
    assertEquals(1, dq1.parts().size());
    assertTrue(
        dq1.parts().get(0)
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart);
    assertEquals(
        "Price: $100",
        ((VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart) dq1.parts().get(0))
            .text());

    // Even backslashes: \\$ is literal \ followed by active $ref
    SourceText source2 =
        SourceText.from("#set($msg = \"Path: \\\\$file\")", TemplateId.of("dq_esc2"));
    VtlParseResult result2 = VtlParser.parse(source2);
    assertFalse(result2.hasErrors(), () -> "Diagnostics: " + result2.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result2.template(), source2);

    VtlSetDirectiveNode set2 = (VtlSetDirectiveNode) result2.template().children().get(0);
    VtlInterpolatedStringExpression dq2 = (VtlInterpolatedStringExpression) set2.value();
    assertEquals(2, dq2.parts().size());
    assertEquals(
        "Path: \\",
        ((VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart) dq2.parts().get(0))
            .text());
    assertTrue(
        dq2.parts().get(1)
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart);
  }

  @Test
  void parsesFormalReferenceWithAlternateInDoubleQuotedString() {
    SourceText source =
        SourceText.from("#set($msg = \"Hello ${user.name|'Guest'}!\")", TemplateId.of("dq_alt"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlSetDirectiveNode set = (VtlSetDirectiveNode) result.template().children().get(0);
    VtlInterpolatedStringExpression dq = (VtlInterpolatedStringExpression) set.value();

    assertEquals(3, dq.parts().size());
    VtlReference ref =
        ((VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart)
                dq.parts().get(1))
            .reference();
    assertEquals("user", ref.rootName());
    assertTrue(ref.alternateValue().isPresent());
    assertTrue(ref.alternateValue().get() instanceof VtlStringLiteralExpression);
    assertEquals("Guest", ((VtlStringLiteralExpression) ref.alternateValue().get()).value());
  }
}
