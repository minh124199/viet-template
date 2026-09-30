package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/**
 * Text range in a document expressed by start and end {@link Position}s.
 *
 * <p>Ranges are inclusive of {@code start} and exclusive of {@code end}.
 */
record Range(Position start, Position end) implements Comparable<Range>, Serializable {

  public Range {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    if (start.compareTo(end) > 0) {
      throw new IllegalArgumentException(
          String.format("start (%s) cannot follow end (%s)", start, end));
    }
  }

  public static Range of(Position start, Position end) {
    return new Range(start, end);
  }

  public static Range of(int startLine, int startChar, int endLine, int endChar) {
    return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
  }

  public boolean contains(Position pos) {
    Objects.requireNonNull(pos, "pos must not be null");
    return start.compareTo(pos) <= 0 && pos.compareTo(end) <= 0;
  }

  @Override
  public int compareTo(Range o) {
    Objects.requireNonNull(o, "o must not be null");
    int startCmp = this.start.compareTo(o.start);
    if (startCmp != 0) {
      return startCmp;
    }
    return this.end.compareTo(o.end);
  }

  @Override
  public String toString() {
    return "[" + start + " -> " + end + "]";
  }
}
