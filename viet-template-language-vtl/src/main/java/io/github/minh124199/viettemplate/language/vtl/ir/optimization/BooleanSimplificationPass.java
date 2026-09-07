package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
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
 * Optimization pass performing boolean algebra simplifications, short-circuit literal reductions,
 * double negation elimination, redundant truthiness flattening, and conditional branch inversion.
 */
public final class BooleanSimplificationPass implements IrOptimizationPass {

  public static final String NAME = "BooleanSimplification";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().booleanSimplification()) {
      return template;
    }

    IrBlock newRoot = simplifyBlock(template.root(), context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = simplifyBlock(fn.body(), context);
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

  private IrBlock simplifyBlock(IrBlock block, OptimizationContext context) {
    List<IrStatement> simplifiedStmts = new ArrayList<>();
    for (IrStatement stmt : block.statements()) {
      simplifiedStmts.add(simplifyStatement(stmt, context));
    }
    return new IrBlock(simplifiedStmts, block.span());
  }

  private IrStatement simplifyStatement(IrStatement stmt, OptimizationContext context) {
    if (stmt instanceof IrWriteValue wv) {
      IrExpression simplifiedVal = simplifyExpression(wv.value(), context);
      return new IrWriteValue(simplifiedVal, wv.escapeMode(), wv.nullMode(), wv.span());
    }

    if (stmt instanceof IrStoreLocal sl) {
      IrExpression simplifiedVal = simplifyExpression(sl.value(), context);
      return new IrStoreLocal(sl.local(), simplifiedVal, sl.span());
    }

    if (stmt instanceof IrIf ifStmt) {
      IrExpression cond = simplifyExpression(ifStmt.condition(), context);
      IrBlock thenBlock = simplifyBlock(ifStmt.thenBlock(), context);
      Optional<IrBlock> elseBlock = ifStmt.elseBlock().map(eb -> simplifyBlock(eb, context));

      // Invert if statement with !condition and both branches present
      if (cond instanceof IrUnaryOp un && un.op() == UnaryOpKind.NOT && elseBlock.isPresent()) {
        context.statistics().recordBooleanSimplified();
        return new IrIf(un.operand(), elseBlock.get(), Optional.of(thenBlock), ifStmt.span());
      }

      return new IrIf(cond, thenBlock, elseBlock, ifStmt.span());
    }

    if (stmt instanceof IrLoop loop) {
      IrExpression iter = simplifyExpression(loop.iterable(), context);
      IrBlock body = simplifyBlock(loop.body(), context);
      Optional<IrBlock> elseBody = loop.elseBody().map(eb -> simplifyBlock(eb, context));
      return new IrLoop(
          loop.plan(),
          iter,
          loop.elementLocal(),
          loop.loopStateLocal(),
          body,
          elseBody,
          loop.span());
    }

    if (stmt instanceof IrCallMacro cm) {
      List<IrExpression> args = new ArrayList<>();
      for (IrExpression a : cm.arguments()) {
        args.add(simplifyExpression(a, context));
      }
      Optional<IrBlock> body = cm.bodyContent().map(bc -> simplifyBlock(bc, context));
      return new IrCallMacro(cm.macroName(), args, body, cm.span());
    }

    if (stmt instanceof IrEvaluate eval) {
      IrExpression simplifiedVal = simplifyExpression(eval.expression(), context);
      return new IrEvaluate(simplifiedVal, eval.span());
    }

    return stmt;
  }

  public IrExpression simplifyExpression(IrExpression expr, OptimizationContext context) {
    if (expr instanceof IrBinaryOp bin) {
      IrExpression left = simplifyExpression(bin.left(), context);
      IrExpression right = simplifyExpression(bin.right(), context);

      if (bin.op() == BinaryOpKind.AND) {
        // false && x -> false
        if (left instanceof IrConst c && Boolean.FALSE.equals(c.value())) {
          context.statistics().recordBooleanSimplified();
          return new IrConst(false, VTypes.BOOLEAN, bin.span());
        }
        // true && x -> x
        if (left instanceof IrConst c && Boolean.TRUE.equals(c.value())) {
          context.statistics().recordBooleanSimplified();
          return right;
        }
        // x && false -> false (if pure)
        if (right instanceof IrConst c && Boolean.FALSE.equals(c.value()) && isPure(left)) {
          context.statistics().recordBooleanSimplified();
          return new IrConst(false, VTypes.BOOLEAN, bin.span());
        }
        // x && true -> x
        if (right instanceof IrConst c && Boolean.TRUE.equals(c.value())) {
          context.statistics().recordBooleanSimplified();
          return left;
        }
      }

      if (bin.op() == BinaryOpKind.OR) {
        // true || x -> true
        if (left instanceof IrConst c && Boolean.TRUE.equals(c.value())) {
          context.statistics().recordBooleanSimplified();
          return new IrConst(true, VTypes.BOOLEAN, bin.span());
        }
        // false || x -> x
        if (left instanceof IrConst c && Boolean.FALSE.equals(c.value())) {
          context.statistics().recordBooleanSimplified();
          return right;
        }
        // x || true -> true (if pure)
        if (right instanceof IrConst c && Boolean.TRUE.equals(c.value()) && isPure(left)) {
          context.statistics().recordBooleanSimplified();
          return new IrConst(true, VTypes.BOOLEAN, bin.span());
        }
        // x || false -> x
        if (right instanceof IrConst c && Boolean.FALSE.equals(c.value())) {
          context.statistics().recordBooleanSimplified();
          return left;
        }
      }

      return new IrBinaryOp(bin.op(), left, right, bin.type(), bin.span());
    }

    if (expr instanceof IrUnaryOp un) {
      IrExpression operand = simplifyExpression(un.operand(), context);

      // !(!x) -> x (when x is boolean)
      if (un.op() == UnaryOpKind.NOT
          && operand instanceof IrUnaryOp inner
          && inner.op() == UnaryOpKind.NOT
          && (inner.operand().type().equals(VTypes.BOOLEAN))) {
        context.statistics().recordBooleanSimplified();
        return inner.operand();
      }

      return new IrUnaryOp(un.op(), operand, un.type(), un.span());
    }

    if (expr instanceof IrTruthiness truth) {
      IrExpression operand = simplifyExpression(truth.expression(), context);

      // IrTruthiness(IrTruthiness(x)) -> IrTruthiness(x)
      if (operand instanceof IrTruthiness inner) {
        context.statistics().recordBooleanSimplified();
        return inner;
      }

      // If operand is already known boolean, truthiness is identity
      if (operand.type().equals(VTypes.BOOLEAN)) {
        context.statistics().recordBooleanSimplified();
        return operand;
      }

      return new IrTruthiness(operand, truth.emptyCheck(), truth.span());
    }

    return expr;
  }

  private boolean isPure(IrExpression expr) {
    return expr instanceof IrConst;
  }
}
