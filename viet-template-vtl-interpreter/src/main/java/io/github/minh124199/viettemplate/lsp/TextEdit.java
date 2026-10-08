package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/**
 * Text edit operation expressing a source replacement over a specific {@link Range}.
 *
 * <p>Supports deterministic natural ordering by range start, end, and new text.
 */
record TextEdit(Range range, String newText) implements Comparable<TextEdit>, Serializable {

  TextEdit {
    Objects.requireNonNull(range, "range must not be null");
    Objects.requireNonNull(newText, "newText must not be null");
  }

  static TextEdit of(Range range, String newText) {
    return new TextEdit(range, newText);
  }

  @Override
  public int compareTo(TextEdit o) {
    Objects.requireNonNull(o, "o must not be null");
    int rangeCmp = this.range.compareTo(o.range);
    if (rangeCmp != 0) {
      return rangeCmp;
    }
    return this.newText.compareTo(o.newText);
  }
}
