package io.github.minh124199.viettemplate.quarkus.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class QuarkusCsrfViewTest {

  @Test
  public void testUnavailableSingleton() {
    QuarkusCsrfView view = QuarkusCsrfView.unavailable();

    assertThat(view.isAvailable()).isFalse();
    assertThat(view.token()).isEmpty();
    assertThat(view.getToken()).isEmpty();
    assertThat(view.parameterName()).isEqualTo("csrf-token");
    assertThat(view.getParameterName()).isEqualTo("csrf-token");
    assertThat(view.headerName()).isEqualTo("X-CSRF-TOKEN");
    assertThat(view.getHeaderName()).isEqualTo("X-CSRF-TOKEN");
  }

  @Test
  public void testOfToken() {
    QuarkusCsrfView view = QuarkusCsrfView.of("csrf-secret-123");

    assertThat(view.isAvailable()).isTrue();
    assertThat(view.token()).isEqualTo("csrf-secret-123");
    assertThat(view.getToken()).isEqualTo("csrf-secret-123");
    assertThat(view.parameterName()).isEqualTo(QuarkusCsrfView.DEFAULT_PARAMETER_NAME);
    assertThat(view.headerName()).isEqualTo(QuarkusCsrfView.DEFAULT_HEADER_NAME);
  }

  @Test
  public void testOfNullOrBlankTokenReturnsUnavailable() {
    assertThat(QuarkusCsrfView.of(null)).isSameAs(QuarkusCsrfView.unavailable());
    assertThat(QuarkusCsrfView.of("")).isSameAs(QuarkusCsrfView.unavailable());
    assertThat(QuarkusCsrfView.of("   ")).isSameAs(QuarkusCsrfView.unavailable());
    assertThat(QuarkusCsrfView.of(null, "p", "h")).isSameAs(QuarkusCsrfView.unavailable());
    assertThat(QuarkusCsrfView.of("   ", "p", "h")).isSameAs(QuarkusCsrfView.unavailable());
  }

  @Test
  public void testOfCustomParametersAndHeaders() {
    QuarkusCsrfView view = QuarkusCsrfView.of("my-token", "custom_csrf_param", "X-CUSTOM-CSRF");

    assertThat(view.isAvailable()).isTrue();
    assertThat(view.token()).isEqualTo("my-token");
    assertThat(view.parameterName()).isEqualTo("custom_csrf_param");
    assertThat(view.headerName()).isEqualTo("X-CUSTOM-CSRF");

    // Null or blank falls back to defaults
    QuarkusCsrfView fallback = QuarkusCsrfView.of("my-token", null, "   ");
    assertThat(fallback.isAvailable()).isTrue();
    assertThat(fallback.parameterName()).isEqualTo(QuarkusCsrfView.DEFAULT_PARAMETER_NAME);
    assertThat(fallback.headerName()).isEqualTo(QuarkusCsrfView.DEFAULT_HEADER_NAME);
  }

  @Test
  public void testConstructors() {
    QuarkusCsrfView v1 = new QuarkusCsrfView(true, "token1", "p1", "h1");
    assertThat(v1.isAvailable()).isTrue();
    assertThat(v1.token()).isEqualTo("token1");
    assertThat(v1.parameterName()).isEqualTo("p1");
    assertThat(v1.headerName()).isEqualTo("h1");

    QuarkusCsrfView v2 = new QuarkusCsrfView("token2", null, null);
    assertThat(v2.isAvailable()).isTrue();
    assertThat(v2.token()).isEqualTo("token2");
    assertThat(v2.parameterName()).isEqualTo(QuarkusCsrfView.DEFAULT_PARAMETER_NAME);
    assertThat(v2.headerName()).isEqualTo(QuarkusCsrfView.DEFAULT_HEADER_NAME);

    QuarkusCsrfView v3 = new QuarkusCsrfView(null, null, null);
    assertThat(v3.isAvailable()).isFalse();
    assertThat(v3.token()).isEmpty();
  }

  @Test
  public void testToStringRedaction() {
    QuarkusCsrfView view = QuarkusCsrfView.of("super-secret-csrf-token-999");

    String repr = view.toString();
    assertThat(repr).doesNotContain("super-secret-csrf-token-999");
    assertThat(repr).contains("[PROTECTED]");
    assertThat(repr).contains("available=true");
    assertThat(repr).contains("parameterName=csrf-token");
    assertThat(repr).contains("headerName=X-CSRF-TOKEN");
  }

  @Test
  public void testEqualityAndHashCode() {
    QuarkusCsrfView v1 = QuarkusCsrfView.of("token-a", "param", "header");
    QuarkusCsrfView v2 = QuarkusCsrfView.of("token-a", "param", "header");
    QuarkusCsrfView v3 = QuarkusCsrfView.of("token-b", "param", "header");
    QuarkusCsrfView v4 = QuarkusCsrfView.of("token-a", "param2", "header");

    assertThat(v1).isEqualTo(v2);
    assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
    assertThat(v1).isNotEqualTo(v3);
    assertThat(v1).isNotEqualTo(v4);
    assertThat(v1).isNotEqualTo("string");
    assertThat(v1).isNotEqualTo(null);
  }
}
