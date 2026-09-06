package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerMalformedTest {

  @Test
  void recoversFromIncompleteQuietReference() {
    SourceText src = SourceText.of("malformed_quiet.vm", "#set($x = $!)");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).isNotEmpty();

    Diagnostic diag = res.diagnostics().get(0);
    assertThat(diag.code().id()).isEqualTo("INCOMPLETE_REFERENCE");

    // Verify tokens still cover the stream and terminate with EOF
    List<VtlToken> tokens = res.tokens();
    assertThat(tokens.get(tokens.size() - 1).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void recoversFromUnterminatedFormalReference() {
    SourceText src = SourceText.of("malformed_formal.vm", "Start ${user.name");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isTrue();

    assertThat(res.diagnostics())
        .anyMatch(d -> d.code().id().equals("UNTERMINATED_FORMAL_REFERENCE"));

    List<VtlToken> tokens = res.tokens();
    assertThat(tokens.get(tokens.size() - 1).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void recoversFromUnterminatedBracedDirective() {
    SourceText src = SourceText.of("malformed_braced.vm", "#{else");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).anyMatch(d -> d.code().id().equals("UNTERMINATED_DIRECTIVE"));
  }

  @Test
  void recoversFromUnclosedStringsInExpressions() {
    SourceText src = SourceText.of("unclosed_str.vm", "#set($x = 'unclosed string)");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).anyMatch(d -> d.code().id().equals("UNTERMINATED_STRING"));
  }

  @Test
  void preservesMonotonicOffsetProgressOnMalformedInput() {
    String adversarial = "$!{##*#[[#{$}{#}}#]]#";
    SourceText src = SourceText.of("adversarial.vm", adversarial);
    VtlLexResult res = VtlLexer.lex(src);

    // Verify all tokens have valid spans and start <= end
    int lastOffset = 0;
    for (VtlToken token : res.tokens()) {
      assertThat(token.span().startOffset()).isGreaterThanOrEqualTo(lastOffset);
      assertThat(token.span().endOffset()).isGreaterThanOrEqualTo(token.span().startOffset());
      lastOffset = token.span().startOffset();
    }
    assertThat(res.tokens().get(res.tokens().size() - 1).kind()).isEqualTo(VtlTokenKind.EOF);
  }
}
