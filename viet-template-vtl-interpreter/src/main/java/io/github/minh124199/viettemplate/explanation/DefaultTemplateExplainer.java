package io.github.minh124199.viettemplate.explanation;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAccessStep;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAssignmentTarget;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBinaryExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBlockDirectiveCallNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDefineDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDirectiveCallNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlForeachDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlGroupedExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIfBranch;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlIfDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlListLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMacroDefinitionNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMapEntry;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMapLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReference;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceOutputNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlSetDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlUnaryExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.OutputSpecializationContext;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.OutputSpecializationDecider;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.WriteDispatchDecision;
import io.github.minh124199.viettemplate.vtl.internal.engine.dependency.StaticDependencyExtractor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/** Package-private default implementation of {@link TemplateExplainer}. */
class DefaultTemplateExplainer implements TemplateExplainer {

  record DiscoveredTemplate(TemplateId templateId, String relPath, Path file) {}

  @Override
  public TemplateExplanation explain(TemplateExplainRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    Map<TemplateId, DiscoveredTemplate> discovered = scanTemplates(request);
    List<DiscoveredTemplate> sortedTemplates = new ArrayList<>(discovered.values());
    sortedTemplates.sort(Comparator.comparing(d -> d.templateId().value()));

    List<TemplateAotDiagnostic> allDiagnostics = new ArrayList<>();
    List<SingleTemplateExplanation> templateExplanations = new ArrayList<>();
    int totalExpressionsAcrossTemplates = 0;
    boolean hasDynamicFallbackError = false;

    for (DiscoveredTemplate dt : sortedTemplates) {
      TemplateId templateId = dt.templateId();
      Path sourceFile = dt.file();

      String sourceText;
      try {
        sourceText = Files.readString(sourceFile, request.encoding());
      } catch (IOException e) {
        allDiagnostics.add(
            new TemplateAotDiagnostic(
                templateId,
                dt.relPath(),
                DiagnosticSeverity.ERROR,
                DiagnosticCode.of("VTLAOT", "1001"),
                "Failed to read template source: " + e.getMessage(),
                -1,
                -1,
                -1,
                -1));
        continue;
      }

      TemplateContract contract = request.contracts().get(templateId);
      if (contract == null) {
        contract =
            ExplanationContractReader.findCompanion(
                    sourceFile, templateId, request.classLoader().orElse(null))
                .orElse(null);
      }

      SourceText source = SourceText.of(templateId, sourceText);
      VtlParseResult parseResult = VtlParser.parse(source);
      for (Diagnostic diag : parseResult.diagnostics()) {
        allDiagnostics.add(TemplateAotDiagnostic.from(templateId, dt.relPath(), diag));
      }

      VtlProfile profile = request.profile().orElse(VtlProfile.VTL_MIGRATION);
      NullRenderMode nullRenderMode =
          request.strictReferences()
              ? NullRenderMode.THROW_ERROR
              : NullRenderMode.LITERAL_EXPRESSION;
      IrEscapeMode escapeMode =
          profile == VtlProfile.VTL_SAFE ? IrEscapeMode.HTML_TEXT : IrEscapeMode.RAW;

      if (parseResult.hasErrors()) {
        templateExplanations.add(
            new SingleTemplateExplanation(
                templateId,
                dt.relPath(),
                profile,
                request.typeCheckingMode(),
                request.strictReferences(),
                nullRenderMode.name(),
                escapeMode.name(),
                contract != null,
                deriveContractClassName(contract),
                List.of(),
                false,
                "UNSUPPORTED_LANGUAGE_FEATURE",
                List.of("PARSE_ERRORS"),
                List.of()));
        continue;
      }

      ModelSchema modelSchema = contract != null ? ModelSchema.fromContract(contract) : null;
      VtlSemanticOptions.Builder semOptionsBuilder =
          VtlSemanticOptions.builder()
              .profile(profile)
              .allowArbitraryMethods(profile.isArbitraryMethodsAllowed())
              .typeCheckingMode(request.typeCheckingMode())
              .strictMode(request.strictReferences());
      if (modelSchema != null) {
        semOptionsBuilder.modelSchema(modelSchema);
      }
      VtlSemanticOptions semanticOptions = semOptionsBuilder.build();

      SemanticAnalysisResult analysis =
          VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
      for (Diagnostic diag : analysis.diagnostics()) {
        allDiagnostics.add(TemplateAotDiagnostic.from(templateId, dt.relPath(), diag));
      }

      IrTemplate ir =
          AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);
      Set<TemplateDependency> rawDeps =
          StaticDependencyExtractor.extract(ir, request.globalMacroLibraries(), request.layoutId());
      List<TemplateDependency> sortedDeps =
          rawDeps.stream()
              .sorted(
                  Comparator.comparing((TemplateDependency d) -> d.target().value())
                      .thenComparing(d -> d.kind().name()))
              .toList();

      boolean isTyped = modelSchema != null && !modelSchema.isEmpty();
      boolean aotEligible =
          ir.capabilities().eligibleForStaticAot()
              && !analysis.hasErrors()
              && !parseResult.hasErrors();

      List<String> templateAotRejectionReasons = new ArrayList<>();
      if (ir.capabilities().requiresDynamicMemberResolution()) {
        templateAotRejectionReasons.add("DYNAMIC_MEMBER_RESOLUTION");
      }
      if (ir.capabilities().requiresArbitraryMethodCalls()) {
        templateAotRejectionReasons.add("ARBITRARY_METHOD_CALLS");
      }
      if (ir.capabilities().requiresDynamicIncludeParse()) {
        templateAotRejectionReasons.add("DYNAMIC_INCLUDE_PARSE");
      }
      if (ir.capabilities().requiresRuntimeEvaluation()) {
        templateAotRejectionReasons.add("RUNTIME_EVALUATION");
      }
      if (ir.capabilities().usesUnknownModelTypes()) {
        templateAotRejectionReasons.add("UNKNOWN_MODEL_TYPES");
      }
      if (!isTyped) {
        templateAotRejectionReasons.add("UNTYPED_TEMPLATE");
      }
      if (analysis.hasErrors()) {
        templateAotRejectionReasons.add("SEMANTIC_ERRORS");
      }

      String compilationStatus;
      if (ir.capabilities().requiresRuntimeEvaluation()
          || ir.capabilities().requiresDynamicIncludeParse()) {
        compilationStatus = "INTERPRETER_REQUIRED_EVALUATE";
      } else if (aotEligible) {
        compilationStatus = "AOT_OK";
      } else {
        compilationStatus = "AOT_OK_WITH_DYNAMIC_SITES";
      }

      if (request.failOnDynamicFallback()
          && "AOT_OK_WITH_DYNAMIC_SITES".equals(compilationStatus)) {
        hasDynamicFallbackError = true;
        allDiagnostics.add(
            new TemplateAotDiagnostic(
                templateId,
                dt.relPath(),
                DiagnosticSeverity.ERROR,
                DiagnosticCode.of("VTLAOT", "1102"),
                "Template requires dynamic sites but failOnDynamicFallback is enabled",
                -1,
                -1,
                -1,
                -1));
      }

      Set<Integer> nonIntLocalSlots =
          OutputSpecializationDecider.computeNonIntLocalSlots(ir, isTyped);
      OutputSpecializationContext specContext =
          new OutputSpecializationContext() {
            @Override
            public boolean isStrict() {
              return request.strictReferences();
            }

            @Override
            public boolean isSafeProfile() {
              return profile == VtlProfile.VTL_SAFE;
            }

            @Override
            public boolean isTyped() {
              return isTyped;
            }

            @Override
            public boolean isNonIntLocal(int slot) {
              return nonIntLocalSlots.contains(slot);
            }
          };

      List<ExpressionExplanation> expressions =
          extractAndExplainExpressions(
              parseResult.template(),
              source,
              analysis,
              ir,
              specContext,
              modelSchema,
              isTyped,
              contract,
              profile,
              request.line(),
              request.column());

      totalExpressionsAcrossTemplates += expressions.size();

      templateExplanations.add(
          new SingleTemplateExplanation(
              templateId,
              dt.relPath(),
              profile,
              request.typeCheckingMode(),
              request.strictReferences(),
              nullRenderMode.name(),
              escapeMode.name(),
              isTyped,
              deriveContractClassName(contract),
              sortedDeps,
              aotEligible,
              compilationStatus,
              templateAotRejectionReasons,
              expressions));
    }

    List<TemplateAotDiagnostic> sortedDiagnostics =
        allDiagnostics.stream()
            .distinct()
            .sorted(
                Comparator.comparing((TemplateAotDiagnostic d) -> d.templateId().value())
                    .thenComparingInt(TemplateAotDiagnostic::startLine)
                    .thenComparingInt(TemplateAotDiagnostic::startColumn)
                    .thenComparing(d -> d.code().qualifiedCode())
                    .thenComparing(TemplateAotDiagnostic::message))
            .toList();

    int errorCount =
        (int)
            sortedDiagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .count();
    boolean success = errorCount == 0 && !hasDynamicFallbackError;

    TemplateExplanation result =
        new TemplateExplanation(
            success,
            templateExplanations.size(),
            totalExpressionsAcrossTemplates,
            templateExplanations,
            sortedDiagnostics);

    if (request.outputFile().isPresent()) {
      Path outPath = request.outputFile().get().toAbsolutePath().normalize();
      try {
        if (outPath.getParent() != null) {
          Files.createDirectories(outPath.getParent());
        }
        String content =
            "json".equalsIgnoreCase(request.format()) ? result.asJson() : result.asText();
        Files.writeString(outPath, content, request.encoding());
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to write explanation output file: " + outPath, e);
      }
    }

    return result;
  }

  private static Optional<String> deriveContractClassName(TemplateContract contract) {
    if (contract == null || contract.isEmpty()) {
      return Optional.empty();
    }
    if (contract.parameters().size() == 1) {
      TemplateParameter p = contract.parameters().get(0);
      return Optional.of(p.type().typeName());
    }
    return Optional.of(contract.templateId().value() + "Contract");
  }

  private static List<ExpressionExplanation> extractAndExplainExpressions(
      VtlTemplate astTemplate,
      SourceText source,
      SemanticAnalysisResult analysis,
      IrTemplate ir,
      OutputSpecializationContext specContext,
      ModelSchema modelSchema,
      boolean isTyped,
      TemplateContract contract,
      VtlProfile profile,
      OptionalInt lineFilter,
      OptionalInt columnFilter) {

    Map<SourceSpan, IrWriteValue> writeValuesBySpan = new LinkedHashMap<>();
    collectWriteValues(ir.root(), writeValuesBySpan);
    for (IrFunction fn : ir.functions()) {
      collectWriteValues(fn.body(), writeValuesBySpan);
    }

    Map<String, VType> localTypes = new LinkedHashMap<>();
    collectLocalTypes(ir.root(), localTypes);
    for (IrFunction fn : ir.functions()) {
      collectLocalTypes(fn.body(), localTypes);
    }

    List<ExpressionExplanation> collected = new ArrayList<>();
    Map<String, String> localSymbols = new LinkedHashMap<>();

    walkNodes(
        astTemplate.children(),
        source,
        analysis,
        writeValuesBySpan,
        localTypes,
        specContext,
        modelSchema,
        isTyped,
        contract,
        profile,
        localSymbols,
        lineFilter,
        columnFilter,
        collected);

    collected.sort(
        Comparator.comparingInt((ExpressionExplanation e) -> e.span().startLine())
            .thenComparingInt(e -> e.span().startColumn())
            .thenComparingInt(e -> e.span().endLine())
            .thenComparingInt(e -> e.span().endColumn())
            .thenComparing(ExpressionExplanation::sourceText));

    return collected;
  }

  private static void collectWriteValues(
      IrBlock block, Map<SourceSpan, IrWriteValue> writeValuesBySpan) {
    if (block == null) {
      return;
    }
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrWriteValue wv) {
        writeValuesBySpan.put(wv.span(), wv);
      } else if (stmt instanceof IrIf ifStmt) {
        collectWriteValues(ifStmt.thenBlock(), writeValuesBySpan);
        ifStmt.elseBlock().ifPresent(b -> collectWriteValues(b, writeValuesBySpan));
      } else if (stmt instanceof IrLoop loop) {
        collectWriteValues(loop.body(), writeValuesBySpan);
        loop.elseBody().ifPresent(b -> collectWriteValues(b, writeValuesBySpan));
      }
    }
  }

  private static void collectLocalTypes(IrBlock block, Map<String, VType> localTypes) {
    if (block == null) {
      return;
    }
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrStoreLocal store) {
        localTypes.put(store.local().name(), store.local().type());
      } else if (stmt instanceof IrLoop loop) {
        localTypes.put(loop.elementLocal().name(), loop.elementLocal().type());
        collectLocalTypes(loop.body(), localTypes);
        loop.elseBody().ifPresent(b -> collectLocalTypes(b, localTypes));
      } else if (stmt instanceof IrIf ifStmt) {
        collectLocalTypes(ifStmt.thenBlock(), localTypes);
        ifStmt.elseBlock().ifPresent(b -> collectLocalTypes(b, localTypes));
      }
    }
  }

  private static void walkNodes(
      List<VtlNode> nodes,
      SourceText source,
      SemanticAnalysisResult analysis,
      Map<SourceSpan, IrWriteValue> writeValuesBySpan,
      Map<String, VType> localTypes,
      OutputSpecializationContext specContext,
      ModelSchema modelSchema,
      boolean isTyped,
      TemplateContract contract,
      VtlProfile profile,
      Map<String, String> localSymbols,
      OptionalInt lineFilter,
      OptionalInt columnFilter,
      List<ExpressionExplanation> out) {
    if (nodes == null) {
      return;
    }
    for (VtlNode node : nodes) {
      walkNode(
          node,
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
    }
  }

  private static void walkNode(
      VtlNode node,
      SourceText source,
      SemanticAnalysisResult analysis,
      Map<SourceSpan, IrWriteValue> writeValuesBySpan,
      Map<String, VType> localTypes,
      OutputSpecializationContext specContext,
      ModelSchema modelSchema,
      boolean isTyped,
      TemplateContract contract,
      VtlProfile profile,
      Map<String, String> localSymbols,
      OptionalInt lineFilter,
      OptionalInt columnFilter,
      List<ExpressionExplanation> out) {
    if (node instanceof VtlReferenceOutputNode refOut) {
      VtlReference ref = refOut.reference();
      IrWriteValue wv = findMatchingWriteValue(refOut.span(), writeValuesBySpan);
      if (matchesSpanFilter(ref.span(), lineFilter, columnFilter)) {
        out.add(
            explainReference(
                ref,
                true,
                wv,
                source,
                analysis,
                localTypes,
                specContext,
                modelSchema,
                isTyped,
                contract,
                profile,
                localSymbols));
      }
      for (VtlAccessStep step : ref.steps()) {
        if (step instanceof VtlAccessStep.MethodCall call) {
          for (VtlExpression arg : call.arguments()) {
            walkExpression(
                arg,
                source,
                analysis,
                writeValuesBySpan,
                localTypes,
                specContext,
                modelSchema,
                isTyped,
                contract,
                profile,
                localSymbols,
                lineFilter,
                columnFilter,
                out);
          }
        }
      }
    } else if (node instanceof VtlSetDirectiveNode setNode) {
      if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget rt) {
        localSymbols.put(rt.reference().rootName(), "LOCAL");
      }
      walkExpression(
          setNode.value(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
    } else if (node instanceof VtlIfDirectiveNode ifNode) {
      VtlIfBranch primary = ifNode.primaryBranch();
      walkExpression(
          primary.condition(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
      walkNodes(
          primary.body(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
      for (int i = 1; i < ifNode.branches().size(); i++) {
        VtlIfBranch b = ifNode.branches().get(i);
        walkExpression(
            b.condition(),
            source,
            analysis,
            writeValuesBySpan,
            localTypes,
            specContext,
            modelSchema,
            isTyped,
            contract,
            profile,
            localSymbols,
            lineFilter,
            columnFilter,
            out);
        walkNodes(
            b.body(),
            source,
            analysis,
            writeValuesBySpan,
            localTypes,
            specContext,
            modelSchema,
            isTyped,
            contract,
            profile,
            localSymbols,
            lineFilter,
            columnFilter,
            out);
      }
      ifNode
          .elseBody()
          .ifPresent(
              b ->
                  walkNodes(
                      b,
                      source,
                      analysis,
                      writeValuesBySpan,
                      localTypes,
                      specContext,
                      modelSchema,
                      isTyped,
                      contract,
                      profile,
                      localSymbols,
                      lineFilter,
                      columnFilter,
                      out));
    } else if (node instanceof VtlForeachDirectiveNode feNode) {
      walkExpression(
          feNode.iterable(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
      localSymbols.put(feNode.loopVariable().rootName(), "LOOP");
      walkNodes(
          feNode.body(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
      feNode
          .elseBody()
          .ifPresent(
              b ->
                  walkNodes(
                      b,
                      source,
                      analysis,
                      writeValuesBySpan,
                      localTypes,
                      specContext,
                      modelSchema,
                      isTyped,
                      contract,
                      profile,
                      localSymbols,
                      lineFilter,
                      columnFilter,
                      out));
    } else if (node instanceof VtlMacroDefinitionNode macroNode) {
      walkNodes(
          macroNode.body(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
    } else if (node instanceof VtlBlockDirectiveCallNode blockCall) {
      for (VtlExpression arg : blockCall.arguments()) {
        walkExpression(
            arg,
            source,
            analysis,
            writeValuesBySpan,
            localTypes,
            specContext,
            modelSchema,
            isTyped,
            contract,
            profile,
            localSymbols,
            lineFilter,
            columnFilter,
            out);
      }
      walkNodes(
          blockCall.body(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
    } else if (node instanceof VtlDirectiveCallNode dirCall) {
      for (VtlExpression arg : dirCall.arguments()) {
        walkExpression(
            arg,
            source,
            analysis,
            writeValuesBySpan,
            localTypes,
            specContext,
            modelSchema,
            isTyped,
            contract,
            profile,
            localSymbols,
            lineFilter,
            columnFilter,
            out);
      }
    } else if (node instanceof VtlDefineDirectiveNode defNode) {
      localSymbols.put(defNode.targetReference().rootName(), "LOCAL");
      walkNodes(
          defNode.body(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
    }
  }

  private static void walkExpression(
      VtlExpression expr,
      SourceText source,
      SemanticAnalysisResult analysis,
      Map<SourceSpan, IrWriteValue> writeValuesBySpan,
      Map<String, VType> localTypes,
      OutputSpecializationContext specContext,
      ModelSchema modelSchema,
      boolean isTyped,
      TemplateContract contract,
      VtlProfile profile,
      Map<String, String> localSymbols,
      OptionalInt lineFilter,
      OptionalInt columnFilter,
      List<ExpressionExplanation> out) {
    if (expr == null) {
      return;
    }

    if (expr instanceof VtlReferenceExpression refExpr) {
      VtlReference ref = refExpr.reference();
      if (matchesSpanFilter(ref.span(), lineFilter, columnFilter)) {
        out.add(
            explainReference(
                ref,
                false,
                null,
                source,
                analysis,
                localTypes,
                specContext,
                modelSchema,
                isTyped,
                contract,
                profile,
                localSymbols));
      }
      for (VtlAccessStep step : ref.steps()) {
        if (step instanceof VtlAccessStep.MethodCall call) {
          for (VtlExpression arg : call.arguments()) {
            walkExpression(
                arg,
                source,
                analysis,
                writeValuesBySpan,
                localTypes,
                specContext,
                modelSchema,
                isTyped,
                contract,
                profile,
                localSymbols,
                lineFilter,
                columnFilter,
                out);
          }
        }
      }
    } else if (expr instanceof VtlBinaryExpression bin) {
      walkExpression(
          bin.left(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
      walkExpression(
          bin.right(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
    } else if (expr instanceof VtlUnaryExpression un) {
      walkExpression(
          un.operand(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
    } else if (expr instanceof VtlGroupedExpression grp) {
      walkExpression(
          grp.expression(),
          source,
          analysis,
          writeValuesBySpan,
          localTypes,
          specContext,
          modelSchema,
          isTyped,
          contract,
          profile,
          localSymbols,
          lineFilter,
          columnFilter,
          out);
    } else if (expr instanceof VtlListLiteralExpression listLit) {
      for (VtlExpression item : listLit.elements()) {
        walkExpression(
            item,
            source,
            analysis,
            writeValuesBySpan,
            localTypes,
            specContext,
            modelSchema,
            isTyped,
            contract,
            profile,
            localSymbols,
            lineFilter,
            columnFilter,
            out);
      }
    } else if (expr instanceof VtlMapLiteralExpression mapLit) {
      for (VtlMapEntry entry : mapLit.entries()) {
        walkExpression(
            entry.value(),
            source,
            analysis,
            writeValuesBySpan,
            localTypes,
            specContext,
            modelSchema,
            isTyped,
            contract,
            profile,
            localSymbols,
            lineFilter,
            columnFilter,
            out);
      }
    }
  }

  private static IrWriteValue findMatchingWriteValue(
      SourceSpan span, Map<SourceSpan, IrWriteValue> writeValuesBySpan) {
    IrWriteValue exact = writeValuesBySpan.get(span);
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<SourceSpan, IrWriteValue> entry : writeValuesBySpan.entrySet()) {
      SourceSpan s = entry.getKey();
      if (s.startLine() == span.startLine() && s.startColumn() == span.startColumn()) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static ExpressionExplanation explainReference(
      VtlReference ref,
      boolean isOutput,
      IrWriteValue wv,
      SourceText source,
      SemanticAnalysisResult analysis,
      Map<String, VType> localTypes,
      OutputSpecializationContext specContext,
      ModelSchema modelSchema,
      boolean isTyped,
      TemplateContract contract,
      VtlProfile profile,
      Map<String, String> localSymbols) {

    SourceSpan span = ref.span();
    String sourceText =
        span.isKnown()
            ? source.content().substring(span.startOffset(), span.endOffset())
            : ref.rootName();

    String rootName = ref.rootName();
    List<VtlAccessStep> steps = ref.steps();

    String symbolOrigin;
    if (localSymbols.containsKey(rootName)) {
      symbolOrigin = localSymbols.get(rootName);
    } else {
      Optional<?> optSym = analysis.symbolTable().resolve(rootName);
      if (optSym.isPresent()) {
        symbolOrigin = getSymbolScopeKind(optSym.get());
      } else if (modelSchema != null && modelSchema.contains(rootName)) {
        symbolOrigin = "ROOT_MODEL";
      } else if ("foreach".equals(rootName)) {
        symbolOrigin = "BUILTIN";
      } else {
        symbolOrigin = "DYNAMIC";
      }
    }

    String expressionKind;
    if (steps.isEmpty()) {
      expressionKind = "VARIABLE";
    } else {
      VtlAccessStep last = steps.get(steps.size() - 1);
      if (last instanceof VtlAccessStep.PropertyAccess) {
        expressionKind = "PROPERTY_ACCESS";
      } else if (last instanceof VtlAccessStep.MethodCall) {
        expressionKind = "METHOD_CALL";
      } else if (last instanceof VtlAccessStep.IndexAccess) {
        expressionKind = "INDEX_ACCESS";
      } else {
        expressionKind = "REFERENCE";
      }
    }

    VType rootType = null;
    if (localTypes.containsKey(rootName)) {
      rootType = localTypes.get(rootName);
    } else if (modelSchema != null && modelSchema.contains(rootName)) {
      rootType =
          modelSchema
              .find(rootName)
              .map(DefaultTemplateExplainer::readModelParameterType)
              .orElse(null);
    } else {
      Optional<?> optSym = analysis.symbolTable().resolve(rootName);
      if (optSym.isPresent()) {
        rootType = getSymbolVType(optSym.get());
      }
    }

    Optional<String> receiverType;
    if (steps.isEmpty()) {
      receiverType = Optional.empty();
    } else if (steps.size() == 1) {
      receiverType =
          rootType != null && !rootType.isDynamic()
              ? Optional.of(rootType.typeName())
              : Optional.empty();
    } else {
      VtlAccessStep prev = steps.get(steps.size() - 2);
      if (prev instanceof VtlAccessStep.PropertyAccess prevProp) {
        receiverType =
            readMemberResolution(analysis.memberResolutionOf(prevProp))
                .map(MemberResInfo::resultType)
                .map(VType::typeName);
      } else if (prev instanceof VtlAccessStep.MethodCall prevCall) {
        receiverType =
            readMethodResolution(analysis.methodResolutionOf(prevCall))
                .map(MethodResInfo::returnType)
                .map(VType::typeName);
      } else {
        receiverType = Optional.empty();
      }
    }

    Optional<String> resolvedMember = Optional.empty();
    String resolutionStrategy = "DYNAMIC";
    boolean directAccess = false;
    VType finalType = null;

    if (steps.isEmpty()) {
      if ("LOCAL".equals(symbolOrigin)) {
        resolutionStrategy = "LOCAL_VARIABLE";
        directAccess = isTyped || (rootType != null && !rootType.isDynamic());
      } else if ("LOOP".equals(symbolOrigin)) {
        resolutionStrategy = "LOOP_VARIABLE";
        directAccess = isTyped || (rootType != null && !rootType.isDynamic());
      } else if ("ROOT_MODEL".equals(symbolOrigin)) {
        resolutionStrategy = "ROOT_PARAMETER";
        directAccess = isTyped;
      } else {
        resolutionStrategy = "DYNAMIC";
        directAccess = false;
      }
      finalType = rootType;
    } else {
      VtlAccessStep last = steps.get(steps.size() - 1);
      if (last instanceof VtlAccessStep.PropertyAccess prop) {
        Optional<MemberResInfo> optRes = readMemberResolution(analysis.memberResolutionOf(prop));
        if (optRes.isPresent()) {
          MemberResInfo res = optRes.get();
          finalType = res.resultType();
          resolvedMember = res.memberName();
          switch (res.kindName()) {
            case "RECORD_COMPONENT" -> {
              resolutionStrategy = "DIRECT_RECORD";
              directAccess = true;
            }
            case "GETTER", "BOOLEAN_GETTER" -> {
              resolutionStrategy = "DIRECT_GETTER";
              directAccess = true;
            }
            case "FIELD" -> {
              resolutionStrategy = "DIRECT_FIELD";
              directAccess = true;
            }
            case "MAP_ENTRY" -> {
              resolutionStrategy = "MAP_GET";
              directAccess = false;
            }
            case "NOT_FOUND" -> {
              resolutionStrategy = "NOT_FOUND";
              directAccess = false;
            }
            case "DENIED" -> {
              resolutionStrategy = "DENIED";
              directAccess = false;
            }
            default -> {
              resolutionStrategy = "DYNAMIC";
              directAccess = false;
            }
          }
        }
      } else if (last instanceof VtlAccessStep.MethodCall call) {
        Optional<MethodResInfo> optRes = readMethodResolution(analysis.methodResolutionOf(call));
        if (optRes.isPresent()) {
          MethodResInfo res = optRes.get();
          finalType = res.returnType();
          resolvedMember = res.methodName();
          switch (res.kindName()) {
            case "RESOLVED" -> {
              resolutionStrategy = "DIRECT_METHOD";
              directAccess = res.specializationStable();
            }
            case "METHOD_NOT_FOUND" -> {
              resolutionStrategy = "NOT_FOUND";
              directAccess = false;
            }
            case "ARITY_MISMATCH" -> {
              resolutionStrategy = "ARITY_MISMATCH";
              directAccess = false;
            }
            case "INCOMPATIBLE_ARGUMENTS" -> {
              resolutionStrategy = "INCOMPATIBLE_ARGUMENTS";
              directAccess = false;
            }
            case "DENIED_BY_POLICY" -> {
              resolutionStrategy = "DENIED";
              directAccess = false;
            }
            default -> {
              resolutionStrategy = "DYNAMIC";
              directAccess = false;
            }
          }
        }
      } else {
        resolutionStrategy = "DYNAMIC";
        directAccess = false;
      }
    }

    if (finalType == null && wv != null && wv.value() != null) {
      finalType = wv.value().type();
    }

    String inferredType =
        finalType != null && !finalType.isDynamic() && !finalType.isError()
            ? finalType.typeName()
            : "dynamic";

    String typeConfidence;
    if ("NOT_FOUND".equals(resolutionStrategy) || "DENIED".equals(resolutionStrategy)) {
      typeConfidence = "ERROR";
    } else if ("dynamic".equals(inferredType) || (!isTyped && "DYNAMIC".equals(symbolOrigin))) {
      typeConfidence = "DYNAMIC";
    } else {
      typeConfidence = "EXACT";
    }

    String nullability;
    if ("int".equals(inferredType)
        || "boolean".equals(inferredType)
        || "long".equals(inferredType)
        || "double".equals(inferredType)
        || "float".equals(inferredType)
        || "short".equals(inferredType)
        || "byte".equals(inferredType)
        || "char".equals(inferredType)) {
      nullability = "NON_NULL";
    } else if ("ROOT_MODEL".equals(symbolOrigin)
        && contract != null
        && contract.parameter(rootName).isPresent()
        && !contract.parameter(rootName).get().nullable()) {
      nullability = "NON_NULL";
    } else if ("dynamic".equals(inferredType)) {
      nullability = "UNKNOWN";
    } else {
      nullability = "NULLABLE";
    }

    boolean aotEligible =
        directAccess
            && !"dynamic".equals(inferredType)
            && !"NOT_FOUND".equals(resolutionStrategy)
            && !"DENIED".equals(resolutionStrategy);

    List<String> aotRejectionReasons = new ArrayList<>();
    if (!aotEligible) {
      if ("NOT_FOUND".equals(resolutionStrategy)) {
        aotRejectionReasons.add(
            "METHOD_CALL".equals(expressionKind) ? "METHOD_NOT_FOUND" : "PROPERTY_NOT_FOUND");
      } else if ("DENIED".equals(resolutionStrategy)) {
        aotRejectionReasons.add("DENIED_BY_SECURITY_POLICY");
      } else if (!isTyped && "DYNAMIC".equals(symbolOrigin)) {
        aotRejectionReasons.add("UNTYPED_TEMPLATE");
      } else if (!directAccess) {
        aotRejectionReasons.add(
            "METHOD_CALL".equals(expressionKind)
                ? "DYNAMIC_METHOD_CALL"
                : "DYNAMIC_MEMBER_RESOLUTION");
      } else {
        aotRejectionReasons.add("DYNAMIC_FALLBACK");
      }
    }

    Optional<String> outputDispatch = Optional.empty();
    Optional<String> outputMethod = Optional.empty();
    Optional<String> escaping = Optional.empty();
    List<String> optimizationRejections = List.of();

    if (isOutput && wv != null) {
      WriteDispatchDecision decision =
          OutputSpecializationDecider.decide(wv.value(), wv.nullMode(), specContext);
      outputDispatch = Optional.of(decision.kind().name());
      outputMethod = Optional.of(decision.selectedPath());
      escaping = Optional.of(wv.escapeMode().name());
      optimizationRejections = decision.specializationRejections();
    }

    Optional<String> securityPolicy =
        "DENIED".equals(resolutionStrategy)
            ? Optional.of("DENIED_BY_POLICY")
            : Optional.of("STANDARD");

    return new ExpressionExplanation(
        span,
        sourceText,
        expressionKind,
        inferredType,
        typeConfidence,
        nullability,
        symbolOrigin,
        receiverType,
        resolvedMember,
        resolutionStrategy,
        directAccess,
        aotEligible,
        aotRejectionReasons,
        outputDispatch,
        outputMethod,
        escaping,
        securityPolicy,
        optimizationRejections);
  }

  private static boolean matchesSpanFilter(
      SourceSpan span, OptionalInt lineFilter, OptionalInt columnFilter) {
    if (!span.isKnown()) {
      return false;
    }
    if (lineFilter.isPresent()) {
      int line = lineFilter.getAsInt();
      if (line < span.startLine() || line > span.endLine()) {
        return false;
      }
    }
    if (columnFilter.isPresent()) {
      int col = columnFilter.getAsInt();
      if (lineFilter.isPresent()) {
        int line = lineFilter.getAsInt();
        if (span.startLine() == span.endLine()) {
          if (col < span.startColumn() || col > span.endColumn()) {
            return false;
          }
        } else if (line == span.startLine()) {
          if (col < span.startColumn()) {
            return false;
          }
        } else if (line == span.endLine()) {
          if (col > span.endColumn()) {
            return false;
          }
        }
      } else {
        if (col < span.startColumn() || col > span.endColumn()) {
          return false;
        }
      }
    }
    return true;
  }

  private static Map<TemplateId, DiscoveredTemplate> scanTemplates(TemplateExplainRequest request) {
    Map<TemplateId, DiscoveredTemplate> discovered = new LinkedHashMap<>();
    List<String> includes = request.includePatterns();
    List<String> excludes = request.excludePatterns();
    Optional<String> filterTemplate = request.template();

    for (Path srcDir : request.sourceDirectories()) {
      Path absSrc = srcDir.toAbsolutePath().normalize();
      if (!Files.exists(absSrc) || !Files.isDirectory(absSrc)) {
        continue;
      }

      try (Stream<Path> stream = Files.walk(absSrc)) {
        List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
        for (Path file : files) {
          Path rel = absSrc.relativize(file);
          if (rel.startsWith("..") || !file.toAbsolutePath().normalize().startsWith(absSrc)) {
            throw new SecurityException("Path traversal outside source directory: " + file);
          }
          String relStr = rel.toString().replace('\\', '/');
          if (!TemplateId.isTraversalSafe(relStr)) {
            throw new SecurityException("Path traversal detected in template path: " + relStr);
          }

          if (matchesAny(rel, includes) && !matchesAny(rel, excludes)) {
            TemplateId templateId = TemplateId.normalize(relStr);
            if (filterTemplate.isPresent()) {
              String filter = filterTemplate.get();
              if (!templateId.value().equals(filter)
                  && !relStr.equals(filter)
                  && !relStr.endsWith("/" + filter)) {
                continue;
              }
            }
            discovered.putIfAbsent(templateId, new DiscoveredTemplate(templateId, relStr, file));
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to scan source directory: " + absSrc, e);
      }
    }

    return discovered;
  }

  private static boolean matchesAny(Path path, List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return false;
    }
    for (String pattern : patterns) {
      if (matchesGlob(path, pattern)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesGlob(Path relativePath, String pattern) {
    FileSystem fs = FileSystems.getDefault();
    if (fs.getPathMatcher("glob:" + pattern).matches(relativePath)) {
      return true;
    }
    if (pattern.startsWith("**/")) {
      String subPattern = pattern.substring(3);
      if (fs.getPathMatcher("glob:" + subPattern).matches(relativePath)) {
        return true;
      }
      if (subPattern.contains("/**/")) {
        if (fs.getPathMatcher("glob:" + subPattern.replace("/**/", "/")).matches(relativePath)) {
          return true;
        }
      }
    }
    if (pattern.contains("/**/")) {
      if (fs.getPathMatcher("glob:" + pattern.replace("/**/", "/")).matches(relativePath)) {
        return true;
      }
    }
    return false;
  }

  private record MemberResInfo(String kindName, VType resultType, Optional<String> memberName) {}

  private record MethodResInfo(
      String kindName,
      VType returnType,
      Optional<String> methodName,
      boolean specializationStable) {}

  @SuppressWarnings("unchecked")
  private static Optional<MemberResInfo> readMemberResolution(Optional<?> optRes) {
    if (optRes == null || optRes.isEmpty()) {
      return Optional.empty();
    }
    Object res = optRes.get();
    try {
      Method kindMethod = res.getClass().getMethod("kind");
      Object kindObj = kindMethod.invoke(res);
      String kindName = kindObj != null ? kindObj.toString() : "DYNAMIC";

      Method resultTypeMethod = res.getClass().getMethod("resultType");
      VType resultType = (VType) resultTypeMethod.invoke(res);

      Method targetMemberMethod = res.getClass().getMethod("targetMember");
      Optional<Member> targetMember = (Optional<Member>) targetMemberMethod.invoke(res);
      Optional<String> memberName = targetMember.map(Member::getName);

      return Optional.of(new MemberResInfo(kindName, resultType, memberName));
    } catch (ReflectiveOperationException e) {
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private static Optional<MethodResInfo> readMethodResolution(Optional<?> optRes) {
    if (optRes == null || optRes.isEmpty()) {
      return Optional.empty();
    }
    Object res = optRes.get();
    try {
      Method kindMethod = res.getClass().getMethod("kind");
      Object kindObj = kindMethod.invoke(res);
      String kindName = kindObj != null ? kindObj.toString() : "DYNAMIC";

      Method returnTypeMethod = res.getClass().getMethod("returnType");
      VType returnType = (VType) returnTypeMethod.invoke(res);

      Method targetMethodMethod = res.getClass().getMethod("targetMethod");
      Optional<Method> targetMethod = (Optional<Method>) targetMethodMethod.invoke(res);
      Optional<String> methodName = targetMethod.map(Method::getName);

      Method specStableMethod = res.getClass().getMethod("isSpecializationStable");
      boolean specStable = (Boolean) specStableMethod.invoke(res);

      return Optional.of(new MethodResInfo(kindName, returnType, methodName, specStable));
    } catch (ReflectiveOperationException e) {
      return Optional.empty();
    }
  }

  private static String getSymbolScopeKind(Object symbol) {
    if (symbol == null) {
      return "DYNAMIC";
    }
    try {
      Method m = symbol.getClass().getMethod("scopeKind");
      Object kind = m.invoke(symbol);
      return kind != null ? kind.toString() : "DYNAMIC";
    } catch (ReflectiveOperationException e) {
      return "DYNAMIC";
    }
  }

  private static VType getSymbolVType(Object symbol) {
    if (symbol == null) {
      return null;
    }
    try {
      Method m = symbol.getClass().getMethod("type");
      return (VType) m.invoke(symbol);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private static VType readModelParameterType(Object modelParam) {
    if (modelParam == null) {
      return null;
    }
    try {
      Method m = modelParam.getClass().getMethod("type");
      return (VType) m.invoke(modelParam);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }
}
