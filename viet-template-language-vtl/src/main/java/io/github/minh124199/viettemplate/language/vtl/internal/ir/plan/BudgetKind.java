package io.github.minh124199.viettemplate.language.vtl.internal.ir.plan;

/** Defensive execution budget guard category. */
public enum BudgetKind {
  OUTPUT_CHARS,
  LOOP_ITERATIONS,
  PARSE_DEPTH,
  CALL_DEPTH
}
