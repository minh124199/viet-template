package io.github.minh124199.viettemplate.language.vtl.semantics.capability;

/** Actions categorized during template capability and security analysis. */
public enum CapabilityAction {
  READ_PROPERTY,
  READ_INDEX,
  CALL_METHOD,
  MUTATE_PROPERTY,
  MUTATE_INDEX,
  LOAD_TEMPLATE_STATIC,
  LOAD_TEMPLATE_DYNAMIC,
  INCLUDE_RESOURCE,
  EVALUATE_SOURCE,
  ACCESS_FRAMEWORK_OBJECT,
  RAW_OUTPUT
}
