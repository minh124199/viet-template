package io.github.minh124199.viettemplate.api;

/**
 * Policy governing template evaluation and rendering behavior when encountering an undefined
 * reference or property.
 */
public enum UndefinedReferencePolicy {
  /**
   * Undefined references are rendered literally as their source text or suppressed according to
   * quiet reference semantics, matching traditional Apache Velocity behavior without emitting
   * warnings or throwing exceptions.
   */
  SILENT,

  /**
   * Undefined references emit a structured warning diagnostic (or log warning) while allowing
   * evaluation and rendering to continue using default undefined fallback behavior.
   */
  WARN,

  /**
   * Undefined references halt rendering immediately by throwing an evaluation or render exception
   * with exact template identifier and source span information.
   */
  ERROR
}
