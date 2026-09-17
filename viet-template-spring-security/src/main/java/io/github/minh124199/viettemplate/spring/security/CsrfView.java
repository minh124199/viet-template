package io.github.minh124199.viettemplate.spring.security;

import io.github.minh124199.viettemplate.api.TemplateData;

/**
 * Immutable, safe presentation facade representing Spring Security CSRF protection state for
 * template rendering.
 *
 * <p><strong>Security Invariant: Token Redaction</strong>
 *
 * <p>Implementations of this interface <strong>must</strong> protect the raw token secret in
 * diagnostic representations. {@link Object#toString()} must redact the token (e.g. replacing it
 * with {@code [PROTECTED]}) to eliminate accidental leakage in logs or debug templates.
 */
@TemplateData
public interface CsrfView {

  /** Returns the CSRF token string. */
  String getToken();

  /** Returns the HTTP request parameter name (e.g. {@code "_csrf"}). */
  String getParameterName();

  /** Returns the HTTP request header name (e.g. {@code "X-CSRF-TOKEN"}). */
  String getHeaderName();

  /**
   * Constructs an immutable {@link CsrfView} from normalized token strings.
   *
   * @param token CSRF token value
   * @param parameterName parameter name
   * @param headerName header name
   * @return immutable csrf view facade
   */
  static CsrfView of(String token, String parameterName, String headerName) {
    return new DefaultCsrfView(token, parameterName, headerName);
  }
}
