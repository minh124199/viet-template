package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.verifier.IrVerifier;
import java.util.List;
import java.util.Objects;

/**
 * Main compiler intermediate representation optimizer orchestrator.
 *
 * <p>Executes a pipeline of verified optimization passes according to {@code
 * docs/06-optimization-pipeline.md}, guaranteeing full semantics preservation and invariant
 * verification via {@link IrVerifier}.
 */
public final class IrOptimizer {

  public record OptimizationResult(IrTemplate template, OptimizationStatistics statistics) {
    public OptimizationResult {
      Objects.requireNonNull(template, "template must not be null");
      Objects.requireNonNull(statistics, "statistics must not be null");
    }
  }

  private IrOptimizer() {}

  /** Optimizes the given template using production-default {@link OptimizationLevel#O2} options. */
  public static IrTemplate optimize(IrTemplate template) {
    return optimize(template, IrOptimizationOptions.defaultOptions());
  }

  /** Optimizes the given template using the specified {@link OptimizationLevel} preset. */
  public static IrTemplate optimize(IrTemplate template, OptimizationLevel level) {
    return optimize(template, IrOptimizationOptions.builder(level).build());
  }

  /** Optimizes the given template using custom {@link IrOptimizationOptions}. */
  public static IrTemplate optimize(IrTemplate template, IrOptimizationOptions options) {
    return optimizeWithStats(template, options).template();
  }

  /**
   * Optimizes the given template and returns both the transformed {@link IrTemplate} and collected
   * {@link OptimizationStatistics}.
   */
  public static OptimizationResult optimizeWithStats(
      IrTemplate template, IrOptimizationOptions options) {
    Objects.requireNonNull(template, "template must not be null");
    Objects.requireNonNull(options, "options must not be null");

    OptimizationContext context = new OptimizationContext(template, options);
    IrTemplate current = new AssignVariableSlots().run(template, context); // O45 (mandatory)

    if (options.level() == OptimizationLevel.O0) {
      IrVerifier.verify(current);
      return new OptimizationResult(current, context.statistics());
    }

    List<IrOptimizationPass> passes =
        List.of(
            new DeadCodeEliminationPass(), // O10
            new MergeTextConstantsPass(), // O20
            new ConstantFoldingPass(), // O30
            new RedundantConversionPass(), // O40
            new DeadCodeEliminationPass(), // O50: Clean up dead branches exposed by folding
            new BooleanSimplificationPass(), // O60
            new DirectAccessorBindingPass(), // O70
            new LoopSpecializationPass(), // O80
            new PrimitiveSpecializationPass(), // O90
            new MacroInliningPass(), // O100
            new EscapeSpecializationPass(), // O120
            new MergeTextConstantsPass(), // Merge constants hoisted by escape pass / inlining
            new PreEncodeUtf8Pass(), // Ensure all constants have UTF-8 byte arrays
            new MethodSizePlanningPass() // O150
            );

    for (IrOptimizationPass pass : passes) {
      current = pass.run(current, context);
    }

    // O160: Verify IR invariants
    IrVerifier.verify(current);

    return new OptimizationResult(current, context.statistics());
  }
}
