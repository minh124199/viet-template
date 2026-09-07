package io.github.minh124199.viettemplate.api;

/** Precedence order among multiple configured global macro libraries. */
public enum GlobalMacroPrecedence {
  /**
   * Later configured global macro libraries override earlier ones (standard cascading override).
   */
  LAST_WINS,

  /** Earlier configured global macro libraries take precedence over later ones. */
  FIRST_WINS
}
