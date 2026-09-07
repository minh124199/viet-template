package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/**
 * Escapes dynamic text for URI component contexts (query parameters, path segments) according to
 * RFC 3986.
 *
 * <p>Preserves unreserved characters ({@code [a-zA-Z0-9_.~-]}) and percent-encodes all other
 * characters using their UTF-8 byte representation. Bypasses escaping for {@link SafeUrl}
 * instances.
 */
public final class UrlComponentEscaper implements Escaper {

  public static final UrlComponentEscaper INSTANCE = new UrlComponentEscaper();
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private UrlComponentEscaper() {}

  @Override
  public EscapeMode mode() {
    return EscapeMode.URL_COMPONENT;
  }

  @Override
  public void escape(CharSequence input, TemplateOutput output) throws IOException {
    if (input == null) {
      return;
    }
    if (input instanceof SafeUrl safe) {
      output.write(safe.content());
      return;
    }
    int len = input.length();
    if (len == 0) {
      return;
    }

    int firstSpecial = -1;
    for (int i = 0; i < len; i++) {
      char c = input.charAt(i);
      if (!isUnreserved(c)) {
        firstSpecial = i;
        break;
      }
    }

    if (firstSpecial == -1) {
      output.write(input);
      return;
    }

    if (firstSpecial > 0) {
      output.write(input.subSequence(0, firstSpecial));
    }

    int last = firstSpecial;
    for (int i = firstSpecial; i < len; i++) {
      char c = input.charAt(i);
      if (isUnreserved(c)) {
        continue;
      }

      if (i > last) {
        output.write(input.subSequence(last, i));
      }

      int cp;
      if (Character.isHighSurrogate(c)
          && i + 1 < len
          && Character.isLowSurrogate(input.charAt(i + 1))) {
        cp = Character.toCodePoint(c, input.charAt(i + 1));
        i++;
      } else {
        cp = c;
      }

      encodeCodePoint(cp, output);
      last = i + 1;
    }

    if (last < len) {
      output.write(input.subSequence(last, len));
    }
  }

  private static boolean isUnreserved(char c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '_'
        || c == '.'
        || c == '~';
  }

  private static void encodeCodePoint(int cp, TemplateOutput output) throws IOException {
    if (cp <= 0x7F) {
      writePercent(cp, output);
    } else if (cp <= 0x7FF) {
      writePercent(0xC0 | (cp >> 6), output);
      writePercent(0x80 | (cp & 0x3F), output);
    } else if (cp <= 0xFFFF) {
      writePercent(0xE0 | (cp >> 12), output);
      writePercent(0x80 | ((cp >> 6) & 0x3F), output);
      writePercent(0x80 | (cp & 0x3F), output);
    } else {
      writePercent(0xF0 | (cp >> 18), output);
      writePercent(0x80 | ((cp >> 12) & 0x3F), output);
      writePercent(0x80 | ((cp >> 6) & 0x3F), output);
      writePercent(0x80 | (cp & 0x3F), output);
    }
  }

  private static void writePercent(int b, TemplateOutput output) throws IOException {
    output.write('%');
    output.write(HEX[(b >> 4) & 0xF]);
    output.write(HEX[b & 0xF]);
  }
}
