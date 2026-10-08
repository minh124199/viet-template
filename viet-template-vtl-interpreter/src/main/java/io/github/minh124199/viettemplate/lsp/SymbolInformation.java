package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/** Standard LSP SymbolInformation representation for workspace-wide symbol query results. */
record SymbolInformation(String name, SymbolKind kind, LocationInfo location, String containerName)
    implements Serializable {

  SymbolInformation {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(location, "location must not be null");
  }

  static SymbolInformation of(String name, SymbolKind kind, LocationInfo location) {
    return new SymbolInformation(name, kind, location, null);
  }

  static SymbolInformation of(
      String name, SymbolKind kind, LocationInfo location, String containerName) {
    return new SymbolInformation(name, kind, location, containerName);
  }
}
