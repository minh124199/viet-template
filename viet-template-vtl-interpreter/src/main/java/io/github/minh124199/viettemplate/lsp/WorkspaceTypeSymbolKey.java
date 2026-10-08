package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;

/**
 * Canonical semantic type identity distinguishing JVM classes from shape-only contract schema
 * types.
 */
sealed interface WorkspaceTypeSymbolKey extends Serializable
    permits JvmTypeSymbolKey, SchemaTypeSymbolKey {

  static JvmTypeSymbolKey jvmType(String className) {
    return new JvmTypeSymbolKey(className);
  }

  static SchemaTypeSymbolKey schemaType(String schemaSource, String typeName) {
    return new SchemaTypeSymbolKey(schemaSource, typeName);
  }
}
