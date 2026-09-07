package io.github.minh124199.viettemplate.vtl.compiler;

import java.util.Objects;

/** Identifier for a template execution/compilation backend. */
public record BackendId(String name) {

  public static final BackendId AOT_BYTECODE = new BackendId("AOT_BYTECODE");
  public static final BackendId INTERPRETER = new BackendId("INTERPRETER");

  public BackendId {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }

  public static BackendId of(String name) {
    return new BackendId(name);
  }
}
