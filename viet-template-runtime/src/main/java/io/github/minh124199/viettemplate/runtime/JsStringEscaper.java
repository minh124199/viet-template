package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/**
 * Escapes dynamic text for JavaScript string literal contexts directly into {@link TemplateOutput}.
 *
 * <p>Safeguards against JavaScript syntax breaking (quotes, backslashes, line breaks) as well as
 * HTML script tag breakouts ({@code </script>}, HTML comments) by escaping {@code <}, {@code >},
 * and {@code &} to unicode escapes.
 */
public final class JsStringEscaper implements Escaper {

  public static final JsStringEscaper INSTANCE = new JsStringEscaper();

  private JsStringEscaper() {}

  @Override
  public EscapeMode mode() {
    return EscapeMode.JS_STRING;
  }

  @Override
  public void escape(CharSequence input, TemplateOutput output) throws IOException {
    if (input == null) {
      return;
    }
    int len = input.length();
    if (len == 0) {
      return;
    }

    int firstSpecial = -1;
    for (int i = 0; i < len; i++) {
      char c = input.charAt(i);
      if (isSpecial(c)) {
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
      if (isSpecial(c)) {
        if (i > last) {
          output.write(input.subSequence(last, i));
        }
        writeEscaped(c, output);
        last = i + 1;
      }
    }

    if (last < len) {
      output.write(input.subSequence(last, len));
    }
  }

  private static boolean isSpecial(char c) {
    return c == '\\'
        || c == '"'
        || c == '\''
        || c == '<'
        || c == '>'
        || c == '&'
        || c == '\n'
        || c == '\r'
        || c == '\t'
        || c == '\b'
        || c == '\f'
        || c == '\u2028'
        || c == '\u2029'
        || c < 0x20;
  }

  private static void writeEscaped(char c, TemplateOutput output) throws IOException {
    switch (c) {
      case '\\' -> output.write("\\\\");
      case '"' -> output.write("\\\"");
      case '\'' -> output.write("\\'");
      case '\n' -> output.write("\\n");
      case '\r' -> output.write("\\r");
      case '\t' -> output.write("\\t");
      case '\b' -> output.write("\\b");
      case '\f' -> output.write("\\f");
      case '<' -> output.write("\\u003C");
      case '>' -> output.write("\\u003E");
      case '&' -> output.write("\\u0026");
      case '\u2028' -> output.write("\\u2028");
      case '\u2029' -> output.write("\\u2029");
      default -> {
        output.write("\\u00");
        int hi = (c >> 4) & 0xF;
        int lo = c & 0xF;
        output.write((char) (hi < 10 ? '0' + hi : 'A' + hi - 10));
        output.write((char) (lo < 10 ? '0' + lo : 'A' + lo - 10));
      }
    }
  }
}
