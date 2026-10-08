package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Objects;

/**
 * Text replacement edit returned in a workspace edit response.
 */
public record LspTextEdit(
    LspRange range,
    String newText
) {
  public LspTextEdit {
    Objects.requireNonNull(range, "range must not be null");
    Objects.requireNonNull(newText, "newText must not be null");
  }
}
