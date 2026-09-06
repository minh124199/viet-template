package io.github.minh124199.viettemplate.api;

import java.io.Serializable;

/**
 * Immutable source text span indicating UTF-16 character offsets and 1-based line/column
 * coordinates.
 */
public record SourceSpan(
    int startOffset, int endOffset, int startLine, int startColumn, int endLine, int endColumn)
    implements Serializable {

  public static final SourceSpan UNKNOWN = new SourceSpan(-1, -1, -1, -1, -1, -1);

  public SourceSpan {
    if (startOffset == -1
        && endOffset == -1
        && startLine == -1
        && startColumn == -1
        && endLine == -1
        && endColumn == -1) {
      // Valid unknown span
    } else {
      if (startOffset < 0
          || endOffset < 0
          || startLine < 1
          || startColumn < 1
          || endLine < 1
          || endColumn < 1) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid negative coordinates in span: [%d:%d - %d:%d, offsets %d-%d]",
                startLine, startColumn, endLine, endColumn, startOffset, endOffset));
      }
      if (startOffset > endOffset) {
        throw new IllegalArgumentException(
            String.format("startOffset (%d) cannot exceed endOffset (%d)", startOffset, endOffset));
      }
      if (startLine > endLine || (startLine == endLine && startColumn > endColumn)) {
        throw new IllegalArgumentException(
            String.format(
                "Start position [%d:%d] cannot follow end position [%d:%d]",
                startLine, startColumn, endLine, endColumn));
      }
    }
  }

  public static SourceSpan of(
      int startOffset, int endOffset, int startLine, int startColumn, int endLine, int endColumn) {
    return new SourceSpan(startOffset, endOffset, startLine, startColumn, endLine, endColumn);
  }

  public boolean isKnown() {
    return this != UNKNOWN && startOffset >= 0;
  }

  public int length() {
    return isKnown() ? (endOffset - startOffset) : 0;
  }
}
