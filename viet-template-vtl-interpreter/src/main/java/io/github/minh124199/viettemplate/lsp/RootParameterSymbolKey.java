package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

record RootParameterSymbolKey(String schemaSourceOrTemplateUri, String rootName)
    implements WorkspaceSymbolKey {

  public RootParameterSymbolKey {
    schemaSourceOrTemplateUri = schemaSourceOrTemplateUri != null ? schemaSourceOrTemplateUri : "";
    Objects.requireNonNull(rootName, "rootName must not be null");
  }

  static RootParameterSymbolKey of(String schemaSourceOrTemplateUri, String rootName) {
    return new RootParameterSymbolKey(schemaSourceOrTemplateUri, rootName);
  }
}
