package io.github.minh124199.viettemplate.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsrfViewTest {

  @Test
  @DisplayName("Constructor enforces non-null invariants")
  void constructorValidation() {
    assertThatNullPointerException().isThrownBy(() -> CsrfView.of(null, "_csrf", "X-CSRF-TOKEN"));
    assertThatNullPointerException().isThrownBy(() -> CsrfView.of("token", null, "X-CSRF-TOKEN"));
    assertThatNullPointerException().isThrownBy(() -> CsrfView.of("token", "_csrf", null));
  }

  @Test
  @DisplayName("CsrfView accessors expose normalized values")
  void accessors() {
    CsrfView csrf = CsrfView.of("secret-token-value-12345", "_csrf", "X-CSRF-TOKEN");

    assertThat(csrf.getToken()).isEqualTo("secret-token-value-12345");
    assertThat(csrf.getParameterName()).isEqualTo("_csrf");
    assertThat(csrf.getHeaderName()).isEqualTo("X-CSRF-TOKEN");
  }

  @Test
  @DisplayName("toString() MUST redact the sensitive CSRF token value")
  void toStringRedactsToken() {
    String sensitiveToken = "sensitive-csrf-secret-999";
    CsrfView csrf = CsrfView.of(sensitiveToken, "_csrf", "X-CSRF-TOKEN");

    String representation = csrf.toString();
    assertThat(representation)
        .contains("_csrf")
        .contains("X-CSRF-TOKEN")
        .contains("[PROTECTED]")
        .doesNotContain(sensitiveToken);
  }

  @Test
  @DisplayName("equals and hashCode contract")
  void equalsAndHashCode() {
    CsrfView csrf1 = CsrfView.of("tok1", "_csrf", "X-CSRF-TOKEN");
    CsrfView csrf2 = CsrfView.of("tok1", "_csrf", "X-CSRF-TOKEN");
    CsrfView csrf3 = CsrfView.of("tok2", "_csrf", "X-CSRF-TOKEN");

    assertThat(csrf1).isEqualTo(csrf2);
    assertThat(csrf1.hashCode()).isEqualTo(csrf2.hashCode());
    assertThat(csrf1).isNotEqualTo(csrf3);
  }
}
