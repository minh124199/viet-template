package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerRawBlockTest {

  @Test
  void lexesRawBlockContent() {
    String template = "Before #[[ $notAVar and #if($neither) neither ]]# After";
    SourceText src = SourceText.of("raw.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).hasSize(4);

    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("Before ");

    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.RAW_TEXT);
    assertThat(tokens.get(1).text(src)).isEqualTo("#[[ $notAVar and #if($neither) neither ]]#");

    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(2).text(src)).isEqualTo(" After");

    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void lexesMultilineRawBlock() {
    String template = "#[[\nline 1: $var\nline 2: #set($x = 10)\n]]#";
    SourceText src = SourceText.of("multiline_raw.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.RAW_TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo(template);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void reportsUnterminatedRawBlock() {
    String template = "Some text #[[ unclosed raw content...";
    SourceText src = SourceText.of("unclosed_raw.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).hasSize(1);
    assertThat(res.diagnostics().get(0).code().id()).isEqualTo("UNTERMINATED_RAW_BLOCK");

    List<VtlToken> tokens = res.tokens();
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.RAW_TEXT);
    assertThat(tokens.get(1).text(src)).isEqualTo("#[[ unclosed raw content...");
  }
}
