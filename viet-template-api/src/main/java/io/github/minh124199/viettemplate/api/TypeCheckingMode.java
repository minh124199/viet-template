package io.github.minh124199.viettemplate.api;

/**
 * Mode governing static contract validation, member resolution checking, and compile-time
 * diagnostics.
 */
public enum TypeCheckingMode {
  /**
   * Type checking is disabled. Dynamic, Velocity-compatible evaluation semantics are preserved.
   * Template contracts are used solely for runtime specialization and direct bytecode access.
   */
  OFF,

  /**
   * Provable type inconsistencies emit warning diagnostics while allowing compilation and artifact
   * generation to proceed.
   */
  WARN,

  /** Provable type inconsistencies emit error diagnostics and fail template compilation. */
  ERROR;

  /** Returns {@code true} if type checking is enabled (either {@link #WARN} or {@link #ERROR}). */
  public boolean isEnabled() {
    return this != OFF;
  }
}
