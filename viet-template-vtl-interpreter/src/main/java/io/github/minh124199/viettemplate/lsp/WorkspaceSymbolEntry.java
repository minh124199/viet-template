package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/** In-memory index entry capturing a discovered workspace symbol. */
record WorkspaceSymbolEntry(
    String name,
    SymbolKind kind,
    String containerName,
    String qualifiedName,
    LocationInfo location,
    String sourceKind,
    Serializable symbolIdentity) {

  WorkspaceSymbolEntry {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(qualifiedName, "qualifiedName must not be null");
    Objects.requireNonNull(location, "location must not be null");
    Objects.requireNonNull(sourceKind, "sourceKind must not be null");
    Objects.requireNonNull(symbolIdentity, "symbolIdentity must not be null");
    containerName = containerName != null ? containerName : "";
  }

  SymbolInformation toSymbolInformation() {
    return new SymbolInformation(name, kind, location, containerName);
  }
}
