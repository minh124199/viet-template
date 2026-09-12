package io.github.minh124199.viettemplate.runtime;

import java.util.Objects;
import java.util.Optional;

/**
 * Capability token representing a validated or trusted URL that bypasses {@code URL_COMPONENT}
 * percent-encoding when emitted into URL contexts.
 *
 * <p>{@code SafeUrl} instances should normally be created through validated factories such as
 * {@link #of}, {@link #ofValidated}, or {@link #tryOf}. The only public unchecked creation path is
 * {@link #ofTrusted}, which is a security-sensitive host escape hatch.
 *
 * <p><b>SECURITY-SENSITIVE CAPABILITY:</b>
 *
 * <ul>
 *   <li><b>Capability Scope:</b> {@code SafeUrl} grants a capability to bypass RFC 3986
 *       percent-encoding in {@code URL_COMPONENT} output context only.
 *   <li><b>Active Protections in Other Contexts:</b> Context-specific escaping continues to apply
 *       when rendered into other output contexts; specifically, inside {@code HTML_TEXT} and {@code
 *       HTML_ATTRIBUTE_QUOTED}, the URL is entity-escaped to prevent HTML injection and attribute
 *       delimiter breakouts.
 *   <li><b>Validation Scope:</b> Validated construction (via {@link #of}, {@link #ofValidated}, or
 *       {@link #tryOf}) verifies that the URL syntax and scheme satisfy the configured allowlist
 *       ({@code http}, {@code https}, {@code mailto}, {@code tel}, or safe relative paths) and
 *       rejects obfuscation. Passing scheme validation does <b>not</b> guarantee that the remote
 *       destination itself is trustworthy.
 * </ul>
 */
public final class SafeUrl implements SafeContent, CharSequence {

  private final CharSequence content;

  private SafeUrl(CharSequence content) {
    this.content = Objects.requireNonNull(content, "content must not be null");
  }

  @Override
  public CharSequence content() {
    return content;
  }

  /**
   * Validates and wraps a URL in a {@link SafeUrl} instance.
   *
   * <p>Runs URL scheme validation and obfuscation checks against {@link SafeUrlValidator}.
   * Validates that the scheme is an approved safe protocol ({@code http}, {@code https}, {@code
   * mailto}, {@code tel}) or a safe relative path, and rejects dangerous protocols ({@code
   * javascript:}, {@code data:}, etc.) and obfuscations.
   *
   * <p><b>Note:</b> Satisfying scheme validation confirms syntax policy conformance; it does not
   * imply that the remote web destination itself is trustworthy.
   *
   * @param content URL content to validate
   * @return a validated SafeUrl wrapper
   * @throws IllegalArgumentException if the URL scheme is dangerous, invalid, or obfuscated
   * @throws NullPointerException if content is null
   */
  public static SafeUrl of(CharSequence content) {
    return ofValidated(content);
  }

  /**
   * Validates and wraps a URL in a {@link SafeUrl} instance.
   *
   * <p>Runs URL scheme validation and obfuscation checks against {@link SafeUrlValidator}.
   * Validates that the scheme is an approved safe protocol ({@code http}, {@code https}, {@code
   * mailto}, {@code tel}) or a safe relative path, and rejects dangerous protocols ({@code
   * javascript:}, {@code data:}, etc.) and obfuscations.
   *
   * <p><b>Note:</b> Satisfying scheme validation confirms syntax policy conformance; it does not
   * imply that the remote web destination itself is trustworthy.
   *
   * @param content URL content to validate
   * @return a validated SafeUrl wrapper
   * @throws IllegalArgumentException if the URL scheme is dangerous, invalid, or obfuscated
   * @throws NullPointerException if content is null
   */
  public static SafeUrl ofValidated(CharSequence content) {
    Objects.requireNonNull(content, "content must not be null");
    return new SafeUrl(SafeUrlValidator.sanitizeOrValidate(content));
  }

  /**
   * Attempts to validate and wrap a candidate URL in a {@link SafeUrl} instance.
   *
   * <p>Runs URL scheme validation and obfuscation checks against {@link SafeUrlValidator}. Returns
   * an {@link Optional} containing the {@link SafeUrl} if valid, or {@link Optional#empty()} if the
   * URL is null, uses an unapproved scheme, or contains obfuscation.
   *
   * @param content candidate URL content
   * @return an Optional containing the validated SafeUrl, or empty if dangerous or invalid
   */
  public static Optional<SafeUrl> tryOf(CharSequence content) {
    if (content == null || !SafeUrlValidator.isValid(content)) {
      return Optional.empty();
    }
    return Optional.of(new SafeUrl(SafeUrlValidator.sanitizeOrValidate(content)));
  }

  /**
   * Wraps an explicitly trusted URL in a {@link SafeUrl} instance without scheme validation.
   *
   * <p><b>SECURITY-SENSITIVE ESCAPE HATCH:</b>
   *
   * <ul>
   *   <li><b>Checks Skipped:</b> Bypasses URL scheme validation and obfuscation checks. The URL is
   *       not validated, sanitized, or encoded by this method.
   *   <li><b>Trust Assertion:</b> The caller explicitly asserts full responsibility that the URL
   *       content is safe, well-formed, and trusted.
   *   <li><b>Trusted Output Context:</b> Bypasses escaping ONLY when rendered in {@code
   *       URL_COMPONENT} context.
   *   <li><b>Remaining Protections:</b> Context-specific escaping continues to apply in other
   *       output contexts; when rendered into {@code HTML_TEXT} or {@code HTML_ATTRIBUTE_QUOTED},
   *       the URL is still entity-escaped to prevent tag injection and attribute delimiter
   *       breakout.
   *   <li><b>Misuse Risk:</b> User-controlled or untrusted input must NEVER be passed directly to
   *       this method. Passing unsafe schemes (such as {@code javascript:} or {@code data:}) can
   *       cause Cross-Site Scripting (XSS) if rendered into raw URL positions.
   * </ul>
   *
   * @param content explicitly trusted URL content
   * @return an unchecked SafeUrl wrapper
   * @throws NullPointerException if content is null
   */
  public static SafeUrl ofTrusted(CharSequence content) {
    return new SafeUrl(content);
  }

  @Override
  public int length() {
    return content.length();
  }

  @Override
  public char charAt(int index) {
    return content.charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return content.subSequence(start, end);
  }

  @Override
  public String toString() {
    return content.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SafeUrl other)) {
      return false;
    }
    return Objects.equals(content, other.content);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(content);
  }
}
