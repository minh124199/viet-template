package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Reference interpreter for the Viet Template VTL AST. Defines the observable runtime semantics for
 * core VTL syntax and directives.
 */
public final class VtlInterpreter {

  private final VtlInterpreterOptions options;
  private final ReferenceAccess referenceAccess;

  public VtlInterpreter() {
    this(VtlInterpreterOptions.DEFAULT);
  }

  public VtlInterpreter(VtlInterpreterOptions options) {
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.referenceAccess = new DefaultReferenceAccess(options.securityPolicy());
  }

  public VtlInterpreterOptions options() {
    return options;
  }

  public void render(
      SourceText source, VtlTemplate template, RenderContext renderContext, TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(renderContext, "renderContext must not be null");
    render(source, template, new ExecutionContext(renderContext), output);
  }

  public void render(
      SourceText source, VtlTemplate template, ExecutionContext context, TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(template, "template must not be null");
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(output, "output must not be null");

    if (options.executionTier() == ExecutionTier.IR) {
      BitSet gobbledIndices = SpaceGobbler.computeGobbledIndices(source, options.spaceGobbling());
      VtlSemanticOptions semanticOptions =
          VtlSemanticOptions.builder()
              .profile(options.profile())
              .strictMode(options.strictReferences())
              .allowArbitraryMethods(options.profile().isArbitraryMethodsAllowed())
              .build();
      SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(template, semanticOptions);
      IrTemplate irTemplate =
          AstToIrLowerer.lower(template, source, analysis, semanticOptions, gobbledIndices);
      IrInterpreter.render(irTemplate, source, context, output, options);
      return;
    }

    CountingTemplateOutput countingOutput =
        new CountingTemplateOutput(
            output, options.limits().maxOutputCharacters(), template.templateId());
    MacroRegistry macroRegistry = new MacroRegistry();

    // 1. Discover top-level macros before execution (supporting call-before-definition)
    discoverMacros(template.children(), source, macroRegistry);

    // 2. Pre-calculate gobbled indices for LINES space gobbling mode
    BitSet gobbledIndices = SpaceGobbler.computeGobbledIndices(source, options.spaceGobbling());

    // 3. Execute template nodes
    ExecutionState state =
        new ExecutionState(
            template.templateId(),
            source,
            context,
            macroRegistry,
            countingOutput,
            gobbledIndices,
            0,
            0,
            0);

    try {
      executeNodes(template.children(), state);
    } catch (StopSignal ignored) {
      // Normal template termination via #stop
    }
  }

  public void render(
      IrTemplate template, SourceText source, RenderContext renderContext, TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(renderContext, "renderContext must not be null");
    render(template, source, new ExecutionContext(renderContext), output);
  }

  public void render(
      IrTemplate template, SourceText source, ExecutionContext context, TemplateOutput output)
      throws IOException {
    IrInterpreter.render(template, source, context, output, options);
  }

  public void render(
      VtlTemplate template, SourceText source, RenderContext renderContext, TemplateOutput output)
      throws IOException {
    render(source, template, renderContext, output);
  }

  public void interpret(
      VtlTemplate template, SourceText source, RenderContext renderContext, TemplateOutput output)
      throws IOException {
    render(source, template, renderContext, output);
  }

  private void discoverMacros(List<VtlNode> nodes, SourceText source, MacroRegistry registry) {
    for (VtlNode node : nodes) {
      if (node instanceof VtlMacroDefinitionNode macroDef) {
        registry.register(
            new MacroDefinition(
                macroDef.name(),
                macroDef.parameters(),
                macroDef.body(),
                source.templateId(),
                source));
      }
    }
  }

  private void executeNodes(List<VtlNode> nodes, ExecutionState state) throws IOException {
    for (int i = 0; i < nodes.size(); i++) {
      VtlNode node = nodes.get(i);
      VtlNode nextNode = (i + 1 < nodes.size()) ? nodes.get(i + 1) : null;
      executeNode(node, nextNode, state);
    }
  }

  private void executeNode(VtlNode node, VtlNode nextNode, ExecutionState state)
      throws IOException {
    if (node instanceof VtlTextNode textNode) {
      executeTextNode(textNode, nextNode, state);
    } else if (node instanceof VtlRawTextNode rawNode) {
      state.output.write(rawNode.innerContent(state.source));
    } else if (node instanceof VtlReferenceOutputNode refOut) {
      executeReferenceOutputNode(refOut, state);
    } else if (node instanceof VtlSetDirectiveNode setNode) {
      executeSetDirective(setNode, state);
    } else if (node instanceof VtlIfDirectiveNode ifNode) {
      executeIfDirective(ifNode, state);
    } else if (node instanceof VtlForeachDirectiveNode foreachNode) {
      executeForeachDirective(foreachNode, state);
    } else if (node instanceof VtlBreakDirectiveNode) {
      throw BreakSignal.INSTANCE;
    } else if (node instanceof VtlStopDirectiveNode stopNode) {
      if (stopNode.messageExpression().isPresent()) {
        EvaluationValue msgVal = evaluateExpression(stopNode.messageExpression().get(), state);
        throw new StopSignal(String.valueOf(msgVal.asObjectOrNull()));
      }
      throw StopSignal.INSTANCE;
    } else if (node instanceof VtlMacroDefinitionNode) {
      // Registered during discovery; no output
    } else if (node instanceof VtlDirectiveCallNode callNode) {
      executeDirectiveCall(callNode, state);
    } else if (node instanceof VtlBlockDirectiveCallNode blockCall) {
      executeBlockDirectiveCall(blockCall, state);
    } else if (node instanceof VtlDefineDirectiveNode defineNode) {
      executeDefineDirective(defineNode, state);
    } else if (node instanceof VtlIncludeDirectiveNode includeNode) {
      executeIncludeDirective(includeNode, state);
    } else if (node instanceof VtlParseDirectiveNode parseNode) {
      executeParseDirective(parseNode, state);
    } else if (node instanceof VtlEvaluateDirectiveNode evaluateNode) {
      executeEvaluateDirective(evaluateNode, state);
    } else if (node instanceof VtlErrorNode) {
      // Preserved error recovery node produces no runtime output
    }
  }

  private void executeTextNode(VtlTextNode node, VtlNode nextNode, ExecutionState state)
      throws IOException {
    SourceSpan span = node.span();
    int start = span.startOffset();
    int end = span.endOffset();

    String rawText;
    if (state.gobbledIndices == null || state.gobbledIndices.isEmpty()) {
      rawText = node.text(state.source);
    } else {
      int nextGobbled = state.gobbledIndices.nextSetBit(start);
      if (nextGobbled < 0 || nextGobbled >= end) {
        rawText = node.text(state.source);
      } else {
        String content = state.source.content();
        StringBuilder sb = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
          if (!state.gobbledIndices.get(i)) {
            sb.append(content.charAt(i));
          }
        }
        rawText = sb.toString();
      }
    }

    if (rawText.isEmpty()) {
      return;
    }

    // If immediately followed by an active reference or directive, adjust trailing backslashes
    if (rawText.endsWith("\\")) {
      if (nextNode instanceof VtlReferenceOutputNode refOut) {
        EvaluationValue rootVal = state.context.lookup(refOut.reference().rootName());
        rawText =
            VtlEscaping.adjustTrailingBackslashesBeforeActiveReference(
                rawText, rootVal.isNonNull());
      } else if (nextNode instanceof VtlDirectiveNode) {
        rawText = VtlEscaping.adjustTrailingBackslashesBeforeActiveDirective(rawText);
      }
    }

    String rendered = VtlEscaping.renderText(rawText, state.context);
    state.output.write(rendered);
  }

  private void executeReferenceOutputNode(VtlReferenceOutputNode node, ExecutionState state)
      throws IOException {
    VtlReference ref = node.reference();
    EvaluationValue val = evaluateReference(ref, state);

    if (options.strictReferences()) {
      if (val.isUndefined()) {
        throw new TemplateRenderException(
            "Variable '$" + ref.rootName() + "' has not been set",
            state.templateId,
            node.span(),
            InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
      }
      if (val.isNull()) {
        if (ref.isQuiet()) {
          return;
        }
        throw new TemplateRenderException(
            "Reference '$" + ref.rootName() + "' evaluated to null when attempting to render",
            state.templateId,
            node.span(),
            InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
      }
      renderValue(val.value(), state);
      return;
    }

    if (ref.isQuiet()) {
      if (val.isNonNull()) {
        renderValue(val.value(), state);
      }
      return;
    }

    if (val.isUndefined() || val.isNull()) {
      // Render literal source representation (e.g. "$var" or "${var}")
      state.output.write(
          state.source.slice(node.span().startOffset(), node.span().endOffset()).toString());
    } else {
      renderValue(val.value(), state);
    }
  }

  private void renderValue(Object value, ExecutionState state) throws IOException {
    if (value == null) {
      return;
    }
    if (value instanceof RenderableBlock block) {
      ExecutionState subState = state.withContext(block.capturedContext());
      if (block.capturedSource() != null) {
        BitSet blockGobbled =
            SpaceGobbler.computeGobbledIndices(block.capturedSource(), options.spaceGobbling());
        subState =
            subState.withTemplate(block.capturedTemplateId(), block.capturedSource(), blockGobbled);
      }
      try {
        executeNodes(block.body(), subState);
      } catch (StopSignal ignored) {
      }
      return;
    } else if (value instanceof CharSequence cs) {
      state.output.write(cs);
    } else {
      state.output.write(String.valueOf(value));
    }
  }

  private void executeSetDirective(VtlSetDirectiveNode node, ExecutionState state) {
    EvaluationValue rhsValue = evaluateExpression(node.value(), state);

    // Check null RHS behavior (Velocity compatibility: skip assignment if null not allowed)
    if ((rhsValue.isUndefined() || rhsValue.isNull()) && !options.setNullAllowed()) {
      return;
    }

    VtlAssignmentTarget target = node.target();
    if (target instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
      VtlReference ref = refTarget.reference();
      if (ref.accessSteps().isEmpty()) {
        // Root variable assignment
        state.context.set(ref.rootName(), rhsValue);
      } else {
        // Navigated assignment: resolve target object then assign property or index
        EvaluationValue current = state.context.lookup(ref.rootName());
        List<VtlAccessStep> steps = ref.accessSteps();
        for (int i = 0; i < steps.size() - 1; i++) {
          VtlAccessStep step = steps.get(i);
          current = evaluateAccessStep(current, step, state);
          if (current.isUndefined() || current.isNull()) {
            throw new TemplateRenderException(
                "Cannot set property on null or undefined parent",
                state.templateId,
                step.span(),
                InterpreterDiagnosticCodes.SYNTAX_ERROR);
          }
        }
        VtlAccessStep lastStep = steps.get(steps.size() - 1);
        if (lastStep instanceof VtlAccessStep.PropertyAccess propAccess) {
          referenceAccess.setProperty(
              current.value(),
              propAccess.propertyName(),
              rhsValue,
              lastStep.span(),
              state.templateId);
        } else if (lastStep instanceof VtlAccessStep.IndexAccess idxAccess) {
          EvaluationValue idxVal = evaluateExpression(idxAccess.indexExpression(), state);
          referenceAccess.setIndex(
              current.value(), idxVal, rhsValue, lastStep.span(), state.templateId);
        } else {
          throw new TemplateRenderException(
              "Invalid assignment target: " + lastStep,
              state.templateId,
              lastStep.span(),
              InterpreterDiagnosticCodes.SYNTAX_ERROR);
        }
      }
    }
  }

  private void executeIfDirective(VtlIfDirectiveNode node, ExecutionState state)
      throws IOException {
    for (VtlIfBranch branch : node.branches()) {
      EvaluationValue condVal = evaluateExpression(branch.condition(), state);
      if (VtlTruthiness.isTruthy(condVal, options.emptyCheck())) {
        executeNodes(branch.body(), state);
        return;
      }
    }
    if (node.elseBody().isPresent()) {
      executeNodes(node.elseBody().get(), state);
    }
  }

  private void executeForeachDirective(VtlForeachDirectiveNode node, ExecutionState state)
      throws IOException {
    EvaluationValue iterVal = evaluateExpression(node.iterable(), state);
    Iterable<?> iterable = toIterable(iterVal);

    if (iterable == null) {
      if (node.elseBody().isPresent()) {
        executeNodes(node.elseBody().get(), state);
      }
      return;
    }

    Iterator<?> iterator = iterable.iterator();
    if (!iterator.hasNext()) {
      if (node.elseBody().isPresent()) {
        executeNodes(node.elseBody().get(), state);
      }
      return;
    }

    String loopVar = node.loopVariable().rootName();
    int index = 0;
    int limit = options.limits().maxLoopIterations();

    // Look up parent $foreach metadata if nested
    ForeachMetadata parentMeta = null;
    EvaluationValue existingMeta = state.context.lookup("foreach");
    if (existingMeta.isNonNull() && existingMeta.value() instanceof ForeachMetadata fm) {
      parentMeta = fm;
    }

    state.context.pushForeachScope(loopVar, EvaluationValue.undefined(), parentMeta);

    try {
      while (iterator.hasNext()) {
        if (index >= limit) {
          throw new TemplateLimitException(
              "Exceeded maximum foreach iterations: " + limit,
              state.templateId,
              node.span(),
              InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
        }

        Object item = iterator.next();
        boolean hasNext = iterator.hasNext();
        boolean first = (index == 0);
        boolean last = !hasNext;
        int count = index + 1;

        ForeachMetadata meta = new ForeachMetadata(index, count, first, last, hasNext, parentMeta);
        state.context.updateLoopVariable(loopVar, EvaluationValue.of(item), meta);

        try {
          executeNodes(node.body(), state);
        } catch (BreakSignal bs) {
          break;
        }

        index++;
      }
    } finally {
      state.context.popScope();
    }
  }

  private void executeDirectiveCall(VtlDirectiveCallNode node, ExecutionState state)
      throws IOException {
    MacroDefinition macro = state.macroRegistry.lookup(node.name());
    if (macro == null) {
      if (options.strictReferences()) {
        throw new TemplateRenderException(
            "Unknown macro or directive: #" + node.name(),
            state.templateId,
            node.span(),
            InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return;
    }
    invokeMacro(macro, node.arguments(), null, node.span(), state);
  }

  private void executeBlockDirectiveCall(VtlBlockDirectiveCallNode node, ExecutionState state)
      throws IOException {
    MacroDefinition macro = state.macroRegistry.lookup(node.name());
    if (macro == null) {
      if (options.strictReferences()) {
        throw new TemplateRenderException(
            "Unknown block macro: #" + node.name(),
            state.templateId,
            node.span(),
            InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return;
    }
    RenderableBlock bodyContent =
        new RenderableBlock(node.body(), state.context, state.source, state.templateId);
    invokeMacro(macro, node.arguments(), bodyContent, node.span(), state);
  }

  private void invokeMacro(
      MacroDefinition macro,
      List<VtlExpression> argumentExprs,
      RenderableBlock bodyContent,
      SourceSpan span,
      ExecutionState state)
      throws IOException {
    if (state.macroDepth >= options.limits().maxMacroDepth()) {
      throw new TemplateLimitException(
          "Exceeded maximum macro recursion depth: " + options.limits().maxMacroDepth(),
          state.templateId,
          span,
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }

    // Evaluate argument expressions once per invocation
    List<EvaluationValue> argValues = new ArrayList<>();
    for (VtlExpression argExpr : argumentExprs) {
      argValues.add(evaluateExpression(argExpr, state));
    }

    Map<String, EvaluationValue> bindings = new HashMap<>();
    List<VtlMacroParameter> params = macro.parameters();

    for (int i = 0; i < params.size(); i++) {
      VtlMacroParameter param = params.get(i);
      String paramName = param.name();
      if (i < argValues.size()) {
        bindings.put(paramName, argValues.get(i));
      } else if (param.defaultValue().isPresent()) {
        // Evaluate default expression lazily
        EvaluationValue defVal = evaluateExpression(param.defaultValue().get(), state);
        bindings.put(paramName, defVal);
      } else {
        bindings.put(paramName, EvaluationValue.undefined());
      }
    }

    if (bodyContent != null) {
      bindings.put("bodyContent", EvaluationValue.of(bodyContent));
    }

    state.context.pushScope(bindings, false);
    try {
      ExecutionState subState = state.withMacroDepth(state.macroDepth + 1);
      if (macro.sourceText() != null) {
        BitSet macroGobbled =
            SpaceGobbler.computeGobbledIndices(macro.sourceText(), options.spaceGobbling());
        subState =
            subState.withTemplate(macro.sourceTemplateId(), macro.sourceText(), macroGobbled);
      }
      executeNodes(macro.body(), subState);
    } finally {
      state.context.popScope();
    }
  }

  private void executeDefineDirective(VtlDefineDirectiveNode node, ExecutionState state) {
    RenderableBlock block =
        new RenderableBlock(node.body(), state.context, state.source, state.templateId);
    state.context.set(node.targetReference().rootName(), EvaluationValue.of(block));
  }

  private void executeIncludeDirective(VtlIncludeDirectiveNode node, ExecutionState state)
      throws IOException {
    for (VtlExpression argExpr : node.arguments()) {
      EvaluationValue val = evaluateExpression(argExpr, state);
      String path = String.valueOf(val.asObjectOrNull());
      Optional<TemplateResource> res = options.resourceResolver().resolve(state.templateId, path);
      if (res.isEmpty()) {
        throw new TemplateResourceException(
            "Resource not found for #include: " + path,
            state.templateId,
            argExpr.span(),
            InterpreterDiagnosticCodes.RESOURCE_NOT_FOUND);
      }
      state.output.write(res.get().content());
    }
  }

  private void executeParseDirective(VtlParseDirectiveNode node, ExecutionState state)
      throws IOException {
    if (state.parseDepth >= options.limits().maxParseDepth()) {
      throw new TemplateLimitException(
          "Exceeded maximum #parse depth (maxParseDepth): " + options.limits().maxParseDepth(),
          state.templateId,
          node.span(),
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }

    EvaluationValue val = evaluateExpression(node.templateExpression(), state);
    String path = String.valueOf(val.asObjectOrNull());
    Optional<TemplateResource> res = options.resourceResolver().resolve(state.templateId, path);
    if (res.isEmpty()) {
      throw new TemplateResourceException(
          "Resource not found for #parse: " + path,
          state.templateId,
          node.span(),
          InterpreterDiagnosticCodes.RESOURCE_NOT_FOUND);
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

    // Discover macros defined in the parsed template
    discoverMacros(parseResult.template().children(), subSource, state.macroRegistry);

    BitSet subGobbled = SpaceGobbler.computeGobbledIndices(subSource, options.spaceGobbling());

    ExecutionState subState =
        state
            .withParseDepth(state.parseDepth + 1)
            .withTemplate(res.get().templateId(), subSource, subGobbled);

    executeNodes(parseResult.template().children(), subState);
  }

  private void executeEvaluateDirective(VtlEvaluateDirectiveNode node, ExecutionState state)
      throws IOException {
    if (!options.profile().isEvaluateAllowed()) {
      throw new TemplateSecurityException(
          "#evaluate is disabled in profile " + options.profile(),
          state.templateId,
          node.span(),
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    if (state.evaluateDepth >= options.limits().maxEvaluateDepth()) {
      throw new TemplateLimitException(
          "Exceeded maximum #evaluate depth: " + options.limits().maxEvaluateDepth(),
          state.templateId,
          node.span(),
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }

    EvaluationValue val = evaluateExpression(node.expression(), state);
    String dynamicSource = String.valueOf(val.asObjectOrNull());
    if (dynamicSource.length() > options.limits().maxDynamicSourceLength()) {
      throw new TemplateLimitException(
          "Dynamic template source exceeds maximum length ("
              + dynamicSource.length()
              + " > "
              + options.limits().maxDynamicSourceLength()
              + ")",
          state.templateId,
          node.span(),
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }

    TemplateId evalId =
        TemplateId.of(state.templateId.value() + "#evaluate[" + state.evaluateDepth + "]");
    SourceText subSource = SourceText.of(evalId.value(), dynamicSource);
    VtlParseResult parseResult = VtlParser.parse(subSource);
    if (parseResult.hasErrors()) {
      throw new TemplateRenderException(
          "Syntax error in dynamically evaluated template",
          evalId,
          parseResult.template().span(),
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    discoverMacros(parseResult.template().children(), subSource, state.macroRegistry);
    BitSet subGobbled = SpaceGobbler.computeGobbledIndices(subSource, options.spaceGobbling());

    ExecutionState subState =
        state
            .withEvaluateDepth(state.evaluateDepth + 1)
            .withTemplate(evalId, subSource, subGobbled);

    executeNodes(parseResult.template().children(), subState);
  }

  public EvaluationValue evaluateExpression(VtlExpression expr, ExecutionState state) {
    if (expr instanceof VtlIntegerLiteralExpression intLit) {
      java.math.BigInteger val = intLit.value();
      if (val.compareTo(java.math.BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
          && val.compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
        return EvaluationValue.of(val.intValue());
      }
      if (val.compareTo(java.math.BigInteger.valueOf(Long.MIN_VALUE)) >= 0
          && val.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
        return EvaluationValue.of(val.longValue());
      }
      return EvaluationValue.of(val);
    }
    if (expr instanceof VtlDecimalLiteralExpression decLit) {
      return EvaluationValue.of(decLit.value());
    }
    if (expr instanceof VtlBooleanLiteralExpression boolLit) {
      return EvaluationValue.of(boolLit.value());
    }
    if (expr instanceof VtlStringLiteralExpression strLit) {
      return EvaluationValue.of(strLit.value());
    }
    if (expr instanceof VtlNullLiteralExpression nullLit) {
      if (!options.allowBareNullLiteral()) {
        throw new TemplateRenderException(
            "Bare 'null' literal is disabled in this profile",
            state.templateId,
            nullLit.span(),
            InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return EvaluationValue.definedNull();
    }
    if (expr instanceof VtlInterpolatedStringExpression interpStr) {
      return evaluateInterpolatedString(interpStr, state);
    }
    if (expr instanceof VtlReferenceExpression refExpr) {
      return evaluateReference(refExpr.reference(), state);
    }
    if (expr instanceof VtlListLiteralExpression listLit) {
      List<Object> list = new ArrayList<>();
      for (VtlExpression elem : listLit.elements()) {
        list.add(evaluateExpression(elem, state).asObjectOrNull());
      }
      return EvaluationValue.of(list);
    }
    if (expr instanceof VtlMapLiteralExpression mapLit) {
      Map<Object, Object> map = new LinkedHashMap<>();
      for (VtlMapEntry entry : mapLit.entries()) {
        Object key = evaluateExpression(entry.key(), state).asObjectOrNull();
        Object val = evaluateExpression(entry.value(), state).asObjectOrNull();
        map.put(key, val);
      }
      return EvaluationValue.of(map);
    }
    if (expr instanceof VtlRangeExpression rangeExpr) {
      return evaluateRange(rangeExpr, state);
    }
    if (expr instanceof VtlUnaryExpression unary) {
      return evaluateUnary(unary, state);
    }
    if (expr instanceof VtlBinaryExpression binary) {
      return evaluateBinary(binary, state);
    }
    if (expr instanceof VtlGroupedExpression grouped) {
      return evaluateExpression(grouped.expression(), state);
    }
    if (expr instanceof VtlErrorExpression) {
      return EvaluationValue.undefined();
    }

    return EvaluationValue.undefined();
  }

  private EvaluationValue evaluateInterpolatedString(
      VtlInterpolatedStringExpression interpStr, ExecutionState state) {
    StringBuilder sb = new StringBuilder();
    for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : interpStr.parts()) {
      if (part instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart tp) {
        sb.append(tp.text());
      } else if (part
          instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
        EvaluationValue val = evaluateReference(rp.reference(), state);
        if (rp.reference().isQuiet()) {
          if (val.isDefined() && !val.isNull()) {
            sb.append(val.value());
          }
        } else if (val.isDefined() && !val.isNull()) {
          sb.append(val.value());
        } else {
          // Undefined/null in interpolated string renders literal
          sb.append(
              state.source.slice(
                  rp.reference().span().startOffset(), rp.reference().span().endOffset()));
        }
      }
    }
    return EvaluationValue.of(sb.toString());
  }

  private EvaluationValue evaluateRange(VtlRangeExpression rangeExpr, ExecutionState state) {
    EvaluationValue startVal = evaluateExpression(rangeExpr.start(), state);
    EvaluationValue endVal = evaluateExpression(rangeExpr.end(), state);

    int start = toInt(startVal, rangeExpr.start().span(), state.templateId);
    int end = toInt(endVal, rangeExpr.end().span(), state.templateId);

    int size = Math.abs(end - start) + 1;
    if (size > options.limits().maxRangeSize()) {
      throw new TemplateLimitException(
          "Range size exceeds maximum limit ("
              + size
              + " > "
              + options.limits().maxRangeSize()
              + ")",
          state.templateId,
          rangeExpr.span(),
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
    return EvaluationValue.of(list);
  }

  private EvaluationValue evaluateUnary(VtlUnaryExpression unary, ExecutionState state) {
    EvaluationValue operandVal = evaluateExpression(unary.operand(), state);
    return switch (unary.operator()) {
      case NOT -> EvaluationValue.of(!VtlTruthiness.isTruthy(operandVal, options.emptyCheck()));
      case MINUS ->
          EvaluationValue.of(
              VtlNumericOperations.negate(operandVal.value(), unary.span(), state.templateId));
      case PLUS -> operandVal;
    };
  }

  private EvaluationValue evaluateBinary(VtlBinaryExpression binary, ExecutionState state) {
    VtlBinaryOperator op = binary.operator();

    // Short-circuiting operators
    if (op == VtlBinaryOperator.LOGICAL_AND) {
      EvaluationValue left = evaluateExpression(binary.left(), state);
      if (!VtlTruthiness.isTruthy(left, options.emptyCheck())) {
        return EvaluationValue.of(false);
      }
      EvaluationValue right = evaluateExpression(binary.right(), state);
      return EvaluationValue.of(VtlTruthiness.isTruthy(right, options.emptyCheck()));
    }

    if (op == VtlBinaryOperator.LOGICAL_OR) {
      EvaluationValue left = evaluateExpression(binary.left(), state);
      if (VtlTruthiness.isTruthy(left, options.emptyCheck())) {
        return EvaluationValue.of(true);
      }
      EvaluationValue right = evaluateExpression(binary.right(), state);
      return EvaluationValue.of(VtlTruthiness.isTruthy(right, options.emptyCheck()));
    }

    EvaluationValue left = evaluateExpression(binary.left(), state);
    EvaluationValue right = evaluateExpression(binary.right(), state);

    Object l = left.asObjectOrNull();
    Object r = right.asObjectOrNull();

    return switch (op) {
      case ADD ->
          EvaluationValue.of(VtlNumericOperations.add(l, r, binary.span(), state.templateId));
      case SUBTRACT ->
          EvaluationValue.of(VtlNumericOperations.subtract(l, r, binary.span(), state.templateId));
      case MULTIPLY ->
          EvaluationValue.of(VtlNumericOperations.multiply(l, r, binary.span(), state.templateId));
      case DIVIDE ->
          EvaluationValue.of(VtlNumericOperations.divide(l, r, binary.span(), state.templateId));
      case MODULO ->
          EvaluationValue.of(VtlNumericOperations.remainder(l, r, binary.span(), state.templateId));
      case EQUAL -> EvaluationValue.of(VtlComparisonOperations.equals(l, r));
      case NOT_EQUAL -> EvaluationValue.of(!VtlComparisonOperations.equals(l, r));
      case LESS_THAN ->
          EvaluationValue.of(
              VtlComparisonOperations.compare(l, r, binary.span(), state.templateId) < 0);
      case LESS_THAN_OR_EQUAL ->
          EvaluationValue.of(
              VtlComparisonOperations.compare(l, r, binary.span(), state.templateId) <= 0);
      case GREATER_THAN ->
          EvaluationValue.of(
              VtlComparisonOperations.compare(l, r, binary.span(), state.templateId) > 0);
      case GREATER_THAN_OR_EQUAL ->
          EvaluationValue.of(
              VtlComparisonOperations.compare(l, r, binary.span(), state.templateId) >= 0);
      case LOGICAL_AND, LOGICAL_OR -> throw new IllegalStateException("Handled above");
    };
  }

  public EvaluationValue evaluateReference(VtlReference ref, ExecutionState state) {
    EvaluationValue current = state.context.lookup(ref.rootName());

    for (VtlAccessStep step : ref.accessSteps()) {
      if (current.isUndefined() || current.isNull()) {
        if (options.strictReferences()) {
          throw new TemplateRenderException(
              "Cannot navigate property/method on null or undefined reference",
              state.templateId,
              step.span(),
              InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
        }
        return EvaluationValue.undefined();
      }
      Object targetObj = current.value();
      current = evaluateAccessStep(current, step, state);
      if (options.strictReferences() && current.isUndefined()) {
        if (step instanceof VtlAccessStep.PropertyAccess prop) {
          throw new TemplateRenderException(
              "Object '"
                  + (targetObj != null ? targetObj.getClass().getName() : "null")
                  + "' does not contain property '"
                  + prop.propertyName()
                  + "'",
              state.templateId,
              step.span(),
              InterpreterDiagnosticCodes.INVALID_PROPERTY);
        }
        if (step instanceof VtlAccessStep.MethodCall call) {
          throw new TemplateRenderException(
              "Object '"
                  + (targetObj != null ? targetObj.getClass().getName() : "null")
                  + "' does not contain method '"
                  + call.methodName()
                  + "'",
              state.templateId,
              step.span(),
              InterpreterDiagnosticCodes.INVALID_METHOD);
        }
      }
    }

    // Alternate value evaluation (${name|'default'}): Velocity always checks emptiness for default
    // values
    if (ref.alternateValue().isPresent()) {
      if (!VtlTruthiness.isTruthy(current, true)) {
        // Lazily evaluate alternate expression
        return evaluateExpression(ref.alternateValue().get(), state);
      }
    }

    return current;
  }

  private EvaluationValue evaluateAccessStep(
      EvaluationValue target, VtlAccessStep step, ExecutionState state) {
    Object obj = target.value();
    if (step instanceof VtlAccessStep.PropertyAccess prop) {
      return referenceAccess.getProperty(obj, prop.propertyName(), prop.span(), state.templateId);
    }
    if (step instanceof VtlAccessStep.MethodCall call) {
      List<EvaluationValue> args = new ArrayList<>();
      for (VtlExpression argExpr : call.arguments()) {
        args.add(evaluateExpression(argExpr, state));
      }
      return referenceAccess.invokeMethod(
          obj, call.methodName(), args, call.span(), state.templateId);
    }
    if (step instanceof VtlAccessStep.IndexAccess idx) {
      EvaluationValue idxVal = evaluateExpression(idx.indexExpression(), state);
      return referenceAccess.getIndex(obj, idxVal, idx.span(), state.templateId);
    }
    return EvaluationValue.undefined();
  }

  private int toInt(EvaluationValue val, SourceSpan span, TemplateId id) {
    if (val.isDefined() && val.value() instanceof Number n) {
      return n.intValue();
    }
    throw new TemplateRenderException(
        "Expected integer in range endpoint, but was: " + val,
        id,
        span,
        InterpreterDiagnosticCodes.SYNTAX_ERROR);
  }

  private Iterable<?> toIterable(EvaluationValue val) {
    if (val.isUndefined() || val.isNull()) {
      return null;
    }
    Object obj = val.value();
    if (obj instanceof Iterable<?> iter) {
      return iter;
    }
    if (obj instanceof Map<?, ?> map) {
      return map.values();
    }
    if (obj.getClass().isArray()) {
      int len = Array.getLength(obj);
      List<Object> list = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        list.add(Array.get(obj, i));
      }
      return list;
    }
    if (obj instanceof Iterator<?> it) {
      List<Object> list = new ArrayList<>();
      while (it.hasNext()) {
        list.add(it.next());
      }
      return list;
    }
    return null;
  }

  private static final class ExecutionState {
    final TemplateId templateId;
    final SourceText source;
    final ExecutionContext context;
    final MacroRegistry macroRegistry;
    final TemplateOutput output;
    final BitSet gobbledIndices;
    final int macroDepth;
    final int parseDepth;
    final int evaluateDepth;

    ExecutionState(
        TemplateId templateId,
        SourceText source,
        ExecutionContext context,
        MacroRegistry macroRegistry,
        TemplateOutput output,
        BitSet gobbledIndices,
        int macroDepth,
        int parseDepth,
        int evaluateDepth) {
      this.templateId = templateId;
      this.source = source;
      this.context = context;
      this.macroRegistry = macroRegistry;
      this.output = output;
      this.gobbledIndices = gobbledIndices;
      this.macroDepth = macroDepth;
      this.parseDepth = parseDepth;
      this.evaluateDepth = evaluateDepth;
    }

    ExecutionState withContext(ExecutionContext newContext) {
      return new ExecutionState(
          templateId,
          source,
          newContext,
          macroRegistry,
          output,
          gobbledIndices,
          macroDepth,
          parseDepth,
          evaluateDepth);
    }

    ExecutionState withMacroDepth(int newMacroDepth) {
      return new ExecutionState(
          templateId,
          source,
          context,
          macroRegistry,
          output,
          gobbledIndices,
          newMacroDepth,
          parseDepth,
          evaluateDepth);
    }

    ExecutionState withParseDepth(int newParseDepth) {
      return new ExecutionState(
          templateId,
          source,
          context,
          macroRegistry,
          output,
          gobbledIndices,
          macroDepth,
          newParseDepth,
          evaluateDepth);
    }

    ExecutionState withEvaluateDepth(int newEvaluateDepth) {
      return new ExecutionState(
          templateId,
          source,
          context,
          macroRegistry,
          output,
          gobbledIndices,
          macroDepth,
          parseDepth,
          newEvaluateDepth);
    }

    ExecutionState withTemplate(TemplateId newId, SourceText newSource, BitSet newGobbled) {
      return new ExecutionState(
          newId,
          newSource,
          context,
          macroRegistry,
          output,
          newGobbled,
          macroDepth,
          parseDepth,
          evaluateDepth);
    }
  }
}
