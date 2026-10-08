package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Atomic workspace change description mapping document URIs to lists of non-overlapping {@link
 * TextEdit}s.
 */
record WorkspaceEdit(Map<String, List<TextEdit>> changes) implements Serializable {

  WorkspaceEdit {
    Objects.requireNonNull(changes, "changes must not be null");
  }

  static WorkspaceEdit empty() {
    return new WorkspaceEdit(Map.of());
  }

  static WorkspaceEdit of(Map<String, List<TextEdit>> rawChanges) {
    Objects.requireNonNull(rawChanges, "rawChanges must not be null");
    Map<String, List<TextEdit>> sortedChanges = new TreeMap<>();

    for (Map.Entry<String, List<TextEdit>> entry : rawChanges.entrySet()) {
      String uri = entry.getKey();
      List<TextEdit> edits = entry.getValue();
      if (edits == null || edits.isEmpty()) {
        continue;
      }

      List<TextEdit> sorted = new ArrayList<>(edits);
      Collections.sort(sorted);

      List<TextEdit> deduplicated = new ArrayList<>();
      for (TextEdit edit : sorted) {
        if (deduplicated.isEmpty() || !deduplicated.get(deduplicated.size() - 1).equals(edit)) {
          deduplicated.add(edit);
        }
      }

      for (int i = 0; i < deduplicated.size() - 1; i++) {
        TextEdit cur = deduplicated.get(i);
        TextEdit next = deduplicated.get(i + 1);
        if (cur.range().end().compareTo(next.range().start()) > 0) {
          throw new RenameConflictException(
              RenameConflictException.Reason.OVERLAPPING_EDITS,
              String.format(
                  "Overlapping edits detected in %s between %s and %s",
                  uri, cur.range(), next.range()));
        }
      }

      sortedChanges.put(uri, Collections.unmodifiableList(deduplicated));
    }

    return new WorkspaceEdit(Collections.unmodifiableMap(sortedChanges));
  }
}
