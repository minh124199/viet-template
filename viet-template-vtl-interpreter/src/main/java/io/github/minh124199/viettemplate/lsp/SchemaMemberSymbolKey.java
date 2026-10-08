package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

record SchemaMemberSymbolKey(String schemaSource, String typeName, String propertyName)
    implements WorkspaceSymbolKey {

  public SchemaMemberSymbolKey {
    schemaSource = schemaSource != null ? schemaSource : "";
    Objects.requireNonNull(typeName, "typeName must not be null");
    Objects.requireNonNull(propertyName, "propertyName must not be null");
  }

  static SchemaMemberSymbolKey of(String schemaSource, String typeName, String propertyName) {
    return new SchemaMemberSymbolKey(schemaSource, typeName, propertyName);
  }
}
