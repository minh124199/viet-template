package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsStringEscaperTest {

  private final JsStringEscaper escaper = JsStringEscaper.INSTANCE;

  @Test
  @DisplayName("EscapeMode is JS_STRING")
  void testMode() {
    assertThat(escaper.mode()).isEqualTo(EscapeMode.JS_STRING);
  }

  @Test
  @DisplayName("Escapes quotes, backslashes, and control characters")
  void testQuotesAndControls() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("alert('hello \"world\"\\test\nline');", out);
    assertThat(out.toString()).isEqualTo("alert(\\'hello \\\"world\\\"\\\\test\\nline\\');");
  }

  @Test
  @DisplayName("Neutralizes script tag breakouts and comments")
  void testScriptBreakouts() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("</script><script>alert(1)</script>-->", out);
    assertThat(out.toString())
        .isEqualTo(
            "\\u003C/script\\u003E\\u003Cscript\\u003Ealert(1)\\u003C/script\\u003E--\\u003E");
  }

  @Test
  @DisplayName("Escapes JavaScript line separators U+2028 and U+2029")
  void testLineSeparators() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("line1\u2028line2\u2029line3", out);
    assertThat(out.toString()).isEqualTo("line1\\u2028line2\\u2029line3");
  }
}
