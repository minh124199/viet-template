package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrTextConstant;
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
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullAccessMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBreak;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBudgetCheck;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetIndex;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.EscapeMode;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.SafeUrl;
import io.github.minh124199.viettemplate.runtime.StandardEscapers;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference interpreter for the intermediate representation ({@link IrTemplate}).
 *
 * <p>Implements Milestone M7 reference execution backend, executing the same semantic IR
 * representation consumed by compiled bytecode backends. Supports streaming output, exact Velocity
 * compatibility semantics, deterministic execution limits, source-span error reporting, and dynamic
 * template evaluation.
 */
public final class IrInterpreter {

  private IrInterpreter() {}

  /** Renders the given {@link IrTemplate} using the reference IR interpreter. */
  public static void render(
      IrTemplate template,
      SourceText source,
      ExecutionContext context,
      TemplateOutput output,
      VtlInterpreterOptions options)
      throws IOException {
    Objects.requireNonNull(template, "template must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(output, "output must not be null");
    Objects.requireNonNull(options, "options must not be null");

    CountingTemplateOutput countingOutput =
        (output instanceof CountingTemplateOutput cto)
            ? cto
            : new CountingTemplateOutput(
                output, options.limits().createRenderBudget(), template.id());

    Map<String, IrFunction> functionMap = new HashMap<>();
    for (IrFunction function : template.functions()) {
      functionMap.put(function.name(), function);
    }

    IrSlotLayout.SlotLayout layout = IrSlotLayout.layout(template);
    InterpretedFrame frame =
        new InterpretedFrame(
            template.id(),
            source,
            context,
            countingOutput,
            template.constants(),
            functionMap,
            options,
            new LinkedReferenceAccess(options.securityPolicy()),
            0,
            0,
            0,
            layout);

    for (IrSlotLayout.SlotMetadata meta : layout.seededSlots()) {
      frame.seedLocal(meta.slot(), context.lookup(meta.name()));
    }

    try {
      executeBlock(template.root(), frame);
    } catch (StopSignal | BreakSignal ignored) {
      // Normal template termination via #stop or #break
    }
  }

  static void executeBlock(IrBlock block, InterpretedFrame frame) throws IOException {
    List<IrStatement> statements = block.statements();
    int size = statements.size();

    for (int i = 0; i < size; i++) {
      IrStatement stmt = statements.get(i);
      IrStatement nextStmt = (i + 1 < size) ? statements.get(i + 1) : null;
      executeStatement(stmt, nextStmt, frame);
    }
  }

  private static void executeStatement(
      IrStatement stmt, IrStatement nextStmt, InterpretedFrame frame) throws IOException {
    if (stmt instanceof IrWriteConst wc) {
      executeWriteConst(wc, nextStmt, frame);
    } else if (stmt instanceof IrWriteValue wv) {
      executeWriteValue(wv, frame);
    } else if (stmt instanceof IrStoreLocal sl) {
      executeStoreLocal(sl, frame);
    } else if (stmt instanceof IrSetProperty sp) {
      executeSetProperty(sp, frame);
    } else if (stmt instanceof IrSetIndex si) {
      executeSetIndex(si, frame);
    } else if (stmt instanceof IrIf ifStmt) {
      executeIf(ifStmt, frame);
    } else if (stmt instanceof IrLoop loop) {
      executeLoop(loop, frame);
    } else if (stmt instanceof IrCallMacro callM) {
      executeCallMacro(callM, frame);
    } else if (stmt instanceof IrCallTemplate callT) {
      executeCallTemplate(callT, frame);
    } else if (stmt instanceof IrEvaluate eval) {
      executeEvaluate(eval, frame);
    } else if (stmt instanceof IrBreak) {
      throw BreakSignal.INSTANCE;
    } else if (stmt instanceof IrStop) {
      throw StopSignal.INSTANCE;
    } else if (stmt instanceof IrReturn) {
      return;
    } else if (stmt instanceof IrBudgetCheck bc) {
      if (frame.output instanceof CountingTemplateOutput counting) {
        counting.budget().checkDeadline(frame.templateId, bc.span());
      }
    } else if (stmt instanceof IrNoOp) {
      // no-op
    }
  }

  private static void executeWriteConst(
      IrWriteConst wc, IrStatement nextStmt, InterpretedFrame frame) throws IOException {
    Optional<IrTextConstant> opt = frame.constantPool.getTextConstant(wc.constantId());
    if (opt.isEmpty()) {
      return;
    }
    IrTextConstant constant = opt.get();
    String text = constant.text();

    // Adjust trailing backslashes if immediately followed by an active reference or directive
    if (text.endsWith("\\")) {
      if (nextStmt instanceof IrWriteValue nextWv) {
        boolean isDefined = isValueDefined(nextWv.value(), frame);
        text = VtlEscaping.adjustTrailingBackslashesBeforeActiveReference(text, isDefined);
      } else if (nextStmt instanceof IrIf
          || nextStmt instanceof IrLoop
          || nextStmt instanceof IrCallMacro
          || nextStmt instanceof IrCallTemplate
          || nextStmt instanceof IrEvaluate) {
        text = VtlEscaping.adjustTrailingBackslashesBeforeActiveDirective(text);
      }
    }

    if (text.indexOf('\\') != -1) {
      String rendered = VtlEscaping.renderText(text, frame.context);
      frame.output.write(rendered);
    } else if (text.equals(constant.text()) && constant.utf8Bytes().isPresent()) {
      frame.output.writeUtf8(constant.utf8Bytes().get());
    } else {
      frame.output.write(text);
    }
  }

  private static boolean isValueDefined(IrExpression expr, InterpretedFrame frame) {
    if (expr instanceof IrDynamicDispatch dyn && dyn.receiver().isEmpty()) {
      EvaluationValue rootVal = frame.context.lookup(dyn.targetName());
      return rootVal.isNonNull();
    }
    if (expr instanceof IrLoadLocal load) {
      return frame.getLocal(load.slot()).isNonNull();
    }
    if (expr instanceof IrLoadParam param) {
      return frame.getLocal(param.slot()).isNonNull();
    }
    Object val = evaluateExpression(expr, frame);
    if (val instanceof EvaluationValue ev) {
      return ev.isNonNull();
    }
    return val != null;
  }

  private static void executeWriteValue(IrWriteValue wv, InterpretedFrame frame)
      throws IOException {
    Object result = evaluateExpression(wv.value(), frame);
    EvaluationValue val;
    if (result instanceof EvaluationValue ev) {
      val = ev;
    } else if (result == null) {
      val = EvaluationValue.definedNull();
    } else {
      val = EvaluationValue.of(result);
    }

    NullRenderMode nullMode = wv.nullMode();

    if (frame.options.strictReferences()) {
      if (val.isUndefined()) {
        String varName = extractRootName(wv.value());
        throw new TemplateRenderException(
            "Variable '$" + varName + "' has not been set",
            frame.templateId,
            wv.span(),
            InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
      }
      if (val.isNull()) {
        if (nullMode == NullRenderMode.EMPTY_STRING) {
          return;
        }
        String varName = extractRootName(wv.value());
        throw new TemplateRenderException(
            "Reference '$" + varName + "' evaluated to null when attempting to render",
            frame.templateId,
            wv.span(),
            InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
      }
      renderRenderable(val.value(), wv, frame);
      return;
    }

    if (nullMode == NullRenderMode.EMPTY_STRING) {
      if (val.isNonNull()) {
        renderRenderable(val.value(), wv, frame);
      }
      return;
    }

    if (val.isUndefined() || val.isNull()) {
      // In non-strict mode, render literal source representation
      if (wv.span().isKnown()) {
        String literal =
            frame.source.slice(wv.span().startOffset(), wv.span().endOffset()).toString();
        frame.output.write(literal);
      }
    } else {
      renderRenderable(val.value(), wv, frame);
    }
  }

  private static String extractRootName(IrExpression expr) {
    if (expr instanceof IrDynamicDispatch dyn && dyn.receiver().isEmpty()) {
      return dyn.targetName();
    }
    if (expr instanceof IrLoadLocal load) {
      return load.name();
    }
    if (expr instanceof IrLoadParam param) {
      return param.name();
    }
    if (expr instanceof IrGetProperty prop) {
      return extractRootName(prop.receiver()) + "." + prop.propertyName();
    }
    return expr.toString();
  }

  private static void renderRenderable(Object value, IrWriteValue wv, InterpretedFrame frame)
      throws IOException {
    if (value == null) {
      return;
    }
    if (value instanceof IrBlock block) {
      executeBlock(block, frame);
      return;
    }
    if (value instanceof RenderableBlock rb) {
      InterpretedFrame subFrame = frame.withContext(rb.capturedContext());
      if (rb.capturedSource() != null) {
        subFrame =
            subFrame.withParseDepth(
                frame.parseDepth,
                rb.capturedTemplateId(),
                rb.capturedSource(),
                frame.constantPool,
                frame.variables.size());
      }
      VtlInterpreter legacy = new VtlInterpreter(frame.options);
      legacy.render(
          rb.capturedSource(),
          new io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate(
              rb.capturedTemplateId(), SourceSpan.UNKNOWN, rb.body()),
          subFrame.context,
          subFrame.output);
      return;
    }

    if (value instanceof Integer i) {
      frame.output.writeInt(i);
      return;
    } else if (value instanceof Long l) {
      frame.output.writeLong(l);
      return;
    } else if (value instanceof Double d) {
      frame.output.writeDouble(d);
      return;
    } else if (value instanceof Float f) {
      frame.output.writeFloat(f);
      return;
    } else if (value instanceof Short s) {
      frame.output.writeShort(s);
      return;
    } else if (value instanceof Byte b) {
      frame.output.writeByte(b);
      return;
    } else if (value instanceof Boolean b) {
      frame.output.writeBoolean(b);
      return;
    }

    EscapeMode mode =
        switch (wv.escapeMode()) {
          case RAW -> EscapeMode.RAW;
          case HTML_TEXT -> EscapeMode.HTML_TEXT;
          case HTML_ATTRIBUTE_QUOTED -> EscapeMode.HTML_ATTRIBUTE_QUOTED;
          case URL_COMPONENT -> EscapeMode.URL_COMPONENT;
        };

    // Safe content handling (context-specific: SafeHtml only in HTML_TEXT, SafeUrl only in
    // URL_COMPONENT)
    if (mode == EscapeMode.HTML_TEXT) {
      if (value instanceof SafeHtml safe) {
        frame.output.write(safe.content());
        return;
      }
    } else if (mode == EscapeMode.URL_COMPONENT) {
      if (value instanceof SafeUrl safe) {
        frame.output.write(safe.content());
        return;
      }
    }

    if (frame.options.profile() == VtlProfile.VTL_SAFE) {
      if (!frame.options.securityPolicy().isClassPermitted(value.getClass())) {
        throw new TemplateSecurityException(
            "Rendering class " + value.getClass().getName() + " is denied by security policy",
            frame.templateId,
            wv.span(),
            InterpreterDiagnosticCodes.SECURITY_VIOLATION);
      }
    }

    CharSequence charSeq;
    if (value instanceof CharSequence cs) {
      charSeq = cs;
    } else {
      charSeq = String.valueOf(value);
    }

    StandardEscapers.get(mode).escape(charSeq, frame.output);
  }

  private static void executeStoreLocal(IrStoreLocal sl, InterpretedFrame frame) {
    Object val = evaluateExpression(sl.value(), frame);
    boolean isNullOrUndef =
        (val == null) || (val instanceof EvaluationValue ev && (ev.isUndefined() || ev.isNull()));

    if (isNullOrUndef && !frame.options.setNullAllowed()) {
      return;
    }

    frame.setLocal(sl.local().slot(), sl.local().name(), val);
  }

  private static void executeSetProperty(IrSetProperty sp, InterpretedFrame frame) {
    Object target = unwrap(evaluateExpression(sp.target(), frame));
    Object val = evaluateExpression(sp.value(), frame);
    EvaluationValue evalVal = (val instanceof EvaluationValue ev) ? ev : EvaluationValue.of(val);
    frame.referenceAccess.setProperty(
        target, sp.propertyName(), evalVal, sp.span(), frame.templateId);
  }

  private static void executeSetIndex(IrSetIndex si, InterpretedFrame frame) {
    Object target = unwrap(evaluateExpression(si.target(), frame));
    Object idx = evaluateExpression(si.index(), frame);
    Object val = evaluateExpression(si.value(), frame);
    EvaluationValue evalIdx = (idx instanceof EvaluationValue ev) ? ev : EvaluationValue.of(idx);
    EvaluationValue evalVal = (val instanceof EvaluationValue ev) ? ev : EvaluationValue.of(val);
    frame.referenceAccess.setIndex(target, evalIdx, evalVal, si.span(), frame.templateId);
  }

  private static void executeIf(IrIf ifStmt, InterpretedFrame frame) throws IOException {
    Object cond = evaluateExpression(ifStmt.condition(), frame);
    boolean truthy = isTruthy(cond, frame.options.emptyCheck(), frame.options.securityPolicy());
    if (truthy) {
      executeBlock(ifStmt.thenBlock(), frame);
    } else if (ifStmt.elseBlock().isPresent()) {
      executeBlock(ifStmt.elseBlock().get(), frame);
    }
  }

  private static void executeLoop(IrLoop loop, InterpretedFrame frame) throws IOException {
    Object iterObj = evaluateExpression(loop.iterable(), frame);
    Iterable<?> iterable = toIterable(iterObj, frame, loop.span());

    if (iterable == null) {
      if (loop.elseBody().isPresent()) {
        executeBlock(loop.elseBody().get(), frame);
      }
      return;
    }

    Iterator<?> iterator = iterable.iterator();
    if (!iterator.hasNext()) {
      if (loop.elseBody().isPresent()) {
        executeBlock(loop.elseBody().get(), frame);
      }
      return;
    }

    String loopVar = loop.elementLocal().name();
    int index = 0;
    int limit = frame.options.limits().maxLoopIterations();

    ForeachMetadata parentMeta = null;
    EvaluationValue existingMeta = frame.context.lookup("foreach");
    if (existingMeta.isNonNull() && existingMeta.value() instanceof ForeachMetadata fm) {
      parentMeta = fm;
    }

    List<Integer> loopOwnedSlots =
        frame.layout != null && frame.layout.loopLocals() != null
            ? frame.layout.loopLocals().getOrDefault(loop, List.of())
            : List.of();

    frame.context.pushForeachScope(loopVar, EvaluationValue.undefined(), parentMeta);

    try {
      while (iterator.hasNext()) {
        if (frame.output instanceof CountingTemplateOutput counting) {
          counting.budget().countLoopIteration(frame.templateId, loop.span());
        }
        if (index >= limit) {
          throw new TemplateLimitException(
              "Exceeded maximum foreach iterations: " + limit,
              frame.templateId,
              loop.span(),
              InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
        }

        Object item = iterator.next();
        boolean hasNext = iterator.hasNext();
        boolean first = (index == 0);
        boolean last = !hasNext;
        int count = index + 1;

        ForeachMetadata meta = new ForeachMetadata(index, count, first, last, hasNext, parentMeta);

        for (int slot : loopOwnedSlots) {
          IrSlotLayout.SlotMetadata slotMeta =
              frame.layout != null ? frame.layout.slots().get(slot) : null;
          if (slotMeta != null) {
            frame.context.clearLocalScope(slotMeta.name());
          }
          frame.variables.reset(slot);
        }

        frame.setLocal(loop.elementLocal().slot(), loopVar, item);
        if (loop.loopStateLocal().isPresent()) {
          frame.setLocal(loop.loopStateLocal().get().slot(), "foreach", meta);
        }
        frame.context.updateLoopVariable(loopVar, EvaluationValue.of(item), meta);

        try {
          executeBlock(loop.body(), frame);
        } catch (BreakSignal bs) {
          break;
        }

        index++;
      }
    } finally {
      frame.variables.reset(loop.elementLocal().slot());
      if (loop.loopStateLocal().isPresent()) {
        frame.variables.reset(loop.loopStateLocal().get().slot());
      }
      for (int slot : loopOwnedSlots) {
        frame.variables.reset(slot);
      }
      frame.context.popScope();
    }
  }

  private static void executeCallMacro(IrCallMacro callM, InterpretedFrame frame)
      throws IOException {
    if (frame.macroDepth >= frame.options.limits().maxMacroDepth()) {
      throw new TemplateLimitException(
          "Exceeded maximum macro recursion depth: " + frame.options.limits().maxMacroDepth(),
          frame.templateId,
          callM.span(),
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }

    IrFunction function = frame.functions.get(callM.macroName());
    if (function == null) {
      if (frame.options.strictReferences()) {
        throw new TemplateRenderException(
            "Unknown macro or directive: #" + callM.macroName(),
            frame.templateId,
            callM.span(),
            InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return;
    }

    List<Object> argValues = new ArrayList<>();
    for (IrExpression argExpr : callM.arguments()) {
      argValues.add(evaluateExpression(argExpr, frame));
    }

    IrSlotLayout.SlotLayout fnLayout = IrSlotLayout.layout(function);
    ExecutionFrame macroVariables = new ExecutionFrame(fnLayout.frameSize());
    Map<String, EvaluationValue> bindings = new HashMap<>();
    List<IrParameter> params = function.parameters();

    for (int i = 0; i < params.size(); i++) {
      IrParameter p = params.get(i);
      String paramName = p.name();
      Object val;
      if (i < argValues.size()) {
        val = argValues.get(i);
      } else if (p.defaultValue().isPresent()) {
        val = evaluateExpression(p.defaultValue().get(), frame);
      } else {
        val = EvaluationValue.undefined();
      }
      EvaluationValue evaluationValue = EvaluationValue.of(val);
      macroVariables.set(p.slot(), evaluationValue);
      bindings.put(paramName, evaluationValue);
    }

    if (callM.bodyContent().isPresent()) {
      IrBlock bodyBlock = callM.bodyContent().get();
      bindings.put("bodyContent", EvaluationValue.of(bodyBlock));
      for (IrLocal local : function.locals()) {
        if ("bodyContent".equals(local.name())) {
          macroVariables.set(local.slot(), EvaluationValue.of(bodyBlock));
        }
      }
    }

    frame.context.pushScope(bindings, false);
    try {
      InterpretedFrame macroFrame =
          frame.withMacroDepth(frame.macroDepth + 1, macroVariables, fnLayout);
      executeBlock(function.body(), macroFrame);
    } catch (BreakSignal ignored) {
      // #break inside macro body terminates macro execution
    } finally {
      frame.context.popScope();
    }
  }

  private static void executeCallTemplate(IrCallTemplate callT, InterpretedFrame frame)
      throws IOException {
    Object templateObj = evaluateExpression(callT.templateNameExpr(), frame);
    String path = callT.staticTemplateName().orElseGet(() -> String.valueOf(unwrap(templateObj)));

    Optional<TemplateResource> res =
        frame.options.resourceResolver().resolve(frame.templateId, path);
    if (res.isEmpty()) {
      throw new TemplateResourceException(
          "Resource not found for " + (callT.isParse() ? "#parse" : "#include") + ": " + path,
          frame.templateId,
          callT.span(),
          InterpreterDiagnosticCodes.RESOURCE_NOT_FOUND);
    }

    if (!callT.isParse()) {
      frame.output.write(res.get().content());
      return;
    }

    if (frame.parseDepth >= frame.options.limits().maxParseDepth()) {
      throw new TemplateLimitException(
          "Exceeded maximum #parse depth (maxParseDepth): "
              + frame.options.limits().maxParseDepth(),
          frame.templateId,
          callT.span(),
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }

    SourceText subSource = SourceText.of(path, res.get().content());
    VtlParseResult parseResult = VtlParser.parse(subSource);
    if (parseResult.hasErrors()) {
      throw new TemplateRenderException(
          "Syntax error in parsed template: " + path,
          res.get().templateId(),
          parseResult.template().span(),
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    BitSet subGobbled =
        SpaceGobbler.computeGobbledIndices(subSource, frame.options.spaceGobbling());
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder()
            .profile(frame.options.profile())
            .strictMode(frame.options.strictReferences())
            .allowArbitraryMethods(frame.options.profile().isArbitraryMethodsAllowed())
            .build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate subIr =
        AstToIrLowerer.lower(
            parseResult.template(), subSource, analysis, semanticOptions, subGobbled);
    subIr = IrOptimizer.optimize(subIr, frame.options.optimizationOptions());

    for (IrFunction function : subIr.functions()) {
      frame.functions.put(function.name(), function);
    }

    IrSlotLayout.SlotLayout subLayout = IrSlotLayout.layout(subIr);
    InterpretedFrame subFrame =
        frame.withParseDepth(
            frame.parseDepth + 1, res.get().templateId(), subSource, subIr.constants(), subLayout);
    try {
      executeBlock(subIr.root(), subFrame);
    } catch (BreakSignal ignored) {
      // #break inside parsed template terminates parsed template
    }
    if (frame.layout != null) {
      frame.syncFromContext(frame.layout.slots().values());
    }
  }

  private static void executeEvaluate(IrEvaluate eval, InterpretedFrame frame) throws IOException {
    if (!frame.options.profile().isEvaluateAllowed()) {
      throw new TemplateSecurityException(
          "#evaluate is disabled in profile " + frame.options.profile(),
          frame.templateId,
          eval.span(),
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    if (frame.evaluateDepth >= frame.options.limits().maxEvaluateDepth()) {
      throw new TemplateLimitException(
          "Exceeded maximum #evaluate depth: " + frame.options.limits().maxEvaluateDepth(),
          frame.templateId,
          eval.span(),
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }

    Object val = evaluateExpression(eval.expression(), frame);
    String dynamicSource = String.valueOf(unwrap(val));
    if (dynamicSource.length() > frame.options.limits().maxDynamicSourceLength()) {
      throw new TemplateLimitException(
          "Dynamic template source exceeds maximum length ("
              + dynamicSource.length()
              + " > "
              + frame.options.limits().maxDynamicSourceLength()
              + ")",
          frame.templateId,
          eval.span(),
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }

    TemplateId evalId =
        TemplateId.of(frame.templateId.value() + "#evaluate[" + frame.evaluateDepth + "]");
    SourceText subSource = SourceText.of(evalId.value(), dynamicSource);
    VtlParseResult parseResult = VtlParser.parse(subSource);
    if (parseResult.hasErrors()) {
      throw new TemplateRenderException(
          "Syntax error in dynamically evaluated template",
          evalId,
          parseResult.template().span(),
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    BitSet subGobbled =
        SpaceGobbler.computeGobbledIndices(subSource, frame.options.spaceGobbling());
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder()
            .profile(frame.options.profile())
            .strictMode(frame.options.strictReferences())
            .allowArbitraryMethods(frame.options.profile().isArbitraryMethodsAllowed())
            .build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate subIr =
        AstToIrLowerer.lower(
            parseResult.template(), subSource, analysis, semanticOptions, subGobbled);
    subIr = IrOptimizer.optimize(subIr, frame.options.optimizationOptions());

    for (IrFunction function : subIr.functions()) {
      frame.functions.put(function.name(), function);
    }

    IrSlotLayout.SlotLayout evalLayout = IrSlotLayout.layout(subIr);
    InterpretedFrame subFrame =
        frame.withEvaluateDepth(
            frame.evaluateDepth + 1, evalId, subSource, subIr.constants(), evalLayout);
    executeBlock(subIr.root(), subFrame);
    if (frame.layout != null) {
      frame.syncFromContext(frame.layout.slots().values());
    }
  }

  public static Object evaluateExpression(IrExpression expr, InterpretedFrame frame) {
    if (expr instanceof IrConst c) {
      return c.value();
    }
    if (expr instanceof IrLoadLocal load) {
      return frame.getLocal(load.slot());
    }
    if (expr instanceof IrLoadParam param) {
      return frame.getLocal(param.slot());
    }
    if (expr instanceof IrDynamicDispatch dyn) {
      return evaluateDynamicDispatch(dyn, frame);
    }
    if (expr instanceof IrGetProperty prop) {
      return evaluateGetProperty(prop, frame);
    }
    if (expr instanceof IrInvokeAllowedMethod inv) {
      return evaluateInvokeAllowedMethod(inv, frame);
    }
    if (expr instanceof IrIndexGet idx) {
      return evaluateIndexGet(idx, frame);
    }
    if (expr instanceof IrBinaryOp bin) {
      return evaluateBinaryOp(bin, frame);
    }
    if (expr instanceof IrUnaryOp un) {
      return evaluateUnaryOp(un, frame);
    }
    if (expr instanceof IrTruthiness tr) {
      Object val = evaluateExpression(tr.expression(), frame);
      boolean emptyCheck = tr.emptyCheck() && frame.options.emptyCheck();
      return isTruthy(val, emptyCheck, frame.options.securityPolicy());
    }
    if (expr instanceof IrIsNull isNull) {
      Object val = evaluateExpression(isNull.expression(), frame);
      return val == null
          || (val instanceof EvaluationValue ev && (ev.isNull() || ev.isUndefined()));
    }
    if (expr instanceof IrConvert conv) {
      return evaluateExpression(conv.expression(), frame);
    }
    if (expr instanceof IrAlternateValue alt) {
      Object primaryVal = evaluateExpression(alt.primary(), frame);
      if (isTruthy(primaryVal, true, frame.options.securityPolicy())) {
        return primaryVal;
      }
      return evaluateExpression(alt.fallback(), frame);
    }
    return EvaluationValue.undefined();
  }

  private static Object evaluateDynamicDispatch(IrDynamicDispatch dyn, InterpretedFrame frame) {
    if (dyn.receiver().isEmpty()) {
      return frame.context.lookup(dyn.targetName());
    }
    Object recv = unwrap(evaluateExpression(dyn.receiver().get(), frame));
    if (recv == null) {
      if (frame.options.strictReferences()) {
        throw new TemplateRenderException(
            "Cannot navigate property/method on null or undefined reference",
            frame.templateId,
            dyn.span(),
            InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
      }
      return EvaluationValue.definedNull();
    }
    List<EvaluationValue> args = new ArrayList<>();
    for (IrExpression argExpr : dyn.arguments()) {
      Object arg = evaluateExpression(argExpr, frame);
      args.add((arg instanceof EvaluationValue ev) ? ev : EvaluationValue.of(arg));
    }
    return frame.referenceAccess.invokeMethod(
        recv, dyn.targetName(), args, dyn.span(), frame.templateId);
  }

  private static Object evaluateGetProperty(IrGetProperty prop, InterpretedFrame frame) {
    Object recv = unwrap(evaluateExpression(prop.receiver(), frame));
    if (recv == null) {
      if (prop.nullMode() == NullAccessMode.THROW_IF_NULL || frame.options.strictReferences()) {
        throw new TemplateRenderException(
            "Cannot navigate property/method on null or undefined reference",
            frame.templateId,
            prop.span(),
            InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
      }
      return EvaluationValue.definedNull();
    }

    AccessPlan plan = prop.accessPlan();
    if (plan instanceof AccessPlan.DirectRecord rec) {
      try {
        return rec.accessor().invoke(recv);
      } catch (Exception e) {
        return frame.referenceAccess.getProperty(
            recv, prop.propertyName(), prop.span(), frame.templateId);
      }
    } else if (plan instanceof AccessPlan.DirectGetter getter) {
      try {
        return getter.getter().invoke(recv);
      } catch (Exception e) {
        return frame.referenceAccess.getProperty(
            recv, prop.propertyName(), prop.span(), frame.templateId);
      }
    } else if (plan instanceof AccessPlan.DirectField field) {
      try {
        return field.field().get(recv);
      } catch (Exception e) {
        return frame.referenceAccess.getProperty(
            recv, prop.propertyName(), prop.span(), frame.templateId);
      }
    } else if (plan instanceof AccessPlan.MapLookup mapLookup) {
      if (recv instanceof Map<?, ?> map) {
        return map.containsKey(mapLookup.keyConstant())
            ? EvaluationValue.of(map.get(mapLookup.keyConstant()))
            : EvaluationValue.undefined();
      }
      return frame.referenceAccess.getProperty(
          recv, mapLookup.keyConstant(), prop.span(), frame.templateId);
    } else if (plan instanceof AccessPlan.ExtensionCall ext) {
      try {
        return ext.method().invoke(null, recv);
      } catch (Exception e) {
        return frame.referenceAccess.getProperty(
            recv, prop.propertyName(), prop.span(), frame.templateId);
      }
    } else {
      return frame.referenceAccess.getProperty(
          recv, prop.propertyName(), prop.span(), frame.templateId);
    }
  }

  private static Object evaluateInvokeAllowedMethod(
      IrInvokeAllowedMethod inv, InterpretedFrame frame) {
    Object recv = unwrap(evaluateExpression(inv.receiver(), frame));
    if (recv == null) {
      return EvaluationValue.definedNull();
    }
    Object[] args = new Object[inv.arguments().size()];
    for (int i = 0; i < args.length; i++) {
      args[i] = unwrap(evaluateExpression(inv.arguments().get(i), frame));
    }
    try {
      return inv.targetMethod().invoke(recv, args);
    } catch (Exception e) {
      throw new TemplateRenderException(
          "Error invoking method '" + inv.methodName() + "': " + e.getMessage(),
          frame.templateId,
          inv.span(),
          InterpreterDiagnosticCodes.INVALID_METHOD,
          e);
    }
  }

  private static Object evaluateIndexGet(IrIndexGet idx, InterpretedFrame frame) {
    Object recv = unwrap(evaluateExpression(idx.receiver(), frame));
    Object indexVal = evaluateExpression(idx.index(), frame);
    EvaluationValue evalIdx =
        (indexVal instanceof EvaluationValue ev) ? ev : EvaluationValue.of(indexVal);
    return frame.referenceAccess.getIndex(recv, evalIdx, idx.span(), frame.templateId);
  }

  private static Object evaluateBinaryOp(IrBinaryOp bin, InterpretedFrame frame) {
    BinaryOpKind op = bin.op();

    // Short-circuit logical operators
    if (op == BinaryOpKind.AND) {
      Object leftVal = evaluateExpression(bin.left(), frame);
      if (!isTruthy(leftVal, frame.options.emptyCheck(), frame.options.securityPolicy())) {
        return false;
      }
      Object rightVal = evaluateExpression(bin.right(), frame);
      return isTruthy(rightVal, frame.options.emptyCheck(), frame.options.securityPolicy());
    }

    if (op == BinaryOpKind.OR) {
      Object leftVal = evaluateExpression(bin.left(), frame);
      if (isTruthy(leftVal, frame.options.emptyCheck(), frame.options.securityPolicy())) {
        return true;
      }
      Object rightVal = evaluateExpression(bin.right(), frame);
      return isTruthy(rightVal, frame.options.emptyCheck(), frame.options.securityPolicy());
    }

    Object left = unwrap(evaluateExpression(bin.left(), frame));
    Object right = unwrap(evaluateExpression(bin.right(), frame));

    // Range expression lowering creates BinaryOpKind.ADD with ArrayType
    if (bin.type() instanceof VType.ArrayType) {
      return evaluateRangeList(left, right, bin.span(), frame);
    }

    // String concatenation if either operand is String and op is ADD
    if (op == BinaryOpKind.ADD && (left instanceof String || right instanceof String)) {
      return String.valueOf(left != null ? left : "") + String.valueOf(right != null ? right : "");
    }

    return switch (op) {
      case ADD -> VtlNumericOperations.add(left, right, bin.span(), frame.templateId);
      case SUBTRACT -> VtlNumericOperations.subtract(left, right, bin.span(), frame.templateId);
      case MULTIPLY -> VtlNumericOperations.multiply(left, right, bin.span(), frame.templateId);
      case DIVIDE -> VtlNumericOperations.divide(left, right, bin.span(), frame.templateId);
      case REMAINDER -> VtlNumericOperations.remainder(left, right, bin.span(), frame.templateId);
      case EQUALS -> VtlComparisonOperations.equals(left, right, frame.options.securityPolicy());
      case NOT_EQUALS ->
          !VtlComparisonOperations.equals(left, right, frame.options.securityPolicy());
      case LESS_THAN ->
          VtlComparisonOperations.compare(
                  left, right, bin.span(), frame.templateId, frame.options.securityPolicy())
              < 0;
      case LESS_THAN_OR_EQUAL ->
          VtlComparisonOperations.compare(
                  left, right, bin.span(), frame.templateId, frame.options.securityPolicy())
              <= 0;
      case GREATER_THAN ->
          VtlComparisonOperations.compare(
                  left, right, bin.span(), frame.templateId, frame.options.securityPolicy())
              > 0;
      case GREATER_THAN_OR_EQUAL ->
          VtlComparisonOperations.compare(
                  left, right, bin.span(), frame.templateId, frame.options.securityPolicy())
              >= 0;
      default -> EvaluationValue.undefined();
    };
  }

  private static List<Integer> evaluateRangeList(
      Object left, Object right, SourceSpan span, InterpretedFrame frame) {
    if (!(left instanceof Number startNum) || !(right instanceof Number endNum)) {
      throw new TemplateRenderException(
          "Expected integer in range endpoint, but was: [" + left + ".." + right + "]",
          frame.templateId,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }
    int start = startNum.intValue();
    int end = endNum.intValue();
    int size = Math.abs(end - start) + 1;
    if (size > frame.options.limits().maxRangeSize()) {
      throw new TemplateLimitException(
          "Range size exceeds maximum limit ("
              + size
              + " > "
              + frame.options.limits().maxRangeSize()
              + ")",
          frame.templateId,
          span,
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }
    List<Integer> list = new ArrayList<>(size);
    if (start <= end) {
      for (int i = start; i <= end; i++) {
        list.add(i);
      }
    } else {
      for (int i = start; i >= end; i--) {
        list.add(i);
      }
    }
    return list;
  }

  private static Object evaluateUnaryOp(IrUnaryOp un, InterpretedFrame frame) {
    Object operand = evaluateExpression(un.operand(), frame);
    return switch (un.op()) {
      case NOT -> !isTruthy(operand, frame.options.emptyCheck(), frame.options.securityPolicy());
      case NEGATE -> VtlNumericOperations.negate(unwrap(operand), un.span(), frame.templateId);
    };
  }

  static Object unwrap(Object val) {
    if (val instanceof EvaluationValue ev) {
      return ev.asObjectOrNull();
    }
    return val;
  }

  static boolean isTruthy(Object val, boolean emptyCheck) {
    return isTruthy(val, emptyCheck, VtlSecurityPolicy.standard());
  }

  static boolean isTruthy(Object val, boolean emptyCheck, VtlSecurityPolicy securityPolicy) {
    if (val instanceof EvaluationValue ev) {
      return VtlTruthiness.isTruthy(ev, emptyCheck, securityPolicy);
    }
    return VtlTruthiness.isTruthyObject(val, emptyCheck, securityPolicy);
  }

  static Iterable<?> toIterable(Object val) {
    return toIterable(val, null, null);
  }

  static Iterable<?> toIterable(Object val, InterpretedFrame frame, SourceSpan span) {
    if (val == null) {
      return null;
    }
    if (val instanceof EvaluationValue ev) {
      if (ev.isUndefined() || ev.isNull()) {
        return null;
      }
      val = ev.value();
    }
    if (val == null) {
      return null;
    }
    if (frame != null
        && frame.options.securityPolicy() != null
        && !frame.options.securityPolicy().isClassPermitted(val.getClass())) {
      throw new TemplateSecurityException(
          "Access to class " + val.getClass().getName() + " in loop is denied by security policy",
          frame.templateId,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }
    if (val instanceof Iterable<?> iter) {
      return iter;
    }
    if (val instanceof String s && s.startsWith("VtlListLiteralExpression[")) {
      Pattern p =
          Pattern.compile(
              "Vtl(?:String|Integer|Decimal|Boolean)LiteralExpression\\[value=([^,\\]]+)");
      Matcher m = p.matcher(s);
      List<Object> list = new ArrayList<>();
      while (m.find()) {
        String raw = m.group(1);
        if (raw.matches("-?\\d+")) {
          try {
            list.add(Integer.parseInt(raw));
          } catch (NumberFormatException e) {
            list.add(raw);
          }
        } else {
          list.add(raw);
        }
      }
      return list;
    }
    if (val instanceof Map<?, ?> map) {
      return map.values();
    }
    if (val.getClass().isArray()) {
      int len = Array.getLength(val);
      List<Object> list = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        list.add(Array.get(val, i));
      }
      return list;
    }
    if (val instanceof Iterator<?> it) {
      List<Object> list = new ArrayList<>();
      while (it.hasNext()) {
        list.add(it.next());
      }
      return list;
    }
    return null;
  }
}
