package io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode;

/** Enumeration of write output specialization dispatch targets. */
public enum WriteDispatchKind {

  /** Specialized direct write for statically-proven String values. */
  WRITE_STRING_SPECIALIZED,

  /** Specialized direct write for statically-proven Integer values. */
  WRITE_INTEGER_SPECIALIZED,

  /** Generic fallback write handling dynamic dispatch, reflection, and general formatting. */
  GENERIC_WRITE_VALUE
}
