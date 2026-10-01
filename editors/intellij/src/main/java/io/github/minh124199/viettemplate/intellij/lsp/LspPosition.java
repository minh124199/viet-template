package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Objects;

/**
 * LSP 0-based position (line, character).
 */
public record LspPosition(int line, int character) {
  public LspPosition {
    if (line < 0 || character < 0) {
      throw new IllegalArgumentException("Position line and character must be >= 0");
    }
  }

  public static LspPosition of(int line, int character) {
    return new LspPosition(line, character);
  }
}
