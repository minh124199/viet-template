package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/**
 * Escapes dynamic text for HTML quoted attribute context directly into {@link TemplateOutput}.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>Neutralizes delimiters: {@code &}, {@code <}, {@code >}, {@code "}, {@code '}, and backtick
 *       {@code `}.
 *   <li>Neutralizes ASCII control characters (0x00 to 0x1F except {@code \t}, {@code \n}, {@code
 *       \r}) and {@code 0x7F}.
 *   <li>Streams directly into {@link TemplateOutput} without intermediate allocations on the clean
 *       fast-path.
 * </ul>
 */
public final class HtmlAttributeEscaper implements Escaper {

  public static final HtmlAttributeEscaper INSTANCE = new HtmlAttributeEscaper();

  private HtmlAttributeEscaper() {}

  @Override
  public EscapeMode mode() {
    return EscapeMode.HTML_ATTRIBUTE_QUOTED;
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
        writeEscapedChar(c, output);
        last = i + 1;
      }
    }

    if (last < len) {
      output.write(input.subSequence(last, len));
    }
  }

  private static boolean isSpecial(char c) {
    if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'' || c == '`') {
      return true;
    }
    if (c < 0x20) {
      return c != '\t' && c != '\n' && c != '\r';
    }
    return c == 0x7F;
  }

  private static void writeEscapedChar(char c, TemplateOutput output) throws IOException {
    switch (c) {
      case '&' -> output.write("&amp;");
      case '<' -> output.write("&lt;");
      case '>' -> output.write("&gt;");
      case '"' -> output.write("&quot;");
      case '\'' -> output.write("&#39;");
      case '`' -> output.write("&#96;");
      default -> {
        output.write("&#");
        output.writeInt(c);
        output.write(';');
      }
    }
  }
}
