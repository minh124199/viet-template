package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrAlternateValue;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrDynamicDispatch;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIndexGet;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrInvokeAllowedMethod;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIsNull;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.DynamicKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullAccessMode;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Optimization pass converting generic dynamic dispatches ({@link IrDynamicDispatch}) into direct,
 * statically bound property access plans ({@link AccessPlan.DirectRecord}, {@link
 * AccessPlan.DirectGetter}, {@link AccessPlan.DirectField}, {@link AccessPlan.MapLookup}) or
 * allowed direct method calls when receiver types are statically known.
 */
public final class DirectAccessorBindingPass implements IrOptimizationPass {

  public static final String NAME = "DirectAccessorBinding";

  private static final Set<String> DENIED_METHODS =
      Set.of("getClass", "wait", "notify", "notifyAll", "clone", "finalize");

  private static final Set<String> DENIED_CLASSES =
      Set.of(
          "java.lang.Class",
          "java.lang.ClassLoader",
          "java.lang.Runtime",
          "java.lang.Process",
          "java.lang.ProcessBuilder",
          "java.lang.System",
          "java.lang.Thread",
          "java.lang.ThreadGroup");

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().directAccessorBinding()) {
      return template;
    }

    IrBlock newRoot = bindBlock(template.root(), context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = bindBlock(fn.body(), context);
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

  private IrBlock bindBlock(IrBlock block, OptimizationContext context) {
    List<IrStatement> boundStmts = new ArrayList<>();
    for (IrStatement stmt : block.statements()) {
      boundStmts.add(bindStatement(stmt, context));
    }
    return new IrBlock(boundStmts, block.span());
  }

  private IrStatement bindStatement(IrStatement stmt, OptimizationContext context) {
    if (stmt instanceof IrWriteValue wv) {
      IrExpression boundVal = bindExpression(wv.value(), context);
      return new IrWriteValue(boundVal, wv.escapeMode(), wv.nullMode(), wv.span());
    }

    if (stmt instanceof IrStoreLocal sl) {
      IrExpression boundVal = bindExpression(sl.value(), context);
      return new IrStoreLocal(sl.local(), boundVal, sl.span());
    }

    if (stmt instanceof IrSetProperty sp) {
      IrExpression boundRec = bindExpression(sp.target(), context);
      IrExpression boundVal = bindExpression(sp.value(), context);
      return new IrSetProperty(boundRec, sp.propertyName(), boundVal, sp.span());
    }

    if (stmt instanceof IrSetIndex si) {
      IrExpression boundRec = bindExpression(si.target(), context);
      IrExpression boundIdx = bindExpression(si.index(), context);
      IrExpression boundVal = bindExpression(si.value(), context);
      return new IrSetIndex(boundRec, boundIdx, boundVal, si.span());
    }

    if (stmt instanceof IrIf ifStmt) {
      IrExpression boundCond = bindExpression(ifStmt.condition(), context);
      IrBlock boundThen = bindBlock(ifStmt.thenBlock(), context);
      Optional<IrBlock> boundElse = ifStmt.elseBlock().map(eb -> bindBlock(eb, context));
      return new IrIf(boundCond, boundThen, boundElse, ifStmt.span());
    }

    if (stmt instanceof IrLoop loop) {
      IrExpression boundIter = bindExpression(loop.iterable(), context);
      IrBlock boundBody = bindBlock(loop.body(), context);
      Optional<IrBlock> boundElse = loop.elseBody().map(eb -> bindBlock(eb, context));
      return new IrLoop(
          loop.plan(),
          boundIter,
          loop.elementLocal(),
          loop.loopStateLocal(),
          boundBody,
          boundElse,
          loop.span());
    }

    if (stmt instanceof IrCallMacro cm) {
      List<IrExpression> boundArgs = new ArrayList<>();
      for (IrExpression a : cm.arguments()) {
        boundArgs.add(bindExpression(a, context));
      }
      Optional<IrBlock> boundBody = cm.bodyContent().map(bc -> bindBlock(bc, context));
      return new IrCallMacro(cm.macroName(), boundArgs, boundBody, cm.span());
    }

    if (stmt instanceof IrCallTemplate ct) {
      IrExpression boundName = bindExpression(ct.templateNameExpr(), context);
      return new IrCallTemplate(boundName, ct.staticTemplateName(), ct.isParse(), ct.span());
    }

    if (stmt instanceof IrEvaluate eval) {
      IrExpression boundExpr = bindExpression(eval.expression(), context);
      return new IrEvaluate(boundExpr, eval.span());
    }

    return stmt;
  }

  public IrExpression bindExpression(IrExpression expr, OptimizationContext context) {
    if (expr instanceof IrDynamicDispatch dyn) {
      Optional<IrExpression> recOpt = dyn.receiver().map(r -> bindExpression(r, context));
      List<IrExpression> boundArgs = new ArrayList<>();
      for (IrExpression a : dyn.arguments()) {
        boundArgs.add(bindExpression(a, context));
      }

      if (recOpt.isPresent()) {
        IrExpression rec = recOpt.get();
        VType recType = rec.type();

        if (recType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
          Class<?> clazz = ct.javaClass().get();
          if (!isDeniedClass(clazz)) {
            // Case 1: Property Get or 0-arg access
            if (dyn.callSite().kind() == DynamicKind.PROPERTY_GET && boundArgs.isEmpty()) {
              IrExpression boundProp = tryBindProperty(rec, clazz, dyn.targetName(), dyn, context);
              if (boundProp != null) {
                return boundProp;
              }
            }

            // Case 2: Method Call
            if (dyn.callSite().kind() == DynamicKind.METHOD_CALL || !boundArgs.isEmpty()) {
              IrExpression boundMethod =
                  tryBindMethod(rec, clazz, dyn.targetName(), boundArgs, dyn, context);
              if (boundMethod != null) {
                return boundMethod;
              }
            }
          }
        }
      }

      return new IrDynamicDispatch(
          dyn.callSite(), recOpt, dyn.targetName(), boundArgs, dyn.type(), dyn.span());
    }

    if (expr instanceof IrBinaryOp bin) {
      IrExpression left = bindExpression(bin.left(), context);
      IrExpression right = bindExpression(bin.right(), context);
      return new IrBinaryOp(bin.op(), left, right, bin.type(), bin.span());
    }

    if (expr instanceof IrUnaryOp un) {
      IrExpression operand = bindExpression(un.operand(), context);
      return new IrUnaryOp(un.op(), operand, un.type(), un.span());
    }

    if (expr instanceof IrTruthiness tr) {
      IrExpression operand = bindExpression(tr.expression(), context);
      return new IrTruthiness(operand, tr.emptyCheck(), tr.span());
    }

    if (expr instanceof IrIsNull isNull) {
      IrExpression operand = bindExpression(isNull.expression(), context);
      return new IrIsNull(operand, isNull.span());
    }

    if (expr instanceof IrConvert conv) {
      IrExpression inner = bindExpression(conv.expression(), context);
      return new IrConvert(inner, conv.type(), conv.span());
    }

    if (expr instanceof IrAlternateValue alt) {
      IrExpression primary = bindExpression(alt.primary(), context);
      IrExpression fallback = bindExpression(alt.fallback(), context);
      return new IrAlternateValue(primary, fallback, alt.type(), alt.span());
    }

    if (expr instanceof IrGetProperty gp) {
      IrExpression rec = bindExpression(gp.receiver(), context);
      return new IrGetProperty(
          rec, gp.propertyName(), gp.type(), gp.accessPlan(), gp.nullMode(), gp.span());
    }

    if (expr instanceof IrIndexGet ig) {
      IrExpression rec = bindExpression(ig.receiver(), context);
      IrExpression idx = bindExpression(ig.index(), context);
      return new IrIndexGet(rec, idx, ig.type(), ig.span());
    }

    if (expr instanceof IrInvokeAllowedMethod im) {
      IrExpression rec = bindExpression(im.receiver(), context);
      List<IrExpression> args = new ArrayList<>();
      for (IrExpression a : im.arguments()) {
        args.add(bindExpression(a, context));
      }
      return new IrInvokeAllowedMethod(
          rec, im.methodName(), args, im.targetMethod(), im.type(), im.span());
    }

    return expr;
  }

  private IrExpression tryBindProperty(
      IrExpression rec,
      Class<?> clazz,
      String propertyName,
      IrDynamicDispatch dyn,
      OptimizationContext context) {
    if (DENIED_METHODS.contains(propertyName)) {
      return null;
    }

    // 1. Record Component Accessor
    if (clazz.isRecord()) {
      for (RecordComponent rc : clazz.getRecordComponents()) {
        if (rc.getName().equals(propertyName)) {
          Method accessor = rc.getAccessor();
          if (Modifier.isPublic(accessor.getModifiers())) {
            context.statistics().recordAccessorBound();
            return new IrGetProperty(
                rec,
                propertyName,
                VTypes.fromJavaClass(rc.getType()),
                new AccessPlan.DirectRecord(clazz, propertyName, rc.getType(), accessor),
                NullAccessMode.PROPAGATE_NULL,
                dyn.span());
          }
        }
      }
    }

    // 2. Map Lookup
    if (Map.class.isAssignableFrom(clazz)) {
      context.statistics().recordAccessorBound();
      return new IrGetProperty(
          rec,
          propertyName,
          VTypes.OBJECT,
          new AccessPlan.MapLookup(propertyName),
          NullAccessMode.PROPAGATE_NULL,
          dyn.span());
    }

    // 3. JavaBean Getter: getX() or isX()
    String cap =
        Character.toUpperCase(propertyName.charAt(0))
            + (propertyName.length() > 1 ? propertyName.substring(1) : "");
    String getName = "get" + cap;
    String isName = "is" + cap;

    for (Method m : clazz.getMethods()) {
      if (m.getParameterCount() == 0 && Modifier.isPublic(m.getModifiers())) {
        if (m.getName().equals(getName)
            || (m.getName().equals(isName) && m.getReturnType() == boolean.class)) {
          if (!DENIED_METHODS.contains(m.getName())) {
            context.statistics().recordAccessorBound();
            return new IrGetProperty(
                rec,
                propertyName,
                VTypes.fromJavaClass(m.getReturnType()),
                new AccessPlan.DirectGetter(clazz, m.getName(), m.getReturnType(), m),
                NullAccessMode.PROPAGATE_NULL,
                dyn.span());
          }
        }
      }
    }

    // 4. Public Field
    try {
      Field f = clazz.getField(propertyName);
      if (Modifier.isPublic(f.getModifiers()) && !Modifier.isStatic(f.getModifiers())) {
        context.statistics().recordAccessorBound();
        return new IrGetProperty(
            rec,
            propertyName,
            VTypes.fromJavaClass(f.getType()),
            new AccessPlan.DirectField(clazz, propertyName, f.getType(), f),
            NullAccessMode.PROPAGATE_NULL,
            dyn.span());
      }
    } catch (NoSuchFieldException ignored) {
    }

    return null;
  }

  private IrExpression tryBindMethod(
      IrExpression rec,
      Class<?> clazz,
      String methodName,
      List<IrExpression> args,
      IrDynamicDispatch dyn,
      OptimizationContext context) {
    if (DENIED_METHODS.contains(methodName)) {
      return null;
    }

    int arity = args.size();
    for (Method m : clazz.getMethods()) {
      if (m.getName().equals(methodName)
          && m.getParameterCount() == arity
          && Modifier.isPublic(m.getModifiers())) {
        context.statistics().recordAccessorBound();
        return new IrInvokeAllowedMethod(
            rec, methodName, args, m, VTypes.fromJavaClass(m.getReturnType()), dyn.span());
      }
    }

    return null;
  }

  private boolean isDeniedClass(Class<?> clazz) {
    String name = clazz.getName();
    if (DENIED_CLASSES.contains(name)) {
      return true;
    }
    return name.startsWith("java.lang.reflect.")
        || name.startsWith("java.lang.invoke.")
        || name.startsWith("sun.")
        || name.startsWith("jdk.internal.");
  }
}
