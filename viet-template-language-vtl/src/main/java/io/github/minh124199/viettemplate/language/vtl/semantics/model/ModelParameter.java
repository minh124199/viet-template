package io.github.minh124199.viettemplate.language.vtl.semantics.model;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;

/** Represents a declared parameter in a template's root model schema. */
public record ModelParameter(String name, VType type) {
  public ModelParameter {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }

  public static ModelParameter of(String name, VType type) {
    return new ModelParameter(name, type);
  }
}
