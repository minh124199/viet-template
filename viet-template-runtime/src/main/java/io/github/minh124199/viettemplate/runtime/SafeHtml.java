package io.github.minh124199.viettemplate.runtime;

import java.util.Objects;

/**
 * Trusted HTML markup that can be emitted directly to HTML output without additional HTML escaping.
 */
public record SafeHtml(CharSequence content) implements SafeContent, CharSequence {

  public SafeHtml {
    Objects.requireNonNull(content, "content must not be null");
  }

  /**
   * Wraps trusted HTML markup in a {@link SafeHtml} instance.
   *
   * @param content trusted HTML content
   * @return a SafeHtml wrapper
   */
  public static SafeHtml of(CharSequence content) {
    return new SafeHtml(content);
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
