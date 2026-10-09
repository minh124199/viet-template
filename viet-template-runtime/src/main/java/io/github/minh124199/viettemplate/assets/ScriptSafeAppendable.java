package io.github.minh124199.viettemplate.assets;

import java.io.IOException;
import java.util.Objects;

/**
 * An {@link Appendable} filter that escapes HTML-significant characters inside JSON payloads into
 * standard JSON unicode escape sequences.
 *
 * <p>Specifically transforms:
 *
 * <ul>
 *   <li>{@code '<'} to {@code "\\u003C"}
 *   <li>{@code '>'} to {@code "\\u003E"}
 *   <li>{@code '&'} to {@code "\\u0026"}
 *   <li>{@code '\u2028'} (Line separator) to {@code "\\u2028"}
 *   <li>{@code '\u2029'} (Paragraph separator) to {@code "\\u2029"}
 * </ul>
 *
 * <p>This guarantees that emitted JSON within an HTML {@code <script>} element cannot prematurely
 * terminate the script element or trigger HTML parser tag/comment transitions, while remaining
 * fully valid JSON parseable by browser {@code JSON.parse()}.
 */
public final class ScriptSafeAppendable implements Appendable {

  private final Appendable delegate;

  public ScriptSafeAppendable(Appendable delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
  }

  @Override
  public Appendable append(char c) throws IOException {
    switch (c) {
      case '<' -> delegate.append("\\u003C");
      case '>' -> delegate.append("\\u003E");
      case '&' -> delegate.append("\\u0026");
      case '\u2028' -> delegate.append("\\u2028");
      case '\u2029' -> delegate.append("\\u2029");
      default -> delegate.append(c);
    }
    return this;
  }

  @Override
  public Appendable append(CharSequence csq) throws IOException {
    if (csq == null) {
      return append("null");
    }
    return append(csq, 0, csq.length());
  }

  @Override
  public Appendable append(CharSequence csq, int start, int end) throws IOException {
    if (csq == null) {
      csq = "null";
      start = 0;
      end = 4;
    }
    int chunkStart = start;
    for (int i = start; i < end; i++) {
      char c = csq.charAt(i);
      if (c == '<' || c == '>' || c == '&' || c == '\u2028' || c == '\u2029') {
        if (i > chunkStart) {
          delegate.append(csq, chunkStart, i);
        }
        append(c);
        chunkStart = i + 1;
      }
    }
    if (chunkStart < end) {
      delegate.append(csq, chunkStart, end);
    }
    return this;
  }
}
