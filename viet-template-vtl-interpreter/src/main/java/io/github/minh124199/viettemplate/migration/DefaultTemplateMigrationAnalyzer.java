package io.github.minh124199.viettemplate.migration;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAccessStep;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAssignmentTarget;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBinaryExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBinaryOperator;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlBlockDirectiveCallNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDecimalLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDefineDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDirectiveCallNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlDirectiveNode;
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
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMapEntry;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlMapLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNullLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlParseDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlRangeExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReference;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReferenceOutputNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlSetDirectiveNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlStringLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlUnaryExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.internal.engine.dependency.StaticDependencyExtractor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Package-private default implementation of {@link TemplateMigrationAnalyzer}. */
class DefaultTemplateMigrationAnalyzer implements TemplateMigrationAnalyzer {

  private static final String SOURCE_ENGINE = "Apache Velocity";
  private static final String SOURCE_VERSION = "2.4.1";
  private static final String TARGET_ENGINE = "Viet Template";
  private static final String TARGET_VERSION = "1.2.1-SNAPSHOT";

  record DiscoveredTemplate(TemplateId templateId, String relPath, Path file) {}

  record FindingKey(TemplateId templateId, SourceSpan span, String ruleId) {}

  @Override
  public MigrationReport analyze(TemplateMigrationRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    Map<TemplateId, DiscoveredTemplate> discovered = scanTemplates(request);
    List<DiscoveredTemplate> sortedTemplates = new ArrayList<>(discovered.values());
    sortedTemplates.sort(Comparator.comparing(d -> d.templateId().value()));

    List<TemplateAotDiagnostic> allDiagnostics = new ArrayList<>();
    List<SingleTemplateMigration> singleMigrations = new ArrayList<>();
    List<MigrationFinding> allFindings = new ArrayList<>();

    VtlProfile profile = request.profile().orElse(VtlProfile.VTL_MIGRATION);

    for (DiscoveredTemplate dt : sortedTemplates) {
      TemplateId templateId = dt.templateId();
      Path sourceFile = dt.file();
      List<TemplateAotDiagnostic> templateDiagnostics = new ArrayList<>();

      String sourceText;
      try {
        sourceText = Files.readString(sourceFile, request.encoding());
      } catch (IOException e) {
        TemplateAotDiagnostic readDiag =
            new TemplateAotDiagnostic(
                templateId,
                dt.relPath(),
                DiagnosticSeverity.ERROR,
                DiagnosticCode.of("VTLAOT", "1001"),
                "Failed to read template source: " + e.getMessage(),
                -1,
                -1,
                -1,
                -1);
        templateDiagnostics.add(readDiag);
        allDiagnostics.add(readDiag);
        singleMigrations.add(
            new SingleTemplateMigration(
                templateId, dt.relPath(), List.of(), templateDiagnostics, false, false));
        continue;
      }

      TemplateContract contract = request.contracts().get(templateId);
      if (contract == null) {
        contract =
            MigrationContractReader.findCompanion(
                    sourceFile, templateId, request.classLoader().orElse(null))
                .orElse(null);
      }

      SourceText source = SourceText.of(templateId, sourceText);
      VtlParseResult parseResult = VtlParser.parse(source);
      for (Diagnostic diag : parseResult.diagnostics()) {
        TemplateAotDiagnostic aotDiag = TemplateAotDiagnostic.from(templateId, dt.relPath(), diag);
        templateDiagnostics.add(aotDiag);
        allDiagnostics.add(aotDiag);
      }

      if (parseResult.hasErrors()) {
        singleMigrations.add(
            new SingleTemplateMigration(
                templateId, dt.relPath(), List.of(), templateDiagnostics, false, false));
        continue;
      }

      ModelSchema modelSchema = contract != null ? ModelSchema.fromContract(contract) : null;
      VtlProfile semProfile =
          profile == VtlProfile.VTL_SAFE ? VtlProfile.VTL_SAFE : VtlProfile.VTL_DYNAMIC;
      VtlSemanticOptions.Builder semOptionsBuilder =
          VtlSemanticOptions.builder()
              .profile(semProfile)
              .allowArbitraryMethods(profile.isArbitraryMethodsAllowed())
              .typeCheckingMode(request.typeCheckingMode());
      if (modelSchema != null) {
        semOptionsBuilder.modelSchema(modelSchema);
      }
      VtlSemanticOptions semOptions = semOptionsBuilder.build();

      SemanticAnalysisResult semResult =
          VtlSemanticAnalyzer.analyze(parseResult.template(), semOptions);
      for (Diagnostic diag : semResult.diagnostics()) {
        TemplateAotDiagnostic aotDiag = TemplateAotDiagnostic.from(templateId, dt.relPath(), diag);
        templateDiagnostics.add(aotDiag);
        allDiagnostics.add(aotDiag);
      }

      // Check static dependencies
      try {
        IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, semResult, semOptions);
        Set<TemplateDependency> deps =
            StaticDependencyExtractor.extract(
                ir, request.globalMacroLibraries(), request.layoutId());
        for (TemplateDependency dep : deps) {
          TemplateId targetId = dep.target();
          if (!existsTarget(targetId, dt, request.sourceDirectories(), discovered)) {
            TemplateAotDiagnostic missingDep =
                new TemplateAotDiagnostic(
                    templateId,
                    dt.relPath(),
                    DiagnosticSeverity.ERROR,
                    DiagnosticCode.of("RESOURCE", "NOT_FOUND"),
                    "Referenced template dependency not found: " + targetId.value(),
                    -1,
                    -1,
                    -1,
                    -1);
            templateDiagnostics.add(missingDep);
            allDiagnostics.add(missingDep);
          }
        }
      } catch (RuntimeException ignored) {
        // AST analysis proceeds independently even if lowering encounters dynamic-heavy patterns
      }

      // AST visitor inspection for migration findings
      Set<String> localSymbols = collectLocalSymbols(parseResult.template());
      List<MigrationFinding> rawFindings = new ArrayList<>();
      Map<FindingKey, MigrationFinding> deduplicated = new LinkedHashMap<>();

      analyzeNode(
          parseResult.template(),
          templateId,
          profile,
          contract,
          localSymbols,
          semResult,
          request,
          rawFindings);

      for (MigrationFinding f : rawFindings) {
        FindingKey key = new FindingKey(f.templateId(), f.sourceSpan(), f.ruleId());
        deduplicated.putIfAbsent(key, f);
      }

      List<MigrationFinding> templateFindings = new ArrayList<>();
      for (MigrationFinding f : deduplicated.values()) {
        if (f.severity().ordinal() >= request.minimumSeverity().ordinal()) {
          templateFindings.add(f);
        }
      }

      templateFindings.sort(
          Comparator.comparing((MigrationFinding f) -> f.templateId().value())
              .thenComparingInt(f -> f.sourceSpan() != null ? f.sourceSpan().startLine() : 0)
              .thenComparingInt(f -> f.sourceSpan() != null ? f.sourceSpan().startColumn() : 0)
              .thenComparingInt(f -> f.severity().ordinal())
              .thenComparing(f -> f.category().name())
              .thenComparing(MigrationFinding::ruleId));

      allFindings.addAll(templateFindings);

      boolean hasErrorDiag =
          templateDiagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
      boolean valid = !parseResult.hasErrors() && !hasErrorDiag;
      boolean compatible =
          valid
              && templateFindings.stream()
                  .noneMatch(f -> f.severity() == MigrationSeverity.BLOCKER);

      singleMigrations.add(
          new SingleTemplateMigration(
              templateId, dt.relPath(), templateFindings, templateDiagnostics, valid, compatible));
    }

    allFindings.sort(
        Comparator.comparing((MigrationFinding f) -> f.templateId().value())
            .thenComparingInt(f -> f.sourceSpan() != null ? f.sourceSpan().startLine() : 0)
            .thenComparingInt(f -> f.sourceSpan() != null ? f.sourceSpan().startColumn() : 0)
            .thenComparingInt(f -> f.severity().ordinal())
            .thenComparing(f -> f.category().name())
            .thenComparing(MigrationFinding::ruleId));

    allDiagnostics.sort(
        Comparator.comparing((TemplateAotDiagnostic d) -> d.templateId().value())
            .thenComparingInt(TemplateAotDiagnostic::startLine)
            .thenComparingInt(TemplateAotDiagnostic::startColumn)
            .thenComparing(d -> d.code().qualifiedCode())
            .thenComparing(TemplateAotDiagnostic::message));

    // Summary calculation
    int totalTemplates = singleMigrations.size();
    int validTemplates =
        (int) singleMigrations.stream().filter(SingleTemplateMigration::valid).count();
    int compatibleTemplates =
        (int) singleMigrations.stream().filter(SingleTemplateMigration::compatible).count();
    int totalFindings = allFindings.size();

    Map<MigrationSeverity, Integer> findingsBySeverity = new LinkedHashMap<>();
    for (MigrationSeverity s : MigrationSeverity.values()) {
      findingsBySeverity.put(s, 0);
    }
    for (MigrationFinding f : allFindings) {
      findingsBySeverity.compute(f.severity(), (k, v) -> (v == null ? 0 : v) + 1);
    }

    Map<MigrationCategory, Integer> findingsByCategory = new LinkedHashMap<>();
    for (MigrationCategory c : MigrationCategory.values()) {
      findingsByCategory.put(c, 0);
    }
    for (MigrationFinding f : allFindings) {
      findingsByCategory.compute(f.category(), (k, v) -> (v == null ? 0 : v) + 1);
    }

    boolean hasBlockers = findingsBySeverity.getOrDefault(MigrationSeverity.BLOCKER, 0) > 0;
    boolean hasValidationErrors =
        allDiagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    boolean allValid = totalTemplates == 0 || validTemplates == totalTemplates;
    boolean hasErrors = findingsBySeverity.getOrDefault(MigrationSeverity.ERROR, 0) > 0;
    boolean hasDynamicFindings =
        allFindings.stream()
            .anyMatch(
                f ->
                    f.classification() == MigrationClassification.DYNAMICALLY_UNVERIFIABLE
                        || f.confidence() == MigrationConfidence.DYNAMICALLY_UNVERIFIABLE);
    boolean hasWarnings = findingsBySeverity.getOrDefault(MigrationSeverity.WARNING, 0) > 0;

    MigrationReadinessStatus readinessStatus;
    if (hasBlockers || hasValidationErrors || !allValid) {
      readinessStatus = MigrationReadinessStatus.BLOCKED;
    } else if (hasErrors || hasDynamicFindings) {
      readinessStatus = MigrationReadinessStatus.ATTENTION_REQUIRED;
    } else if (hasWarnings) {
      readinessStatus = MigrationReadinessStatus.READY_WITH_WARNINGS;
    } else {
      readinessStatus = MigrationReadinessStatus.READY;
    }

    boolean success = true;
    if (hasValidationErrors || !allValid) {
      success = false;
    }
    if (request.failOnBlocker() && hasBlockers) {
      success = false;
    }
    if (request.failOnWarning() && (hasWarnings || hasErrors)) {
      success = false;
    }

    MigrationSummary summary =
        new MigrationSummary(
            totalTemplates,
            validTemplates,
            compatibleTemplates,
            totalFindings,
            readinessStatus,
            findingsBySeverity,
            findingsByCategory);

    MigrationReport report =
        new MigrationReport(
            SOURCE_ENGINE,
            SOURCE_VERSION,
            TARGET_ENGINE,
            TARGET_VERSION,
            summary,
            singleMigrations,
            allFindings,
            allDiagnostics,
            readinessStatus,
            success);

    if (request.outputFile().isPresent()) {
      Path out = request.outputFile().get();
      try {
        if (out.getParent() != null) {
          Files.createDirectories(out.getParent());
        }
        String content =
            "json".equalsIgnoreCase(request.format()) ? report.asJson() : report.asText();
        Files.writeString(out, content, request.encoding());
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to write migration report to: " + out, e);
      }
    }

    return report;
  }

  // AST Analysis Visitor
  private void analyzeNode(
      VtlNode node,
      TemplateId templateId,
      VtlProfile profile,
      TemplateContract contract,
      Set<String> localSymbols,
      SemanticAnalysisResult semResult,
      TemplateMigrationRequest request,
      List<MigrationFinding> findings) {
    if (node == null) {
      return;
    }

    if (node instanceof VtlTemplate tmpl) {
      for (VtlNode child : tmpl.children()) {
        analyzeNode(
            child, templateId, profile, contract, localSymbols, semResult, request, findings);
      }
    } else if (node instanceof VtlReferenceOutputNode refOut) {
      analyzeReference(
          refOut.reference(), templateId, profile, contract, localSymbols, request, findings);
    } else if (node instanceof VtlDirectiveNode dir) {
      analyzeDirective(
          dir, templateId, profile, contract, localSymbols, semResult, request, findings);
    }
  }

  private void analyzeDirective(
      VtlDirectiveNode dir,
      TemplateId templateId,
      VtlProfile profile,
      TemplateContract contract,
      Set<String> localSymbols,
      SemanticAnalysisResult semResult,
      TemplateMigrationRequest request,
      List<MigrationFinding> findings) {
    if (dir instanceof VtlSetDirectiveNode setNode) {
      analyzeExpression(
          setNode.value(), templateId, profile, contract, localSymbols, request, findings);
      if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
        analyzeReference(
            refTarget.reference(), templateId, profile, contract, localSymbols, request, findings);
      }
      if (isSetNullOrUndefinedRhs(setNode, contract, semResult)) {
        MigrationRule rule = MigrationRuleRegistry.RULE_SET_NULL_RHS;
        findings.add(
            new MigrationFinding(
                rule.id(),
                templateId,
                setNode.span(),
                rule.category(),
                rule.defaultSeverity(),
                rule.classification(),
                rule.confidence(),
                "#set(..., null)",
                rule.title()
                    + ": #set with null or undefined RHS assigns null in Velocity 2.x, but"
                    + " preserves previous value if ignoreSetNullRhs is enabled.",
                rule.velocityBehavior(),
                rule.vietTemplateBehavior(),
                rule.migrationAction(),
                Optional.empty()));
      }
    } else if (dir instanceof VtlIfDirectiveNode ifNode) {
      for (VtlIfBranch branch : ifNode.branches()) {
        analyzeExpression(
            branch.condition(), templateId, profile, contract, localSymbols, request, findings);
        for (VtlNode child : branch.body()) {
          analyzeNode(
              child, templateId, profile, contract, localSymbols, semResult, request, findings);
        }
      }
      if (ifNode.elseBody().isPresent()) {
        for (VtlNode child : ifNode.elseBody().get()) {
          analyzeNode(
              child, templateId, profile, contract, localSymbols, semResult, request, findings);
        }
      }
    } else if (dir instanceof VtlForeachDirectiveNode feNode) {
      analyzeExpression(
          feNode.iterable(), templateId, profile, contract, localSymbols, request, findings);
      for (VtlNode child : feNode.body()) {
        analyzeNode(
            child, templateId, profile, contract, localSymbols, semResult, request, findings);
      }
      if (feNode.elseBody().isPresent()) {
        for (VtlNode child : feNode.elseBody().get()) {
          analyzeNode(
              child, templateId, profile, contract, localSymbols, semResult, request, findings);
        }
      }
    } else if (dir instanceof VtlParseDirectiveNode parseNode) {
      analyzeExpression(
          parseNode.templateExpression(),
          templateId,
          profile,
          contract,
          localSymbols,
          request,
          findings);
      if (!(parseNode.templateExpression() instanceof VtlStringLiteralExpression)) {
        MigrationRule rule = MigrationRuleRegistry.RULE_PARSE_DYNAMIC;
        findings.add(
            new MigrationFinding(
                rule.id(),
                templateId,
                parseNode.span(),
                rule.category(),
                rule.defaultSeverity(),
                rule.classification(),
                rule.confidence(),
                "#parse(...)",
                "Dynamic template target in #parse cannot be verified statically at build time.",
                rule.velocityBehavior(),
                rule.vietTemplateBehavior(),
                rule.migrationAction(),
                Optional.empty()));
      }
    } else if (dir instanceof VtlIncludeDirectiveNode incNode) {
      for (VtlExpression arg : incNode.arguments()) {
        analyzeExpression(arg, templateId, profile, contract, localSymbols, request, findings);
        if (!(arg instanceof VtlStringLiteralExpression)) {
          MigrationRule rule = MigrationRuleRegistry.RULE_INCLUDE_DYNAMIC;
          findings.add(
              new MigrationFinding(
                  rule.id(),
                  templateId,
                  incNode.span(),
                  rule.category(),
                  rule.defaultSeverity(),
                  rule.classification(),
                  rule.confidence(),
                  "#include(...)",
                  "Dynamic resource target in #include cannot be verified statically at build"
                      + " time.",
                  rule.velocityBehavior(),
                  rule.vietTemplateBehavior(),
                  rule.migrationAction(),
                  Optional.empty()));
        }
      }
    } else if (dir instanceof VtlEvaluateDirectiveNode evalNode) {
      analyzeExpression(
          evalNode.expression(), templateId, profile, contract, localSymbols, request, findings);
      MigrationRule rule = MigrationRuleRegistry.RULE_EVALUATE_DYNAMIC;
      if (profile == VtlProfile.VTL_SAFE) {
        findings.add(
            new MigrationFinding(
                rule.id(),
                templateId,
                evalNode.span(),
                rule.category(),
                MigrationSeverity.BLOCKER,
                MigrationClassification.SECURITY_RESTRICTED,
                MigrationConfidence.STATICALLY_VERIFIED,
                "#evaluate(...)",
                "#evaluate directive is forbidden in VTL_SAFE security profile due to dynamic code"
                    + " execution risks.",
                rule.velocityBehavior(),
                rule.vietTemplateBehavior(),
                rule.migrationAction(),
                Optional.of(DiagnosticCode.of("SECURITY", "ACCESS_DENIED"))));
      } else {
        findings.add(
            new MigrationFinding(
                rule.id(),
                templateId,
                evalNode.span(),
                rule.category(),
                rule.defaultSeverity(),
                rule.classification(),
                rule.confidence(),
                "#evaluate(...)",
                "#evaluate directive executes dynamic runtime VTL and cannot be statically analyzed"
                    + " or compiled AOT.",
                rule.velocityBehavior(),
                rule.vietTemplateBehavior(),
                rule.migrationAction(),
                Optional.empty()));
      }
    } else if (dir instanceof VtlDefineDirectiveNode defNode) {
      for (VtlNode child : defNode.body()) {
        analyzeNode(
            child, templateId, profile, contract, localSymbols, semResult, request, findings);
      }
    } else if (dir instanceof VtlMacroDefinitionNode macroNode) {
      for (VtlNode child : macroNode.body()) {
        analyzeNode(
            child, templateId, profile, contract, localSymbols, semResult, request, findings);
      }
    } else if (dir instanceof VtlDirectiveCallNode callNode) {
      for (VtlExpression arg : callNode.arguments()) {
        analyzeExpression(arg, templateId, profile, contract, localSymbols, request, findings);
      }
    } else if (dir instanceof VtlBlockDirectiveCallNode blockCallNode) {
      for (VtlExpression arg : blockCallNode.arguments()) {
        analyzeExpression(arg, templateId, profile, contract, localSymbols, request, findings);
      }
      for (VtlNode child : blockCallNode.body()) {
        analyzeNode(
            child, templateId, profile, contract, localSymbols, semResult, request, findings);
      }
    }
  }

  private void analyzeExpression(
      VtlExpression expr,
      TemplateId templateId,
      VtlProfile profile,
      TemplateContract contract,
      Set<String> localSymbols,
      TemplateMigrationRequest request,
      List<MigrationFinding> findings) {
    if (expr == null) {
      return;
    }

    if (expr instanceof VtlBinaryExpression bin) {
      analyzeExpression(bin.left(), templateId, profile, contract, localSymbols, request, findings);
      analyzeExpression(
          bin.right(), templateId, profile, contract, localSymbols, request, findings);

      if (bin.operator() == VtlBinaryOperator.DIVIDE
          || bin.operator() == VtlBinaryOperator.MODULO) {
        MigrationRule rule = MigrationRuleRegistry.RULE_ARITH_DIV_ZERO;
        if (isLiteralZero(bin.right())) {
          findings.add(
              new MigrationFinding(
                  rule.id(),
                  templateId,
                  bin.span(),
                  rule.category(),
                  MigrationSeverity.BLOCKER,
                  MigrationClassification.KNOWN_BEHAVIOR_DIFFERENCE,
                  MigrationConfidence.STATICALLY_VERIFIED,
                  bin.operator().symbol() + " 0",
                  "Division or modulo by literal zero fails fast with DIVISION_BY_ZERO in Viet"
                      + " Template instead of returning null.",
                  rule.velocityBehavior(),
                  rule.vietTemplateBehavior(),
                  rule.migrationAction(),
                  Optional.of(DiagnosticCode.of("INTERPRETER", "ERROR"))));
        } else if (!isNonZeroLiteral(bin.right())) {
          findings.add(
              new MigrationFinding(
                  rule.id(),
                  templateId,
                  bin.span(),
                  rule.category(),
                  MigrationSeverity.WARNING,
                  MigrationClassification.KNOWN_BEHAVIOR_DIFFERENCE,
                  MigrationConfidence.DYNAMICALLY_UNVERIFIABLE,
                  bin.operator().symbol() + " ...",
                  "Division or modulo with dynamic divisor may throw DIVISION_BY_ZERO in Viet"
                      + " Template if divisor evaluates to zero, whereas Velocity silently returns"
                      + " null.",
                  rule.velocityBehavior(),
                  rule.vietTemplateBehavior(),
                  rule.migrationAction(),
                  Optional.of(DiagnosticCode.of("INTERPRETER", "ERROR"))));
        }
      }
    } else if (expr instanceof VtlUnaryExpression un) {
      analyzeExpression(
          un.operand(), templateId, profile, contract, localSymbols, request, findings);
    } else if (expr instanceof VtlGroupedExpression grp) {
      analyzeExpression(
          grp.expression(), templateId, profile, contract, localSymbols, request, findings);
    } else if (expr instanceof VtlReferenceExpression refExpr) {
      analyzeReference(
          refExpr.reference(), templateId, profile, contract, localSymbols, request, findings);
    } else if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : interp.parts()) {
        if (part
            instanceof
            VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart refPart) {
          analyzeReference(
              refPart.reference(), templateId, profile, contract, localSymbols, request, findings);
        }
      }
    } else if (expr instanceof VtlListLiteralExpression listLit) {
      for (VtlExpression el : listLit.elements()) {
        analyzeExpression(el, templateId, profile, contract, localSymbols, request, findings);
      }
    } else if (expr instanceof VtlMapLiteralExpression mapLit) {
      for (VtlMapEntry entry : mapLit.entries()) {
        analyzeExpression(
            entry.key(), templateId, profile, contract, localSymbols, request, findings);
        analyzeExpression(
            entry.value(), templateId, profile, contract, localSymbols, request, findings);
      }
    } else if (expr instanceof VtlRangeExpression range) {
      analyzeExpression(
          range.start(), templateId, profile, contract, localSymbols, request, findings);
      analyzeExpression(
          range.end(), templateId, profile, contract, localSymbols, request, findings);
    }
  }

  private void analyzeReference(
      VtlReference ref,
      TemplateId templateId,
      VtlProfile profile,
      TemplateContract contract,
      Set<String> localSymbols,
      TemplateMigrationRequest request,
      List<MigrationFinding> findings) {
    if (ref == null) {
      return;
    }

    // 1. Extension: alternate value ${var|'default'}
    if (ref.alternateValue().isPresent()) {
      MigrationRule rule = MigrationRuleRegistry.RULE_EXT_ALT_VALUE;
      findings.add(
          new MigrationFinding(
              rule.id(),
              templateId,
              ref.span(),
              rule.category(),
              rule.defaultSeverity(),
              rule.classification(),
              rule.confidence(),
              "${" + ref.rootName() + "|...}",
              "Alternate default value syntax ${...|...} is a Viet Template extension not supported"
                  + " in Apache Velocity 2.4.1.",
              rule.velocityBehavior(),
              rule.vietTemplateBehavior(),
              rule.migrationAction(),
              Optional.empty()));
      analyzeExpression(
          ref.alternateValue().get(),
          templateId,
          profile,
          contract,
          localSymbols,
          request,
          findings);
    }

    // 2. Extension: $foreach.stop()
    if ("foreach".equals(ref.rootName()) && ref.steps().size() == 1) {
      if (ref.steps().get(0) instanceof VtlAccessStep.MethodCall mc
          && "stop".equals(mc.methodName())
          && mc.arguments().isEmpty()) {
        MigrationRule rule = MigrationRuleRegistry.RULE_EXT_FOREACH_STOP;
        findings.add(
            new MigrationFinding(
                rule.id(),
                templateId,
                ref.span(),
                rule.category(),
                rule.defaultSeverity(),
                rule.classification(),
                rule.confidence(),
                "$foreach.stop()",
                "$foreach.stop() is a Viet Template extension for legacy programmatic loop"
                    + " termination; Velocity 2.4.1 requires #break.",
                rule.velocityBehavior(),
                rule.vietTemplateBehavior(),
                rule.migrationAction(),
                Optional.empty()));
      }
    }

    // 3. Security: .class, getClass(), getClassLoader()
    for (VtlAccessStep step : ref.steps()) {
      if (step instanceof VtlAccessStep.PropertyAccess pa) {
        if ("class".equals(pa.propertyName()) || "classLoader".equals(pa.propertyName())) {
          MigrationRule rule = MigrationRuleRegistry.RULE_SEC_CLASS_ACCESS;
          findings.add(
              new MigrationFinding(
                  rule.id(),
                  templateId,
                  pa.span(),
                  rule.category(),
                  MigrationSeverity.BLOCKER,
                  MigrationClassification.SECURITY_RESTRICTED,
                  MigrationConfidence.STATICALLY_VERIFIED,
                  "." + pa.propertyName(),
                  "Access to property '."
                      + pa.propertyName()
                      + "' is restricted by security policy to prevent reflection sandbox escapes.",
                  rule.velocityBehavior(),
                  rule.vietTemplateBehavior(),
                  rule.migrationAction(),
                  Optional.of(DiagnosticCode.of("VTLSEC", "2401"))));
        }
      } else if (step instanceof VtlAccessStep.MethodCall mc) {
        if ("getClass".equals(mc.methodName()) || "getClassLoader".equals(mc.methodName())) {
          MigrationRule rule = MigrationRuleRegistry.RULE_SEC_CLASS_ACCESS;
          findings.add(
              new MigrationFinding(
                  rule.id(),
                  templateId,
                  mc.span(),
                  rule.category(),
                  MigrationSeverity.BLOCKER,
                  MigrationClassification.SECURITY_RESTRICTED,
                  MigrationConfidence.STATICALLY_VERIFIED,
                  "." + mc.methodName() + "()",
                  "Invocation of method '."
                      + mc.methodName()
                      + "()' is restricted by security policy to prevent reflection sandbox"
                      + " escapes.",
                  rule.velocityBehavior(),
                  rule.vietTemplateBehavior(),
                  rule.migrationAction(),
                  Optional.of(DiagnosticCode.of("VTLSEC", "2401"))));
        } else if (profile == VtlProfile.VTL_SAFE
            && !("foreach".equals(ref.rootName()) && "stop".equals(mc.methodName()))) {
          MigrationRule rule = MigrationRuleRegistry.RULE_SEC_SAFE_PROFILE;
          findings.add(
              new MigrationFinding(
                  rule.id(),
                  templateId,
                  mc.span(),
                  rule.category(),
                  MigrationSeverity.BLOCKER,
                  MigrationClassification.SECURITY_RESTRICTED,
                  MigrationConfidence.STATICALLY_VERIFIED,
                  "." + mc.methodName() + "()",
                  "Arbitrary method invocation '."
                      + mc.methodName()
                      + "()' is prohibited in VTL_SAFE profile.",
                  rule.velocityBehavior(),
                  rule.vietTemplateBehavior(),
                  rule.migrationAction(),
                  Optional.of(DiagnosticCode.of("SECURITY", "ACCESS_DENIED"))));
        }

        for (VtlExpression arg : mc.arguments()) {
          analyzeExpression(arg, templateId, profile, contract, localSymbols, request, findings);
        }
      } else if (step instanceof VtlAccessStep.IndexAccess idx) {
        analyzeExpression(
            idx.indexExpression(), templateId, profile, contract, localSymbols, request, findings);
      }
    }

    // 4. Strict references check
    if (request.strictReferences() && !"foreach".equals(ref.rootName())) {
      boolean locallyDefined = localSymbols.contains(ref.rootName());
      if (contract != null) {
        boolean inContract = contract.hasParameter(ref.rootName());
        if (!inContract && !locallyDefined) {
          MigrationRule rule = MigrationRuleRegistry.RULE_STRICT_REF;
          findings.add(
              new MigrationFinding(
                  rule.id(),
                  templateId,
                  ref.span(),
                  rule.category(),
                  MigrationSeverity.WARNING,
                  rule.classification(),
                  rule.confidence(),
                  "$" + ref.rootName(),
                  "Reference '$"
                      + ref.rootName()
                      + "' is not declared in template contract and will throw"
                      + " TemplateRenderException under strictReferences mode if undefined or"
                      + " null.",
                  rule.velocityBehavior(),
                  rule.vietTemplateBehavior(),
                  rule.migrationAction(),
                  Optional.of(DiagnosticCode.of("INTERPRETER", "VARIABLE_UNDEFINED"))));
        }
      } else {
        if (!locallyDefined) {
          MigrationRule rule = MigrationRuleRegistry.RULE_STRICT_REF;
          findings.add(
              new MigrationFinding(
                  rule.id(),
                  templateId,
                  ref.span(),
                  rule.category(),
                  MigrationSeverity.WARNING,
                  rule.classification(),
                  rule.confidence(),
                  "$" + ref.rootName(),
                  "Reference '$"
                      + ref.rootName()
                      + "' has no contract declaration and will throw TemplateRenderException under"
                      + " strictReferences mode if undefined at runtime.",
                  rule.velocityBehavior(),
                  rule.vietTemplateBehavior(),
                  rule.migrationAction(),
                  Optional.of(DiagnosticCode.of("INTERPRETER", "VARIABLE_UNDEFINED"))));
        }
      }
    }
  }

  private static boolean isLiteralZero(VtlExpression expr) {
    VtlExpression unwrapped = unwrapGrouped(expr);
    if (unwrapped instanceof VtlIntegerLiteralExpression intLit) {
      return intLit.value().equals(BigInteger.ZERO);
    }
    if (unwrapped instanceof VtlDecimalLiteralExpression decLit) {
      return decLit.value().compareTo(BigDecimal.ZERO) == 0;
    }
    if (unwrapped instanceof VtlUnaryExpression un) {
      return isLiteralZero(un.operand());
    }
    return false;
  }

  private static boolean isNonZeroLiteral(VtlExpression expr) {
    VtlExpression unwrapped = unwrapGrouped(expr);
    if (unwrapped instanceof VtlIntegerLiteralExpression intLit) {
      return !intLit.value().equals(BigInteger.ZERO);
    }
    if (unwrapped instanceof VtlDecimalLiteralExpression decLit) {
      return decLit.value().compareTo(BigDecimal.ZERO) != 0;
    }
    if (unwrapped instanceof VtlUnaryExpression un) {
      return isNonZeroLiteral(un.operand());
    }
    return false;
  }

  private static VtlExpression unwrapGrouped(VtlExpression expr) {
    while (expr instanceof VtlGroupedExpression grp) {
      expr = grp.expression();
    }
    return expr;
  }

  private static boolean isSetNullOrUndefinedRhs(
      VtlSetDirectiveNode setNode, TemplateContract contract, SemanticAnalysisResult semResult) {
    VtlExpression val = unwrapGrouped(setNode.value());
    if (val instanceof VtlNullLiteralExpression) {
      return true;
    }
    if (semResult != null) {
      VType type = semResult.expressionTypes().get(val);
      if (type != null && type.isNull()) {
        return true;
      }
    }
    if (val instanceof VtlReferenceExpression refExpr) {
      String name = refExpr.reference().rootName();
      if ("null".equals(name) || "undefined".equals(name) || "missing".equals(name)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> collectLocalSymbols(VtlTemplate template) {
    Set<String> symbols = new HashSet<>();
    symbols.add("foreach");
    symbols.add("velocityCount");
    symbols.add("velocityHasNext");
    collectLocalSymbolsRecursive(template, symbols);
    return symbols;
  }

  private static void collectLocalSymbolsRecursive(VtlNode node, Set<String> symbols) {
    if (node == null) {
      return;
    }
    if (node instanceof VtlTemplate tmpl) {
      for (VtlNode child : tmpl.children()) {
        collectLocalSymbolsRecursive(child, symbols);
      }
    } else if (node instanceof VtlSetDirectiveNode setNode) {
      if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
        symbols.add(refTarget.reference().rootName());
      }
    } else if (node instanceof VtlForeachDirectiveNode feNode) {
      symbols.add(feNode.loopVariable().rootName());
      for (VtlNode child : feNode.body()) {
        collectLocalSymbolsRecursive(child, symbols);
      }
      feNode
          .elseBody()
          .ifPresent(
              eb -> {
                for (VtlNode child : eb) {
                  collectLocalSymbolsRecursive(child, symbols);
                }
              });
    } else if (node instanceof VtlIfDirectiveNode ifNode) {
      for (VtlIfBranch b : ifNode.branches()) {
        for (VtlNode child : b.body()) {
          collectLocalSymbolsRecursive(child, symbols);
        }
      }
      ifNode
          .elseBody()
          .ifPresent(
              eb -> {
                for (VtlNode child : eb) {
                  collectLocalSymbolsRecursive(child, symbols);
                }
              });
    } else if (node instanceof VtlDefineDirectiveNode defNode) {
      symbols.add(defNode.targetReference().rootName());
      for (VtlNode child : defNode.body()) {
        collectLocalSymbolsRecursive(child, symbols);
      }
    } else if (node instanceof VtlMacroDefinitionNode macroNode) {
      for (VtlMacroParameter param : macroNode.parameters()) {
        symbols.add(param.name());
      }
      for (VtlNode child : macroNode.body()) {
        collectLocalSymbolsRecursive(child, symbols);
      }
    } else if (node instanceof VtlBlockDirectiveCallNode blockCall) {
      for (VtlNode child : blockCall.body()) {
        collectLocalSymbolsRecursive(child, symbols);
      }
    }
  }

  private static boolean existsTarget(
      TemplateId targetId,
      DiscoveredTemplate current,
      List<Path> sourceDirectories,
      Map<TemplateId, DiscoveredTemplate> discovered) {
    if (discovered.containsKey(targetId)) {
      return true;
    }
    for (DiscoveredTemplate dt : discovered.values()) {
      if (dt.relPath().equals(targetId.value()) || dt.relPath().endsWith("/" + targetId.value())) {
        return true;
      }
    }
    for (Path src : sourceDirectories) {
      Path abs = src.toAbsolutePath().normalize().resolve(targetId.value()).normalize();
      if (Files.isRegularFile(abs)) {
        return true;
      }
    }
    return false;
  }

  private static Map<TemplateId, DiscoveredTemplate> scanTemplates(
      TemplateMigrationRequest request) {
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
}
