package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrlComponentEscaperTest {

  private final UrlComponentEscaper escaper = UrlComponentEscaper.INSTANCE;

  @Test
  @DisplayName("EscapeMode is URL_COMPONENT")
  void testMode() {
    assertThat(escaper.mode()).isEqualTo(EscapeMode.URL_COMPONENT);
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
  @DisplayName("Preserves RFC 3986 unreserved characters without change")
  void testUnreservedChars() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~", out);
    assertThat(out.toString())
        .isEqualTo("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~");
  }

  @Test
  @DisplayName("Percent-encodes URI delimiters and spaces")
  void testReservedCharsAndSpaces() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("hello world! /?:@&=+$#", out);
    assertThat(out.toString()).isEqualTo("hello%20world%21%20%2F%3F%3A%40%26%3D%2B%24%23");
  }

  @Test
  @DisplayName("Percent-encodes non-ASCII UTF-8 characters properly")
  void testUtf8NonAscii() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("Việt Nam", out);
    // V = V, i = i, ệ = %E1%BB%87, t = t, ' ' = %20, N = N, a = a, m = m
    assertThat(out.toString()).isEqualTo("Vi%E1%BB%87t%20Nam");
  }

  @Test
  @DisplayName("SafeUrl instances bypass URL escaping")
  void testSafeUrlBypass() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape(SafeUrl.of("https://example.com/api?user=viet&lang=vi"), out);
    assertThat(out.toString()).isEqualTo("https://example.com/api?user=viet&lang=vi");
  }
}
