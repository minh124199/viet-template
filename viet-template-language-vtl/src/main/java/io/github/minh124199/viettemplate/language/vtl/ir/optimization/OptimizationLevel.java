package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

/**
 * Standard optimization levels defining pass presets for the IR compiler optimizer.
 *
 * <p>Preserves all semantic invariants specified in {@code docs/06-optimization-pipeline.md}.
 */
public enum OptimizationLevel {
  /** O0: Correctness and debug baseline; runs no transformations, verifies IR integrity. */
  O0,

  /**
   * O1: Cheap deterministic passes (dead-code elimination, text chunk merging, constant folding,
   * UTF-8 pre-encoding, redundant conversion elimination).
   */
  O1,

  /**
   * O2: Production default; includes all O1 passes plus boolean simplification, direct accessor
   * binding, loop specialization, primitive specialization, macro inlining, escape specialization,
   * and method-size planning.
   */
  O2,

  /** O3: Aggressive experimental optimization pipeline. */
  O3
}
