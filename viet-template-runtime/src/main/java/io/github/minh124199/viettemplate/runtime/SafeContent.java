package io.github.minh124199.viettemplate.runtime;

/**
 * Marker interface for trusted, pre-escaped, or sanitized content.
 *
 * <p>Templates treat implementations of {@code SafeContent} as trusted markup or URLs, bypassing
 * contextual escaping in corresponding contexts (such as HTML or URL interpolation).
 */
public sealed interface SafeContent permits SafeHtml, SafeUrl {

  /**
   * Returns the underlying character sequence.
   *
   * @return the raw content
   */
  CharSequence content();
}
