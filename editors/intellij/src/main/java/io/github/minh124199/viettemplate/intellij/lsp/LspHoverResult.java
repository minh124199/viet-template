package io.github.minh124199.viettemplate.intellij.lsp;

import org.jetbrains.annotations.Nullable;

/**
 * Hover information returned from the Viet Template Language Server.
 */
public record LspHoverResult(
    String contents,
    @Nullable LspRange range
) {
  public LspHoverResult {
    contents = (contents != null) ? contents : "";
  }
}
