package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
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
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetIndex;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Optimization pass inlining small, non-recursive macro function bodies directly at their call
 * sites within configured depth and statement count budgets.
 */
public final class MacroInliningPass implements IrOptimizationPass {

  public static final String NAME = "MacroInlining";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().macroInlining()) {
      return template;
    }

    Map<String, IrFunction> functionMap = new HashMap<>();
    for (IrFunction fn : template.functions()) {
      functionMap.put(fn.name(), fn);
    }

    Set<String> recursiveFunctions = findRecursiveFunctions(template.functions());

    IrBlock newRoot = inlineBlock(template.root(), functionMap, recursiveFunctions, 0, context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = inlineBlock(fn.body(), functionMap, recursiveFunctions, 0, context);
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

  private IrBlock inlineBlock(
      IrBlock block,
      Map<String, IrFunction> functions,
      Set<String> recursiveFunctions,
      int depth,
      OptimizationContext context) {
    List<IrStatement> inlinedStmts = new ArrayList<>();

    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrCallMacro cm) {
        if (canInline(cm, functions, recursiveFunctions, depth, context)) {
          IrFunction target = functions.get(cm.macroName());
          List<IrStatement> inlined =
              inlineMacro(cm, target, functions, recursiveFunctions, depth, context);
          inlinedStmts.addAll(inlined);
          context.statistics().recordMacroInlined();
          continue;
        }
      }

      if (stmt instanceof IrIf ifStmt) {
        IrBlock thenBlock =
            inlineBlock(ifStmt.thenBlock(), functions, recursiveFunctions, depth, context);
        Optional<IrBlock> elseBlock =
            ifStmt
                .elseBlock()
                .map(eb -> inlineBlock(eb, functions, recursiveFunctions, depth, context));
        inlinedStmts.add(new IrIf(ifStmt.condition(), thenBlock, elseBlock, ifStmt.span()));
        continue;
      }

      if (stmt instanceof IrLoop loop) {
        IrBlock body = inlineBlock(loop.body(), functions, recursiveFunctions, depth, context);
        Optional<IrBlock> elseBody =
            loop.elseBody()
                .map(eb -> inlineBlock(eb, functions, recursiveFunctions, depth, context));
        inlinedStmts.add(
            new IrLoop(
                loop.plan(),
                loop.iterable(),
                loop.elementLocal(),
                loop.loopStateLocal(),
                body,
                elseBody,
                loop.span()));
        continue;
      }

      inlinedStmts.add(stmt);
    }

    return new IrBlock(inlinedStmts, block.span());
  }

  private boolean canInline(
      IrCallMacro cm,
      Map<String, IrFunction> functions,
      Set<String> recursiveFunctions,
      int depth,
      OptimizationContext context) {
    if (depth >= context.options().maxInliningDepth()) {
      return false;
    }
    if (cm.bodyContent().isPresent()) {
      return false; // Block macros with body content are not inlined
    }
    if (recursiveFunctions.contains(cm.macroName())) {
      return false;
    }
    IrFunction target = functions.get(cm.macroName());
    if (target == null) {
      return false;
    }
    if (target.body().size() > context.options().maxInliningStatements()) {
      return false;
    }
    // Check if target contains return or stop directives that would prematurely escape caller
    for (IrStatement s : target.body().statements()) {
      if (s instanceof IrReturn || s instanceof IrStop) {
        return false;
      }
    }
    return true;
  }

  private List<IrStatement> inlineMacro(
      IrCallMacro cm,
      IrFunction target,
      Map<String, IrFunction> functions,
      Set<String> recursiveFunctions,
      int depth,
      OptimizationContext context) {
    List<IrStatement> result = new ArrayList<>();
    Map<Integer, Integer> slotMap = new HashMap<>();

    // Map parameters to freshly allocated caller local slots
    List<IrParameter> params = target.parameters();
    List<IrExpression> args = cm.arguments();
    for (int i = 0; i < params.size(); i++) {
      IrParameter p = params.get(i);
      int freshSlot = context.allocateLocalSlot();
      slotMap.put(p.slot(), freshSlot);

      IrExpression argVal = (i < args.size()) ? args.get(i) : p.defaultValue().orElseThrow();
      IrLocal freshLocal = new IrLocal(p.name(), p.type(), freshSlot, p.span());
      result.add(new IrStoreLocal(freshLocal, argVal, p.span()));
    }

    // Map function locals to freshly allocated slots
    for (IrLocal l : target.locals()) {
      int freshSlot = context.allocateLocalSlot();
      slotMap.put(l.slot(), freshSlot);
    }

    // Remap macro body statements to newly assigned slots
    for (IrStatement s : target.body().statements()) {
      result.add(remapStatement(s, slotMap));
    }

    // Recursively inline nested calls if depth budget permits
    IrBlock inlinedBlock =
        inlineBlock(
            new IrBlock(result, cm.span()), functions, recursiveFunctions, depth + 1, context);
    return inlinedBlock.statements();
  }

  private IrStatement remapStatement(IrStatement stmt, Map<Integer, Integer> slotMap) {
    if (stmt instanceof IrStoreLocal sl) {
      int newSlot = slotMap.getOrDefault(sl.local().slot(), sl.local().slot());
      IrLocal remappedLocal =
          new IrLocal(sl.local().name(), sl.local().type(), newSlot, sl.local().span());
      IrExpression newVal = remapExpression(sl.value(), slotMap);
      return new IrStoreLocal(remappedLocal, newVal, sl.span());
    }

    if (stmt instanceof IrWriteValue wv) {
      IrExpression newVal = remapExpression(wv.value(), slotMap);
      return new IrWriteValue(newVal, wv.escapeMode(), wv.nullMode(), wv.span());
    }

    if (stmt instanceof IrSetProperty sp) {
      IrExpression newTarget = remapExpression(sp.target(), slotMap);
      IrExpression newVal = remapExpression(sp.value(), slotMap);
      return new IrSetProperty(newTarget, sp.propertyName(), newVal, sp.span());
    }

    if (stmt instanceof IrSetIndex si) {
      IrExpression newTarget = remapExpression(si.target(), slotMap);
      IrExpression newIdx = remapExpression(si.index(), slotMap);
      IrExpression newVal = remapExpression(si.value(), slotMap);
      return new IrSetIndex(newTarget, newIdx, newVal, si.span());
    }

    if (stmt instanceof IrIf ifStmt) {
      IrExpression newCond = remapExpression(ifStmt.condition(), slotMap);
      IrBlock newThen = remapBlock(ifStmt.thenBlock(), slotMap);
      Optional<IrBlock> newElse = ifStmt.elseBlock().map(eb -> remapBlock(eb, slotMap));
      return new IrIf(newCond, newThen, newElse, ifStmt.span());
    }

    if (stmt instanceof IrLoop loop) {
      IrExpression newIter = remapExpression(loop.iterable(), slotMap);
      int newElemSlot =
          slotMap.getOrDefault(loop.elementLocal().slot(), loop.elementLocal().slot());
      IrLocal newElem =
          new IrLocal(
              loop.elementLocal().name(),
              loop.elementLocal().type(),
              newElemSlot,
              loop.elementLocal().span());
      Optional<IrLocal> newState =
          loop.loopStateLocal()
              .map(
                  ls -> {
                    int ns = slotMap.getOrDefault(ls.slot(), ls.slot());
                    return new IrLocal(ls.name(), ls.type(), ns, ls.span());
                  });
      IrBlock newBody = remapBlock(loop.body(), slotMap);
      Optional<IrBlock> newElse = loop.elseBody().map(eb -> remapBlock(eb, slotMap));
      return new IrLoop(loop.plan(), newIter, newElem, newState, newBody, newElse, loop.span());
    }

    if (stmt instanceof IrCallMacro cm) {
      List<IrExpression> newArgs = new ArrayList<>();
      for (IrExpression a : cm.arguments()) {
        newArgs.add(remapExpression(a, slotMap));
      }
      Optional<IrBlock> newBody = cm.bodyContent().map(bc -> remapBlock(bc, slotMap));
      return new IrCallMacro(cm.macroName(), newArgs, newBody, cm.span());
    }

    if (stmt instanceof IrCallTemplate ct) {
      IrExpression newName = remapExpression(ct.templateNameExpr(), slotMap);
      return new IrCallTemplate(newName, ct.staticTemplateName(), ct.isParse(), ct.span());
    }

    if (stmt instanceof IrEvaluate eval) {
      IrExpression newExpr = remapExpression(eval.expression(), slotMap);
      return new IrEvaluate(newExpr, eval.span());
    }

    return stmt;
  }

  private IrBlock remapBlock(IrBlock block, Map<Integer, Integer> slotMap) {
    List<IrStatement> remapped = new ArrayList<>();
    for (IrStatement s : block.statements()) {
      remapped.add(remapStatement(s, slotMap));
    }
    return new IrBlock(remapped, block.span());
  }

  private IrExpression remapExpression(IrExpression expr, Map<Integer, Integer> slotMap) {
    if (expr instanceof IrLoadLocal load) {
      int newSlot = slotMap.getOrDefault(load.slot(), load.slot());
      return new IrLoadLocal(load.name(), newSlot, load.type(), load.span());
    }

    if (expr instanceof IrLoadParam param) {
      int newSlot = slotMap.getOrDefault(param.slot(), param.slot());
      return new IrLoadLocal(param.name(), newSlot, param.type(), param.span());
    }

    if (expr instanceof IrBinaryOp bin) {
      IrExpression left = remapExpression(bin.left(), slotMap);
      IrExpression right = remapExpression(bin.right(), slotMap);
      return new IrBinaryOp(bin.op(), left, right, bin.type(), bin.span());
    }

    if (expr instanceof IrUnaryOp un) {
      IrExpression operand = remapExpression(un.operand(), slotMap);
      return new IrUnaryOp(un.op(), operand, un.type(), un.span());
    }

    if (expr instanceof IrTruthiness tr) {
      IrExpression operand = remapExpression(tr.expression(), slotMap);
      return new IrTruthiness(operand, tr.emptyCheck(), tr.span());
    }

    if (expr instanceof IrIsNull isNull) {
      IrExpression operand = remapExpression(isNull.expression(), slotMap);
      return new IrIsNull(operand, isNull.span());
    }

    if (expr instanceof IrConvert conv) {
      IrExpression inner = remapExpression(conv.expression(), slotMap);
      return new IrConvert(inner, conv.type(), conv.span());
    }

    if (expr instanceof IrAlternateValue alt) {
      IrExpression primary = remapExpression(alt.primary(), slotMap);
      IrExpression fallback = remapExpression(alt.fallback(), slotMap);
      return new IrAlternateValue(primary, fallback, alt.type(), alt.span());
    }

    if (expr instanceof IrGetProperty gp) {
      IrExpression rec = remapExpression(gp.receiver(), slotMap);
      return new IrGetProperty(
          rec, gp.propertyName(), gp.type(), gp.accessPlan(), gp.nullMode(), gp.span());
    }

    if (expr instanceof IrIndexGet ig) {
      IrExpression rec = remapExpression(ig.receiver(), slotMap);
      IrExpression idx = remapExpression(ig.index(), slotMap);
      return new IrIndexGet(rec, idx, ig.type(), ig.span());
    }

    if (expr instanceof IrInvokeAllowedMethod im) {
      IrExpression rec = remapExpression(im.receiver(), slotMap);
      List<IrExpression> args = new ArrayList<>();
      for (IrExpression a : im.arguments()) {
        args.add(remapExpression(a, slotMap));
      }
      return new IrInvokeAllowedMethod(
          rec, im.methodName(), args, im.targetMethod(), im.type(), im.span());
    }

    if (expr instanceof IrDynamicDispatch dd) {
      Optional<IrExpression> rec = dd.receiver().map(r -> remapExpression(r, slotMap));
      List<IrExpression> args = new ArrayList<>();
      for (IrExpression a : dd.arguments()) {
        args.add(remapExpression(a, slotMap));
      }
      return new IrDynamicDispatch(dd.callSite(), rec, dd.targetName(), args, dd.type(), dd.span());
    }

    return expr;
  }

  private Set<String> findRecursiveFunctions(List<IrFunction> functions) {
    Set<String> recursive = new HashSet<>();
    for (IrFunction fn : functions) {
      if (callsMacroDirectly(fn.body(), fn.name())) {
        recursive.add(fn.name());
      }
    }
    return recursive;
  }

  private boolean callsMacroDirectly(IrBlock block, String macroName) {
    for (IrStatement s : block.statements()) {
      if (s instanceof IrCallMacro cm && cm.macroName().equals(macroName)) {
        return true;
      }
      if (s instanceof IrIf ifStmt) {
        if (callsMacroDirectly(ifStmt.thenBlock(), macroName)
            || (ifStmt.elseBlock().isPresent()
                && callsMacroDirectly(ifStmt.elseBlock().get(), macroName))) {
          return true;
        }
      }
      if (s instanceof IrLoop loop) {
        if (callsMacroDirectly(loop.body(), macroName)
            || (loop.elseBody().isPresent()
                && callsMacroDirectly(loop.elseBody().get(), macroName))) {
          return true;
        }
      }
    }
    return false;
  }
}
