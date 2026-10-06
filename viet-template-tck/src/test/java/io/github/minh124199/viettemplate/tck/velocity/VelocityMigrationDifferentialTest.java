package io.github.minh124199.viettemplate.tck.velocity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.migration.MigrationClassification;
import io.github.minh124199.viettemplate.migration.MigrationFinding;
import io.github.minh124199.viettemplate.migration.MigrationReadinessStatus;
import io.github.minh124199.viettemplate.migration.MigrationReport;
import io.github.minh124199.viettemplate.migration.MigrationRule;
import io.github.minh124199.viettemplate.migration.MigrationRuleRegistry;
import io.github.minh124199.viettemplate.migration.MigrationSeverity;
import io.github.minh124199.viettemplate.migration.TemplateMigrationAnalyzer;
import io.github.minh124199.viettemplate.migration.TemplateMigrationRequest;
import io.github.minh124199.viettemplate.tck.probe.Velocity241Probe;
import io.github.minh124199.viettemplate.tck.velocity.corpus.CompatibilityCorpus;
import io.github.minh124199.viettemplate.tck.velocity.engine.EngineAdapter;
import io.github.minh124199.viettemplate.tck.velocity.engine.VietReferenceEngineAdapter;
import io.github.minh124199.viettemplate.tck.velocity.model.PersonBean;
import io.github.minh124199.viettemplate.tck.velocity.result.EngineResult;
import io.github.minh124199.viettemplate.tck.velocity.result.ExceptionCategory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Differential regression and oracle qualification test verifying Apache Velocity 2.4.1 runtime
 * parity, known differences, extensions, configuration dependencies, and migration analyzer rules.
 */
class VelocityMigrationDifferentialTest {

  private final EngineAdapter vietEngine = new VietReferenceEngineAdapter();
  private final TemplateMigrationAnalyzer analyzer = TemplateMigrationAnalyzer.create();

  private EngineResult executeViet(String template, Map<String, Object> context) {
    return executeViet(template, context, CompatibilityConfiguration.defaultConfiguration());
  }

  private EngineResult executeViet(
      String template, Map<String, Object> context, CompatibilityConfiguration configuration) {
    CompatibilityScenario scenario =
        CompatibilityScenario.simple("probe-test", ScenarioCategory.REFERENCE, template);
    return vietEngine.execute(scenario, context, configuration, Map.of());
  }

  private MigrationReport analyzeTemplate(String filename, String content, Path tempDir)
      throws IOException {
    Path file = tempDir.resolve(filename);
    Files.writeString(file, content);
    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).build();
    return analyzer.analyze(request);
  }

  @Test
  @DisplayName(
      "Exact Compatibility: standard expressions produce identical outputs on Velocity 2.4.1 and"
          + " Viet Template, and MigrationAnalyzer reports READY with 0 findings")
  void testExactCompatibilityStandardExpressions(@TempDir Path tempDir) throws IOException {
    // 1. Math expressions
    String mathTemplate = "#set($sum = $a + $b)Sum: $sum | Raw: $a + $b";
    Map<String, Object> mathCtx = Map.of("a", 15, "b", 27);
    String velMath = Velocity241Probe.render(mathTemplate, mathCtx).outputOrThrow();
    EngineResult vietMath = executeViet(mathTemplate, mathCtx);
    assertThat(vietMath.isSuccess()).isTrue();
    assertThat(vietMath.output()).isEqualTo(velMath);

    // 2. Bean getter property expressions
    String beanTemplate = "Hello, $user.name! Age: $user.age";
    Map<String, Object> beanCtx = Map.of("user", new PersonBean("Alice", 30, true, null));
    String velBean = Velocity241Probe.render(beanTemplate, beanCtx).outputOrThrow();
    EngineResult vietBean = executeViet(beanTemplate, beanCtx);
    assertThat(vietBean.isSuccess()).isTrue();
    assertThat(vietBean.output()).isEqualTo(velBean);

    // 3. Conditional directives (#if / #else)
    String ifTemplate = "Result: #if($flag)YES#else NO#end";
    for (boolean flag : List.of(true, false)) {
      Map<String, Object> ifCtx = Map.of("flag", flag);
      String velIf = Velocity241Probe.render(ifTemplate, ifCtx).outputOrThrow();
      EngineResult vietIf = executeViet(ifTemplate, ifCtx);
      assertThat(vietIf.isSuccess()).isTrue();
      assertThat(vietIf.output()).isEqualTo(velIf);
    }

    // 4. Foreach loop directive
    String foreachTemplate = "Items: #foreach($i in $list)[$i]#end";
    Map<String, Object> listCtx = Map.of("list", List.of("alpha", "beta", "gamma"));
    String velForeach = Velocity241Probe.render(foreachTemplate, listCtx).outputOrThrow();
    EngineResult vietForeach = executeViet(foreachTemplate, listCtx);
    assertThat(vietForeach.isSuccess()).isTrue();
    assertThat(vietForeach.output()).isEqualTo(velForeach);

    // 5. TemplateMigrationAnalyzer validation on standard templates
    String combinedTemplate =
        "#set($sum = $a + $b)\n"
            + "Sum: $sum | Raw: $a + $b\n"
            + "Hello, $user.name! Age: $user.age\n"
            + "#if($flag)YES#else NO#end\n"
            + "Items: #foreach($i in $list)[$i]#end\n";

    MigrationReport report = analyzeTemplate("exact_clean.vtl", combinedTemplate, tempDir);
    assertThat(report.success()).isTrue();
    assertThat(report.readinessStatus()).isEqualTo(MigrationReadinessStatus.READY);
    assertThat(report.summary().totalFindings()).isZero();
    assertThat(report.allFindings()).isEmpty();
    assertThat(report.validationDiagnostics()).isEmpty();
  }

  @Test
  @DisplayName(
      "DIFF-001: Division by zero succeeds in Velocity 2.4.1 (logs warning / returns null) vs"
          + " throws fast in Viet Template, reported as KNOWN_BEHAVIOR_DIFFERENCE by analyzer")
  void testDiff001DivisionByZero(@TempDir Path tempDir) throws IOException {
    String template = "#set($x = 10 / 0)[$x]";

    // Velocity 2.4.1 executes successfully without throwing, setting $x to null/undefined
    Velocity241Probe.Result velResult = Velocity241Probe.render(template);
    assertThat(velResult.isSuccess()).isTrue();
    assertThat(velResult.output()).isEqualTo("[$x]");

    // Viet Template deliberately fails fast with TemplateRenderException / ARITHMETIC_ERROR
    EngineResult vietResult = executeViet(template, Map.of());
    assertThat(vietResult.isFailure()).isTrue();
    assertThat(vietResult.exception().category()).isEqualTo(ExceptionCategory.ARITHMETIC_ERROR);
    assertThat(vietResult.exception().message()).containsIgnoringCase("division by zero");

    // Analyzer detects literal division by zero: rule MIG-ARITH-DIV-ZERO,
    // KNOWN_BEHAVIOR_DIFFERENCE, BLOCKER
    MigrationReport report = analyzeTemplate("div_zero.vtl", template, tempDir);
    assertThat(report.allFindings()).hasSize(1);
    MigrationFinding finding = report.allFindings().get(0);
    assertThat(finding.ruleId()).isEqualTo(MigrationRuleRegistry.ID_ARITH_DIV_ZERO);
    assertThat(finding.classification())
        .isEqualTo(MigrationClassification.KNOWN_BEHAVIOR_DIFFERENCE);
    assertThat(finding.severity()).isEqualTo(MigrationSeverity.BLOCKER);
    assertThat(finding.relatedDiagnosticCode()).isPresent();
    assertThat(finding.relatedDiagnosticCode().get().qualifiedCode())
        .isEqualTo("INTERPRETER:ERROR");
  }

  @Test
  @DisplayName(
      "DIFF-002: Reflection access ($obj.getClass()) succeeds on Velocity 2.4.1 vs denied by"
          + " standard policy on Viet Template, reported as BLOCKER by analyzer")
  void testDiff002SecurityReflectionDenial(@TempDir Path tempDir) throws IOException {
    String template = "Class: $obj.getClass()";
    Map<String, Object> context = Map.of("obj", new PersonBean("Bob", 20, true, null));

    // Velocity 2.4.1 permits getClass() invocation by default
    Velocity241Probe.Result velResult = Velocity241Probe.render(template, context);
    assertThat(velResult.isSuccess()).isTrue();
    assertThat(velResult.output()).contains("PersonBean");

    // Viet Template denies reflection access under default security policy
    EngineResult vietResult = executeViet(template, context);
    assertThat(vietResult.isFailure()).isTrue();
    assertThat(vietResult.exception().category()).isEqualTo(ExceptionCategory.SECURITY_DENIED);

    // Also verify via TemplateEngine standard member access policy throws TemplateSecurityException
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("reflect.vm", template);
    TemplateEngine engine =
        TemplateEngine.builder()
            .repository(repo)
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .build();
    assertThatThrownBy(
            () -> engine.render("reflect.vm", RenderContext.builder().putAll(context).build()))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining("getClass");

    // Analyzer reports MIG-SEC-CLASS-ACCESS as BLOCKER and SECURITY_RESTRICTED
    MigrationReport report = analyzeTemplate("sec_denial.vtl", template, tempDir);
    assertThat(report.readinessStatus()).isEqualTo(MigrationReadinessStatus.BLOCKED);
    assertThat(report.allFindings()).hasSize(1);
    MigrationFinding finding = report.allFindings().get(0);
    assertThat(finding.ruleId()).isEqualTo(MigrationRuleRegistry.ID_SEC_CLASS_ACCESS);
    assertThat(finding.severity()).isEqualTo(MigrationSeverity.BLOCKER);
    assertThat(finding.classification()).isEqualTo(MigrationClassification.SECURITY_RESTRICTED);
    assertThat(finding.relatedDiagnosticCode()).isPresent();
    assertThat(finding.relatedDiagnosticCode().get().qualifiedCode()).isEqualTo("VTLSEC:2401");
  }

  @Test
  @DisplayName(
      "EXT-001: $foreach.stop() programmatic loop termination is a Viet Template extension,"
          + " reported as VIET_TEMPLATE_EXTENSION by analyzer")
  void testExt001ForeachStopExtension(@TempDir Path tempDir) throws IOException {
    String template = "#foreach($i in [1..5])#if($i == 3)$foreach.stop()#end$i#end";

    // Velocity 2.4.1 removed $foreach.stop(), does not terminate loop
    Velocity241Probe.Result velResult = Velocity241Probe.render(template);
    assertThat(velResult.isSuccess()).isTrue();
    assertThat(velResult.output()).isNotEqualTo("12");

    // Viet Template supports $foreach.stop() and cleanly stops after printing 1 and 2
    EngineResult vietResult = executeViet(template, Map.of());
    assertThat(vietResult.isSuccess()).isTrue();
    assertThat(vietResult.output()).isEqualTo("12");

    // Analyzer reports MIG-EXT-FOREACH-STOP with VIET_TEMPLATE_EXTENSION classification
    MigrationReport report = analyzeTemplate("foreach_stop.vtl", template, tempDir);
    assertThat(report.allFindings()).hasSize(1);
    MigrationFinding finding = report.allFindings().get(0);
    assertThat(finding.ruleId()).isEqualTo(MigrationRuleRegistry.ID_EXT_FOREACH_STOP);
    assertThat(finding.classification()).isEqualTo(MigrationClassification.VIET_TEMPLATE_EXTENSION);
    assertThat(finding.severity()).isEqualTo(MigrationSeverity.INFO);
    assertThat(report.readinessStatus()).isEqualTo(MigrationReadinessStatus.READY);
  }

  @Test
  @DisplayName(
      "DIFF-003: #set null RHS depends on configuration (Velocity 2.x null-assignment vs legacy"
          + " 1.x preservation), reported by analyzer")
  void testDiff003SetNullRhsConfigurationDependency(@TempDir Path tempDir) throws IOException {
    String template = "#set($x = 'initial')#set($x = $missing)[$x]";

    // Modern Velocity 2.x mode (default: setNullAllowed = true) overwrites $x with null
    EngineResult defaultResult =
        executeViet(template, Map.of(), CompatibilityConfiguration.defaultConfiguration());
    assertThat(defaultResult.isSuccess()).isTrue();
    assertThat(defaultResult.output()).isEqualTo("[$x]");

    // Legacy Velocity 1.x mode (setNullAllowed = false) preserves existing value
    CompatibilityConfiguration legacyConfig =
        CompatibilityConfiguration.defaultConfiguration().withSetNullAllowed(false);
    EngineResult legacyResult = executeViet(template, Map.of(), legacyConfig);
    assertThat(legacyResult.isSuccess()).isTrue();
    assertThat(legacyResult.output()).isEqualTo("[initial]");

    // Migration analyzer identifies #set with null/undefined RHS: MIG-SET-NULL-RHS
    MigrationReport report = analyzeTemplate("set_null.vtl", "#set($val = $missing)\n", tempDir);
    assertThat(report.allFindings()).hasSize(1);
    MigrationFinding finding = report.allFindings().get(0);
    assertThat(finding.ruleId()).isEqualTo(MigrationRuleRegistry.ID_SET_NULL_RHS);
    assertThat(finding.classification())
        .isIn(
            MigrationClassification.COMPATIBLE_WITH_CONFIGURATION,
            MigrationClassification.KNOWN_BEHAVIOR_DIFFERENCE);
  }

  @Test
  @DisplayName(
      "Dynamic / Unverifiable: dynamic #parse($template) reported as DYNAMICALLY_UNVERIFIABLE with"
          + " WARNING severity (not UNSUPPORTED)")
  void testDynamicParseReportedAsUnverifiableWarning(@TempDir Path tempDir) throws IOException {
    String template = "#parse($dynamicTemplate)";

    MigrationReport report = analyzeTemplate("dyn_parse.vtl", template, tempDir);
    assertThat(report.allFindings()).hasSize(1);
    MigrationFinding finding = report.allFindings().get(0);
    assertThat(finding.ruleId()).isEqualTo(MigrationRuleRegistry.ID_PARSE_DYNAMIC);
    assertThat(finding.classification())
        .isEqualTo(MigrationClassification.DYNAMICALLY_UNVERIFIABLE);
    assertThat(finding.severity()).isEqualTo(MigrationSeverity.WARNING);
    assertThat(finding.severity()).isNotEqualTo(MigrationSeverity.BLOCKER);
    assertThat(finding.classification()).isNotEqualTo(MigrationClassification.UNSUPPORTED);
    assertThat(report.readinessStatus()).isEqualTo(MigrationReadinessStatus.ATTENTION_REQUIRED);
  }

  @Test
  @DisplayName(
      "TCK Evidence Mapping: all migration rules have evidence scenarios and key rules map"
          + " directly to CompatibilityCorpus")
  void testTckEvidenceMappingVerification() {
    List<MigrationRule> allRules = MigrationRuleRegistry.allRules();
    assertThat(allRules).isNotEmpty();

    // 1. Every semantic rule in the registry must define at least one evidence scenario ID
    for (MigrationRule rule : allRules) {
      assertThat(rule.evidenceScenarioIds())
          .as("Migration rule '%s' must have non-empty evidenceScenarioIds", rule.id())
          .isNotEmpty();
    }

    // 2. Verified corpus scenario IDs must exist in CompatibilityCorpus.allScenarios()
    Set<String> corpusScenarioIds =
        CompatibilityCorpus.allScenarios().stream()
            .map(CompatibilityScenario::id)
            .collect(Collectors.toSet());

    List<String> keyCorpusScenarios =
        List.of(
            "arithmetic.divide-by-zero",
            "security.denial.class-property",
            "security.denial.get-class-method",
            "foreach.control.stop-method",
            "set.null-rhs.legacy-preserved.undefined",
            "set.null-rhs.legacy-preserved.method-null");

    for (String scenarioId : keyCorpusScenarios) {
      assertThat(corpusScenarioIds)
          .as(
              "Scenario ID '%s' referenced in migration rules must exist in CompatibilityCorpus",
              scenarioId)
          .contains(scenarioId);
    }
  }
}
