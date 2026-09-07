package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HtmlTextEscaperTest {

  private final HtmlTextEscaper escaper = HtmlTextEscaper.INSTANCE;

  @Test
  @DisplayName("EscapeMode is HTML_TEXT")
  void testMode() {
    assertThat(escaper.mode()).isEqualTo(EscapeMode.HTML_TEXT);
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
  @DisplayName(
      "Fast-path: string without special characters is written unchanged without modification")
  void testNoEscapingNeeded() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("Hello World 123", out);
    assertThat(out.toString()).isEqualTo("Hello World 123");
  }

  @Test
  @DisplayName("Standard HTML text entities are correctly escaped")
  void testHtmlTextEscaping() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("a < b && c > d 'single' \"double\"", out);
    assertThat(out.toString())
        .isEqualTo("a &lt; b &amp;&amp; c &gt; d &#39;single&#39; &quot;double&quot;");
  }

  @Test
  @DisplayName("SafeHtml instances bypass escaping")
  void testSafeHtmlBypass() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape(SafeHtml.of("<span><b>bold</b> & <i>italic</i></span>"), out);
    assertThat(out.toString()).isEqualTo("<span><b>bold</b> & <i>italic</i></span>");
  }

  @Test
  @DisplayName("Escaping at beginning, middle, and end of string")
  void testBoundaryEscaping() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("&middle<", out);
    assertThat(out.toString()).isEqualTo("&amp;middle&lt;");
  }
}
