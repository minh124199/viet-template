package io.github.minh124199.viettemplate.runtime.linker;

/** Kind of dynamic member access operation performed at a call site. */
public enum MemberOperation {
  PROPERTY_GET,
  PROPERTY_SET,
  METHOD_CALL,
  INDEX_GET,
  INDEX_SET
}
