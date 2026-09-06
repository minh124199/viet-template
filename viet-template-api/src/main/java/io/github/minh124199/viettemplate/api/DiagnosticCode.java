package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.util.Objects;

/** Stable diagnostic code identifying specific error, warning, or informational events. */
public record DiagnosticCode(String category, String id) implements Serializable {

  public DiagnosticCode {
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(id, "id must not be null");
    if (category.isBlank()) {
      throw new IllegalArgumentException("category must not be blank");
    }
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
  }

  public static DiagnosticCode of(String category, String id) {
    return new DiagnosticCode(category, id);
  }

  public String qualifiedCode() {
    return category + ":" + id;
  }

  @Override
  public String toString() {
    return qualifiedCode();
  }
}
