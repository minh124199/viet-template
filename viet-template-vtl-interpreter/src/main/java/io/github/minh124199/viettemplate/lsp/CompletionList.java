package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Immutable container for completion proposals. */
record CompletionList(boolean isIncomplete, List<CompletionItem> items) implements Serializable {

  public CompletionList {
    items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
  }

  public static CompletionList of(List<CompletionItem> items) {
    return new CompletionList(false, items);
  }

  public static CompletionList empty() {
    return new CompletionList(false, List.of());
  }
}
