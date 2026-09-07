package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeContentTest {

  @Test
  @DisplayName("SafeHtml wraps content and implements CharSequence properly")
  void testSafeHtml() {
    SafeHtml safeHtml = SafeHtml.of("<b>bold</b>");
    assertThat(safeHtml.content()).isEqualTo("<b>bold</b>");
    assertThat(safeHtml.length()).isEqualTo(11);
    assertThat(safeHtml.charAt(1)).isEqualTo('b');
    assertThat(safeHtml.subSequence(3, 7)).isEqualTo("bold");
    assertThat(safeHtml.toString()).isEqualTo("<b>bold</b>");

    assertThatThrownBy(() -> SafeHtml.of(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("SafeUrl wraps content and implements CharSequence properly")
  void testSafeUrl() {
    SafeUrl safeUrl = SafeUrl.of("https://example.com/search?q=viet+template");
    assertThat(safeUrl.content()).isEqualTo("https://example.com/search?q=viet+template");
    assertThat(safeUrl.length()).isEqualTo(42);
    assertThat(safeUrl.charAt(0)).isEqualTo('h');
    assertThat(safeUrl.subSequence(0, 5)).isEqualTo("https");
    assertThat(safeUrl.toString()).isEqualTo("https://example.com/search?q=viet+template");

    assertThatThrownBy(() -> SafeUrl.of(null)).isInstanceOf(NullPointerException.class);
  }
}
