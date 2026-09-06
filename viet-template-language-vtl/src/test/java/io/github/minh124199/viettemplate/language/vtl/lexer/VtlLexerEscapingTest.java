package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlLexerEscapingTest {

  @Test
  void escapesReferenceWithSingleBackslash() {
    // \$foo is rendered as literal text in Velocity
    SourceText src = SourceText.of("esc1.vm", "Value: \\$foo");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    // Should be a single TEXT token containing "Value: \$foo" and EOF
    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("Value: \\$foo");
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void rendersEvenBackslashesAsLiteralAndActivatesReference() {
    // \\$foo in Velocity renders as literal \ followed by evaluation of $foo
    SourceText src = SourceText.of("esc2.vm", "Value: \\\\$foo");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    // Text "Value: \\", then DOLLAR, IDENTIFIER("foo"), EOF
    assertThat(tokens).hasSize(4);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("Value: \\\\");
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.DOLLAR);
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(2).text(src)).isEqualTo("foo");
    assertThat(tokens.get(3).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void escapesReferenceWithTripleBackslashes() {
    // \\\$foo in Velocity renders as literal \\$foo
    SourceText src = SourceText.of("esc3.vm", "Value: \\\\\\$foo");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("Value: \\\\\\$foo");
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void escapesDirectiveWithSingleBackslash() {
    // \#if(\$test) in Velocity renders as literal text
    SourceText src = SourceText.of("esc_dir1.vm", "\\#if(\\$test) Literal \\#end");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("\\#if(\\$test) Literal \\#end");
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void rendersEvenBackslashesBeforeDirectiveAsLiteralAndActivatesDirective() {
    // \\#if($test) in Velocity renders literal \ and executes #if
    SourceText src = SourceText.of("esc_dir2.vm", "\\\\#if($test) True \\#end");
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo("\\\\");
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.HASH);
    assertThat(tokens.get(2).kind()).isEqualTo(VtlTokenKind.IDENTIFIER);
    assertThat(tokens.get(2).text(src)).isEqualTo("if");
  }

  @Test
  void preservesNonReferenceDollarSignsAsContiguousText() {
    String template = "Total: $100.00, item $2.50, double $$, hyphen $-, end $";
    SourceText src = SourceText.of("dollar_text.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo(template);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.EOF);
  }

  @Test
  void preservesNonDirectiveHashSignsAsContiguousText() {
    String template = "Color: #ffffff, #123, Issue #42, languages C# and F#";
    SourceText src = SourceText.of("hash_text.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    List<VtlToken> tokens = res.nonTriviaTokens();
    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0).kind()).isEqualTo(VtlTokenKind.TEXT);
    assertThat(tokens.get(0).text(src)).isEqualTo(template);
    assertThat(tokens.get(1).kind()).isEqualTo(VtlTokenKind.EOF);
  }
}
