package io.github.minh124199.viettemplate.runtime;

/**
 * Marker interface for trusted content wrappers that selectively bypass contextual escaping.
 *
 * <p>Templates treat implementations of {@code SafeContent} as trusted markup or validated URLs,
 * bypassing escaping strictly in matching output contexts:
 *
 * <ul>
 *   <li>{@link SafeHtml}: Bypasses escaping in {@code HTML_TEXT} context (still escaped in {@code
 *       HTML_ATTRIBUTE_QUOTED}).
 *   <li>{@link SafeUrl}: Bypasses escaping in {@code URL_COMPONENT} context (still escaped in
 *       {@code HTML_TEXT} and {@code HTML_ATTRIBUTE_QUOTED}).
 * </ul>
 */
public sealed interface SafeContent permits SafeHtml, SafeUrl {

  /**
   * Returns the underlying character sequence.
   *
   * @return the raw content
   */
  CharSequence content();
}
