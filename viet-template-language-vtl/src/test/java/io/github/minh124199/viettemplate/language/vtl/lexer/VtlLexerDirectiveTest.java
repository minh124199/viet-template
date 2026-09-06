package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerDirectiveTest {

  @Test
  void lexesSetDirective() {
    SourceText src = SourceText.of("set.vm", "#set($x = 42)");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("set");
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.LEFT_PAREN);
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("x");
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.EQUAL);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.INTEGER);
    assertThat(tokens.get(6).text(src)).isEqualTo("42");
    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.RIGHT_PAREN);
  }

  @Test
  void lexesIfElseIfElseEndBlock() {
    String template =
        "#if($user.admin)\n"
            + "  Admin\n"
            + "#elseif($user.moderator)\n"
            + "  Mod\n"
            + "#else\n"
            + "  User\n"
            + "#end";

    SourceText src = SourceText.of("if.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    // Check #if
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("if");
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.LEFT_PAREN);
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("user");
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.DOT);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(6).text(src)).isEqualTo("admin");
    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.RIGHT_PAREN);

    // Body text: "\n  Admin\n"
    assertThat(tokens.get(8).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(8).text(src)).isEqualTo("\n  Admin\n");

    // #elseif
    assertThat(tokens.get(9).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(10).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(10).text(src)).isEqualTo("elseif");

    // #else
    int elseIdx = -1;
    for (int i = 0; i < tokens.size(); i++) {
      if (tokens.get(i).kind() == VtlTokenKind.IDENTIFIER
          && "else".equals(tokens.get(i).text(src))) {
        elseIdx = i;
        break;
      }
    }
    assertThat(elseIdx).isGreaterThan(0);
    assertThat(tokens.get(elseIdx - 1).kind()).isEqualTo(VtlTokenKind.HASH);

    // #end
    VtlToken endToken = tokens.get(tokens.size() - 2);
    assertThat(endToken.kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(endToken.text(src)).isEqualTo("end");
    assertThat(tokens.get(tokens.size() - 3).kind()).isEqualTo(VtlTokenKind.HASH);
  }

  @Test
  void lexesForeachDirective() {
    String template = "#foreach($item in $items)\n  $item.name\n#end";
    SourceText src = SourceText.of("foreach.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("foreach");
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.LEFT_PAREN);
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("item");
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.IN);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(7).text(src)).isEqualTo("items");
    assertThat(tokens.get(8).kind()).isEqualTo(VtlTokenKind.RIGHT_PAREN);
  }

  @Test
  void lexesBracedDirectives() {
    SourceText src = SourceText.of("braced.vm", "#{else}Content#{end}");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    // #{else}
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.LEFT_BRACE);
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(2).text(src)).isEqualTo("else");
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.RIGHT_BRACE);

    // Content
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(4).text(src)).isEqualTo("Content");

    // #{end}
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.LEFT_BRACE);
    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(7).text(src)).isEqualTo("end");
    assertThat(tokens.get(8).kind()).isEqualTo(VtlTokenKind.RIGHT_BRACE);
  }

  @Test
  void lexesUnknownMacroDirective() {
    SourceText src = SourceText.of("macro.vm", "#myCustomDirective($foo, 'bar')");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("myCustomDirective");
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.LEFT_PAREN);
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("foo");
    assertThat(tokens.get(5).kind()).isEqualTo(VtlTokenKind.COMMA);
    assertThat(tokens.get(6).kind()).isEqualTo(VtlTokenKind.STRING_SINGLE);
    assertThat(tokens.get(6).text(src)).isEqualTo("'bar'");
    assertThat(tokens.get(7).kind()).isEqualTo(VtlTokenKind.RIGHT_PAREN);
  }

  @Test
  void lexesBreakAndStopDirectives() {
    SourceText src = SourceText.of("flow.vm", "#break\n#stop");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(1).text(src)).isEqualTo("break");

    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.TEXT); // "\n"

    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(4).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(4).text(src)).isEqualTo("stop");
  }
}
