package io.github.minh124199.viettemplate.language.vtl.ir.plan;

/** Mode governing output behavior when an expression evaluates to null or undefined. */
public enum NullRenderMode {
  /** Render the literal source text (e.g. "$var" or "${var}") in standard non-strict mode. */
  LITERAL_EXPRESSION,
  /** Render empty string (e.g. for quiet references "$!var"). */
  EMPTY_STRING,
  /** Throw an exception at runtime (e.g. strict references mode). */
  THROW_ERROR
}
