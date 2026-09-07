package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CssStringEscaperTest {

  private final CssStringEscaper escaper = CssStringEscaper.INSTANCE;

  @Test
  @DisplayName("EscapeMode is CSS_STRING")
  void testMode() {
    assertThat(escaper.mode()).isEqualTo(EscapeMode.CSS_STRING);
  }

  @Test
  @DisplayName("Escapes quotes, backslashes, and newlines in CSS string")
  void testCssEscaping() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("color: \"red\"; font: 'Arial'\\test\nline", out);
    assertThat(out.toString()).isEqualTo("color: \\\"red\\\"; font: \\'Arial\\'\\\\test\\A line");
  }

  @Test
  @DisplayName("Neutralizes HTML delimiters in CSS to prevent style tag breakouts")
  void testTagBreakouts() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("</style>", out);
    assertThat(out.toString()).isEqualTo("\\3C /style\\3E ");
  }
}
