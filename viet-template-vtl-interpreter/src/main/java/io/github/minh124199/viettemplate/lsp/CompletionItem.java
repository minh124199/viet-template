package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable completion proposal returned by completion queries.
 *
 * <p>Implements deterministic natural ordering based on sortText and label.
 */
record CompletionItem(
    String label, CompletionItemKind kind, String detail, String documentation, String sortText)
    implements Comparable<CompletionItem>, Serializable {

  public CompletionItem {
    Objects.requireNonNull(label, "label must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    detail = detail == null ? "" : detail;
    documentation = documentation == null ? "" : documentation;
    sortText = sortText == null ? label : sortText;
  }

  public static CompletionItem of(String label, CompletionItemKind kind, String detail) {
    return new CompletionItem(label, kind, detail, "", label);
  }

  public static CompletionItem of(
      String label, CompletionItemKind kind, String detail, String documentation) {
    return new CompletionItem(label, kind, detail, documentation, label);
  }

  @Override
  public int compareTo(CompletionItem o) {
    Objects.requireNonNull(o, "o must not be null");
    int cmp = this.sortText.compareTo(o.sortText);
    if (cmp != 0) {
      return cmp;
    }
    cmp = this.label.compareTo(o.label);
    if (cmp != 0) {
      return cmp;
    }
    return Integer.compare(this.kind.value(), o.kind.value());
  }
}
