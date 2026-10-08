package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Objects;

/**
 * Symbol information returned by the Viet Template Language Server for workspace symbol queries.
 */
public record LspSymbolInformation(
    String name,
    int kind,
    LspLocation location,
    String containerName
) {
  public LspSymbolInformation {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(location, "location must not be null");
    containerName = containerName != null ? containerName : "";
  }
}
