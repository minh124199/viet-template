package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimization pass planning method-size segmentation and splitting oversized render blocks into
 * smaller, manageable function chunks to prevent exceeding JVM method bytecode limits (64KB) in
 * downstream code generators.
 */
public final class MethodSizePlanningPass implements IrOptimizationPass {

  public static final String NAME = "MethodSizePlanning";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().methodSizePlanning()) {
      return template;
    }

    int threshold = context.options().methodSplitThreshold();
    IrBlock root = template.root();

    if (root.size() <= threshold) {
      return template;
    }

    List<IrStatement> originalStmts = root.statements();
    List<IrStatement> newRootStmts = new ArrayList<>();
    List<IrFunction> newFunctions = new ArrayList<>(template.functions());

    // Prepare argument expressions for passing template parameters to chunk functions
    List<IrExpression> paramArgs = new ArrayList<>();
    for (IrParameter p : template.parameters()) {
      paramArgs.add(new IrLoadParam(p.name(), p.slot(), p.type(), p.span()));
    }

    int total = originalStmts.size();
    int start = 0;

    while (start < total) {
      int end = Math.min(start + threshold, total);
      List<IrStatement> slice = originalStmts.subList(start, end);
      SourceSpan sliceSpan = slice.get(0).span();

      if (start == 0) {
        // Keep the first chunk in the root block
        newRootStmts.addAll(slice);
      } else {
        // Extract subsequent chunks into synthetic helper functions
        String chunkName = context.nextChunkFunctionName();
        IrBlock chunkBody = new IrBlock(new ArrayList<>(slice), sliceSpan);
        IrFunction chunkFunction =
            new IrFunction(chunkName, template.parameters(), List.of(), chunkBody, sliceSpan);
        newFunctions.add(chunkFunction);

        newRootStmts.add(IrCallMacro.of(chunkName, paramArgs, sliceSpan));
        context.statistics().recordMethodSplit();
      }

      start = end;
    }

    IrBlock newRoot = new IrBlock(newRootStmts, root.span());

    return new IrTemplate(
        template.id(),
        template.parameters(),
        newRoot,
        template.constants(),
        template.capabilities(),
        newFunctions,
        template.span());
  }
}
