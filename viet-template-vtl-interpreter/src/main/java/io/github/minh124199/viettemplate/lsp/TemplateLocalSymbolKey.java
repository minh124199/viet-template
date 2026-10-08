package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

record TemplateLocalSymbolKey(
    String templateUri, String variableName, int definitionStartOffset, int definitionEndOffset)
    implements WorkspaceSymbolKey {

  public TemplateLocalSymbolKey {
    Objects.requireNonNull(templateUri, "templateUri must not be null");
    Objects.requireNonNull(variableName, "variableName must not be null");
    if (definitionStartOffset < 0 || definitionEndOffset < definitionStartOffset) {
      throw new IllegalArgumentException("Invalid local definition offsets");
    }
  }

  static TemplateLocalSymbolKey of(
      String templateUri, String variableName, int definitionStartOffset, int definitionEndOffset) {
    return new TemplateLocalSymbolKey(
        templateUri, variableName, definitionStartOffset, definitionEndOffset);
  }
}
