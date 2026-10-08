package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Workspace edit response mapping document URIs to lists of {@link LspTextEdit}s.
 */
public record LspWorkspaceEdit(
    Map<String, List<LspTextEdit>> changes
) {
  public LspWorkspaceEdit {
    Objects.requireNonNull(changes, "changes must not be null");
  }

  public static LspWorkspaceEdit empty() {
    return new LspWorkspaceEdit(Collections.emptyMap());
  }
}
