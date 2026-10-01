package io.github.minh124199.viettemplate.intellij.syntax;

import com.intellij.psi.tree.IElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VietTemplateLexerTest {

  record TokenInfo(IElementType type, String text, int start, int end) {}

  private List<TokenInfo> tokenize(String input) {
    VietTemplateLexer lexer = new VietTemplateLexer();
    lexer.start(input, 0, input.length(), VietTemplateLexer.STATE_DEFAULT);
    List<TokenInfo> tokens = new ArrayList<>();
    while (lexer.getTokenType() != null) {
      tokens.add(new TokenInfo(
          lexer.getTokenType(),
          input.substring(lexer.getTokenStart(), lexer.getTokenEnd()),
          lexer.getTokenStart(),
          lexer.getTokenEnd()
      ));
      lexer.advance();
    }
    return tokens;
  }

  @Test
  void shouldTokenizeDirectives() {
    String input = "#if #else #elseif #end #foreach #set #macro #evaluate #parse #define #stop #break";
    List<TokenInfo> tokens = tokenize(input);

    List<TokenInfo> directiveTokens = tokens.stream()
        .filter(t -> t.type == VietTemplateTokenTypes.DIRECTIVE)
        .toList();

    assertThat(directiveTokens).extracting(TokenInfo::text).containsExactly(
        "#if", "#else", "#elseif", "#end", "#foreach", "#set", "#macro", "#evaluate", "#parse", "#define", "#stop", "#break"
    );
  }

  @Test
  void shouldTokenizeFormalDirectives() {
    String input = "#{if} #{end} #{foreach}";
    List<TokenInfo> tokens = tokenize(input);

    List<TokenInfo> directiveTokens = tokens.stream()
        .filter(t -> t.type == VietTemplateTokenTypes.DIRECTIVE)
        .toList();

    assertThat(directiveTokens).extracting(TokenInfo::text).containsExactly(
        "#{if}", "#{end}", "#{foreach}"
    );
  }

  @Test
  void shouldTokenizeReferences() {
    String input = "$foo $!foo ${bar} $!{bar} $user.name $customer.getAddress().city";
    List<TokenInfo> tokens = tokenize(input);

    List<TokenInfo> refTokens = tokens.stream()
        .filter(t -> t.type == VietTemplateTokenTypes.REFERENCE)
        .toList();

    assertThat(refTokens).extracting(TokenInfo::text).containsExactly(
        "$foo", "$!foo", "${bar}", "$!{bar}", "$user.name", "$customer.getAddress().city"
    );
  }

  @Test
  void shouldTokenizeComments() {
    String input = "## This is a line comment\nText #* Block comment *# more text";
    List<TokenInfo> tokens = tokenize(input);

    TokenInfo lineComment = tokens.stream()
        .filter(t -> t.type == VietTemplateTokenTypes.COMMENT)
        .findFirst()
        .orElseThrow();
    assertThat(lineComment.text()).isEqualTo("## This is a line comment");

    TokenInfo blockComment = tokens.stream()
        .filter(t -> t.type == VietTemplateTokenTypes.BLOCK_COMMENT)
        .findFirst()
        .orElseThrow();
    assertThat(blockComment.text()).isEqualTo("#* Block comment *#");
  }

  @Test
  void shouldTokenizeDirectiveWithArguments() {
    String input = "#set($val = 123 + 45.67 == \"test\" && 'other')";
    List<TokenInfo> tokens = tokenize(input);

    assertThat(tokens.get(0).type()).isEqualTo(VietTemplateTokenTypes.DIRECTIVE);
    assertThat(tokens.get(0).text()).isEqualTo("#set");

    assertThat(tokens.get(1).type()).isEqualTo(VietTemplateTokenTypes.OPERATOR);
    assertThat(tokens.get(1).text()).isEqualTo("(");

    assertThat(tokens.get(2).type()).isEqualTo(VietTemplateTokenTypes.REFERENCE);
    assertThat(tokens.get(2).text()).isEqualTo("$val");

    List<String> opTexts = tokens.stream()
        .filter(t -> t.type == VietTemplateTokenTypes.OPERATOR)
        .map(TokenInfo::text)
        .toList();
    assertThat(opTexts).contains("(", "=", "+", "==", "&&", ")");

    List<String> stringTexts = tokens.stream()
        .filter(t -> t.type == VietTemplateTokenTypes.STRING_LITERAL)
        .map(TokenInfo::text)
        .toList();
    assertThat(stringTexts).containsExactly("\"test\"", "'other'");

    List<String> numberTexts = tokens.stream()
        .filter(t -> t.type == VietTemplateTokenTypes.NUMBER)
        .map(TokenInfo::text)
        .toList();
    assertThat(numberTexts).containsExactly("123", "45.67");
  }

  @Test
  void shouldTreatNonDirectiveHashAsTemplateText() {
    String input = "Color #ffffff and C# code with #123";
    List<TokenInfo> tokens = tokenize(input);

    assertThat(tokens).allMatch(t -> t.type == VietTemplateTokenTypes.TEXT);
  }
}
