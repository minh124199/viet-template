package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Objects;

/**
 * Result of prepareRename query specifying the active token range and placeholder string.
 */
public record LspPrepareRenameResult(
    LspRange range,
    String placeholder
) {
  public LspPrepareRenameResult {
    Objects.requireNonNull(range, "range must not be null");
    Objects.requireNonNull(placeholder, "placeholder must not be null");
  }
}
