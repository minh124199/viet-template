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

  /**
   * Parses a case-insensitive configuration string into a {@link TypeCheckingMode}.
   *
   * @param value configuration string (e.g. "OFF", "WARN", "ERROR", "true", "false")
   * @return the resolved {@link TypeCheckingMode}, defaulting to {@link #OFF} if null or blank
   * @throws IllegalArgumentException if the value cannot be recognized
   */
  public static TypeCheckingMode parse(String value) {
    if (value == null || value.isBlank()) {
      return OFF;
    }
    String normalized = value.trim().toUpperCase();
    return switch (normalized) {
      case "OFF", "FALSE", "NONE" -> OFF;
      case "WARN", "WARNING" -> WARN;
      case "ERROR", "STRICT", "TRUE" -> ERROR;
      default -> throw new IllegalArgumentException("Unknown TypeCheckingMode: " + value);
    };
  }
}
