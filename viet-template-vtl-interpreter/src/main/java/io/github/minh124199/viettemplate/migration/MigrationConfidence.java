package io.github.minh124199.viettemplate.migration;

/** Confidence level of static migration analysis. */
public enum MigrationConfidence {
  STATICALLY_VERIFIED,
  DOCUMENTED_DIFFERENCE,
  DYNAMICALLY_UNVERIFIABLE
}
