package io.github.minh124199.viettemplate.language.vtl.ir.plan;

/** Defensive execution budget guard category. */
public enum BudgetKind {
  OUTPUT_CHARS,
  LOOP_ITERATIONS,
  PARSE_DEPTH,
  CALL_DEPTH
}
