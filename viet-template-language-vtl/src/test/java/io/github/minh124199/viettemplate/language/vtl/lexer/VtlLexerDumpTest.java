package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import org.junit.jupiter.api.Test;

class VtlLexerDumpTest {

  @Test
  void generatesFormattedTokenDumpForEmailTemplate() {
    String template =
        "## Welcome email template\n"
            + "Hello ${user.name},\n"
            + "#if($user.isSubscribed())\n"
            + "  Thank you for subscribing to $newsletter.title!\n"
            + "#else\n"
            + "  Subscribe now for $9.99/mo: $subscribeUrl\n"
            + "#end\n"
            + "Regards,\n"
            + "Team";

    SourceText src = SourceText.of("email.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    String dump = res.dumpTokenStream();
    assertThat(dump).contains("Index");
    assertThat(dump).contains("Token Kind");
    assertThat(dump).contains("Offsets");
    assertThat(dump).contains("Line:Col");
    assertThat(dump).contains("Raw Text Preview");

    // Verify representative tokens are present in dump
    assertThat(dump).contains("COMMENT");
    assertThat(dump).contains("## Welcome email template");
    assertThat(dump).contains("DOLLAR");
    assertThat(dump).contains("LEFT_BRACE");
    assertThat(dump).contains("RIGHT_BRACE");
    assertThat(dump).contains("HASH");
    assertThat(dump).contains("IDENTIFIER");
    assertThat(dump).contains("if");
    assertThat(dump).contains("EOF");
  }

  @Test
  void generatesFormattedTokenDumpForLoopAndEscaping() {
    String template =
        "#foreach($item in $cart.items)\n"
            + "  * $item.name - \\$$item.price (cost: \\$item.price)\n"
            + "#end\n"
            + "#[[ Literal: $notEvaluated ]]#\n";

    SourceText src = SourceText.of("loop.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    String dump = res.dumpTokenStream();
    assertThat(dump).contains("RAW_TEXT");
    assertThat(dump).contains("#[[ Literal: $notEvaluated ]]#");
    assertThat(dump).contains("IDENTIFIER");
    assertThat(dump).contains("foreach");
    assertThat(dump).contains("IN");
  }
}
