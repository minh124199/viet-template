package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class VtlParserExpressionTest {

  private VtlExpression parseSingleExpression(String exprText) {
    SourceText source = SourceText.from("#set($res = " + exprText + ")", TemplateId.of("expr"));
    VtlParseResult result = VtlParser.parse(source);
    assertFalse(result.hasErrors(), () -> "Parsing errors: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlSetDirectiveNode set = (VtlSetDirectiveNode) result.template().children().get(0);
    return set.value();
  }

  @Test
  void verifiesArithmeticPrecedenceAndAssociativity() {
    // 1 + 2 * 3 -> 1 + (2 * 3)
    VtlExpression expr1 = parseSingleExpression("1 + 2 * 3");
    assertTrue(expr1 instanceof VtlBinaryExpression);
    VtlBinaryExpression bin1 = (VtlBinaryExpression) expr1;
    assertEquals(VtlBinaryOperator.ADD, bin1.operator());
    assertTrue(bin1.left() instanceof VtlIntegerLiteralExpression);
    assertTrue(bin1.right() instanceof VtlBinaryExpression);
    assertEquals(VtlBinaryOperator.MULTIPLY, ((VtlBinaryExpression) bin1.right()).operator());

    // 1 * 2 + 3 -> (1 * 2) + 3
    VtlExpression expr2 = parseSingleExpression("1 * 2 + 3");
    assertTrue(expr2 instanceof VtlBinaryExpression);
    VtlBinaryExpression bin2 = (VtlBinaryExpression) expr2;
    assertEquals(VtlBinaryOperator.ADD, bin2.operator());
    assertTrue(bin2.left() instanceof VtlBinaryExpression);
    assertEquals(VtlBinaryOperator.MULTIPLY, ((VtlBinaryExpression) bin2.left()).operator());
    assertTrue(bin2.right() instanceof VtlIntegerLiteralExpression);

    // 1 - 2 - 3 -> (1 - 2) - 3 (Left-associative)
    VtlExpression expr3 = parseSingleExpression("1 - 2 - 3");
    assertTrue(expr3 instanceof VtlBinaryExpression);
    VtlBinaryExpression bin3 = (VtlBinaryExpression) expr3;
    assertEquals(VtlBinaryOperator.SUBTRACT, bin3.operator());
    assertTrue(bin3.left() instanceof VtlBinaryExpression);
    assertEquals(VtlBinaryOperator.SUBTRACT, ((VtlBinaryExpression) bin3.left()).operator());
    assertTrue(bin3.right() instanceof VtlIntegerLiteralExpression);
  }

  @Test
  void verifiesLogicalPrecedence() {
    // $a || $b && $c -> $a || ($b && $c)
    VtlExpression expr1 = parseSingleExpression("$a || $b && $c");
    assertTrue(expr1 instanceof VtlBinaryExpression);
    VtlBinaryExpression bin1 = (VtlBinaryExpression) expr1;
    assertEquals(VtlBinaryOperator.LOGICAL_OR, bin1.operator());
    assertTrue(bin1.right() instanceof VtlBinaryExpression);
    assertEquals(VtlBinaryOperator.LOGICAL_AND, ((VtlBinaryExpression) bin1.right()).operator());

    // Textual: $a or $b and $c -> $a or ($b and $c)
    VtlExpression expr2 = parseSingleExpression("$a or $b and $c");
    assertTrue(expr2 instanceof VtlBinaryExpression);
    VtlBinaryExpression bin2 = (VtlBinaryExpression) expr2;
    assertEquals(VtlBinaryOperator.LOGICAL_OR, bin2.operator());
    assertTrue(bin2.right() instanceof VtlBinaryExpression);
    assertEquals(VtlBinaryOperator.LOGICAL_AND, ((VtlBinaryExpression) bin2.right()).operator());
  }

  @Test
  void verifiesRelationalAndEqualityOperators() {
    VtlExpression expr1 = parseSingleExpression("$x < 10");
    assertEquals(VtlBinaryOperator.LESS_THAN, ((VtlBinaryExpression) expr1).operator());

    VtlExpression expr2 = parseSingleExpression("$x <= 10");
    assertEquals(VtlBinaryOperator.LESS_THAN_OR_EQUAL, ((VtlBinaryExpression) expr2).operator());

    VtlExpression expr3 = parseSingleExpression("$x > 10");
    assertEquals(VtlBinaryOperator.GREATER_THAN, ((VtlBinaryExpression) expr3).operator());

    VtlExpression expr4 = parseSingleExpression("$x >= 10");
    assertEquals(VtlBinaryOperator.GREATER_THAN_OR_EQUAL, ((VtlBinaryExpression) expr4).operator());

    VtlExpression expr5 = parseSingleExpression("$x == 'test'");
    assertEquals(VtlBinaryOperator.EQUAL, ((VtlBinaryExpression) expr5).operator());

    VtlExpression expr6 = parseSingleExpression("$x != 'test'");
    assertEquals(VtlBinaryOperator.NOT_EQUAL, ((VtlBinaryExpression) expr6).operator());

    // Textual versions
    VtlExpression expr7 = parseSingleExpression("$x lt 10");
    assertEquals(VtlBinaryOperator.LESS_THAN, ((VtlBinaryExpression) expr7).operator());

    VtlExpression expr8 = parseSingleExpression("$x le 10");
    assertEquals(VtlBinaryOperator.LESS_THAN_OR_EQUAL, ((VtlBinaryExpression) expr8).operator());

    VtlExpression expr9 = parseSingleExpression("$x gt 10");
    assertEquals(VtlBinaryOperator.GREATER_THAN, ((VtlBinaryExpression) expr9).operator());

    VtlExpression expr10 = parseSingleExpression("$x ge 10");
    assertEquals(
        VtlBinaryOperator.GREATER_THAN_OR_EQUAL, ((VtlBinaryExpression) expr10).operator());

    VtlExpression expr11 = parseSingleExpression("$x eq 'test'");
    assertEquals(VtlBinaryOperator.EQUAL, ((VtlBinaryExpression) expr11).operator());

    VtlExpression expr12 = parseSingleExpression("$x ne 'test'");
    assertEquals(VtlBinaryOperator.NOT_EQUAL, ((VtlBinaryExpression) expr12).operator());
  }

  @Test
  void verifiesUnaryOperators() {
    VtlExpression expr1 = parseSingleExpression("!$flag");
    assertTrue(expr1 instanceof VtlUnaryExpression);
    assertEquals(VtlUnaryOperator.NOT, ((VtlUnaryExpression) expr1).operator());

    VtlExpression expr2 = parseSingleExpression("not $flag");
    assertTrue(expr2 instanceof VtlUnaryExpression);
    assertEquals(VtlUnaryOperator.NOT, ((VtlUnaryExpression) expr2).operator());

    VtlExpression expr3 = parseSingleExpression("-$count");
    assertTrue(expr3 instanceof VtlUnaryExpression);
    assertEquals(VtlUnaryOperator.MINUS, ((VtlUnaryExpression) expr3).operator());

    VtlExpression expr4 = parseSingleExpression("+$count");
    assertTrue(expr4 instanceof VtlUnaryExpression);
    assertEquals(VtlUnaryOperator.PLUS, ((VtlUnaryExpression) expr4).operator());

    // -$a * $b -> (-$a) * $b
    VtlExpression expr5 = parseSingleExpression("-$a * $b");
    assertTrue(expr5 instanceof VtlBinaryExpression);
    VtlBinaryExpression bin5 = (VtlBinaryExpression) expr5;
    assertEquals(VtlBinaryOperator.MULTIPLY, bin5.operator());
    assertTrue(bin5.left() instanceof VtlUnaryExpression);
  }

  @Test
  void verifiesGroupedExpression() {
    // (1 + 2) * 3
    VtlExpression expr = parseSingleExpression("(1 + 2) * 3");
    assertTrue(expr instanceof VtlBinaryExpression);
    VtlBinaryExpression bin = (VtlBinaryExpression) expr;
    assertEquals(VtlBinaryOperator.MULTIPLY, bin.operator());
    assertTrue(bin.left() instanceof VtlGroupedExpression);
    VtlGroupedExpression grp = (VtlGroupedExpression) bin.left();
    assertTrue(grp.expression() instanceof VtlBinaryExpression);
    assertEquals(VtlBinaryOperator.ADD, ((VtlBinaryExpression) grp.expression()).operator());
  }

  @Test
  void verifiesLiterals() {
    VtlExpression expr1 = parseSingleExpression("9223372036854775807123");
    assertTrue(expr1 instanceof VtlIntegerLiteralExpression);
    assertEquals(
        new BigInteger("9223372036854775807123"), ((VtlIntegerLiteralExpression) expr1).value());

    VtlExpression expr2 = parseSingleExpression("3.14159265358979323846");
    assertTrue(expr2 instanceof VtlDecimalLiteralExpression);
    assertEquals(
        new BigDecimal("3.14159265358979323846"), ((VtlDecimalLiteralExpression) expr2).value());

    VtlExpression expr3 = parseSingleExpression("true");
    assertTrue(expr3 instanceof VtlBooleanLiteralExpression);
    assertTrue(((VtlBooleanLiteralExpression) expr3).value());

    VtlExpression expr4 = parseSingleExpression("false");
    assertTrue(expr4 instanceof VtlBooleanLiteralExpression);
    assertFalse(((VtlBooleanLiteralExpression) expr4).value());

    // Bare null is rejected by default in Velocity compatibility mode
    SourceText nullSource = SourceText.from("#set($res = null)", TemplateId.of("null_expr"));
    VtlParseResult nullResult = VtlParser.parse(nullSource);
    assertTrue(nullResult.hasErrors());

    // Bare null is accepted when allowBareNullLiteral is explicitly enabled
    VtlParseResult optInResult =
        VtlParser.parse(nullSource, VtlParserOptions.ofDefaults().withAllowBareNullLiteral(true));
    assertFalse(optInResult.hasErrors());
    VtlSetDirectiveNode setNode = (VtlSetDirectiveNode) optInResult.template().children().get(0);
    assertTrue(setNode.value() instanceof VtlNullLiteralExpression);

    VtlExpression expr6 = parseSingleExpression("'plain single quoted'");
    assertTrue(expr6 instanceof VtlStringLiteralExpression);
    assertEquals("plain single quoted", ((VtlStringLiteralExpression) expr6).value());
  }

  @Test
  void verifiesCollectionsAndRanges() {
    // Range: [1..5]
    VtlExpression expr1 = parseSingleExpression("[1..5]");
    assertTrue(expr1 instanceof VtlRangeExpression);
    VtlRangeExpression range = (VtlRangeExpression) expr1;
    assertTrue(range.start() instanceof VtlIntegerLiteralExpression);
    assertTrue(range.end() instanceof VtlIntegerLiteralExpression);

    // List: [1, 2, 3]
    VtlExpression expr2 = parseSingleExpression("[1, 2, 3]");
    assertTrue(expr2 instanceof VtlListLiteralExpression);
    assertEquals(3, ((VtlListLiteralExpression) expr2).elements().size());

    // Empty list: []
    VtlExpression expr3 = parseSingleExpression("[]");
    assertTrue(expr3 instanceof VtlListLiteralExpression);
    assertTrue(((VtlListLiteralExpression) expr3).elements().isEmpty());

    // Map: {"name": "Viet", "age": 25}
    VtlExpression expr4 = parseSingleExpression("{\"name\": 'Viet', \"age\": 25}");
    assertTrue(expr4 instanceof VtlMapLiteralExpression);
    VtlMapLiteralExpression map = (VtlMapLiteralExpression) expr4;
    assertEquals(2, map.entries().size());

    // Empty map: {}
    VtlExpression expr5 = parseSingleExpression("{}");
    assertTrue(expr5 instanceof VtlMapLiteralExpression);
    assertTrue(((VtlMapLiteralExpression) expr5).entries().isEmpty());
  }
}
