package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HtmlAttributeEscaperTest {

  private final HtmlAttributeEscaper escaper = HtmlAttributeEscaper.INSTANCE;

  @Test
  @DisplayName("EscapeMode is HTML_ATTRIBUTE_QUOTED")
  void testMode() {
    assertThat(escaper.mode()).isEqualTo(EscapeMode.HTML_ATTRIBUTE_QUOTED);
  }

  @Test
  @DisplayName("Null or empty inputs produce empty output")
  void testNullOrEmpty() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape(null, out);
    assertThat(out.toString()).isEmpty();

    escaper.escape("", out);
    assertThat(out.toString()).isEmpty();
  }

  @Test
  @DisplayName("Fast-path: attribute string without special characters is written unchanged")
  void testCleanAttributeString() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("btn-primary active_class", out);
    assertThat(out.toString()).isEqualTo("btn-primary active_class");
  }

  @Test
  @DisplayName("Escapes HTML delimiters including backtick and quotes")
  void testDelimitersAndBacktick() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("<attr>&'\"`val", out);
    assertThat(out.toString()).isEqualTo("&lt;attr&gt;&amp;&#39;&quot;&#96;val");
  }

  @Test
  @DisplayName("Preserves valid whitespace (tab, newline, CR) in quoted attributes")
  void testValidWhitespacePreserved() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("line1\nline2\tindent\rcarriage", out);
    assertThat(out.toString()).isEqualTo("line1\nline2\tindent\rcarriage");
  }

  @Test
  @DisplayName("Neutralizes ASCII control characters")
  void testControlCharactersNeutralized() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("test\u0000null\u001Funit", out);
    assertThat(out.toString()).isEqualTo("test&#0;null&#31;unit");
  }

  @Test
  @DisplayName("SafeHtml instances bypass attribute escaping")
  void testSafeHtmlBypass() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape(SafeHtml.of("data-safe=\"1\""), out);
    assertThat(out.toString()).isEqualTo("data-safe=\"1\"");
  }
}
