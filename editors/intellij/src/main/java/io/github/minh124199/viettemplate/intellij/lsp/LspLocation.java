package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Objects;

/**
 * Definition target location returned by the Viet Template Language Server.
 */
public record LspLocation(
    String uri,
    LspRange range
) {
  public LspLocation {
    Objects.requireNonNull(uri, "uri must not be null");
    Objects.requireNonNull(range, "range must not be null");
  }
}
