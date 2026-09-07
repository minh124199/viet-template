package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrAlternateValue;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrDynamicDispatch;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIndexGet;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrInvokeAllowedMethod;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIsNull;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetIndex;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Optimization pass that evaluates pure compile-time constant expressions (arithmetic, comparisons,
 * string concatenations, logical operations, unary negations, and truthiness).
 */
public final class ConstantFoldingPass implements IrOptimizationPass {

  public static final String NAME = "ConstantFolding";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().constantFolding()) {
      return template;
    }

    IrBlock newRoot = foldBlock(template.root(), context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = foldBlock(fn.body(), context);
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

  private IrBlock foldBlock(IrBlock block, OptimizationContext context) {
    List<IrStatement> foldedStmts = new ArrayList<>();
    for (IrStatement stmt : block.statements()) {
      foldedStmts.add(foldStatement(stmt, context));
    }
    return new IrBlock(foldedStmts, block.span());
  }

  private IrStatement foldStatement(IrStatement stmt, OptimizationContext context) {
    if (stmt instanceof IrWriteValue wv) {
      IrExpression foldedVal = foldExpression(wv.value(), context);
      return new IrWriteValue(foldedVal, wv.escapeMode(), wv.nullMode(), wv.span());
    }

    if (stmt instanceof IrStoreLocal sl) {
      IrExpression foldedVal = foldExpression(sl.value(), context);
      return new IrStoreLocal(sl.local(), foldedVal, sl.span());
    }

    if (stmt instanceof IrSetProperty sp) {
      IrExpression foldedTarget = foldExpression(sp.target(), context);
      IrExpression foldedVal = foldExpression(sp.value(), context);
      return new IrSetProperty(foldedTarget, sp.propertyName(), foldedVal, sp.span());
    }

    if (stmt instanceof IrSetIndex si) {
      IrExpression foldedTarget = foldExpression(si.target(), context);
      IrExpression foldedIndex = foldExpression(si.index(), context);
      IrExpression foldedVal = foldExpression(si.value(), context);
      return new IrSetIndex(foldedTarget, foldedIndex, foldedVal, si.span());
    }

    if (stmt instanceof IrIf ifStmt) {
      IrExpression foldedCond = foldExpression(ifStmt.condition(), context);
      IrBlock foldedThen = foldBlock(ifStmt.thenBlock(), context);
      Optional<IrBlock> foldedElse = ifStmt.elseBlock().map(eb -> foldBlock(eb, context));
      return new IrIf(foldedCond, foldedThen, foldedElse, ifStmt.span());
    }

    if (stmt instanceof IrLoop loop) {
      IrExpression foldedIterable = foldExpression(loop.iterable(), context);
      IrBlock foldedBody = foldBlock(loop.body(), context);
      Optional<IrBlock> foldedElse = loop.elseBody().map(eb -> foldBlock(eb, context));
      return new IrLoop(
          loop.plan(),
          foldedIterable,
          loop.elementLocal(),
          loop.loopStateLocal(),
          foldedBody,
          foldedElse,
          loop.span());
    }

    if (stmt instanceof IrCallMacro cm) {
      List<IrExpression> foldedArgs = new ArrayList<>();
      for (IrExpression arg : cm.arguments()) {
        foldedArgs.add(foldExpression(arg, context));
      }
      Optional<IrBlock> foldedBody = cm.bodyContent().map(bc -> foldBlock(bc, context));
      return new IrCallMacro(cm.macroName(), foldedArgs, foldedBody, cm.span());
    }

    if (stmt instanceof IrCallTemplate ct) {
      IrExpression foldedName = foldExpression(ct.templateNameExpr(), context);
      return new IrCallTemplate(foldedName, ct.staticTemplateName(), ct.isParse(), ct.span());
    }

    if (stmt instanceof IrEvaluate eval) {
      IrExpression foldedExpr = foldExpression(eval.expression(), context);
      return new IrEvaluate(foldedExpr, eval.span());
    }

    return stmt;
  }

  public IrExpression foldExpression(IrExpression expr, OptimizationContext context) {
    if (expr instanceof IrBinaryOp bin) {
      IrExpression left = foldExpression(bin.left(), context);
      IrExpression right = foldExpression(bin.right(), context);

      if (left instanceof IrConst c1 && right instanceof IrConst c2) {
        Optional<IrConst> folded = foldBinary(bin.op(), c1, c2, bin.span(), bin.type());
        if (folded.isPresent()) {
          context.statistics().recordConstantFolded();
          return folded.get();
        }
      }
      return new IrBinaryOp(bin.op(), left, right, bin.type(), bin.span());
    }

    if (expr instanceof IrUnaryOp un) {
      IrExpression operand = foldExpression(un.operand(), context);
      if (operand instanceof IrConst c) {
        Optional<IrConst> folded = foldUnary(un.op(), c, un.span(), un.type());
        if (folded.isPresent()) {
          context.statistics().recordConstantFolded();
          return folded.get();
        }
      }
      return new IrUnaryOp(un.op(), operand, un.type(), un.span());
    }

    if (expr instanceof IrTruthiness truth) {
      IrExpression operand = foldExpression(truth.expression(), context);
      if (operand instanceof IrConst c) {
        boolean b = evaluateTruthiness(c.value());
        context.statistics().recordConstantFolded();
        return new IrConst(b, VTypes.BOOLEAN, truth.span());
      }
      return new IrTruthiness(operand, truth.emptyCheck(), truth.span());
    }

    if (expr instanceof IrIsNull isNull) {
      IrExpression operand = foldExpression(isNull.expression(), context);
      if (operand instanceof IrConst c) {
        boolean valIsNull = c.value() == null;
        context.statistics().recordConstantFolded();
        return new IrConst(valIsNull, VTypes.BOOLEAN, isNull.span());
      }
      return new IrIsNull(operand, isNull.span());
    }

    if (expr instanceof IrConvert conv) {
      IrExpression inner = foldExpression(conv.expression(), context);
      if (inner instanceof IrConst c) {
        if (conv.type().equals(VTypes.STRING)) {
          context.statistics().recordConstantFolded();
          return new IrConst(String.valueOf(c.value()), VTypes.STRING, conv.span());
        }
      }
      return new IrConvert(inner, conv.type(), conv.span());
    }

    if (expr instanceof IrAlternateValue alt) {
      IrExpression primary = foldExpression(alt.primary(), context);
      IrExpression fallback = foldExpression(alt.fallback(), context);
      if (primary instanceof IrConst c) {
        boolean b = evaluateTruthiness(c.value());
        if (b) {
          context.statistics().recordConstantFolded();
          return primary;
        } else {
          context.statistics().recordConstantFolded();
          return fallback;
        }
      }
      return new IrAlternateValue(primary, fallback, alt.type(), alt.span());
    }

    if (expr instanceof IrGetProperty gp) {
      IrExpression rec = foldExpression(gp.receiver(), context);
      return new IrGetProperty(
          rec, gp.propertyName(), gp.type(), gp.accessPlan(), gp.nullMode(), gp.span());
    }

    if (expr instanceof IrIndexGet ig) {
      IrExpression rec = foldExpression(ig.receiver(), context);
      IrExpression idx = foldExpression(ig.index(), context);
      return new IrIndexGet(rec, idx, ig.type(), ig.span());
    }

    if (expr instanceof IrInvokeAllowedMethod im) {
      IrExpression rec = foldExpression(im.receiver(), context);
      List<IrExpression> args = new ArrayList<>();
      for (IrExpression a : im.arguments()) {
        args.add(foldExpression(a, context));
      }
      return new IrInvokeAllowedMethod(
          rec, im.methodName(), args, im.targetMethod(), im.type(), im.span());
    }

    if (expr instanceof IrDynamicDispatch dd) {
      Optional<IrExpression> rec = dd.receiver().map(r -> foldExpression(r, context));
      List<IrExpression> args = new ArrayList<>();
      for (IrExpression a : dd.arguments()) {
        args.add(foldExpression(a, context));
      }
      return new IrDynamicDispatch(dd.callSite(), rec, dd.targetName(), args, dd.type(), dd.span());
    }

    return expr;
  }

  private Optional<IrConst> foldBinary(
      BinaryOpKind op, IrConst c1, IrConst c2, SourceSpan span, VType fallbackType) {
    if (fallbackType instanceof VType.ArrayType) {
      return Optional.empty();
    }
    Object v1 = c1.value();
    Object v2 = c2.value();

    // 1. String Concatenation: ADD where at least one operand is String
    if (op == BinaryOpKind.ADD && (v1 instanceof String || v2 instanceof String)) {
      return Optional.of(new IrConst(String.valueOf(v1) + String.valueOf(v2), VTypes.STRING, span));
    }

    // 2. Boolean Logical Operations
    if (v1 instanceof Boolean b1 && v2 instanceof Boolean b2) {
      if (op == BinaryOpKind.AND) {
        return Optional.of(new IrConst(b1 && b2, VTypes.BOOLEAN, span));
      }
      if (op == BinaryOpKind.OR) {
        return Optional.of(new IrConst(b1 || b2, VTypes.BOOLEAN, span));
      }
    }

    // 3. Equality / Inequality
    if (op == BinaryOpKind.EQUALS) {
      if (v1 instanceof Number n1 && v2 instanceof Number n2) {
        return Optional.of(new IrConst(compareNumbers(n1, n2) == 0, VTypes.BOOLEAN, span));
      }
      return Optional.of(new IrConst(Objects.equals(v1, v2), VTypes.BOOLEAN, span));
    }
    if (op == BinaryOpKind.NOT_EQUALS) {
      if (v1 instanceof Number n1 && v2 instanceof Number n2) {
        return Optional.of(new IrConst(compareNumbers(n1, n2) != 0, VTypes.BOOLEAN, span));
      }
      return Optional.of(new IrConst(!Objects.equals(v1, v2), VTypes.BOOLEAN, span));
    }

    // 4. Numeric Comparisons & Arithmetic
    if (v1 instanceof Number n1 && v2 instanceof Number n2) {
      int cmp = compareNumbers(n1, n2);
      if (op == BinaryOpKind.LESS_THAN) {
        return Optional.of(new IrConst(cmp < 0, VTypes.BOOLEAN, span));
      }
      if (op == BinaryOpKind.LESS_THAN_OR_EQUAL) {
        return Optional.of(new IrConst(cmp <= 0, VTypes.BOOLEAN, span));
      }
      if (op == BinaryOpKind.GREATER_THAN) {
        return Optional.of(new IrConst(cmp > 0, VTypes.BOOLEAN, span));
      }
      if (op == BinaryOpKind.GREATER_THAN_OR_EQUAL) {
        return Optional.of(new IrConst(cmp >= 0, VTypes.BOOLEAN, span));
      }

      // Arithmetic operations
      return foldNumericArithmetic(op, n1, n2, span);
    }

    return Optional.empty();
  }

  private Optional<IrConst> foldNumericArithmetic(
      BinaryOpKind op, Number n1, Number n2, SourceSpan span) {
    boolean isFloating =
        (n1 instanceof Double
            || n1 instanceof Float
            || n2 instanceof Double
            || n2 instanceof Float);
    boolean isLong = (n1 instanceof Long || n2 instanceof Long);

    if (isFloating) {
      double d1 = n1.doubleValue();
      double d2 = n2.doubleValue();
      if ((op == BinaryOpKind.DIVIDE || op == BinaryOpKind.REMAINDER) && d2 == 0.0) {
        return Optional.empty(); // Avoid folding division by zero
      }
      double res =
          switch (op) {
            case ADD -> d1 + d2;
            case SUBTRACT -> d1 - d2;
            case MULTIPLY -> d1 * d2;
            case DIVIDE -> d1 / d2;
            case REMAINDER -> d1 % d2;
            default -> Double.NaN;
          };
      return Optional.of(new IrConst(res, VTypes.DOUBLE, span));
    }

    if (isLong) {
      long l1 = n1.longValue();
      long l2 = n2.longValue();
      if ((op == BinaryOpKind.DIVIDE || op == BinaryOpKind.REMAINDER) && l2 == 0L) {
        return Optional.empty();
      }
      long res =
          switch (op) {
            case ADD -> l1 + l2;
            case SUBTRACT -> l1 - l2;
            case MULTIPLY -> l1 * l2;
            case DIVIDE -> l1 / l2;
            case REMAINDER -> l1 % l2;
            default -> 0L;
          };
      return Optional.of(new IrConst(res, VTypes.LONG, span));
    }

    // Default integer arithmetic
    int i1 = n1.intValue();
    int i2 = n2.intValue();
    if ((op == BinaryOpKind.DIVIDE || op == BinaryOpKind.REMAINDER) && i2 == 0) {
      return Optional.empty();
    }
    int res =
        switch (op) {
          case ADD -> i1 + i2;
          case SUBTRACT -> i1 - i2;
          case MULTIPLY -> i1 * i2;
          case DIVIDE -> i1 / i2;
          case REMAINDER -> i1 % i2;
          default -> 0;
        };
    return Optional.of(new IrConst(res, VTypes.INT, span));
  }

  private Optional<IrConst> foldUnary(
      UnaryOpKind op, IrConst c, SourceSpan span, VType fallbackType) {
    Object val = c.value();
    if (op == UnaryOpKind.NOT && val instanceof Boolean b) {
      return Optional.of(new IrConst(!b, VTypes.BOOLEAN, span));
    }
    if (op == UnaryOpKind.NEGATE && val instanceof Number n) {
      if (n instanceof Double d) {
        return Optional.of(new IrConst(-d, VTypes.DOUBLE, span));
      }
      if (n instanceof Long l) {
        return Optional.of(new IrConst(-l, VTypes.LONG, span));
      }
      if (n instanceof Integer i) {
        return Optional.of(new IrConst(-i, VTypes.INT, span));
      }
    }
    return Optional.empty();
  }

  private int compareNumbers(Number n1, Number n2) {
    if (n1 instanceof Double
        || n1 instanceof Float
        || n2 instanceof Double
        || n2 instanceof Float) {
      return Double.compare(n1.doubleValue(), n2.doubleValue());
    }
    if (n1 instanceof BigDecimal bd1) {
      BigDecimal bd2 = (n2 instanceof BigDecimal b2) ? b2 : BigDecimal.valueOf(n2.doubleValue());
      return bd1.compareTo(bd2);
    }
    if (n2 instanceof BigDecimal bd2) {
      return BigDecimal.valueOf(n1.doubleValue()).compareTo(bd2);
    }
    if (n1 instanceof BigInteger bi1) {
      BigInteger bi2 = (n2 instanceof BigInteger b2) ? b2 : BigInteger.valueOf(n2.longValue());
      return bi1.compareTo(bi2);
    }
    if (n2 instanceof BigInteger bi2) {
      return BigInteger.valueOf(n1.longValue()).compareTo(bi2);
    }
    return Long.compare(n1.longValue(), n2.longValue());
  }

  private boolean evaluateTruthiness(Object val) {
    if (val == null) {
      return false;
    }
    if (val instanceof Boolean b) {
      return b;
    }
    if (val instanceof Number n) {
      return n.intValue() != 0;
    }
    if (val instanceof String s) {
      return !s.isEmpty();
    }
    return true;
  }
}
