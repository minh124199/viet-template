package io.github.minh124199.viettemplate.runtime;

import java.util.Objects;

/**
 * Capability token representing pre-verified or trusted HTML markup that bypasses {@code HTML_TEXT}
 * auto-escaping.
 *
 * <p><b>SECURITY-SENSITIVE CAPABILITY:</b>
 *
 * <ul>
 *   <li><b>Capability Scope:</b> {@code SafeHtml} grants a capability to bypass HTML body text
 *       escaping ({@code HTML_TEXT}) only.
 *   <li><b>No Sanitization:</b> {@code SafeHtml} does <b>not</b> sanitize, validate, or parse the
 *       markup. It simply wraps the provided {@link CharSequence}.
 *   <li><b>Trust Assertion:</b> The caller explicitly asserts that the markup is trusted (for
 *       example, a compile-time constant or the output of a dedicated HTML sanitizer such as OWASP
 *       Java HTML Sanitizer).
 *   <li><b>Active Protections in Other Contexts:</b> Context-specific escaping continues to apply
 *       when rendered into other output contexts; specifically, inside {@code
 *       HTML_ATTRIBUTE_QUOTED}, the content is still entity-escaped to prevent attribute delimiter
 *       breakout.
 *   <li><b>Misuse Risk:</b> Passing untrusted user input directly will cause Cross-Site Scripting
 *       (XSS) vulnerabilities when rendered into HTML body text.
 * </ul>
 */
public record SafeHtml(CharSequence content) implements SafeContent, CharSequence {

  public SafeHtml {
    Objects.requireNonNull(content, "content must not be null");
  }

  /**
   * Wraps pre-verified or trusted HTML markup in a {@link SafeHtml} instance.
   *
   * <p><b>SECURITY-SENSITIVE:</b> Bypasses HTML body text escaping. Only use for statically trusted
   * markup or output from an independent, verified HTML sanitizer. This method simply wraps the
   * content and does not perform any sanitization or validation.
   *
   * @param content trusted HTML content
   * @return a SafeHtml wrapper
   * @throws NullPointerException if content is null
   */
  public static SafeHtml of(CharSequence content) {
    return new SafeHtml(content);
  }

  /**
   * Explicit capability factory for trusted HTML markup that bypasses {@code HTML_TEXT}
   * auto-escaping.
   *
   * <p><b>SECURITY-SENSITIVE ESCAPE HATCH:</b>
   *
   * <ul>
   *   <li><b>Checks Skipped:</b> HTML body text escaping ({@code HTML_TEXT}) is bypassed.
   *   <li><b>No Sanitization:</b> This method does NOT sanitize, validate, or filter the provided
   *       markup.
   *   <li><b>Trust Assertion:</b> The caller explicitly asserts that the markup is safe and
   *       trusted.
   *   <li><b>Trusted Output Context:</b> Bypasses escaping ONLY when rendered in {@code HTML_TEXT}
   *       context.
   *   <li><b>Remaining Protections:</b> Context-specific escaping continues to apply in other
   *       contexts (such as {@code HTML_ATTRIBUTE_QUOTED} or {@code URL_COMPONENT}).
   *   <li><b>Misuse Risk:</b> Calling {@code SafeHtml.ofTrusted(userInput)} with untrusted or
   *       unescaped input directly causes Cross-Site Scripting (XSS) vulnerabilities.
   * </ul>
   *
   * @param content trusted HTML markup
   * @return a SafeHtml wrapper
   * @throws NullPointerException if content is null
   */
  public static SafeHtml ofTrusted(CharSequence content) {
    return of(content);
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
}
