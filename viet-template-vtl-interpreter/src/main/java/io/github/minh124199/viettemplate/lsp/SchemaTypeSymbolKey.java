package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

/** Type symbol key uniquely identifying a shape-only schema type declaration. */
record SchemaTypeSymbolKey(String schemaSource, String typeName) implements WorkspaceTypeSymbolKey {

  public SchemaTypeSymbolKey {
    Objects.requireNonNull(schemaSource, "schemaSource must not be null");
    Objects.requireNonNull(typeName, "typeName must not be null");
  }

  static SchemaTypeSymbolKey of(String schemaSource, String typeName) {
    return new SchemaTypeSymbolKey(schemaSource, typeName);
  }
}
