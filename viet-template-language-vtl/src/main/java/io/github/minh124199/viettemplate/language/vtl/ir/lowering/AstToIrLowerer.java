package io.github.minh124199.viettemplate.language.vtl.ir.lowering;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAccessStep;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAssignmentTarget;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBinaryExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBinaryOperator;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBlockDirectiveCallNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBooleanLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBreakDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDecimalLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDefineDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDirectiveCallNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlEvaluateDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlForeachDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlGroupedExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIfBranch;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIfDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIncludeDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIntegerLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlInterpolatedStringExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMacroDefinitionNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMacroParameter;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNullLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlParseDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlRangeExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlRawTextNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReference;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceOutputNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlSetDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlStopDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlStringLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTextNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlUnaryExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrAlternateValue;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrDynamicDispatch;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIndexGet;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrInvokeAllowedMethod;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.DynamicAccessSite;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.DynamicKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullAccessMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBreak;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetIndex;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.ir.verifier.IrVerifier;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelParameter;
import io.github.minh124199.viettemplate.language.vtl.semantics.resolve.MemberResolution;
import io.github.minh124199.viettemplate.language.vtl.semantics.resolve.MethodResolution;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lowers a parsed AST ({@link VtlTemplate}) and its completed {@link SemanticAnalysisResult} into
 * an optimized, typed, and verifiable {@link IrTemplate}.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Collapses adjacent static text nodes into single constant pool entries.
 *   <li>Separates expression value computation from output write effects.
 *   <li>Converts resolved property and method access steps to explicit {@link AccessPlan}s.
 *   <li>Represents unresolved or dynamic accesses as explicit {@link DynamicAccessSite}s.
 *   <li>Preserves source spans across all effectful statements and expressions.
 *   <li>Verifies IR invariants using {@link IrVerifier} before returning.
 * </ul>
 */
public final class AstToIrLowerer {

  private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
  private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
  private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
  private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);

  private final SourceText source;
  private final SemanticAnalysisResult analysis;
  private final VtlSemanticOptions options;
  private final BitSet gobbledIndices;
  private final IrConstantPool constantPool = new IrConstantPool();
  private final Map<String, IrParameter> parameters = new LinkedHashMap<>();
  private final List<IrFunction> functions = new ArrayList<>();
  private int nextCallSiteId = 1;
  private int nextLocalSlot = 0;

  private AstToIrLowerer(
      SourceText source,
      SemanticAnalysisResult analysis,
      VtlSemanticOptions options,
      BitSet gobbledIndices) {
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.analysis = Objects.requireNonNull(analysis, "analysis must not be null");
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.gobbledIndices = gobbledIndices;
  }

  /**
   * Lowers the analyzed template to {@link IrTemplate} with verification enabled and space gobbling
   * applied.
   */
  public static IrTemplate lower(
      VtlTemplate template,
      SourceText source,
      SemanticAnalysisResult analysis,
      VtlSemanticOptions options,
      BitSet gobbledIndices) {
    AstToIrLowerer lowerer = new AstToIrLowerer(source, analysis, options, gobbledIndices);
    return lowerer.run(template);
  }

  /** Lowers the analyzed template to {@link IrTemplate} with verification enabled. */
  public static IrTemplate lower(
      VtlTemplate template,
      SourceText source,
      SemanticAnalysisResult analysis,
      VtlSemanticOptions options) {
    return lower(template, source, analysis, options, null);
  }

  public static IrTemplate lower(
      VtlTemplate template, SourceText source, SemanticAnalysisResult analysis) {
    return lower(template, source, analysis, VtlSemanticOptions.defaults(), null);
  }

  private IrTemplate run(VtlTemplate template) {
    // 1. Seed parameters from model schema
    int paramSlot = 0;
    for (ModelParameter p : options.modelSchema().parameters().values()) {
      IrParameter param = new IrParameter(p.name(), p.type(), paramSlot++, template.span());
      parameters.put(p.name(), param);
    }

    // 2. Lower root statements with local scoping
    Scope lowerScope = new Scope(null);
    for (IrParameter param : parameters.values()) {
      lowerScope.defineParam(param.name(), param);
    }

    IrBlock rootBlock = lowerBlock(template.children(), lowerScope, template.span());

    // 3. Assemble IR Template
    IrTemplate irTemplate =
        new IrTemplate(
            template.templateId(),
            List.copyOf(parameters.values()),
            rootBlock,
            constantPool,
            analysis.capabilities(),
            functions,
            template.span());

    // 4. Verify invariants
    IrVerifier.verify(irTemplate);

    return irTemplate;
  }

  private IrBlock lowerBlock(List<VtlNode> nodes, Scope scope, SourceSpan blockSpan) {
    List<IrStatement> statements = new ArrayList<>();

    // Collapse adjacent text nodes
    StringBuilder pendingText = new StringBuilder();
    SourceSpan pendingSpan = null;

    for (VtlNode node : nodes) {
      if (node instanceof VtlTextNode txt) {
        String text = extractText(txt);
        if (!text.isEmpty()) {
          pendingText.append(text);
          pendingSpan = mergeSpans(pendingSpan, txt.span());
        }
        continue;
      }
      if (node instanceof VtlRawTextNode raw) {
        pendingText.append(raw.innerContent(source));
        pendingSpan = mergeSpans(pendingSpan, raw.span());
        continue;
      }

      // Flush any accumulated text chunk before other statements
      if (pendingText.length() > 0) {
        int constId = constantPool.registerText(pendingText.toString(), pendingSpan);
        statements.add(new IrWriteConst(constId, pendingSpan));
        pendingText.setLength(0);
        pendingSpan = null;
      }

      lowerNode(node, statements, scope);
    }

    // Final flush of trailing text chunk
    if (pendingText.length() > 0) {
      int constId = constantPool.registerText(pendingText.toString(), pendingSpan);
      statements.add(new IrWriteConst(constId, pendingSpan));
    }

    return new IrBlock(statements, blockSpan);
  }

  private String extractText(VtlTextNode node) {
    if (gobbledIndices == null || gobbledIndices.isEmpty()) {
      return node.text(source);
    }
    SourceSpan span = node.span();
    int start = span.startOffset();
    int end = span.endOffset();
    int nextGobbled = gobbledIndices.nextSetBit(start);
    if (nextGobbled < 0 || nextGobbled >= end) {
      return node.text(source);
    }
    String content = source.content();
    StringBuilder sb = new StringBuilder(end - start);
    for (int i = start; i < end; i++) {
      if (!gobbledIndices.get(i)) {
        sb.append(content.charAt(i));
      }
    }
    return sb.toString();
  }

  private void lowerNode(VtlNode node, List<IrStatement> statements, Scope scope) {
    if (node instanceof VtlReferenceOutputNode refOut) {
      IrExpression value = lowerReference(refOut.reference(), scope);
      NullRenderMode nullMode =
          refOut.reference().isQuiet()
              ? NullRenderMode.EMPTY_STRING
              : (options.strictMode()
                  ? NullRenderMode.THROW_ERROR
                  : NullRenderMode.LITERAL_EXPRESSION);
      IrEscapeMode escapeMode =
          (options.profile() == VtlProfile.VTL_SAFE) ? IrEscapeMode.HTML_TEXT : IrEscapeMode.RAW;
      statements.add(new IrWriteValue(value, escapeMode, nullMode, refOut.span()));
      return;
    }

    if (node instanceof VtlSetDirectiveNode set) {
      if (set.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
        VtlReference ref = refTarget.reference();
        IrExpression rhs = lowerExpression(set.value(), scope);
        if (ref.steps().isEmpty()) {
          String varName = ref.rootName();
          IrLocal local = scope.getOrCreateLocal(varName, rhs.type(), set.span());
          statements.add(new IrStoreLocal(local, rhs, set.span()));
        } else {
          IrExpression target = lowerReferenceUpTo(ref, ref.steps().size() - 1, scope);
          VtlAccessStep lastStep = ref.steps().get(ref.steps().size() - 1);
          if (lastStep instanceof VtlAccessStep.PropertyAccess prop) {
            statements.add(new IrSetProperty(target, prop.propertyName(), rhs, set.span()));
          } else if (lastStep instanceof VtlAccessStep.IndexAccess idx) {
            IrExpression idxExpr = lowerExpression(idx.indexExpression(), scope);
            statements.add(new IrSetIndex(target, idxExpr, rhs, set.span()));
          }
        }
      }
      return;
    }

    if (node instanceof VtlIfDirectiveNode ifNode) {
      statements.add(lowerIfDirective(ifNode, scope));
      return;
    }

    if (node instanceof VtlForeachDirectiveNode foreach) {
      statements.add(lowerForeachDirective(foreach, scope));
      return;
    }

    if (node instanceof VtlIncludeDirectiveNode inc) {
      for (VtlExpression arg : inc.arguments()) {
        if (arg instanceof VtlStringLiteralExpression str) {
          statements.add(IrCallTemplate.staticCall(str.value(), false, arg.span()));
        } else {
          IrExpression expr = lowerExpression(arg, scope);
          statements.add(IrCallTemplate.dynamicCall(expr, false, arg.span()));
        }
      }
      return;
    }

    if (node instanceof VtlParseDirectiveNode parse) {
      if (parse.templateExpression() instanceof VtlStringLiteralExpression str) {
        statements.add(IrCallTemplate.staticCall(str.value(), true, parse.span()));
      } else {
        IrExpression expr = lowerExpression(parse.templateExpression(), scope);
        statements.add(IrCallTemplate.dynamicCall(expr, true, parse.span()));
      }
      return;
    }

    if (node instanceof VtlMacroDefinitionNode macro) {
      lowerMacroDefinition(macro, scope);
      return;
    }

    if (node instanceof VtlDefineDirectiveNode define) {
      Scope defScope = new Scope(scope);
      IrBlock body = lowerBlock(define.body(), defScope, define.span());
      IrLocal local =
          scope.getOrCreateLocal(define.targetReference().rootName(), VTypes.STRING, define.span());
      statements.add(
          new IrStoreLocal(local, new IrConst(body, VTypes.STRING, define.span()), define.span()));
      return;
    }

    if (node instanceof VtlDirectiveCallNode call) {
      List<IrExpression> args = new ArrayList<>();
      for (VtlExpression arg : call.arguments()) {
        args.add(lowerExpression(arg, scope));
      }
      statements.add(IrCallMacro.of(call.name(), args, call.span()));
      return;
    }

    if (node instanceof VtlBlockDirectiveCallNode blockCall) {
      List<IrExpression> args = new ArrayList<>();
      for (VtlExpression arg : blockCall.arguments()) {
        args.add(lowerExpression(arg, scope));
      }
      Scope bodyScope = new Scope(scope);
      bodyScope.getOrCreateLocal("bodyContent", VTypes.STRING, blockCall.span());
      IrBlock bodyBlock = lowerBlock(blockCall.body(), bodyScope, blockCall.span());
      statements.add(IrCallMacro.of(blockCall.name(), args, bodyBlock, blockCall.span()));
      return;
    }

    if (node instanceof VtlBreakDirectiveNode brk) {
      statements.add(new IrBreak(brk.span()));
      return;
    }

    if (node instanceof VtlStopDirectiveNode stop) {
      statements.add(new IrStop(stop.span()));
      return;
    }

    if (node instanceof VtlEvaluateDirectiveNode eval) {
      IrExpression expr = lowerExpression(eval.expression(), scope);
      statements.add(new IrEvaluate(expr, eval.span()));
    }
  }

  private IrIf lowerIfDirective(VtlIfDirectiveNode ifNode, Scope scope) {
    VtlIfBranch mainBranch = ifNode.primaryBranch();
    IrExpression mainCond = lowerExpression(mainBranch.condition(), scope);
    if (!VTypes.BOOLEAN.equals(mainCond.type())) {
      mainCond = new IrTruthiness(mainCond, true, mainCond.span());
    }

    Scope thenScope = new Scope(scope);
    IrBlock thenBlock = lowerBlock(mainBranch.body(), thenScope, mainBranch.span());

    List<VtlIfBranch> branches = ifNode.branches();
    List<VtlIfBranch> elifBranches =
        branches.size() > 1 ? branches.subList(1, branches.size()) : List.of();

    Optional<IrBlock> elseBlock = lowerElifChain(elifBranches, ifNode.elseBody(), scope);

    return new IrIf(mainCond, thenBlock, elseBlock, ifNode.span());
  }

  private Optional<IrBlock> lowerElifChain(
      List<VtlIfBranch> elseifBranches, Optional<List<VtlNode>> elseBranch, Scope scope) {
    if (elseifBranches.isEmpty() && elseBranch.isEmpty()) {
      return Optional.empty();
    }

    if (elseifBranches.isEmpty()) {
      Scope elseScope = new Scope(scope);
      return Optional.of(
          lowerBlock(elseBranch.get(), elseScope, scopeLeadingSpan(elseBranch.get())));
    }

    VtlIfBranch firstElif = elseifBranches.get(0);
    IrExpression cond = lowerExpression(firstElif.condition(), scope);
    if (!VTypes.BOOLEAN.equals(cond.type())) {
      cond = new IrTruthiness(cond, true, cond.span());
    }

    Scope elifScope = new Scope(scope);
    IrBlock elifThen = lowerBlock(firstElif.body(), elifScope, firstElif.span());

    List<VtlIfBranch> remainingElifs = elseifBranches.subList(1, elseifBranches.size());
    Optional<IrBlock> nextElse = lowerElifChain(remainingElifs, elseBranch, scope);

    IrIf nestedIf = new IrIf(cond, elifThen, nextElse, firstElif.span());
    return Optional.of(IrBlock.of(firstElif.span(), nestedIf));
  }

  private IrLoop lowerForeachDirective(VtlForeachDirectiveNode foreach, Scope scope) {
    IrExpression iterable = lowerExpression(foreach.iterable(), scope);
    VType iterType = iterable.type();

    LoopPlan plan;
    VType elemType;

    if (iterType instanceof VType.ArrayType at) {
      plan = LoopPlan.ARRAY;
      elemType = at.componentType();
    } else if (iterType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> c = ct.javaClass().get();
      if (List.class.isAssignableFrom(c)) {
        plan = LoopPlan.LIST_INDEXED;
        elemType = ct.typeArguments().isEmpty() ? VTypes.DYNAMIC : ct.typeArguments().get(0);
      } else if (Iterator.class.isAssignableFrom(c)) {
        plan = LoopPlan.ITERATOR;
        elemType = ct.typeArguments().isEmpty() ? VTypes.DYNAMIC : ct.typeArguments().get(0);
      } else if (Iterable.class.isAssignableFrom(c)) {
        plan = LoopPlan.ITERABLE;
        elemType = ct.typeArguments().isEmpty() ? VTypes.DYNAMIC : ct.typeArguments().get(0);
      } else {
        plan = LoopPlan.DYNAMIC;
        elemType = VTypes.DYNAMIC;
      }
    } else if (foreach.iterable() instanceof VtlRangeExpression) {
      plan = LoopPlan.RANGE;
      elemType = VTypes.INT;
    } else {
      plan = LoopPlan.DYNAMIC;
      elemType = VTypes.DYNAMIC;
    }

    Scope loopScope = new Scope(scope);
    IrLocal elemLocal =
        loopScope.getOrCreateLocal(foreach.loopVariable().rootName(), elemType, foreach.span());
    IrLocal loopStateLocal = loopScope.getOrCreateLocal("foreach", VTypes.DYNAMIC, foreach.span());

    IrBlock body = lowerBlock(foreach.body(), loopScope, foreach.span());

    Optional<IrBlock> elseBody = Optional.empty();
    if (foreach.elseBody().isPresent()) {
      Scope elseScope = new Scope(scope);
      elseBody = Optional.of(lowerBlock(foreach.elseBody().get(), elseScope, foreach.span()));
    }

    return new IrLoop(
        plan, iterable, elemLocal, Optional.of(loopStateLocal), body, elseBody, foreach.span());
  }

  private void lowerMacroDefinition(VtlMacroDefinitionNode macro, Scope parentScope) {
    int savedLocalSlot = nextLocalSlot;
    try {
      Scope macroScope = new Scope(null);
      List<IrParameter> macroParams = new ArrayList<>();
      int slot = 0;
      for (VtlMacroParameter mp : macro.parameters()) {
        Optional<IrExpression> defExpr = mp.defaultValue().map(d -> lowerExpression(d, macroScope));
        IrParameter param = new IrParameter(mp.name(), VTypes.DYNAMIC, slot++, defExpr, mp.span());
        macroParams.add(param);
        macroScope.defineParam(mp.name(), param);
      }
      nextLocalSlot = slot;
      macroScope.getOrCreateLocal("bodyContent", VTypes.STRING, macro.span());

      IrBlock body = lowerBlock(macro.body(), macroScope, macro.span());
      List<IrLocal> locals = macroScope.allLocals();

      IrFunction function = new IrFunction(macro.name(), macroParams, locals, body, macro.span());
      functions.add(function);
    } finally {
      nextLocalSlot = savedLocalSlot;
    }
  }

  public IrExpression lowerExpression(VtlExpression expr, Scope scope) {
    Objects.requireNonNull(expr, "expr must not be null");

    if (expr instanceof VtlReferenceExpression refExpr) {
      return lowerReference(refExpr.reference(), scope);
    }

    if (expr instanceof VtlStringLiteralExpression str) {
      return IrConst.ofString(str.value(), str.span());
    }

    if (expr instanceof VtlIntegerLiteralExpression intLit) {
      BigInteger val = intLit.value();
      if (val.compareTo(INT_MAX) <= 0 && val.compareTo(INT_MIN) >= 0) {
        return IrConst.ofInt(val.intValue(), intLit.span());
      } else if (val.compareTo(LONG_MAX) <= 0 && val.compareTo(LONG_MIN) >= 0) {
        return IrConst.ofLong(val.longValue(), intLit.span());
      } else {
        return new IrConst(val, VTypes.BIG_INTEGER, intLit.span());
      }
    }

    if (expr instanceof VtlDecimalLiteralExpression dec) {
      return IrConst.ofDouble(dec.value().doubleValue(), dec.span());
    }

    if (expr instanceof VtlBooleanLiteralExpression bool) {
      return IrConst.ofBoolean(bool.value(), bool.span());
    }

    if (expr instanceof VtlNullLiteralExpression nullLit) {
      return IrConst.ofNull(nullLit.span());
    }

    if (expr instanceof VtlGroupedExpression group) {
      return lowerExpression(group.expression(), scope);
    }

    if (expr instanceof VtlUnaryExpression un) {
      IrExpression operand = lowerExpression(un.operand(), scope);
      VType resType = analysis.typeOf(un).orElse(operand.type());
      return switch (un.operator()) {
        case NOT -> {
          if (!VTypes.BOOLEAN.equals(operand.type())) {
            operand = new IrTruthiness(operand, true, operand.span());
          }
          yield new IrUnaryOp(UnaryOpKind.NOT, operand, VTypes.BOOLEAN, un.span());
        }
        case MINUS -> new IrUnaryOp(UnaryOpKind.NEGATE, operand, resType, un.span());
        case PLUS -> operand;
      };
    }

    if (expr instanceof VtlBinaryExpression bin) {
      IrExpression left = lowerExpression(bin.left(), scope);
      IrExpression right = lowerExpression(bin.right(), scope);
      VType resType = analysis.typeOf(bin).orElse(VTypes.DYNAMIC);
      BinaryOpKind opKind = mapBinaryOp(bin.operator());

      if (opKind == BinaryOpKind.AND || opKind == BinaryOpKind.OR) {
        if (!VTypes.BOOLEAN.equals(left.type())) {
          left = new IrTruthiness(left, true, left.span());
        }
        if (!VTypes.BOOLEAN.equals(right.type())) {
          right = new IrTruthiness(right, true, right.span());
        }
      }
      return new IrBinaryOp(opKind, left, right, resType, bin.span());
    }

    if (expr instanceof VtlRangeExpression rng) {
      IrExpression start = lowerExpression(rng.start(), scope);
      IrExpression end = lowerExpression(rng.end(), scope);
      VType arrayType = new VType.ArrayType(VTypes.INT, Nullability.NON_NULL);
      return new IrBinaryOp(BinaryOpKind.ADD, start, end, arrayType, rng.span());
    }

    if (expr instanceof VtlInterpolatedStringExpression interp) {
      IrExpression current = null;
      for (var part : interp.parts()) {
        IrExpression partExpr;
        if (part instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart tp) {
          partExpr = IrConst.ofString(tp.text(), tp.span());
        } else if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          partExpr = lowerReference(rp.reference(), scope);
        } else {
          continue;
        }

        if (current == null) {
          current = partExpr;
        } else {
          current =
              new IrBinaryOp(BinaryOpKind.ADD, current, partExpr, VTypes.STRING, interp.span());
        }
      }
      return current != null ? current : IrConst.ofString("", interp.span());
    }

    // Fallback dynamic
    return new IrConst(expr.toString(), VTypes.DYNAMIC, expr.span());
  }

  public IrExpression lowerReference(VtlReference ref, Scope scope) {
    IrExpression current = lowerReferenceUpTo(ref, ref.steps().size(), scope);
    if (ref.alternateValue().isPresent()) {
      IrExpression alt = lowerExpression(ref.alternateValue().get(), scope);
      return IrAlternateValue.of(current, alt, ref.span());
    }
    return current;
  }

  public IrExpression lowerReferenceUpTo(VtlReference ref, int limitSteps, Scope scope) {
    String rootName = ref.rootName();
    IrExpression current;

    // Check local scope first
    IrLocal local = scope.resolveLocal(rootName);
    if (local != null) {
      current = new IrLoadLocal(rootName, local.slot(), local.type(), ref.span());
    } else {
      // Check parameters in current scope hierarchy
      IrParameter param = scope.resolveParam(rootName);
      if (param != null) {
        current = new IrLoadParam(rootName, param.slot(), param.type(), ref.span());
      } else {
        DynamicAccessSite site =
            new DynamicAccessSite(nextCallSiteId++, DynamicKind.PROPERTY_GET, rootName, ref.span());
        current = IrDynamicDispatch.root(site, rootName, VTypes.DYNAMIC, ref.span());
      }
    }

    // Traverse access steps
    for (int stepIdx = 0; stepIdx < limitSteps && stepIdx < ref.steps().size(); stepIdx++) {
      VtlAccessStep step = ref.steps().get(stepIdx);
      if (step instanceof VtlAccessStep.PropertyAccess prop) {
        Optional<MemberResolution> optRes = analysis.memberResolutionOf(prop);
        AccessPlan plan;
        VType resType;

        if (optRes.isPresent() && optRes.get().isFound()) {
          MemberResolution res = optRes.get();
          resType = res.resultType();
          plan = buildAccessPlan(res, prop.propertyName());
        } else {
          resType = VTypes.DYNAMIC;
          plan = new AccessPlan.DynamicCallSite(nextCallSiteId++, prop.propertyName());
        }

        current =
            new IrGetProperty(
                current,
                prop.propertyName(),
                resType,
                plan,
                NullAccessMode.PROPAGATE_NULL,
                prop.span());
      } else if (step instanceof VtlAccessStep.MethodCall call) {
        List<IrExpression> args = new ArrayList<>();
        for (VtlExpression arg : call.arguments()) {
          args.add(lowerExpression(arg, scope));
        }

        Optional<MethodResolution> optRes = analysis.methodResolutionOf(call);
        if (optRes.isPresent() && optRes.get().isResolved()) {
          MethodResolution res = optRes.get();
          Method targetMethod = res.targetMethod().orElseThrow();
          current =
              new IrInvokeAllowedMethod(
                  current, call.methodName(), args, targetMethod, res.returnType(), call.span());
        } else {
          DynamicAccessSite site =
              new DynamicAccessSite(
                  nextCallSiteId++, DynamicKind.METHOD_CALL, call.methodName(), call.span());
          current =
              IrDynamicDispatch.onReceiver(
                  site, current, call.methodName(), args, VTypes.DYNAMIC, call.span());
        }
      } else if (step instanceof VtlAccessStep.IndexAccess idx) {
        IrExpression indexExpr = lowerExpression(idx.indexExpression(), scope);
        VType resType = VTypes.DYNAMIC;
        if (current.type() instanceof VType.ArrayType at) {
          resType = at.componentType();
        } else if (current.type() instanceof VType.ClassType ct && !ct.typeArguments().isEmpty()) {
          resType = ct.typeArguments().get(0);
        }
        current = new IrIndexGet(current, indexExpr, resType, idx.span());
      }
    }

    return current;
  }

  private AccessPlan buildAccessPlan(MemberResolution res, String propertyName) {
    return switch (res.kind()) {
      case RECORD_COMPONENT -> {
        Method accessor = (Method) res.targetMember().orElseThrow();
        yield new AccessPlan.DirectRecord(
            accessor.getDeclaringClass(), propertyName, accessor.getReturnType(), accessor);
      }
      case GETTER, BOOLEAN_GETTER -> {
        Method getter = (Method) res.targetMember().orElseThrow();
        yield new AccessPlan.DirectGetter(
            getter.getDeclaringClass(), getter.getName(), getter.getReturnType(), getter);
      }
      case FIELD -> {
        Field field = (Field) res.targetMember().orElseThrow();
        yield new AccessPlan.DirectField(
            field.getDeclaringClass(), field.getName(), field.getType(), field);
      }
      case MAP_ENTRY -> new AccessPlan.MapLookup(propertyName);
      case EXTENSION -> {
        Method method = (Method) res.targetMember().orElseThrow();
        yield new AccessPlan.ExtensionCall(method.getDeclaringClass(), method.getName(), method);
      }
      case DYNAMIC, NOT_FOUND -> new AccessPlan.DynamicCallSite(nextCallSiteId++, propertyName);
    };
  }

  private static BinaryOpKind mapBinaryOp(VtlBinaryOperator op) {
    return switch (op) {
      case MULTIPLY -> BinaryOpKind.MULTIPLY;
      case DIVIDE -> BinaryOpKind.DIVIDE;
      case MODULO -> BinaryOpKind.REMAINDER;
      case ADD -> BinaryOpKind.ADD;
      case SUBTRACT -> BinaryOpKind.SUBTRACT;
      case LESS_THAN -> BinaryOpKind.LESS_THAN;
      case LESS_THAN_OR_EQUAL -> BinaryOpKind.LESS_THAN_OR_EQUAL;
      case GREATER_THAN -> BinaryOpKind.GREATER_THAN;
      case GREATER_THAN_OR_EQUAL -> BinaryOpKind.GREATER_THAN_OR_EQUAL;
      case EQUAL -> BinaryOpKind.EQUALS;
      case NOT_EQUAL -> BinaryOpKind.NOT_EQUALS;
      case LOGICAL_AND -> BinaryOpKind.AND;
      case LOGICAL_OR -> BinaryOpKind.OR;
    };
  }

  private static SourceSpan mergeSpans(SourceSpan s1, SourceSpan s2) {
    if (s1 == null || !s1.isKnown()) {
      return s2;
    }
    if (s2 == null || !s2.isKnown()) {
      return s1;
    }
    int startOffset = Math.min(s1.startOffset(), s2.startOffset());
    int endOffset = Math.max(s1.endOffset(), s2.endOffset());
    int startLine = s1.startOffset() <= s2.startOffset() ? s1.startLine() : s2.startLine();
    int startColumn = s1.startOffset() <= s2.startOffset() ? s1.startColumn() : s2.startColumn();
    int endLine = s1.endOffset() >= s2.endOffset() ? s1.endLine() : s2.endLine();
    int endColumn = s1.endOffset() >= s2.endOffset() ? s1.endColumn() : s2.endColumn();
    return new SourceSpan(startOffset, endOffset, startLine, startColumn, endLine, endColumn);
  }

  private static SourceSpan scopeLeadingSpan(List<VtlNode> nodes) {
    if (nodes.isEmpty()) {
      return SourceSpan.UNKNOWN;
    }
    return mergeSpans(nodes.get(0).span(), nodes.get(nodes.size() - 1).span());
  }

  private final class Scope {
    private final Scope parent;
    private final Map<String, IrLocal> locals = new LinkedHashMap<>();
    private final Map<String, IrParameter> scopeParams = new HashMap<>();

    Scope(Scope parent) {
      this.parent = parent;
    }

    void defineParam(String name, IrParameter param) {
      scopeParams.put(name, param);
    }

    IrParameter resolveParam(String name) {
      IrParameter p = scopeParams.get(name);
      if (p != null) {
        return p;
      }
      return parent != null ? parent.resolveParam(name) : null;
    }

    IrLocal getOrCreateLocal(String name, VType type, SourceSpan span) {
      IrLocal existing = resolveLocal(name);
      if (existing != null) {
        return existing;
      }
      IrLocal created = new IrLocal(name, type, nextLocalSlot++, span);
      locals.put(name, created);
      return created;
    }

    IrLocal resolveLocal(String name) {
      IrLocal loc = locals.get(name);
      if (loc != null) {
        return loc;
      }
      return parent != null ? parent.resolveLocal(name) : null;
    }

    List<IrLocal> allLocals() {
      return List.copyOf(locals.values());
    }
  }
}
