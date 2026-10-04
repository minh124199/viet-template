package io.github.minh124199.viettemplate.language.vtl.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VtlLexerDumpTest {

  @Test
  @DisplayName("dumpTokenStream formats table header and token rows conforming to contract")
  void dumpTokenStreamConformsToContract() {
    String template = "Hello $user.name!\n\"Quote\"\t\\$foo";
    SourceText src = SourceText.of("test.vm", template);
    VtlLexResult res = VtlLexer.lex(src);
    assertThat(res.hasErrors()).isFalse();

    String dump = res.dumpTokenStream();
    List<String> lines = dump.lines().toList();

    String expectedHeader =
        String.format(
            "%-5s %-16s %-10s %-17s %s",
            "Index", "Token Kind", "Offsets", "Line:Col", "Raw Text Preview");
    assertThat(lines.get(0)).isEqualTo(expectedHeader);
    assertThat(lines).hasSize(res.tokens().size() + 1);

    for (int i = 0; i < res.tokens().size(); i++) {
      VtlToken token = res.tokens().get(i);
      String row = lines.get(i + 1);
      String expectedPrefix =
          String.format(
              "%04d %-16s [%d..%d] (line %d:%d..%d:%d) \"",
              i,
              token.kind().name(),
              token.startOffset(),
              token.endOffset(),
              token.span().startLine(),
              token.span().startColumn(),
              token.span().endLine(),
              token.span().endColumn());
      assertThat(row).startsWith(expectedPrefix).endsWith("\"");
    }

    // Verify raw text escaping in previews (newlines, tabs, quotes, backslashes)
    assertThat(dump).contains("\\n").contains("\\t").contains("\\\"").contains("\\\\");
  }
}
