package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Objects;

/**
 * Completion item returned from the Viet Template Language Server.
 */
public record LspCompletionItem(
    String label,
    int kind,
    String detail,
    String documentation,
    String sortText
) {
  public LspCompletionItem {
    Objects.requireNonNull(label, "label must not be null");
    detail = (detail != null) ? detail : "";
    documentation = (documentation != null) ? documentation : "";
    sortText = (sortText != null) ? sortText : label;
  }
}
