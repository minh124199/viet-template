package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerReferenceTest {

  @Test
  void lexesSimpleReference() {
    SourceText src = SourceText.of("ref.vm", "$foo");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).hasSize(3);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("foo");
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void lexesChainedProperties() {
    SourceText src = SourceText.of("ref.vm", "Hello $user.profile.name!");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("Hello ");

    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(2).text(src)).isEqualTo("user");
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOT);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("profile");
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.DOT);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(6).text(src)).isEqualTo("name");

    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(7).text(src)).isEqualTo("!");
  }

  @Test
  void lexesMethodCallWithArguments() {
    SourceText src = SourceText.of("call.vm", "$service.lookup($id, 'ACTIVE', 42)");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("service");
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.DOT);
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(3).text(src)).isEqualTo("lookup");
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.LEFT_PAREN);
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(6).text(src)).isEqualTo("id");
    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.COMMA);
    assertThat(tokens.get(8).kind()).isEqualTo(VtlTokenKind.STRING_SINGLE);
    assertThat(tokens.get(8).text(src)).isEqualTo("'ACTIVE'");
    assertThat(tokens.get(9).kind()).isEqualTo(VtlTokenKind.COMMA);
    assertThat(tokens.get(10).kind()).isEqualTo(VtlTokenKind.INTEGER);
    assertThat(tokens.get(10).text(src)).isEqualTo("42");
    assertThat(tokens.get(11).kind()).isEqualTo(VtlTokenKind.RIGHT_PAREN);
  }

  @Test
  void lexesFormalReference() {
    SourceText src = SourceText.of("formal.vm", "${user.name}");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.LEFT_BRACE);
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(2).text(src)).isEqualTo("user");
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOT);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("name");
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.RIGHT_BRACE);
  }

  @Test
  void lexesQuietReference() {
    SourceText src = SourceText.of("quiet.vm", "$!user");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.BANG);
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(2).text(src)).isEqualTo("user");
  }

  @Test
  void lexesQuietFormalReference() {
    SourceText src = SourceText.of("quiet_formal.vm", "$!{user.name}");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.BANG);
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.LEFT_BRACE);
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(3).text(src)).isEqualTo("user");
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.DOT);
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(5).text(src)).isEqualTo("name");
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.RIGHT_BRACE);
  }

  @Test
  void lexesFormalReferenceWithAlternateValue() {
    SourceText src = SourceText.of("alt.vm", "${user.name|'Unknown'}");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.LEFT_BRACE);
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(2).text(src)).isEqualTo("user");
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOT);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("name");
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.PIPE);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.STRING_SINGLE);
    assertThat(tokens.get(6).text(src)).isEqualTo("'Unknown'");
    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.RIGHT_BRACE);
  }

  @Test
  void distinguishesAdjacentTextBoundaries() {
    // Unbraced: $nameWorld is a single identifier
    SourceText src1 = SourceText.of("unbraced.vm", "Hello $nameWorld");
    VtlLexResult res1 = VtlLexer.lex(src1);
    List<VtlToken> tokens1 = res1.nonTriviaTokens();
    assertThat(tokens1.get(1).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens1.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens1.get(2).text(src1)).isEqualTo("nameWorld");

    // Braced: ${name}World isolates $name from World
    SourceText src2 = SourceText.of("braced.vm", "Hello ${name}World");
    VtlLexResult res2 = VtlLexer.lex(src2);
    List<VtlToken> tokens2 = res2.nonTriviaTokens();
    assertThat(tokens2.get(1).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens2.get(2).kind()).isEqualTo(VtlTokenKind.LEFT_BRACE);
    assertThat(tokens2.get(3).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens2.get(3).text(src2)).isEqualTo("name");
    assertThat(tokens2.get(4).kind()).isEqualTo(VtlTokenKind.RIGHT_BRACE);
    assertThat(tokens2.get(5).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens2.get(5).text(src2)).isEqualTo("World");
  }

  @Test
  void lexesIndexAccess() {
    SourceText src = SourceText.of("idx.vm", "$matrix[$row][$col]");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("matrix");
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.LEFT_BRACKET);
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("row");
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.RIGHT_BRACKET);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.LEFT_BRACKET);
    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(8).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(8).text(src)).isEqualTo("col");
    assertThat(tokens.get(9).kind()).isEqualTo(VtlTokenKind.RIGHT_BRACKET);
  }

  @Test
  void lexesUnderscoreReferencePrefixPreflight() {
    SourceText src = SourceText.of("underscore.vm", "$_foo and $_");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    // tokens: DOLLAR, IDENTIFIER("_foo"), TEXT(" and "), DOLLAR, IDENTIFIER("_"), EOF
    assertThat(tokens).hasSize(6);

    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("_foo");

    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(2).text(src)).isEqualTo(" and ");

    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("_");

    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void lexesHyphenatedIdentifiersAccordingToOptionsPreflight() {
    String template = "$foo-bar";
    SourceText src = SourceText.of("hyphen.vm", template);

    // Default options: allowHyphenatedIdentifiers is false
    VtlLexResult resDefault = VtlLexer.lex(src);
    assertThat(resDefault.hasErrors()).isFalse();
    List<VtlToken> tokensDefault = resDefault.nonTriviaTokens();
    // $foo followed by text "-bar"
    assertThat(tokensDefault).hasSize(4);
    assertThat(tokensDefault.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokensDefault.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokensDefault.get(1).text(src)).isEqualTo("foo");
    assertThat(tokensDefault.get(2).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokensDefault.get(2).text(src)).isEqualTo("-bar");
    assertThat(tokensDefault.get(3).kind()).isEqualTo(VtlTokenKind.EOF);

    // Enabled option: allowHyphenatedIdentifiers is true
    VtlLexerOptions enabledOptions = new VtlLexerOptions(true, true);
    VtlLexResult resEnabled = VtlLexer.lex(src, enabledOptions);
    assertThat(resEnabled.hasErrors()).isFalse();
    List<VtlToken> tokensEnabled = resEnabled.nonTriviaTokens();
    assertThat(tokensEnabled).hasSize(3);
    assertThat(tokensEnabled.get(0).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokensEnabled.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokensEnabled.get(1).text(src)).isEqualTo("foo-bar");
    assertThat(tokensEnabled.get(2).kind()).isEqualTo(VtlTokenKind.EOF);
  }
}
