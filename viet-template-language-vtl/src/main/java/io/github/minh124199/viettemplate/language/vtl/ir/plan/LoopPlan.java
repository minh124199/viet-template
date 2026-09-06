package io.github.minh124199.viettemplate.language.vtl.ir.plan;

/** Execution strategy for a loop statement based on iterable type. */
public enum LoopPlan {
  ARRAY,
  LIST_INDEXED,
  ITERABLE,
  ITERATOR,
  RANGE,
  DYNAMIC
}
