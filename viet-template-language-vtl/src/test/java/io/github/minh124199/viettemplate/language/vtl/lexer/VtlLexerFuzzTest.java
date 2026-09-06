package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VtlLexerFuzzTest {

  private static final String[] SYMBOLS = {
    "$",
    "#",
    "{",
    "}",
    "(",
    ")",
    "[",
    "]",
    "\"",
    "'",
    "\\",
    "!",
    "*",
    "\r",
    "\n",
    "##",
    "#*",
    "*#",
    "#[[",
    "]]#",
    "$!",
    "${",
    "$!{",
    "#{",
    ".",
    ",",
    ":",
    "|",
    "=",
    "==",
    "!=",
    "<",
    "<=",
    ">",
    ">=",
    "&&",
    "||",
    "+",
    "-",
    "*",
    "/",
    "%",
    "..",
    "if",
    "else",
    "elseif",
    "end",
    "set",
    "foreach",
    "in",
    "true",
    "false",
    "null",
    "and",
    "or",
    "not",
    "eq",
    "ne",
    "lt",
    "le",
    "gt",
    "ge",
    "foo",
    "bar123",
    "a-b",
    " ",
    "\t",
    "Xin chào",
    "日本語",
    "😀",
    "\uD83D\uDE00"
  };

  @Test
  void fuzzesRandomSyntaxFragmentsWithDeterministicSeed() {
    Random random = new Random(0xDEADBEEFL);
    int iterations = 1500;

    for (int i = 0; i < iterations; i++) {
      StringBuilder sb = new StringBuilder();
      int tokenCount = random.nextInt(30);
      for (int t = 0; t < tokenCount; t++) {
        if (random.nextBoolean()) {
          sb.append(SYMBOLS[random.nextInt(SYMBOLS.length)]);
        } else {
          // Random ascii or unicode character
          char c = (char) (random.nextInt(128));
          sb.append(c);
        }
      }

      String input = sb.toString();
      SourceText src = SourceText.of("fuzz_" + i + ".vm", input);

      VtlLexResult res = VtlLexer.lex(src);
      List<VtlToken> tokens = res.tokens();

      assertThat(tokens).isNotEmpty();

      // Invariant 1: Final token must be EOF spanning [len, len]
      VtlToken last = tokens.get(tokens.size() - 1);
      assertThat(last.kind()).isEqualTo(VtlTokenKind.EOF);
      assertThat(last.span().startOffset()).isEqualTo(src.length());
      assertThat(last.span().endOffset()).isEqualTo(src.length());

      // Invariant 2: All token spans must be ordered and within bounds
      int prevStart = 0;
      for (VtlToken token : tokens) {
        assertThat(token.span().startOffset()).isGreaterThanOrEqualTo(0);
        assertThat(token.span().startOffset()).isGreaterThanOrEqualTo(prevStart);
        assertThat(token.span().endOffset()).isGreaterThanOrEqualTo(token.span().startOffset());
        assertThat(token.span().endOffset()).isLessThanOrEqualTo(src.length());
        prevStart = token.span().startOffset();
      }
    }
  }
}
