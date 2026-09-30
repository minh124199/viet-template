package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/** Hover information result providing markdown documentation and affected source range. */
record HoverInfo(String markdownValue, Optional<Range> range) implements Serializable {

  public HoverInfo {
    Objects.requireNonNull(markdownValue, "markdownValue must not be null");
    Objects.requireNonNull(range, "range must not be null");
  }

  public String markdown() {
    return markdownValue();
  }

  public static HoverInfo of(String markdownValue) {
    return new HoverInfo(markdownValue, Optional.empty());
  }

  public static HoverInfo of(String markdownValue, Range range) {
    return new HoverInfo(markdownValue, Optional.ofNullable(range));
  }
}
