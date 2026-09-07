package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/**
 * Escapes dynamic text for HTML body context directly into {@link TemplateOutput}.
 *
 * <p>Neutralizes {@code &}, {@code <}, {@code >}, {@code "}, and {@code '} to their standard HTML
 * entities. If the input is an instance of {@link SafeHtml}, it is emitted directly without
 * escaping.
 */
public final class HtmlTextEscaper implements Escaper {

  public static final HtmlTextEscaper INSTANCE = new HtmlTextEscaper();

  private HtmlTextEscaper() {}

  @Override
  public EscapeMode mode() {
    return EscapeMode.HTML_TEXT;
  }

  @Override
  public void escape(CharSequence input, TemplateOutput output) throws IOException {
    if (input == null) {
      return;
    }
    if (input instanceof SafeHtml safe) {
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
      if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'') {
        firstSpecial = i;
        break;
      }
    }

    // Fast-path: no escaping necessary, stream directly without allocation
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
      String entity =
          switch (c) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            case '"' -> "&quot;";
            case '\'' -> "&#39;";
            default -> null;
          };
      if (entity != null) {
        if (i > last) {
          output.write(input.subSequence(last, i));
        }
        output.write(entity);
        last = i + 1;
      }
    }

    if (last < len) {
      output.write(input.subSequence(last, len));
    }
  }
}
