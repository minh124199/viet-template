package io.github.minh124199.viettemplate.migration;

/** Compatibility classification relative to Apache Velocity 2.4.1. */
public enum MigrationClassification {
  EXACT_COMPATIBLE,
  COMPATIBLE_WITH_CONFIGURATION,
  KNOWN_BEHAVIOR_DIFFERENCE,
  DYNAMICALLY_UNVERIFIABLE,
  SECURITY_RESTRICTED,
  VIET_TEMPLATE_EXTENSION,
  UNSUPPORTED
}
