package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerLineEndingTest {

  @Test
  void producesEquivalentTokenStreamsAcrossLineEndings() {
    String templateLf = "Line 1: $user\n#if($active)\n  Line 2: Active\n#end\n";
    String templateCrlf = "Line 1: $user\r\n#if($active)\r\n  Line 2: Active\r\n#end\r\n";
    String templateCr = "Line 1: $user\r#if($active)\r  Line 2: Active\r#end\r";

    SourceText srcLf = SourceText.of("lf.vm", templateLf);
    SourceText srcCrlf = SourceText.of("crlf.vm", templateCrlf);
    SourceText srcCr = SourceText.of("cr.vm", templateCr);

    VtlLexResult resLf = VtlLexer.lex(srcLf);
    VtlLexResult resCrlf = VtlLexer.lex(srcCrlf);
    VtlLexResult resCr = VtlLexer.lex(srcCr);

    assertThat(resLf.hasErrors()).isFalse();
    assertThat(resCrlf.hasErrors()).isFalse();
    assertThat(resCr.hasErrors()).isFalse();

    List<VtlToken> tokensLf = resLf.nonTriviaTokens();
    List<VtlToken> tokensCrlf = resCrlf.nonTriviaTokens();
    List<VtlToken> tokensCr = resCr.nonTriviaTokens();

    assertThat(tokensLf).hasSameSizeAs(tokensCrlf);
    assertThat(tokensLf).hasSameSizeAs(tokensCr);

    for (int i = 0; i < tokensLf.size(); i++) {
      VtlToken tLf = tokensLf.get(i);
      VtlToken tCrlf = tokensCrlf.get(i);
      VtlToken tCr = tokensCr.get(i);

      assertThat(tCrlf.kind()).isEqualTo(tLf.kind());
      assertThat(tCr.kind()).isEqualTo(tLf.kind());

      // Line numbers must be identical across all three line break styles
      assertThat(tCrlf.span().startLine())
          .as("startLine mismatch at token %d", i)
          .isEqualTo(tLf.span().startLine());
      assertThat(tCr.span().startLine())
          .as("startLine mismatch at token %d", i)
          .isEqualTo(tLf.span().startLine());

      // Start columns must be identical across all three line break styles
      assertThat(tCrlf.span().startColumn())
          .as("startColumn mismatch at token %d", i)
          .isEqualTo(tLf.span().startColumn());
      assertThat(tCr.span().startColumn())
          .as("startColumn mismatch at token %d", i)
          .isEqualTo(tLf.span().startColumn());
    }
  }
}
