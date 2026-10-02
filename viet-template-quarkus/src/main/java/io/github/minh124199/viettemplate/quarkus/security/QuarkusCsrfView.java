package io.github.minh124199.viettemplate.quarkus.security;

import io.github.minh124199.viettemplate.api.TemplateData;
import java.util.Objects;

/**
 * Immutable, safe presentation facade representing Quarkus CSRF token state for template rendering.
 *
 * <h2>Security Invariant: Token Redaction</h2>
 *
 * <p><strong>WARNING:</strong> Instances of this class protect the raw CSRF token secret in
 * diagnostic representations. {@link #toString()} redacts the token value (replacing it with {@code
 * [PROTECTED]}) to eliminate accidental leakage in logs or debug templates.
 *
 * <h2>Presentation Only</h2>
 *
 * <p>Template CSRF helpers provide tokens for form submission. They do not validate incoming CSRF
 * tokens on HTTP requests; backend verification is enforced by Quarkus REST CSRF filter.
 */
@TemplateData
public final class QuarkusCsrfView {

  /** Default HTTP form parameter name for CSRF token ({@code "csrf-token"}). */
  public static final String DEFAULT_PARAMETER_NAME = "csrf-token";

  /** Default HTTP header name for CSRF token ({@code "X-CSRF-TOKEN"}). */
  public static final String DEFAULT_HEADER_NAME = "X-CSRF-TOKEN";

  private static final QuarkusCsrfView UNAVAILABLE =
      new QuarkusCsrfView(false, "", DEFAULT_PARAMETER_NAME, DEFAULT_HEADER_NAME);

  private final boolean available;
  private final String token;
  private final String parameterName;
  private final String headerName;

  /**
   * Constructs an immutable {@link QuarkusCsrfView}.
   *
   * @param available whether CSRF protection is active and token is available
   * @param token CSRF token secret (null defaults to empty string)
   * @param parameterName HTTP parameter name (null/blank defaults to {@value
   *     #DEFAULT_PARAMETER_NAME})
   * @param headerName HTTP header name (null/blank defaults to {@value #DEFAULT_HEADER_NAME})
   */
  public QuarkusCsrfView(boolean available, String token, String parameterName, String headerName) {
    this.available = available;
    this.token = token != null ? token : "";
    this.parameterName =
        (parameterName != null && !parameterName.isBlank())
            ? parameterName
            : DEFAULT_PARAMETER_NAME;
    this.headerName =
        (headerName != null && !headerName.isBlank()) ? headerName : DEFAULT_HEADER_NAME;
  }

  /**
   * Constructs an available immutable {@link QuarkusCsrfView}.
   *
   * @param token CSRF token secret (null defaults to empty string)
   * @param parameterName HTTP parameter name (null/blank defaults to {@value
   *     #DEFAULT_PARAMETER_NAME})
   * @param headerName HTTP header name (null/blank defaults to {@value #DEFAULT_HEADER_NAME})
   */
  public QuarkusCsrfView(String token, String parameterName, String headerName) {
    this(token != null && !token.isBlank(), token, parameterName, headerName);
  }

  /**
   * Returns a singleton unavailable CSRF view.
   *
   * @return unavailable CSRF view
   */
  public static QuarkusCsrfView unavailable() {
    return UNAVAILABLE;
  }

  /**
   * Creates an immutable {@link QuarkusCsrfView} with the specified token and default
   * parameter/header names.
   *
   * @param token CSRF token secret
   * @return available view if token is non-empty, otherwise unavailable view
   */
  public static QuarkusCsrfView of(String token) {
    return of(token, DEFAULT_PARAMETER_NAME, DEFAULT_HEADER_NAME);
  }

  /**
   * Creates an immutable {@link QuarkusCsrfView} with the specified token, parameter name, and
   * header name.
   *
   * @param token CSRF token secret
   * @param parameterName HTTP parameter name
   * @param headerName HTTP header name
   * @return available view if token is non-empty, otherwise unavailable view
   */
  public static QuarkusCsrfView of(String token, String parameterName, String headerName) {
    if (token == null || token.isBlank()) {
      return UNAVAILABLE;
    }
    return new QuarkusCsrfView(true, token, parameterName, headerName);
  }

  /**
   * Returns whether a valid CSRF token is available.
   *
   * @return {@code true} if available, {@code false} otherwise
   */
  public boolean isAvailable() {
    return this.available;
  }

  /**
   * Returns the raw CSRF token secret string.
   *
   * @return CSRF token, or empty string if unavailable
   */
  public String token() {
    return this.token;
  }

  /**
   * JavaBean getter alias for {@link #token()}.
   *
   * @return CSRF token
   */
  public String getToken() {
    return this.token;
  }

  /**
   * Returns the form parameter name expected by Quarkus CSRF validation.
   *
   * @return parameter name (e.g. {@code "csrf-token"})
   */
  public String parameterName() {
    return this.parameterName;
  }

  /**
   * JavaBean getter alias for {@link #parameterName()}.
   *
   * @return parameter name
   */
  public String getParameterName() {
    return this.parameterName;
  }

  /**
   * Returns the HTTP header name expected by Quarkus CSRF validation.
   *
   * @return header name (e.g. {@code "X-CSRF-TOKEN"})
   */
  public String headerName() {
    return this.headerName;
  }

  /**
   * JavaBean getter alias for {@link #headerName()}.
   *
   * @return header name
   */
  public String getHeaderName() {
    return this.headerName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof QuarkusCsrfView other)) {
      return false;
    }
    return this.available == other.available
        && Objects.equals(this.token, other.token)
        && Objects.equals(this.parameterName, other.parameterName)
        && Objects.equals(this.headerName, other.headerName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.available, this.token, this.parameterName, this.headerName);
  }

  @Override
  public String toString() {
    return "QuarkusCsrfView[available="
        + this.available
        + ", parameterName="
        + this.parameterName
        + ", headerName="
        + this.headerName
        + ", token=[PROTECTED]]";
  }
}
