package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Optimization pass eliminating redundant local loads, dead local stores, and identity type
 * conversions ({@link IrConvert}).
 */
public final class RedundantConversionPass implements IrOptimizationPass {

  public static final String NAME = "RedundantConversion";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().redundantConversion()) {
      return template;
    }

    IrBlock newRoot = optimizeBlock(template.root(), context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = optimizeBlock(fn.body(), context);
      newFunctions.add(new IrFunction(fn.name(), fn.parameters(), fn.locals(), newBody, fn.span()));
    }

    return new IrTemplate(
        template.id(),
        template.parameters(),
        newRoot,
        template.constants(),
        template.capabilities(),
        newFunctions,
        template.span());
  }

  private IrBlock optimizeBlock(IrBlock block, OptimizationContext context) {
    List<IrStatement> originalStmts = block.statements();
    List<IrStatement> optimizedStmts = new ArrayList<>();

    for (int i = 0; i < originalStmts.size(); i++) {
      IrStatement stmt = originalStmts.get(i);

      // Dead consecutive local store elimination
      if (stmt instanceof IrStoreLocal sl1 && i + 1 < originalStmts.size()) {
        IrStatement next = originalStmts.get(i + 1);
        if (next instanceof IrStoreLocal sl2
            && sl1.local().slot() == sl2.local().slot()
            && isPure(sl1.value())) {
          context.statistics().recordConversionEliminated();
          continue; // Skip the overwritten dead store
        }
      }

      optimizedStmts.add(optimizeStatement(stmt, context));
    }

    return new IrBlock(optimizedStmts, block.span());
  }

  private IrStatement optimizeStatement(IrStatement stmt, OptimizationContext context) {
    if (stmt instanceof IrWriteValue wv) {
      IrExpression optVal = optimizeExpression(wv.value(), context);
      return new IrWriteValue(optVal, wv.escapeMode(), wv.nullMode(), wv.span());
    }

    if (stmt instanceof IrStoreLocal sl) {
      IrExpression optVal = optimizeExpression(sl.value(), context);
      return new IrStoreLocal(sl.local(), optVal, sl.span());
    }

    if (stmt instanceof IrIf ifStmt) {
      IrExpression optCond = optimizeExpression(ifStmt.condition(), context);
      IrBlock optThen = optimizeBlock(ifStmt.thenBlock(), context);
      Optional<IrBlock> optElse = ifStmt.elseBlock().map(eb -> optimizeBlock(eb, context));
      return new IrIf(optCond, optThen, optElse, ifStmt.span());
    }

    if (stmt instanceof IrLoop loop) {
      IrExpression optIter = optimizeExpression(loop.iterable(), context);
      IrBlock optBody = optimizeBlock(loop.body(), context);
      Optional<IrBlock> optElse = loop.elseBody().map(eb -> optimizeBlock(eb, context));
      return new IrLoop(
          loop.plan(),
          optIter,
          loop.elementLocal(),
          loop.loopStateLocal(),
          optBody,
          optElse,
          loop.span());
    }

    if (stmt instanceof IrCallMacro cm) {
      List<IrExpression> optArgs = new ArrayList<>();
      for (IrExpression arg : cm.arguments()) {
        optArgs.add(optimizeExpression(arg, context));
      }
      Optional<IrBlock> optBody = cm.bodyContent().map(bc -> optimizeBlock(bc, context));
      return new IrCallMacro(cm.macroName(), optArgs, optBody, cm.span());
    }

    return stmt;
  }

  public IrExpression optimizeExpression(IrExpression expr, OptimizationContext context) {
    if (expr instanceof IrConvert conv) {
      IrExpression inner = optimizeExpression(conv.expression(), context);

      // Identity conversion: targetType equals inner expression type
      if (conv.type().equals(inner.type())) {
        context.statistics().recordConversionEliminated();
        return inner;
      }

      // Conversion to Object when inner is already reference type
      if (conv.type().equals(VTypes.OBJECT) && !inner.type().isPrimitive()) {
        context.statistics().recordConversionEliminated();
        return inner;
      }

      return new IrConvert(inner, conv.type(), conv.span());
    }

    if (expr instanceof IrBinaryOp bin) {
      IrExpression left = optimizeExpression(bin.left(), context);
      IrExpression right = optimizeExpression(bin.right(), context);
      return new IrBinaryOp(bin.op(), left, right, bin.type(), bin.span());
    }

    if (expr instanceof IrUnaryOp un) {
      IrExpression operand = optimizeExpression(un.operand(), context);
      return new IrUnaryOp(un.op(), operand, un.type(), un.span());
    }

    if (expr instanceof IrTruthiness truth) {
      IrExpression operand = optimizeExpression(truth.expression(), context);
      return new IrTruthiness(operand, truth.emptyCheck(), truth.span());
    }

    return expr;
  }

  private boolean isPure(IrExpression expr) {
    return expr instanceof IrConst || expr instanceof IrLoadLocal || expr instanceof IrLoadParam;
  }
}
