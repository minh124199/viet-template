package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerCommentTest {

  @Test
  void lexesSingleLineComment() {
    String template = "Hello ## This is a comment\nWorld";
    SourceText src = SourceText.of("comment.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.tokens();
    assertThat(tokens).hasSize(4);

    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("Hello ");

    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.COMMENT);
    assertThat(tokens.get(1).text(src)).isEqualTo("## This is a comment\n");

    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(2).text(src)).isEqualTo("World");

    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void lexesBlockComment() {
    String template = "Before #* multiline\nblock comment *# After";
    SourceText src = SourceText.of("block.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.tokens();
    assertThat(tokens).hasSize(4);

    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("Before ");

    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.COMMENT);
    assertThat(tokens.get(1).text(src)).isEqualTo("#* multiline\nblock comment *#");

    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(2).text(src)).isEqualTo(" After");

    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void lexesDocComment() {
    String template = "#**\n * @author Alice\n *#\n$foo";
    SourceText src = SourceText.of("doc.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.tokens();
    assertThat(tokens).hasSize(5);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.COMMENT);
    assertThat(tokens.get(0).text(src)).isEqualTo("#**\n * @author Alice\n *#");

    List<VtlToken> nonTrivia = res.nonTriviaTokens();
    assertThat(nonTrivia).hasSize(4);
    assertThat(nonTrivia.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(nonTrivia.get(0).text(src)).isEqualTo("\n");
    assertThat(nonTrivia.get(1).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(nonTrivia.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(nonTrivia.get(2).text(src)).isEqualTo("foo");
  }

  @Test
  void ignoresVtlConstructsInsideComments() {
    String template =
        "## $foo #if($bar) #set($x = 1)\n" + "#* $user.name #foreach($a in $b) *#\n" + "$realVar";
    SourceText src = SourceText.of("ignore.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> nonTrivia = res.nonTriviaTokens();
    // Should only have the \n, \n, and $realVar, plus EOF
    long dollarCount = nonTrivia.stream().filter(t -> t.kind() == VtlTokenKind.DOLLAR).count();
    assertThat(dollarCount).isEqualTo(1);

    VtlToken realVar =
        nonTrivia.stream()
            .filter(t -> t.kind() == VtlTokenKind.IDENTIFIER)
            .findFirst()
            .orElseThrow();
    assertThat(realVar.text(src)).isEqualTo("realVar");
  }

  @Test
  void reportsUnterminatedBlockComment() {
    String template = "Start #* unclosed comment...";
    SourceText src = SourceText.of("unclosed.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).hasSize(1);
    assertThat(res.diagnostics().get(0).code().id()).isEqualTo("UNTERMINATED_COMMENT");
  }

  @Test
  void honorsIncludeTriviaOption() {
    String template = "Hello ## comment\nWorld";
    SourceText src = SourceText.of("trivia.vm", template);
    VtlLexerOptions noTrivia = new VtlLexerOptions(false, false);
    VtlLexResult res = VtlLexer.lex(src, noTrivia);

    assertThat(res.tokens()).noneMatch(t -> t.kind() == VtlTokenKind.COMMENT);
  }
}
