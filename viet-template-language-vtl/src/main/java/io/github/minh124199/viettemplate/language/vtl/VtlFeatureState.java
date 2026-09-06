package io.github.minh124199.viettemplate.language.vtl;

/** Compatibility status of a VTL feature against reference Apache Velocity behavior. */
public enum VtlFeatureState {
  SUPPORTED_EXACT,
  SUPPORTED_WITH_DECLARED_DIFFERENCE,
  SUPPORTED_ONLY_IN_DYNAMIC_MODE,
  SUPPORTED_ONLY_IN_INTERPRETER,
  PLANNED,
  INTENTIONALLY_UNSUPPORTED
}
