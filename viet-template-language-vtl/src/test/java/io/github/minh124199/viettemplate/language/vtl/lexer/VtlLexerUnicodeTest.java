package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerUnicodeTest {

  @Test
  void lexesVietnameseContentAccurately() {
    String template = "Xin chào $user.fullName, chúc bạn một ngày tốt lành!";
    SourceText src = SourceText.of("vietnamese.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    // TEXT("Xin chào "), DOLLAR, IDENTIFIER("user"), DOT, IDENTIFIER("fullName"), TEXT(", chúc
    // bạn..."), EOF
    assertThat(tokens).hasSize(7);

    VtlToken t0 = tokens.get(0);
    assertThat(t0.kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(t0.text(src)).isEqualTo("Xin chào ");
    assertThat(t0.span().startOffset()).isEqualTo(0);
    assertThat(t0.span().endOffset()).isEqualTo(9);

    VtlToken tDollar = tokens.get(1);
    assertThat(tDollar.kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tDollar.span().startOffset()).isEqualTo(9);
    assertThat(tDollar.span().endOffset()).isEqualTo(10);

    VtlToken tUser = tokens.get(2);
    assertThat(tUser.text(src)).isEqualTo("user");

    VtlToken tTail = tokens.get(5);
    assertThat(tTail.kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tTail.text(src)).isEqualTo(", chúc bạn một ngày tốt lành!");
  }

  @Test
  void lexesJapaneseAndSurrogatePairEmojis() {
    // "😀" is a surrogate pair (2 UTF-16 code units: \uD83D\uDE00)
    String template = "こんにちは 😀 $user.name 🚀";
    SourceText src = SourceText.of("japanese_emoji.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).hasSize(7);

    // Before dollar: "こんにちは 😀 "
    VtlToken prefix = tokens.get(0);
    assertThat(prefix.kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(prefix.text(src)).isEqualTo("こんにちは 😀 ");

    int expectedDollarOffset = "こんにちは 😀 ".length();
    VtlToken dollar = tokens.get(1);
    assertThat(dollar.kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(dollar.span().startOffset()).isEqualTo(expectedDollarOffset);

    VtlToken suffix = tokens.get(5);
    assertThat(suffix.kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(suffix.text(src)).isEqualTo(" 🚀");

    SourceSpan suffixSpan = suffix.span();
    assertThat(suffixSpan.startLine()).isEqualTo(1);
    assertThat(suffix.text(src))
        .isEqualTo(src.slice(suffixSpan.startOffset(), suffixSpan.endOffset()));
  }
}
