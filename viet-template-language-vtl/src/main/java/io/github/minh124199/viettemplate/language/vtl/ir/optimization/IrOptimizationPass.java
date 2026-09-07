package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;

/**
 * Common contract for intermediate representation optimization passes.
 *
 * <p>Each pass takes an input {@link IrTemplate} and {@link OptimizationContext}, applies its
 * transformations, and returns the resulting {@link IrTemplate}. All passes must preserve the core
 * invariants defined in {@code docs/06-optimization-pipeline.md}:
 *
 * <ol>
 *   <li>Observable output
 *   <li>Evaluation order
 *   <li>Permitted side-effects
 *   <li>Exception classification and SourceSpan
 *   <li>Security capability boundaries
 * </ol>
 */
public interface IrOptimizationPass {

  /** Returns the human-readable canonical name of this optimization pass. */
  String name();

  /** Runs the optimization pass over the provided template. */
  IrTemplate run(IrTemplate template, OptimizationContext context);
}
