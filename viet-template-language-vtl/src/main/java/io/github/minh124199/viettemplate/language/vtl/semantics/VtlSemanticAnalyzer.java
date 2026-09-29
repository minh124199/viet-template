package io.github.minh124199.viettemplate.language.vtl.semantics;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
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
import io.github.minh124199.viettemplate.language.vtl.ast.VtlListLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMacroDefinitionNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMacroParameter;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMapLiteralExpression;
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
import io.github.minh124199.viettemplate.language.vtl.ast.VtlUnaryExpression;
import io.github.minh124199.viettemplate.language.vtl.internal.ast.VtlUnaryOperator;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.model.ModelParameter;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.LevenshteinDistance;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MemberResolution;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MemberResolver;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MethodResolution;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MethodResolver;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.scope.ScopeKind;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.scope.Symbol;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.scope.SymbolTable;
import io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.PrimitiveKind;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Semantic analyzer performing symbol resolution, type checking, and capability calculation. */
public final class VtlSemanticAnalyzer {

  private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
  private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
  private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
  private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);

  private final VtlSemanticOptions options;
  private final SymbolTable symbolTable;
  private final List<Diagnostic> diagnostics = new ArrayList<>();
  private final TemplateCapabilities.Builder capabilitiesBuilder = TemplateCapabilities.builder();
  private final Map<VtlExpression, VType> expressionTypes = new LinkedHashMap<>();
  private final Map<VtlNode, VType> nodeTypes = new LinkedHashMap<>();
  private final Map<VtlAccessStep.PropertyAccess, MemberResolution> memberResolutions =
      new LinkedHashMap<>();
  private final Map<VtlAccessStep.MethodCall, MethodResolution> methodResolutions =
      new LinkedHashMap<>();

  private VtlSemanticAnalyzer(VtlSemanticOptions options) {
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.symbolTable = new SymbolTable();
  }

  public static SemanticAnalysisResult analyze(VtlTemplate template, VtlSemanticOptions options) {
    Objects.requireNonNull(template, "template must not be null");
    Objects.requireNonNull(options, "options must not be null");

    VtlSemanticAnalyzer analyzer = new VtlSemanticAnalyzer(options);
    return analyzer.run(template);
  }

  private SemanticAnalysisResult run(VtlTemplate template) {
    // 1. Seed root model scope
    for (ModelParameter param : options.modelSchema().parameters().values()) {
      symbolTable.defineInRoot(Symbol.rootModel(param.name(), param.type()));
    }

    // 2. Traverse template nodes
    for (VtlNode child : template.children()) {
      analyzeNode(child);
    }

    // 3. Build capabilities and result
    if (diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
      capabilitiesBuilder.setHasErrors(true);
    }
    TemplateCapabilities capabilities = capabilitiesBuilder.build();
    return new SemanticAnalysisResult(
        template,
        diagnostics,
        capabilities,
        symbolTable,
        expressionTypes,
        nodeTypes,
        memberResolutions,
        methodResolutions);
  }

  private void analyzeNode(VtlNode node) {
    if (node instanceof VtlRawTextNode) {
      return;
    }

    if (node instanceof VtlReferenceOutputNode refOut) {
      VType refType = analyzeReference(refOut.reference());
      nodeTypes.put(refOut, refType);
      return;
    }

    if (node instanceof VtlSetDirectiveNode set) {
      analyzeSet(set);
      return;
    }

    if (node instanceof VtlIfDirectiveNode ifNode) {
      analyzeIf(ifNode);
      return;
    }

    if (node instanceof VtlForeachDirectiveNode foreach) {
      analyzeForeach(foreach);
      return;
    }

    if (node instanceof VtlIncludeDirectiveNode include) {
      analyzeInclude(include);
      return;
    }

    if (node instanceof VtlParseDirectiveNode parse) {
      analyzeParse(parse);
      return;
    }

    if (node instanceof VtlEvaluateDirectiveNode eval) {
      analyzeEvaluate(eval);
      return;
    }

    if (node instanceof VtlMacroDefinitionNode macro) {
      analyzeMacro(macro);
      return;
    }

    if (node instanceof VtlDefineDirectiveNode define) {
      analyzeDefine(define);
      return;
    }

    if (node instanceof VtlDirectiveCallNode call) {
      for (VtlExpression arg : call.arguments()) {
        analyzeExpression(arg);
      }
      return;
    }

    if (node instanceof VtlBlockDirectiveCallNode blockCall) {
      for (VtlExpression arg : blockCall.arguments()) {
        analyzeExpression(arg);
      }
      for (VtlNode bodyChild : blockCall.body()) {
        analyzeNode(bodyChild);
      }
      return;
    }

    if (node instanceof VtlBreakDirectiveNode || node instanceof VtlStopDirectiveNode) {
      return;
    }
  }

  private void analyzeSet(VtlSetDirectiveNode set) {
    VType rhsType = analyzeExpression(set.value());
    VtlAssignmentTarget target = set.target();

    if (target instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
      VtlReference ref = refTarget.reference();
      if (ref.steps().isEmpty()) {
        String varName = ref.rootName();
        // Check root model immutability
        if (symbolTable.rootScope().resolveCurrent(varName).isPresent()
            && options.typeCheckingMode().isEnabled()) {
          reportStrictIssue(
              VtlSemanticDiagnosticCodes.INVALID_ASSIGNMENT,
              "Cannot mutate declared root model parameter '$" + varName + "'",
              set.span());
        }
        symbolTable.define(Symbol.local(varName, rhsType, set.span()));
      } else {
        analyzeReference(ref);
        capabilitiesBuilder.setRequiresDynamicMemberResolution(true);
      }
    }
  }

  private void analyzeIf(VtlIfDirectiveNode ifNode) {
    for (VtlIfBranch branch : ifNode.branches()) {
      analyzeExpression(branch.condition());
      symbolTable.enterScope(ScopeKind.LOCAL);
      for (String varName : extractNotNullVariables(branch.condition())) {
        refineNonNull(varName);
      }
      for (VtlNode child : branch.body()) {
        analyzeNode(child);
      }
      symbolTable.exitScope();
    }
    if (ifNode.elseBody().isPresent()) {
      symbolTable.enterScope(ScopeKind.LOCAL);
      if (ifNode.branches().size() == 1) {
        for (String varName : extractNullVariables(ifNode.branches().get(0).condition())) {
          refineNonNull(varName);
        }
      }
      for (VtlNode child : ifNode.elseBody().get()) {
        analyzeNode(child);
      }
      symbolTable.exitScope();
    }
  }

  private void analyzeForeach(VtlForeachDirectiveNode foreach) {
    VType iterableType = analyzeExpression(foreach.iterable());

    if (!VTypes.isIterable(iterableType)
        && !(iterableType instanceof VType.DynamicType)
        && !(iterableType instanceof VType.ErrorType)
        && !(iterableType instanceof VType.NullType)) {
      diagnostics.add(
          Diagnostic.error(
              VtlSemanticDiagnosticCodes.INVALID_ITERABLE,
              "Expression in #foreach is not iterable: " + iterableType.typeName(),
              foreach.iterable().span()));
    }

    VType itemType = VTypes.elementType(iterableType);
    symbolTable.enterScope(ScopeKind.LOOP);
    symbolTable.define(
        Symbol.loopVariable(
            foreach.loopVariable().rootName(), itemType, foreach.loopVariable().span()));
    symbolTable.define(
        Symbol.loopMetadata(
            "foreach",
            VType.ClassType.of(ForeachMetadata.class, Nullability.NON_NULL),
            foreach.span()));

    for (VtlNode child : foreach.body()) {
      analyzeNode(child);
    }
    symbolTable.exitScope();

    if (foreach.elseBody().isPresent()) {
      for (VtlNode child : foreach.elseBody().get()) {
        analyzeNode(child);
      }
    }
  }

  private void analyzeInclude(VtlIncludeDirectiveNode include) {
    for (VtlExpression arg : include.arguments()) {
      if (!(arg instanceof VtlStringLiteralExpression)) {
        capabilitiesBuilder.setRequiresDynamicIncludeParse(true);
      }
      analyzeExpression(arg);
    }
  }

  private void analyzeParse(VtlParseDirectiveNode parse) {
    if (!(parse.templateExpression() instanceof VtlStringLiteralExpression)) {
      capabilitiesBuilder.setRequiresDynamicIncludeParse(true);
    }
    analyzeExpression(parse.templateExpression());
  }

  private void analyzeEvaluate(VtlEvaluateDirectiveNode eval) {
    capabilitiesBuilder.setRequiresRuntimeEvaluation(true);
    if (!options.profile().isEvaluateAllowed()) {
      diagnostics.add(
          Diagnostic.error(
              VtlSemanticDiagnosticCodes.SECURITY_DENIED,
              "#evaluate directive is not permitted in profile " + options.profile().name(),
              eval.span()));
    }
    analyzeExpression(eval.expression());
  }

  private void analyzeMacro(VtlMacroDefinitionNode macro) {
    symbolTable.enterScope(ScopeKind.MACRO);
    for (VtlMacroParameter param : macro.parameters()) {
      if (param.defaultValue().isPresent()) {
        analyzeExpression(param.defaultValue().get());
      }
      symbolTable.define(Symbol.macroParameter(param.name(), VTypes.DYNAMIC, param.span()));
    }
    symbolTable.define(Symbol.local("bodyContent", VTypes.STRING, macro.span()));

    for (VtlNode child : macro.body()) {
      analyzeNode(child);
    }
    symbolTable.exitScope();
  }

  private void analyzeDefine(VtlDefineDirectiveNode define) {
    symbolTable.define(
        Symbol.local(define.targetReference().rootName(), VTypes.STRING, define.span()));
    symbolTable.enterScope(ScopeKind.LOCAL);
    for (VtlNode child : define.body()) {
      analyzeNode(child);
    }
    symbolTable.exitScope();
  }

  public VType analyzeExpression(VtlExpression expr) {
    Objects.requireNonNull(expr, "expr must not be null");

    if (expr instanceof VtlReferenceExpression refExpr) {
      VType t = analyzeReference(refExpr.reference());
      expressionTypes.put(refExpr, t);
      return t;
    }

    if (expr instanceof VtlStringLiteralExpression) {
      expressionTypes.put(expr, VTypes.STRING);
      return VTypes.STRING;
    }

    if (expr instanceof VtlIntegerLiteralExpression intLit) {
      BigInteger val = intLit.value();
      VType t;
      if (val.compareTo(INT_MAX) <= 0 && val.compareTo(INT_MIN) >= 0) {
        t = VTypes.INT;
      } else if (val.compareTo(LONG_MAX) <= 0 && val.compareTo(LONG_MIN) >= 0) {
        t = VTypes.LONG;
      } else {
        t = VTypes.BIG_INTEGER;
      }
      expressionTypes.put(expr, t);
      return t;
    }

    if (expr instanceof VtlDecimalLiteralExpression) {
      expressionTypes.put(expr, VTypes.DOUBLE);
      return VTypes.DOUBLE;
    }

    if (expr instanceof VtlBooleanLiteralExpression) {
      expressionTypes.put(expr, VTypes.BOOLEAN);
      return VTypes.BOOLEAN;
    }

    if (expr instanceof VtlNullLiteralExpression) {
      expressionTypes.put(expr, VTypes.NULL);
      return VTypes.NULL;
    }

    if (expr instanceof VtlGroupedExpression grp) {
      VType t = analyzeExpression(grp.expression());
      expressionTypes.put(grp, t);
      return t;
    }

    if (expr instanceof VtlUnaryExpression un) {
      VType operandType = analyzeExpression(un.operand());
      VType res =
          switch (un.operator()) {
            case NOT -> VTypes.BOOLEAN;
            case MINUS, PLUS -> operandType;
          };
      expressionTypes.put(un, res);
      return res;
    }

    if (expr instanceof VtlBinaryExpression bin) {
      if (bin.operator() == VtlBinaryOperator.LOGICAL_AND) {
        VType left = analyzeExpression(bin.left());
        symbolTable.enterScope(ScopeKind.LOCAL);
        for (String varName : extractNotNullVariables(bin.left())) {
          refineNonNull(varName);
        }
        VType right = analyzeExpression(bin.right());
        symbolTable.exitScope();
        expressionTypes.put(bin, VTypes.BOOLEAN);
        return VTypes.BOOLEAN;
      }
      VType left = analyzeExpression(bin.left());
      VType right = analyzeExpression(bin.right());
      VType res =
          switch (bin.operator()) {
            case ADD -> {
              if (VTypes.STRING.equals(left) || VTypes.STRING.equals(right)) {
                yield VTypes.STRING;
              }
              if (left instanceof VType.PrimitiveType pt && pt.kind() == PrimitiveKind.LONG) {
                yield VTypes.LONG;
              }
              if (right instanceof VType.PrimitiveType pt && pt.kind() == PrimitiveKind.LONG) {
                yield VTypes.LONG;
              }
              yield VTypes.INT;
            }
            case SUBTRACT, MULTIPLY, DIVIDE, MODULO -> {
              if (left instanceof VType.PrimitiveType pt && pt.kind() == PrimitiveKind.LONG) {
                yield VTypes.LONG;
              }
              if (right instanceof VType.PrimitiveType pt && pt.kind() == PrimitiveKind.LONG) {
                yield VTypes.LONG;
              }
              yield VTypes.INT;
            }
            case EQUAL,
                NOT_EQUAL,
                LESS_THAN,
                LESS_THAN_OR_EQUAL,
                GREATER_THAN,
                GREATER_THAN_OR_EQUAL,
                LOGICAL_AND,
                LOGICAL_OR ->
                VTypes.BOOLEAN;
          };
      expressionTypes.put(bin, res);
      return res;
    }

    if (expr instanceof VtlRangeExpression rng) {
      analyzeExpression(rng.start());
      analyzeExpression(rng.end());
      VType res = new VType.ArrayType(VTypes.INT, Nullability.NON_NULL);
      expressionTypes.put(rng, res);
      return res;
    }

    if (expr instanceof VtlListLiteralExpression listLit) {
      VType elemType = VTypes.DYNAMIC;
      for (VtlExpression elem : listLit.elements()) {
        elemType = analyzeExpression(elem);
      }
      VType res = new VType.ArrayType(elemType, Nullability.NON_NULL);
      expressionTypes.put(listLit, res);
      return res;
    }

    if (expr instanceof VtlMapLiteralExpression mapLit) {
      for (var entry : mapLit.entries()) {
        analyzeExpression(entry.value());
      }
      VType res = VType.ClassType.of(Map.class);
      expressionTypes.put(mapLit, res);
      return res;
    }

    if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (var part : interp.parts()) {
        if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          analyzeReference(rp.reference());
        }
      }
      expressionTypes.put(interp, VTypes.STRING);
      return VTypes.STRING;
    }

    expressionTypes.put(expr, VTypes.DYNAMIC);
    return VTypes.DYNAMIC;
  }

  public VType analyzeReference(VtlReference ref) {
    Objects.requireNonNull(ref, "ref must not be null");

    if (ref.isQuiet()) {
      capabilitiesBuilder.setAccessesRawUnescapedOutput(true);
    }

    String rootName = ref.rootName();
    Optional<Symbol> optSym = symbolTable.resolve(rootName);
    VType currentType;

    if (optSym.isPresent()) {
      currentType = optSym.get().type();
    } else {
      capabilitiesBuilder.setUsesUnknownModelTypes(true);
      if (options.typeCheckingMode().isEnabled() && !options.modelSchema().isEmpty()) {
        Optional<String> suggestion =
            LevenshteinDistance.findClosestMatch(rootName, options.modelSchema().parameterNames());
        String msg = "Root reference '$" + rootName + "' is not declared in the model schema";
        if (suggestion.isPresent()) {
          msg += ". Did you mean '$" + suggestion.get() + "'?";
        }
        reportStrictIssue(VtlSemanticDiagnosticCodes.UNRESOLVED_ROOT, msg, ref.span());
        currentType =
            options.typeCheckingMode() == TypeCheckingMode.ERROR ? VTypes.ERROR : VTypes.DYNAMIC;
      } else {
        currentType = VTypes.DYNAMIC;
      }
    }

    String targetDesc = "$" + rootName;
    boolean warnedNullable = false;
    for (VtlAccessStep step : ref.steps()) {
      if (!ref.isQuiet()
          && !warnedNullable
          && options.typeCheckingMode().isEnabled()
          && currentType.nullability() == Nullability.NULLABLE
          && !(currentType instanceof VType.DynamicType)
          && !(currentType instanceof VType.ErrorType)) {
        diagnostics.add(
            Diagnostic.warning(
                VtlSemanticDiagnosticCodes.NULLABLE_DEREFERENCE,
                "Dereference of nullable target '"
                    + targetDesc
                    + "' may cause null pointer or evaluation failure. Use quiet reference '$!"
                    + rootName
                    + "' or guard with #if("
                    + targetDesc
                    + ")",
                step.span()));
        warnedNullable = true;
      }

      if (currentType instanceof VType.ErrorType) {
        currentType = VTypes.ERROR;
        continue;
      }

      if (step instanceof VtlAccessStep.PropertyAccess prop) {
        targetDesc += "." + prop.propertyName();
        if (currentType instanceof VType.DynamicType) {
          capabilitiesBuilder.setRequiresDynamicMemberResolution(true);
          memberResolutions.put(prop, MemberResolution.dynamic(VTypes.DYNAMIC));
          currentType = VTypes.DYNAMIC;
        } else {
          MemberResolution res =
              MemberResolver.resolveProperty(
                  currentType, prop.propertyName(), options.memberAccessPolicy());
          memberResolutions.put(prop, res);
          if (res.isFound()) {
            currentType = res.resultType();
          } else if (res.kind() == MemberResolution.Kind.DENIED) {
            diagnostics.add(
                Diagnostic.error(
                    VtlSemanticDiagnosticCodes.SECURITY_DENIED,
                    res.diagnosticMessage().orElse("Access denied by security policy"),
                    prop.span()));
            currentType = VTypes.ERROR;
          } else {
            String msg =
                "Property '"
                    + prop.propertyName()
                    + "' does not exist on type "
                    + currentType.typeName();
            if (res.typoSuggestion().isPresent()) {
              msg += ". Did you mean '" + res.typoSuggestion().get() + "'?";
            }
            reportStrictIssue(VtlSemanticDiagnosticCodes.PROPERTY_NOT_FOUND, msg, prop.span());
            if (options.typeCheckingMode() == TypeCheckingMode.ERROR) {
              currentType = VTypes.ERROR;
            } else {
              capabilitiesBuilder.setRequiresDynamicMemberResolution(true);
              currentType = VTypes.DYNAMIC;
            }
          }
        }
      } else if (step instanceof VtlAccessStep.MethodCall call) {
        targetDesc += "." + call.methodName() + "()";
        capabilitiesBuilder.setRequiresArbitraryMethodCalls(true);
        List<VType> argTypes = new ArrayList<>();
        for (VtlExpression arg : call.arguments()) {
          argTypes.add(analyzeExpression(arg));
        }

        if (!options.allowArbitraryMethods()) {
          diagnostics.add(
              Diagnostic.error(
                  VtlSemanticDiagnosticCodes.SECURITY_DENIED,
                  "Method calls are disabled by " + options.profile().name() + " policy",
                  call.span()));
          methodResolutions.put(call, MethodResolution.denied("Method calls are disabled"));
          currentType = VTypes.ERROR;
        } else {
          MethodResolution res =
              MethodResolver.resolveMethod(
                  currentType, call.methodName(), argTypes, options.memberAccessPolicy());
          methodResolutions.put(call, res);
          if (res.isResolved()) {
            currentType = res.returnType();
          } else if (res.kind() == MethodResolution.Kind.DENIED_BY_POLICY) {
            diagnostics.add(
                Diagnostic.error(
                    VtlSemanticDiagnosticCodes.SECURITY_DENIED,
                    res.diagnosticMessage().orElse("Method is denied by security policy"),
                    call.span()));
            currentType = VTypes.ERROR;
          } else if (res.kind() == MethodResolution.Kind.DYNAMIC) {
            currentType = VTypes.DYNAMIC;
          } else if (res.kind() == MethodResolution.Kind.ARITY_MISMATCH) {
            reportStrictIssue(
                VtlSemanticDiagnosticCodes.METHOD_NOT_FOUND,
                res.diagnosticMessage().orElse("Method arity mismatch"),
                call.span());
            currentType =
                options.typeCheckingMode() == TypeCheckingMode.ERROR
                    ? VTypes.ERROR
                    : VTypes.DYNAMIC;
          } else if (res.kind() == MethodResolution.Kind.INCOMPATIBLE_ARGUMENTS) {
            SourceSpan diagSpan = call.span();
            if (res.incompatibleArgIndex() >= 0
                && res.incompatibleArgIndex() < call.arguments().size()) {
              diagSpan = call.arguments().get(res.incompatibleArgIndex()).span();
            }
            reportStrictIssue(
                VtlSemanticDiagnosticCodes.TYPE_MISMATCH,
                res.diagnosticMessage().orElse("Incompatible argument types for method"),
                diagSpan);
            currentType =
                options.typeCheckingMode() == TypeCheckingMode.ERROR
                    ? VTypes.ERROR
                    : VTypes.DYNAMIC;
          } else {
            if (argTypes.stream().anyMatch(t -> t instanceof VType.ErrorType)) {
              currentType = VTypes.ERROR;
            } else {
              String msg =
                  "Method '"
                      + call.methodName()
                      + "' with "
                      + argTypes.size()
                      + " argument(s) does not exist on type "
                      + currentType.typeName();
              if (res.typoSuggestion().isPresent()) {
                msg += ". Did you mean '" + res.typoSuggestion().get() + "'?";
              }
              reportStrictIssue(VtlSemanticDiagnosticCodes.METHOD_NOT_FOUND, msg, call.span());
              currentType =
                  options.typeCheckingMode() == TypeCheckingMode.ERROR
                      ? VTypes.ERROR
                      : VTypes.DYNAMIC;
            }
          }
        }
      } else if (step instanceof VtlAccessStep.IndexAccess idx) {
        targetDesc += "[...]";
        VType indexType = analyzeExpression(idx.indexExpression());
        if (currentType instanceof VType.ArrayType at) {
          if (options.typeCheckingMode().isEnabled()
              && !indexType.isDynamic()
              && !(indexType instanceof VType.PrimitiveType pt && pt.kind().isIntegral())
              && !(indexType instanceof VType.ClassType ct && isIntegralClass(ct))) {
            reportStrictIssue(
                VtlSemanticDiagnosticCodes.TYPE_MISMATCH,
                "Array index must be an integer, but was " + indexType.typeName(),
                idx.indexExpression().span());
          }
          currentType = at.componentType();
        } else if (currentType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
          Class<?> c = ct.javaClass().get();
          if (List.class.isAssignableFrom(c)) {
            if (options.typeCheckingMode().isEnabled()
                && !indexType.isDynamic()
                && !(indexType instanceof VType.PrimitiveType pt && pt.kind().isIntegral())
                && !(indexType instanceof VType.ClassType ct2 && isIntegralClass(ct2))) {
              reportStrictIssue(
                  VtlSemanticDiagnosticCodes.TYPE_MISMATCH,
                  "List index must be an integer, but was " + indexType.typeName(),
                  idx.indexExpression().span());
            }
            currentType = ct.typeArguments().isEmpty() ? VTypes.DYNAMIC : ct.typeArguments().get(0);
          } else if (Map.class.isAssignableFrom(c)) {
            currentType =
                ct.typeArguments().size() >= 2 ? ct.typeArguments().get(1) : VTypes.DYNAMIC;
          } else {
            if (options.typeCheckingMode().isEnabled()) {
              reportStrictIssue(
                  VtlSemanticDiagnosticCodes.TYPE_MISMATCH,
                  "Type " + currentType.typeName() + " does not support indexing",
                  idx.span());
            }
            capabilitiesBuilder.setRequiresDynamicMemberResolution(true);
            currentType =
                options.typeCheckingMode() == TypeCheckingMode.ERROR
                    ? VTypes.ERROR
                    : VTypes.DYNAMIC;
          }
        } else {
          capabilitiesBuilder.setRequiresDynamicMemberResolution(true);
          currentType = VTypes.DYNAMIC;
        }
      }
    }

    if (ref.alternateValue().isPresent()) {
      VType altType = analyzeExpression(ref.alternateValue().get());
      Nullability unionNullability =
          (currentType.nullability() == Nullability.NON_NULL
                  || altType.nullability() == Nullability.NON_NULL)
              ? Nullability.NON_NULL
              : Nullability.NULLABLE;
      currentType = new VType.UnionType(List.of(currentType, altType), unionNullability);
    }

    return currentType;
  }

  private boolean isNullExpr(VtlExpression expr) {
    if (expr instanceof VtlNullLiteralExpression) {
      return true;
    }
    if (expr instanceof VtlReferenceExpression ref && ref.reference().steps().isEmpty()) {
      return "null".equals(ref.reference().rootName());
    }
    return false;
  }

  private Set<String> extractNotNullVariables(VtlExpression expr) {
    Set<String> vars = new LinkedHashSet<>();
    collectNotNullVariables(expr, vars);
    return vars;
  }

  private void collectNotNullVariables(VtlExpression expr, Set<String> vars) {
    if (expr instanceof VtlReferenceExpression refExpr && refExpr.reference().steps().isEmpty()) {
      vars.add(refExpr.reference().rootName());
      return;
    }
    if (expr instanceof VtlBinaryExpression bin) {
      if (bin.operator() == VtlBinaryOperator.LOGICAL_AND) {
        collectNotNullVariables(bin.left(), vars);
        collectNotNullVariables(bin.right(), vars);
        return;
      }
      if (bin.operator() == VtlBinaryOperator.NOT_EQUAL) {
        if (bin.left() instanceof VtlReferenceExpression ref
            && ref.reference().steps().isEmpty()
            && isNullExpr(bin.right())) {
          vars.add(ref.reference().rootName());
          return;
        }
        if (bin.right() instanceof VtlReferenceExpression ref
            && ref.reference().steps().isEmpty()
            && isNullExpr(bin.left())) {
          vars.add(ref.reference().rootName());
          return;
        }
      }
    }
    if (expr instanceof VtlGroupedExpression grp) {
      collectNotNullVariables(grp.expression(), vars);
    }
  }

  private Set<String> extractNullVariables(VtlExpression expr) {
    Set<String> vars = new LinkedHashSet<>();
    collectNullVariables(expr, vars);
    return vars;
  }

  private void collectNullVariables(VtlExpression expr, Set<String> vars) {
    if (expr instanceof VtlUnaryExpression un && un.operator() == VtlUnaryOperator.NOT) {
      collectNotNullVariables(un.operand(), vars);
      return;
    }
    if (expr instanceof VtlBinaryExpression bin) {
      if (bin.operator() == VtlBinaryOperator.EQUAL) {
        if (bin.left() instanceof VtlReferenceExpression ref
            && ref.reference().steps().isEmpty()
            && isNullExpr(bin.right())) {
          vars.add(ref.reference().rootName());
          return;
        }
        if (bin.right() instanceof VtlReferenceExpression ref
            && ref.reference().steps().isEmpty()
            && isNullExpr(bin.left())) {
          vars.add(ref.reference().rootName());
          return;
        }
      }
    }
    if (expr instanceof VtlGroupedExpression grp) {
      collectNullVariables(grp.expression(), vars);
    }
  }

  private void refineNonNull(String varName) {
    symbolTable
        .resolve(varName)
        .ifPresent(
            sym -> {
              if (sym.type().nullability() == Nullability.NULLABLE) {
                VType nonNullType = sym.type().withNullability(Nullability.NON_NULL);
                symbolTable.define(sym.withType(nonNullType));
              }
            });
  }

  private void reportStrictIssue(DiagnosticCode code, String message, SourceSpan span) {
    if (options.typeCheckingMode() == TypeCheckingMode.ERROR) {
      diagnostics.add(Diagnostic.error(code, message, span));
    } else if (options.typeCheckingMode() == TypeCheckingMode.WARN) {
      diagnostics.add(Diagnostic.warning(code, message, span));
    }
  }

  private static boolean isIntegralClass(VType.ClassType ct) {
    if (ct.javaClass().isEmpty()) return false;
    Class<?> c = ct.javaClass().get();
    return c == Integer.class || c == Long.class || c == Short.class || c == Byte.class;
  }
}
