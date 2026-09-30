package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/**
 * 0-based UTF-16 position in a text document per the Language Server Protocol specification.
 *
 * <p>Both {@code line} and {@code character} offsets are 0-based. Character offsets represent
 * UTF-16 code unit indices matching standard LSP 3.17 semantics.
 */
record Position(int line, int character) implements Comparable<Position>, Serializable {

  public Position {
    if (line < 0) {
      throw new IllegalArgumentException("line must not be negative: " + line);
    }
    if (character < 0) {
      throw new IllegalArgumentException("character must not be negative: " + character);
    }
  }

  public static Position of(int line, int character) {
    return new Position(line, character);
  }

  @Override
  public int compareTo(Position o) {
    Objects.requireNonNull(o, "o must not be null");
    int lineCmp = Integer.compare(this.line, o.line);
    if (lineCmp != 0) {
      return lineCmp;
    }
    return Integer.compare(this.character, o.character);
  }

  @Override
  public String toString() {
    return line + ":" + character;
  }
}
