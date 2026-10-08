package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/**
 * Result of a {@code textDocument/prepareRename} request containing the target identifier range and
 * default placeholder text.
 */
record PrepareRenameResult(Range range, String placeholder) implements Serializable {

  PrepareRenameResult {
    Objects.requireNonNull(range, "range must not be null");
    Objects.requireNonNull(placeholder, "placeholder must not be null");
  }

  static PrepareRenameResult of(Range range, String placeholder) {
    return new PrepareRenameResult(range, placeholder);
  }
}
