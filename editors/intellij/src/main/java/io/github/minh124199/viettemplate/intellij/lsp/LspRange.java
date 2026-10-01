package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Objects;

/**
 * LSP range between start and end positions.
 */
public record LspRange(LspPosition start, LspPosition end) {
  public LspRange {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
  }

  public static LspRange of(int startLine, int startChar, int endLine, int endChar) {
    return new LspRange(LspPosition.of(startLine, startChar), LspPosition.of(endLine, endChar));
  }
}
