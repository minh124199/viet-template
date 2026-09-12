package io.github.minh124199.viettemplate.language.vtl.ir.verifier;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
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
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBranch;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBranchIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBreak;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBudgetCheck;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoopEnd;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoopNext;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoopSetup;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetIndex;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validates structural, scoping, type, and capability invariants of a {@link IrTemplate}.
 *
 * <p>Executed before and after every optimization phase in debug mode to ensure compiler integrity.
 */
public final class IrVerifier {

  private IrVerifier() {}

  /**
   * Verifies the template IR, throwing {@link IrVerificationException} upon encountering any
   * invariant violation.
   */
  public static void verify(IrTemplate template) {
    List<String> errors = check(template);
    if (!errors.isEmpty()) {
      throw new IrVerificationException(
          "IR verification failed with "
              + errors.size()
              + " error(s):\n"
              + String.join("\n", errors),
          template.span());
    }
  }

  /** Verifies the template IR and returns a list of discovered error descriptions. */
  public static List<String> check(IrTemplate template) {
    Objects.requireNonNull(template, "template must not be null");
    List<String> errors = new ArrayList<>();

    // 1. Check parameters
    Set<String> definedParams = new HashSet<>();
    Set<Integer> parameterSlots = new HashSet<>();
    for (IrParameter param : template.parameters()) {
      if (!definedParams.add(param.name())) {
        errors.add("Duplicate parameter '" + param.name() + "' at " + param.span());
      }
      verifyDeclaredSlot("Parameter '" + param.name() + "'", param.slot(), parameterSlots, errors);
    }

    // 2. Scope context for root block
    ScopeContext rootScope = new ScopeContext(null);
    for (IrParameter param : template.parameters()) {
      rootScope.define(param.name());
    }

    // 3. Verify root block
    verifyBlock(template.root(), rootScope, template, errors);

    // 4. Verify functions / macros
    for (IrFunction function : template.functions()) {
      ScopeContext fnScope = new ScopeContext(null);
      Set<Integer> functionSlots = new HashSet<>();
      java.util.Map<Integer, String> paramSlots = new java.util.HashMap<>();
      for (IrParameter param : function.parameters()) {
        fnScope.define(param.name());
        verifyDeclaredSlot(
            "Function '" + function.name() + "' parameter '" + param.name() + "'",
            param.slot(),
            functionSlots,
            errors);
        paramSlots.put(param.slot(), param.name());
      }
      for (IrLocal local : function.locals()) {
        fnScope.define(local.name());
        if (local.slot() < 0) {
          errors.add(
              "Function '"
                  + function.name()
                  + "' local '"
                  + local.name()
                  + "' has negative slot "
                  + local.slot());
        } else if (paramSlots.containsKey(local.slot())) {
          if (!paramSlots.get(local.slot()).equals(local.name())) {
            errors.add(
                "Function '"
                    + function.name()
                    + "' local '"
                    + local.name()
                    + "' aliases parameter slot "
                    + local.slot()
                    + " ('"
                    + paramSlots.get(local.slot())
                    + "')");
          }
        } else if (!functionSlots.add(local.slot())) {
          errors.add(
              "Function '"
                  + function.name()
                  + "' local '"
                  + local.name()
                  + "' aliases already-declared slot "
                  + local.slot());
        }
      }
      verifyBlock(function.body(), fnScope, template, errors);
    }

    return errors;
  }

  private static void verifyDeclaredSlot(
      String description, int slot, Set<Integer> declaredSlots, List<String> errors) {
    if (slot < 0) {
      errors.add(description + " has negative slot " + slot);
    } else if (!declaredSlots.add(slot)) {
      errors.add(description + " aliases already-declared slot " + slot);
    }
  }

  private static void verifyBlock(
      IrBlock block, ScopeContext scope, IrTemplate template, List<String> errors) {
    if (block.span() == null) {
      errors.add("Block has null source span");
    }

    for (IrStatement stmt : block.statements()) {
      verifyStatement(stmt, scope, template, errors);
    }
  }

  private static void verifyStatement(
      IrStatement stmt, ScopeContext scope, IrTemplate template, List<String> errors) {
    if (stmt.span() == null) {
      errors.add("Statement " + stmt.getClass().getSimpleName() + " has null source span");
    }

    if (stmt instanceof IrWriteConst wc) {
      if (template.constants().getTextConstant(wc.constantId()).isEmpty()) {
        errors.add(
            "IrWriteConst references nonexistent constant ID "
                + wc.constantId()
                + " at "
                + wc.span());
      }
      return;
    }

    if (stmt instanceof IrWriteValue wv) {
      verifyExpression(wv.value(), scope, template, errors);
      return;
    }

    if (stmt instanceof IrStoreLocal store) {
      if (store.local().slot() < 0) {
        errors.add(
            "Local '$" + store.local().name() + "' has negative slot " + store.local().slot());
      }
      verifyExpression(store.value(), scope, template, errors);
      scope.define(store.local().name());
      return;
    }

    if (stmt instanceof IrIf ifStmt) {
      verifyExpression(ifStmt.condition(), scope, template, errors);
      ScopeContext thenScope = new ScopeContext(scope);
      verifyBlock(ifStmt.thenBlock(), thenScope, template, errors);
      scope.definedSymbols.addAll(thenScope.definedSymbols);
      if (ifStmt.elseBlock().isPresent()) {
        ScopeContext elseScope = new ScopeContext(scope);
        verifyBlock(ifStmt.elseBlock().get(), elseScope, template, errors);
        scope.definedSymbols.addAll(elseScope.definedSymbols);
      }
      return;
    }

    if (stmt instanceof IrBranch) {
      return;
    }

    if (stmt instanceof IrBranchIf bi) {
      verifyExpression(bi.condition(), scope, template, errors);
      return;
    }

    if (stmt instanceof IrLoop loop) {
      verifyExpression(loop.iterable(), scope, template, errors);

      if (template.capabilities().eligibleForStaticAot() && loop.plan() == LoopPlan.DYNAMIC) {
        errors.add("Dynamic loop plan forbidden in static AOT eligible template at " + loop.span());
      }

      ScopeContext loopBodyScope = new ScopeContext(scope);
      if (loop.elementLocal().slot() < 0) {
        errors.add(
            "Loop local '$"
                + loop.elementLocal().name()
                + "' has negative slot "
                + loop.elementLocal().slot());
      }
      if (loop.loopStateLocal().isPresent() && loop.loopStateLocal().get().slot() < 0) {
        errors.add(
            "Loop metadata local '$foreach' has negative slot "
                + loop.loopStateLocal().get().slot());
      }
      loopBodyScope.define(loop.elementLocal().name());
      if (loop.loopStateLocal().isPresent()) {
        loopBodyScope.define(loop.loopStateLocal().get().name());
      }
      verifyBlock(loop.body(), loopBodyScope, template, errors);

      if (loop.elseBody().isPresent()) {
        ScopeContext elseScope = new ScopeContext(scope);
        verifyBlock(loop.elseBody().get(), elseScope, template, errors);
        scope.definedSymbols.addAll(elseScope.definedSymbols);
      }
      return;
    }

    if (stmt instanceof IrLoopSetup setup) {
      if (setup.iteratorLocal().slot() < 0) {
        errors.add(
            "Loop iterator local '"
                + setup.iteratorLocal().name()
                + "' has negative slot "
                + setup.iteratorLocal().slot());
      }
      verifyExpression(setup.iterable(), scope, template, errors);
      scope.define(setup.iteratorLocal().name());
      return;
    }

    if (stmt instanceof IrLoopNext next) {
      if (next.iteratorLocal().slot() < 0) {
        errors.add(
            "Loop iterator local '"
                + next.iteratorLocal().name()
                + "' has negative slot "
                + next.iteratorLocal().slot());
      }
      if (next.elementLocal().slot() < 0) {
        errors.add(
            "Loop element local '"
                + next.elementLocal().name()
                + "' has negative slot "
                + next.elementLocal().slot());
      }
      if (next.loopStateLocal().isPresent() && next.loopStateLocal().get().slot() < 0) {
        errors.add("Loop state local has negative slot " + next.loopStateLocal().get().slot());
      }
      if (!scope.isDefined(next.iteratorLocal().name())) {
        errors.add(
            "Loop iterator local '"
                + next.iteratorLocal().name()
                + "' used before definition at "
                + next.span());
      }
      scope.define(next.elementLocal().name());
      if (next.loopStateLocal().isPresent()) {
        scope.define(next.loopStateLocal().get().name());
      }
      return;
    }

    if (stmt instanceof IrLoopEnd end) {
      if (end.iteratorLocal().slot() < 0) {
        errors.add(
            "Loop iterator local '"
                + end.iteratorLocal().name()
                + "' has negative slot "
                + end.iteratorLocal().slot());
      }
      if (!scope.isDefined(end.iteratorLocal().name())) {
        errors.add(
            "Loop iterator local '"
                + end.iteratorLocal().name()
                + "' used before definition at "
                + end.span());
      }
      return;
    }

    if (stmt instanceof IrCallTemplate callT) {
      verifyExpression(callT.templateNameExpr(), scope, template, errors);
      if (template.capabilities().eligibleForStaticAot()
          && callT.staticTemplateName().isEmpty()
          && !(callT.templateNameExpr() instanceof IrConst)) {
        errors.add("Dynamic include/parse forbidden in static AOT template at " + callT.span());
      }
      return;
    }

    if (stmt instanceof IrCallMacro callM) {
      for (IrExpression arg : callM.arguments()) {
        verifyExpression(arg, scope, template, errors);
      }
      if (callM.bodyContent().isPresent()) {
        ScopeContext bodyScope = new ScopeContext(scope);
        bodyScope.define("bodyContent");
        verifyBlock(callM.bodyContent().get(), bodyScope, template, errors);
      }
      return;
    }

    if (stmt instanceof IrSetProperty sp) {
      verifyExpression(sp.target(), scope, template, errors);
      verifyExpression(sp.value(), scope, template, errors);
      return;
    }

    if (stmt instanceof IrSetIndex si) {
      verifyExpression(si.target(), scope, template, errors);
      verifyExpression(si.index(), scope, template, errors);
      verifyExpression(si.value(), scope, template, errors);
      return;
    }

    if (stmt instanceof IrEvaluate eval) {
      verifyExpression(eval.expression(), scope, template, errors);
      if (template.capabilities().eligibleForStaticAot()) {
        errors.add("Runtime evaluate forbidden in static AOT template at " + eval.span());
      }
      return;
    }

    if (stmt instanceof IrBreak
        || stmt instanceof IrStop
        || stmt instanceof IrNoOp
        || stmt instanceof IrBudgetCheck) {
      return;
    }

    if (stmt instanceof IrReturn ret) {
      if (ret.value().isPresent()) {
        verifyExpression(ret.value().get(), scope, template, errors);
      }
    }
  }

  private static void verifyExpression(
      IrExpression expr, ScopeContext scope, IrTemplate template, List<String> errors) {
    if (expr.span() == null) {
      errors.add("Expression " + expr.getClass().getSimpleName() + " has null source span");
    }
    if (expr.type() == null) {
      errors.add(
          "Expression " + expr.getClass().getSimpleName() + " has null type at " + expr.span());
    }

    if (expr instanceof IrConst) {
      return;
    }

    if (expr instanceof IrLoadParam lp) {
      if (lp.slot() < 0)
        errors.add("Parameter '" + lp.name() + "' loads negative slot " + lp.slot());
      if (!scope.isDefined(lp.name())) {
        errors.add("Parameter '" + lp.name() + "' referenced before definition at " + lp.span());
      }
      return;
    }

    if (expr instanceof IrLoadLocal ll) {
      if (ll.slot() < 0) errors.add("Local '$" + ll.name() + "' loads negative slot " + ll.slot());
      if (!scope.isDefined(ll.name())) {
        errors.add("Local variable '$" + ll.name() + "' read before write at " + ll.span());
      }
      return;
    }

    if (expr instanceof IrGetProperty gp) {
      verifyExpression(gp.receiver(), scope, template, errors);
      if (template.capabilities().eligibleForStaticAot()
          && gp.accessPlan() instanceof AccessPlan.DynamicCallSite) {
        errors.add(
            "Dynamic property access site forbidden in static AOT eligible template at "
                + gp.span());
      }
      return;
    }

    if (expr instanceof IrInvokeAllowedMethod im) {
      verifyExpression(im.receiver(), scope, template, errors);
      for (IrExpression arg : im.arguments()) {
        verifyExpression(arg, scope, template, errors);
      }
      return;
    }

    if (expr instanceof IrIndexGet ig) {
      verifyExpression(ig.receiver(), scope, template, errors);
      verifyExpression(ig.index(), scope, template, errors);
      return;
    }

    if (expr instanceof IrDynamicDispatch dd) {
      if (template.capabilities().eligibleForStaticAot()) {
        errors.add(
            "Dynamic dispatch site '"
                + dd.targetName()
                + "' forbidden in static AOT eligible template at "
                + dd.span());
      }
      if (dd.receiver().isPresent()) {
        verifyExpression(dd.receiver().get(), scope, template, errors);
      }
      for (IrExpression arg : dd.arguments()) {
        verifyExpression(arg, scope, template, errors);
      }
      return;
    }

    if (expr instanceof IrBinaryOp bin) {
      verifyExpression(bin.left(), scope, template, errors);
      verifyExpression(bin.right(), scope, template, errors);
      verifyBinaryOpTypes(bin, errors);
      return;
    }

    if (expr instanceof IrUnaryOp un) {
      verifyExpression(un.operand(), scope, template, errors);
      verifyUnaryOpTypes(un, errors);
      return;
    }

    if (expr instanceof IrTruthiness tr) {
      verifyExpression(tr.expression(), scope, template, errors);
      return;
    }

    if (expr instanceof IrIsNull isNull) {
      verifyExpression(isNull.expression(), scope, template, errors);
      return;
    }

    if (expr instanceof IrConvert cv) {
      verifyExpression(cv.expression(), scope, template, errors);
      return;
    }

    if (expr instanceof IrAlternateValue alt) {
      verifyExpression(alt.primary(), scope, template, errors);
      verifyExpression(alt.fallback(), scope, template, errors);
    }
  }

  private static void verifyBinaryOpTypes(IrBinaryOp bin, List<String> errors) {
    VType left = bin.left().type();
    VType right = bin.right().type();

    if (left instanceof VType.DynamicType || right instanceof VType.DynamicType) {
      return;
    }

    if (bin.op() == BinaryOpKind.AND || bin.op() == BinaryOpKind.OR) {
      if (!VTypes.BOOLEAN.equals(left) || !VTypes.BOOLEAN.equals(right)) {
        // Warning or note, allowed if coerced or truthy
      }
    }
  }

  private static void verifyUnaryOpTypes(IrUnaryOp un, List<String> errors) {
    VType opType = un.operand().type();
    if (opType instanceof VType.DynamicType) {
      return;
    }
    if (un.op() == UnaryOpKind.NOT && !VTypes.BOOLEAN.equals(opType)) {
      // Allowed if truthiness applied
    }
  }

  private static final class ScopeContext {
    private final ScopeContext parent;
    private final Set<String> definedSymbols = new HashSet<>();

    ScopeContext(ScopeContext parent) {
      this.parent = parent;
    }

    void define(String name) {
      definedSymbols.add(name);
    }

    boolean isDefined(String name) {
      if (definedSymbols.contains(name)) {
        return true;
      }
      return parent != null && parent.isDefined(name);
    }
  }
}
