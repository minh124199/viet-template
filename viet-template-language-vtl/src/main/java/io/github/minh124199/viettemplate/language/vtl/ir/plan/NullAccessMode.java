package io.github.minh124199.viettemplate.language.vtl.ir.plan;

/** Mode governing receiver navigation when receiver evaluates to null. */
public enum NullAccessMode {
  PROPAGATE_NULL,
  THROW_IF_NULL
}
