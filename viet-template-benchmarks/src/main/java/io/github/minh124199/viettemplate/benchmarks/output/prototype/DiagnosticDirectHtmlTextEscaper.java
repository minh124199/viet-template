package io.github.minh124199.viettemplate.benchmarks.output.prototype;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/**
 * Benchmark-only diagnostic prototype evaluating allocation-free HTML text escaping.
 *
 * <p>Unlike the production {@code HtmlTextEscaper} which calls {@code input.subSequence(start,
 * end)} (triggering a heap {@code String.substring} allocation for each slice), this prototype
 * streams character spans directly into {@link TemplateOutput} without allocating intermediate
 * strings.
 */
public final class DiagnosticDirectHtmlTextEscaper {

  private DiagnosticDirectHtmlTextEscaper() {}

  public static void escape(CharSequence input, TemplateOutput output) throws IOException {
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
      if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'') {
        firstSpecial = i;
        break;
      }
    }

    if (firstSpecial == -1) {
      output.write(input);
      return;
    }

    for (int k = 0; k < firstSpecial; k++) {
      output.write(input.charAt(k));
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
        for (int k = last; k < i; k++) {
          output.write(input.charAt(k));
        }
        output.write(entity);
        last = i + 1;
      }
    }

    if (last < len) {
      for (int k = last; k < len; k++) {
        output.write(input.charAt(k));
      }
    }
  }
}
