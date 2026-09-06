package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerLiteralOperatorTest {

  @Test
  void lexesNumbersAndRanges() {
    String template = "#set($range = [1..10]) #set($floatVal = 3.14159)";
    SourceText src = SourceText.of("nums.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();

    // [1..10]
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LEFT_BRACKET);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.RANGE);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.RIGHT_BRACKET);

    VtlToken rangeToken =
        tokens.stream().filter(t -> t.kind() == VtlTokenKind.RANGE).findFirst().orElseThrow();
    assertThat(rangeToken.text(src)).isEqualTo("..");

    // Float 3.14159
    VtlToken floatToken =
        tokens.stream().filter(t -> t.kind() == VtlTokenKind.FLOAT).findFirst().orElseThrow();
    assertThat(floatToken.text(src)).isEqualTo("3.14159");
  }

  @Test
  void lexesSingleAndDoubleQuotedStrings() {
    String template = "#set($s1 = 'single quoted') #set($s2 = \"double \\\"quoted\\\"\")";
    SourceText src = SourceText.of("strings.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    VtlToken s1 =
        tokens.stream()
            .filter(t -> t.kind() == VtlTokenKind.STRING_SINGLE)
            .findFirst()
            .orElseThrow();
    assertThat(s1.text(src)).isEqualTo("'single quoted'");

    VtlToken s2 =
        tokens.stream()
            .filter(t -> t.kind() == VtlTokenKind.STRING_DOUBLE)
            .findFirst()
            .orElseThrow();
    assertThat(s2.text(src)).isEqualTo("\"double \\\"quoted\\\"\"");
  }

  @Test
  void lexesArithmeticOperators() {
    String template = "#set($res = 10 + 20 - 5 * 2 / 1 % 3)";
    SourceText src = SourceText.of("arith.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.PLUS);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.MINUS);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.STAR);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.SLASH);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.PERCENT);
  }

  @Test
  void lexesComparisonAndLogicalOperators() {
    String template =
        "#if($a == $b && $c != $d || !($x < $y && $m <= $n && $p > $q && $r >= $s))#end";
    SourceText src = SourceText.of("logic.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.EQUAL_EQUAL);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.NOT_EQUAL);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LOGICAL_AND);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LOGICAL_OR);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LOGICAL_NOT);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LESS);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LESS_EQUAL);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.GREATER);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.GREATER_EQUAL);
  }

  @Test
  void lexesTextualOperatorsAndKeywords() {
    String template =
        "#if($a eq $b and $c ne $d or not $e and $x lt $y or $m le $n and $p gt $q or $r ge $s and"
            + " true or false or null)#end";
    SourceText src = SourceText.of("textual.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.EQ);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.NE);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LT);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LE);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.GT);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.GE);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.AND);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.OR);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.NOT);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.TRUE);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.FALSE);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.NULL);
  }

  @Test
  void lexesMapAndListDelimiters() {
    String template = "#set($map = {'key': 'val', 'count': 42})";
    SourceText src = SourceText.of("map.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.LEFT_BRACE);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.COLON);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.COMMA);
    assertThat(tokens).anyMatch(t -> t.kind() == VtlTokenKind.RIGHT_BRACE);
  }
}
