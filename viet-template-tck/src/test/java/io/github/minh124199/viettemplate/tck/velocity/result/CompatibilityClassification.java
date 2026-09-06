package io.github.minh124199.viettemplate.tck.velocity.result;

/** Compatibility classification for an individual differential test scenario. */
public enum CompatibilityClassification {
  /**
   * Viet Template intentionally matches Velocity 2.4.1 behavior identically across output,
   * exception, context mutations, and observations.
   */
  EXACT_MATCH,

  /**
   * Viet Template deliberately differs from Velocity 2.4.1 (e.g., security restrictions, defensive
   * limits) with an explicit architectural rationale.
   */
  EXPECTED_DIFFERENCE,

  /** A valid Velocity 2.4.1 feature or syntax form that Viet Template does not yet implement. */
  UNSUPPORTED,

  /**
   * A Viet Template extension or feature not permitted under the standard Velocity compatibility
   * profile.
   */
  VIET_EXTENSION,

  /** A mismatch where Viet Template intends to match Velocity 2.4.1 but fails to do so. */
  BUG
}
