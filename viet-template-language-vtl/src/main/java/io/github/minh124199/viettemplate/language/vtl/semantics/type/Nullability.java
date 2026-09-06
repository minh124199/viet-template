package io.github.minh124199.viettemplate.language.vtl.semantics.type;

/** Explicit nullability state of a value or reference in the Viet Template type system. */
public enum Nullability {
  /** Definitely not null at runtime. */
  NON_NULL,

  /** May be null at runtime. */
  NULLABLE,

  /** Nullability cannot be statically determined or is not constrained. */
  UNKNOWN
}
