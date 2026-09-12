package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EscaperPropertyTest {

  private static final long SEED = 0x35CA9E01L;

  private static boolean isDeepMode() {
    return "deep".equalsIgnoreCase(System.getProperty("vietTemplate.fuzz.mode"))
        || "deep".equalsIgnoreCase(System.getenv("VIET_FUZZ_MODE"));
  }

  private static final String[] DANGEROUS_FRAGMENTS = {
    "<script>",
    "</script>",
    "&amp;",
    "&quot;",
    "\" onmouseover=\"alert(1)\"",
    "' onfocus='alert(2)'",
    "`alert(3)`",
    "javascript:evil()",
    "data:text/html,<script>",
    "\0",
    "\r",
    "\n",
    "\t",
    "&",
    "<",
    ">",
    "\"",
    "'",
    "`",
    "/",
    "?",
    "#",
    " ",
    "100% pure",
    "safe_token-123.vm~",
    "Tiếng Việt có dấu: à, á, ả, ã, ạ"
  };

  @Test
  @DisplayName("Determinism: Repeated escaping produces identical output across all escapers")
  void escapingIsDeterministic() throws IOException {
    SplittableRandom rng = new SplittableRandom(SEED);
    int iterations = isDeepMode() ? 3000 : 300;

    Escaper[] escapers = {
      HtmlTextEscaper.INSTANCE,
      HtmlAttributeEscaper.INSTANCE,
      UrlComponentEscaper.INSTANCE,
      RawEscaper.INSTANCE
    };

    for (int i = 0; i < iterations; i++) {
      int parts = 1 + rng.nextInt(6);
      StringBuilder sb = new StringBuilder();
      for (int p = 0; p < parts; p++) {
        sb.append(DANGEROUS_FRAGMENTS[rng.nextInt(DANGEROUS_FRAGMENTS.length)]);
      }
      String input = sb.toString();

      for (Escaper escaper : escapers) {
        StringTemplateOutput out1 = new StringTemplateOutput();
        StringTemplateOutput out2 = new StringTemplateOutput();

        escaper.escape(input, out1);
        escaper.escape(input, out2);

        assertThat(out1.toString())
            .as("Escaper %s output must be deterministic for input: %s", escaper.mode(), input)
            .isEqualTo(out2.toString());
      }
    }
  }

  @Test
  @DisplayName("P6: SafeHtml trust does not leak into URL_COMPONENT or HTML_ATTRIBUTE_QUOTED")
  void safeHtmlTrustDoesNotLeak() throws IOException {
    String htmlContent = "<b id=\"bold\">Safe & Sound</b>";
    SafeHtml safeHtml = SafeHtml.of(htmlContent);

    // 1. In HTML_TEXT: SafeHtml is emitted verbatim
    StringTemplateOutput htmlTextOut = new StringTemplateOutput();
    HtmlTextEscaper.INSTANCE.escape(safeHtml, htmlTextOut);
    assertThat(htmlTextOut.toString()).isEqualTo(htmlContent);

    // 2. In HTML_ATTRIBUTE_QUOTED: SafeHtml must be entity-escaped
    StringTemplateOutput attrOut = new StringTemplateOutput();
    HtmlAttributeEscaper.INSTANCE.escape(safeHtml, attrOut);
    String attrResult = attrOut.toString();
    assertThat(attrResult).doesNotContain("<b");
    assertThat(attrResult).doesNotContain(">");
    assertThat(attrResult).contains("&lt;b");
    assertThat(attrResult).contains("&gt;");

    // 3. In URL_COMPONENT: SafeHtml must be percent-encoded
    StringTemplateOutput urlOut = new StringTemplateOutput();
    UrlComponentEscaper.INSTANCE.escape(safeHtml, urlOut);
    String urlResult = urlOut.toString();
    assertThat(urlResult).doesNotContain("<");
    assertThat(urlResult).doesNotContain(">");
    assertThat(urlResult).doesNotContain("&");
    assertThat(urlResult).contains("%3C");
  }

  @Test
  @DisplayName("P7: SafeUrl trust does not leak into HTML_TEXT or HTML_ATTRIBUTE_QUOTED")
  void safeUrlTrustDoesNotLeak() throws IOException {
    String urlContent = "https://example.com/search?q=foo&bar=\"<baz>'`";
    SafeUrl safeUrl = SafeUrl.ofTrusted(urlContent);

    // 1. In URL_COMPONENT: SafeUrl is emitted verbatim
    StringTemplateOutput urlOut = new StringTemplateOutput();
    UrlComponentEscaper.INSTANCE.escape(safeUrl, urlOut);
    assertThat(urlOut.toString()).isEqualTo(urlContent);

    // 2. In HTML_TEXT: SafeUrl must be entity-escaped
    StringTemplateOutput textOut = new StringTemplateOutput();
    HtmlTextEscaper.INSTANCE.escape(safeUrl, textOut);
    String textResult = textOut.toString();
    assertThat(textResult).doesNotContain("<baz>");
    assertThat(textResult).contains("&lt;baz&gt;");
    assertThat(textResult).contains("&amp;");
    assertThat(textResult).contains("&quot;");
    assertThat(textResult).contains("&#39;");

    // 3. In HTML_ATTRIBUTE_QUOTED: SafeUrl must have quotes and backticks escaped
    StringTemplateOutput attrOut = new StringTemplateOutput();
    HtmlAttributeEscaper.INSTANCE.escape(safeUrl, attrOut);
    String attrResult = attrOut.toString();
    assertThat(attrResult).doesNotContain("\"<baz>'`");
    assertThat(attrResult).contains("&quot;");
    assertThat(attrResult).contains("&#39;");
    assertThat(attrResult).contains("&#96;");
  }

  @Test
  @DisplayName("Delimiter protection: HTML text and attributes neutralize dangerous characters")
  void delimiterProtection() throws IOException {
    SplittableRandom rng = new SplittableRandom(SEED + 5);
    int iterations = isDeepMode() ? 2000 : 200;

    for (int i = 0; i < iterations; i++) {
      String candidate = DANGEROUS_FRAGMENTS[rng.nextInt(DANGEROUS_FRAGMENTS.length)];

      StringTemplateOutput textOut = new StringTemplateOutput();
      HtmlTextEscaper.INSTANCE.escape(candidate, textOut);
      String textRes = textOut.toString();
      assertThat(textRes)
          .doesNotContain("<script>")
          .doesNotContain("</script>");

      StringTemplateOutput attrOut = new StringTemplateOutput();
      HtmlAttributeEscaper.INSTANCE.escape(candidate, attrOut);
      String attrRes = attrOut.toString();
      assertThat(attrRes)
          .doesNotContain("\" onmouseover")
          .doesNotContain("' onfocus");
    }
  }

  @Test
  @DisplayName("RFC 3986 unreserved characters are preserved in URL_COMPONENT")
  void urlComponentPreservesUnreserved() throws IOException {
    String unreserved = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
    StringTemplateOutput out = new StringTemplateOutput();
    UrlComponentEscaper.INSTANCE.escape(unreserved, out);
    assertThat(out.toString()).isEqualTo(unreserved);
  }
}
