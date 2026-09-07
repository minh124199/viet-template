package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/**
 * Escapes dynamic text for CSS string literal contexts directly into {@link TemplateOutput}.
 *
 * <p>Neutralizes quotes, backslashes, line breaks, and HTML tag delimiters within CSS string
 * literals. Note: CSS contextual safety for property names, dimensions, or unquoted values requires
 * structural CSS validation rather than pure string escaping.
 */
public final class CssStringEscaper implements Escaper {

  public static final CssStringEscaper INSTANCE = new CssStringEscaper();

  private CssStringEscaper() {}

  @Override
  public EscapeMode mode() {
    return EscapeMode.CSS_STRING;
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
    return c == '\\' || c == '"' || c == '\'' || c == '<' || c == '>' || c == '&' || c == '\n'
        || c == '\r' || c == '\f' || c < 0x20;
  }

  private static void writeEscaped(char c, TemplateOutput output) throws IOException {
    switch (c) {
      case '\\' -> output.write("\\\\");
      case '"' -> output.write("\\\"");
      case '\'' -> output.write("\\'");
      case '\n' -> output.write("\\A ");
      case '\r' -> output.write("\\D ");
      case '<' -> output.write("\\3C ");
      case '>' -> output.write("\\3E ");
      case '&' -> output.write("\\26 ");
      default -> {
        output.write('\\');
        output.write(Integer.toHexString(c).toUpperCase());
        output.write(' ');
      }
    }
  }
}
