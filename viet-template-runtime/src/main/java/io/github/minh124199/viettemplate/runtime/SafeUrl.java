package io.github.minh124199.viettemplate.runtime;

import java.util.Objects;

/**
 * Trusted or pre-encoded URL that can be emitted directly to URL component output without
 * additional encoding.
 */
public record SafeUrl(CharSequence content) implements SafeContent, CharSequence {

  public SafeUrl {
    Objects.requireNonNull(content, "content must not be null");
  }

  /**
   * Wraps a trusted URL in a {@link SafeUrl} instance.
   *
   * @param content trusted URL content
   * @return a SafeUrl wrapper
   */
  public static SafeUrl of(CharSequence content) {
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
}
