package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Optimization pass specializing primitive expressions and operations (primitive arithmetic,
 * comparisons, and eliminating boxed wrapper conversions for primitive values).
 */
public final class PrimitiveSpecializationPass implements IrOptimizationPass {

  public static final String NAME = "PrimitiveSpecialization";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().primitiveSpecialization()) {
      return template;
    }

    IrBlock newRoot = specializeBlock(template.root(), context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = specializeBlock(fn.body(), context);
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

  private IrBlock specializeBlock(IrBlock block, OptimizationContext context) {
    List<IrStatement> specializedStmts = new ArrayList<>();
    for (IrStatement stmt : block.statements()) {
      specializedStmts.add(specializeStatement(stmt, context));
    }
    return new IrBlock(specializedStmts, block.span());
  }

  private IrStatement specializeStatement(IrStatement stmt, OptimizationContext context) {
    if (stmt instanceof IrWriteValue wv) {
      IrExpression specializedVal = specializeExpression(wv.value(), context);
      return new IrWriteValue(specializedVal, wv.escapeMode(), wv.nullMode(), wv.span());
    }

    if (stmt instanceof IrStoreLocal sl) {
      IrExpression specializedVal = specializeExpression(sl.value(), context);
      return new IrStoreLocal(sl.local(), specializedVal, sl.span());
    }

    if (stmt instanceof IrIf ifStmt) {
      IrExpression cond = specializeExpression(ifStmt.condition(), context);
      IrBlock thenBlock = specializeBlock(ifStmt.thenBlock(), context);
      Optional<IrBlock> elseBlock = ifStmt.elseBlock().map(eb -> specializeBlock(eb, context));
      return new IrIf(cond, thenBlock, elseBlock, ifStmt.span());
    }

    if (stmt instanceof IrLoop loop) {
      IrExpression iter = specializeExpression(loop.iterable(), context);
      IrBlock body = specializeBlock(loop.body(), context);
      Optional<IrBlock> elseBody = loop.elseBody().map(eb -> specializeBlock(eb, context));
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
        args.add(specializeExpression(a, context));
      }
      Optional<IrBlock> body = cm.bodyContent().map(bc -> specializeBlock(bc, context));
      return new IrCallMacro(cm.macroName(), args, body, cm.span());
    }

    return stmt;
  }

  public IrExpression specializeExpression(IrExpression expr, OptimizationContext context) {
    if (expr instanceof IrBinaryOp bin) {
      IrExpression left = unwrapBoxing(specializeExpression(bin.left(), context));
      IrExpression right = unwrapBoxing(specializeExpression(bin.right(), context));

      // Specialize arithmetic result types for primitives (excluding range expressions)
      VType specializedType = bin.type();
      if (!(bin.type() instanceof VType.ArrayType)
          && VTypes.isNumeric(left.type())
          && VTypes.isNumeric(right.type())) {
        if (isComparisonOp(bin.op())) {
          specializedType = VTypes.BOOLEAN;
        } else {
          specializedType = resolveNumericType(left.type(), right.type());
        }
        if (!specializedType.equals(bin.type())) {
          context.statistics().recordPrimitiveSpecialized();
        }
      }

      return new IrBinaryOp(bin.op(), left, right, specializedType, bin.span());
    }

    if (expr instanceof IrUnaryOp un) {
      IrExpression operand = unwrapBoxing(specializeExpression(un.operand(), context));
      return new IrUnaryOp(un.op(), operand, un.type(), un.span());
    }

    if (expr instanceof IrConvert conv) {
      IrExpression inner = specializeExpression(conv.expression(), context);
      return new IrConvert(inner, conv.type(), conv.span());
    }

    return expr;
  }

  private IrExpression unwrapBoxing(IrExpression expr) {
    if (expr instanceof IrConvert conv && conv.type().equals(VTypes.OBJECT)) {
      if (conv.expression().type().isPrimitive()) {
        return conv.expression();
      }
    }
    return expr;
  }

  private boolean isComparisonOp(BinaryOpKind op) {
    return op == BinaryOpKind.EQUALS
        || op == BinaryOpKind.NOT_EQUALS
        || op == BinaryOpKind.LESS_THAN
        || op == BinaryOpKind.LESS_THAN_OR_EQUAL
        || op == BinaryOpKind.GREATER_THAN
        || op == BinaryOpKind.GREATER_THAN_OR_EQUAL;
  }

  private VType resolveNumericType(VType t1, VType t2) {
    if (t1.equals(VTypes.DOUBLE)
        || t2.equals(VTypes.DOUBLE)
        || t1.equals(VTypes.FLOAT)
        || t2.equals(VTypes.FLOAT)) {
      return VTypes.DOUBLE;
    }
    if (t1.equals(VTypes.LONG) || t2.equals(VTypes.LONG)) {
      return VTypes.LONG;
    }
    return VTypes.INT;
  }
}
