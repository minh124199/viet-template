package io.github.minh124199.viettemplate.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateMigrationAnalyzerTest {

  @TempDir Path tempDir;

  private TemplateMigrationAnalyzer analyzer;

  public record UserRecord(String name, int age) {}

  @BeforeEach
  void setUp() {
    analyzer = TemplateMigrationAnalyzer.create();
  }

  @Test
  void testExactParityProducesCleanReadyReport() throws IOException {
    Path templateFile = tempDir.resolve("clean.vtl");
    Files.writeString(
        templateFile,
        "#set($title = 'Welcome')\n"
            + "<h1>$title</h1>\n"
            + "#if($user)\n"
            + "<p>Hello $user.name</p>\n"
            + "#end\n"
            + "<ul>\n"
            + "#foreach($item in $items)\n"
            + "  <li>$item</li>\n"
            + "#end\n"
            + "</ul>\n");

    TemplateId templateId = TemplateId.of("clean.vtl");
    TemplateContract contract =
        TemplateContract.builder(templateId)
            .parameter("user", UserRecord.class)
            .parameter("items", List.class)
            .build();

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .build();

    MigrationReport report = analyzer.analyze(request);

    assertTrue(report.success());
    assertEquals(MigrationReadinessStatus.READY, report.readinessStatus());
    assertEquals(1, report.summary().totalTemplates());
    assertEquals(1, report.summary().validTemplates());
    assertEquals(1, report.summary().compatibleTemplates());
    assertEquals(0, report.summary().totalFindings());
    assertTrue(report.allFindings().isEmpty());
    assertTrue(report.validationDiagnostics().isEmpty());

    SingleTemplateMigration single = report.templates().get(0);
    assertTrue(single.valid());
    assertTrue(single.compatible());
    assertTrue(single.findings().isEmpty());
    assertTrue(single.diagnostics().isEmpty());
  }

  @Test
  void testDivisionByZeroLiteralVsDynamicVsNonZero() throws IOException {
    // 1. Literal 0 divisor => BLOCKER
    Path litZeroFile = tempDir.resolve("div_zero_lit.vtl");
    Files.writeString(litZeroFile, "#set($x = 10 / 0)\n#set($y = 10 % 0)\n");

    // 2. Dynamic divisor => WARNING
    Path dynFile = tempDir.resolve("div_dyn.vtl");
    Files.writeString(dynFile, "#set($res = 10 / $divisor)\n");

    // 3. Non-zero literal divisor => Safe, 0 findings
    Path nonZeroFile = tempDir.resolve("div_safe.vtl");
    Files.writeString(nonZeroFile, "#set($res = 10 / 2)\n#set($mod = 15 % 4)\n");

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).build();

    MigrationReport report = analyzer.analyze(request);

    // Check literal zero template
    SingleTemplateMigration litZeroResult =
        report.templates().stream()
            .filter(t -> t.templateId().value().equals("div_zero_lit.vtl"))
            .findFirst()
            .orElseThrow();
    assertFalse(litZeroResult.compatible());
    assertEquals(2, litZeroResult.findings().size());
    for (MigrationFinding f : litZeroResult.findings()) {
      assertEquals(MigrationRuleRegistry.ID_ARITH_DIV_ZERO, f.ruleId());
      assertEquals(MigrationSeverity.BLOCKER, f.severity());
      assertEquals(MigrationConfidence.STATICALLY_VERIFIED, f.confidence());
      assertTrue(f.relatedDiagnosticCode().isPresent());
      assertEquals("INTERPRETER:ERROR", f.relatedDiagnosticCode().get().qualifiedCode());
    }

    // Check dynamic divisor template
    SingleTemplateMigration dynResult =
        report.templates().stream()
            .filter(t -> t.templateId().value().equals("div_dyn.vtl"))
            .findFirst()
            .orElseThrow();
    assertTrue(dynResult.compatible());
    assertEquals(1, dynResult.findings().size());
    MigrationFinding dynFinding = dynResult.findings().get(0);
    assertEquals(MigrationRuleRegistry.ID_ARITH_DIV_ZERO, dynFinding.ruleId());
    assertEquals(MigrationSeverity.WARNING, dynFinding.severity());
    assertEquals(MigrationConfidence.DYNAMICALLY_UNVERIFIABLE, dynFinding.confidence());

    // Check non-zero literal divisor template
    SingleTemplateMigration safeResult =
        report.templates().stream()
            .filter(t -> t.templateId().value().equals("div_safe.vtl"))
            .findFirst()
            .orElseThrow();
    assertTrue(safeResult.compatible());
    assertTrue(safeResult.findings().isEmpty());
  }

  @Test
  void testSecurityClassAccessDenial() throws IOException {
    Path templateFile = tempDir.resolve("sec.vtl");
    Files.writeString(
        templateFile,
        "$user.class\n"
            + "$user.getClass()\n"
            + "$user.classLoader\n"
            + "$user.getClassLoader()\n");

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).build();

    MigrationReport report = analyzer.analyze(request);

    assertEquals(MigrationReadinessStatus.BLOCKED, report.readinessStatus());
    assertFalse(report.success());

    SingleTemplateMigration single = report.templates().get(0);
    assertFalse(single.compatible());
    assertEquals(4, single.findings().size());
    for (MigrationFinding f : single.findings()) {
      assertEquals(MigrationRuleRegistry.ID_SEC_CLASS_ACCESS, f.ruleId());
      assertEquals(MigrationSeverity.BLOCKER, f.severity());
      assertEquals(MigrationClassification.SECURITY_RESTRICTED, f.classification());
      assertEquals(MigrationConfidence.STATICALLY_VERIFIED, f.confidence());
      assertTrue(f.relatedDiagnosticCode().isPresent());
      assertEquals("VTLSEC:2401", f.relatedDiagnosticCode().get().qualifiedCode());
    }
  }

  @Test
  void testExtensionForeachStopMethod() throws IOException {
    Path templateFile = tempDir.resolve("foreach_stop.vtl");
    Files.writeString(
        templateFile,
        "#foreach($item in $items)\n"
            + "  #if($item == 'end')\n"
            + "    $foreach.stop()\n"
            + "  #end\n"
            + "#end\n");

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).build();

    MigrationReport report = analyzer.analyze(request);

    assertTrue(report.success());
    assertEquals(MigrationReadinessStatus.READY, report.readinessStatus());

    SingleTemplateMigration single = report.templates().get(0);
    assertTrue(single.compatible());
    assertEquals(1, single.findings().size());

    MigrationFinding f = single.findings().get(0);
    assertEquals(MigrationRuleRegistry.ID_EXT_FOREACH_STOP, f.ruleId());
    assertEquals(MigrationSeverity.INFO, f.severity());
    assertEquals(MigrationClassification.VIET_TEMPLATE_EXTENSION, f.classification());
    assertEquals(MigrationConfidence.STATICALLY_VERIFIED, f.confidence());
  }

  @Test
  void testExtensionAlternateValueExpression() throws IOException {
    Path templateFile = tempDir.resolve("alternate.vtl");
    Files.writeString(templateFile, "Greeting: ${name|'Guest'}\n");

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).build();

    MigrationReport report = analyzer.analyze(request);

    assertTrue(report.success());
    assertEquals(MigrationReadinessStatus.READY, report.readinessStatus());

    SingleTemplateMigration single = report.templates().get(0);
    assertTrue(single.compatible());
    assertEquals(1, single.findings().size());

    MigrationFinding f = single.findings().get(0);
    assertEquals(MigrationRuleRegistry.ID_EXT_ALT_VALUE, f.ruleId());
    assertEquals(MigrationSeverity.INFO, f.severity());
    assertEquals(MigrationClassification.VIET_TEMPLATE_EXTENSION, f.classification());
    assertEquals(MigrationConfidence.STATICALLY_VERIFIED, f.confidence());
  }

  @Test
  void testDynamicParseAndIncludeDirectives() throws IOException {
    Path templateFile = tempDir.resolve("dynamic_res.vtl");
    Files.writeString(templateFile, "#parse($dynamicPage)\n" + "#include($dynamicResource)\n");

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).build();

    MigrationReport report = analyzer.analyze(request);

    assertTrue(report.success());
    assertEquals(MigrationReadinessStatus.ATTENTION_REQUIRED, report.readinessStatus());

    SingleTemplateMigration single = report.templates().get(0);
    assertTrue(single.compatible());
    assertEquals(2, single.findings().size());

    MigrationFinding parseFinding =
        single.findings().stream()
            .filter(f -> f.ruleId().equals(MigrationRuleRegistry.ID_PARSE_DYNAMIC))
            .findFirst()
            .orElseThrow();
    assertEquals(MigrationSeverity.WARNING, parseFinding.severity());
    assertEquals(MigrationClassification.DYNAMICALLY_UNVERIFIABLE, parseFinding.classification());
    assertEquals(MigrationConfidence.DYNAMICALLY_UNVERIFIABLE, parseFinding.confidence());

    MigrationFinding incFinding =
        single.findings().stream()
            .filter(f -> f.ruleId().equals(MigrationRuleRegistry.ID_INCLUDE_DYNAMIC))
            .findFirst()
            .orElseThrow();
    assertEquals(MigrationSeverity.WARNING, incFinding.severity());
    assertEquals(MigrationClassification.DYNAMICALLY_UNVERIFIABLE, incFinding.classification());
    assertEquals(MigrationConfidence.DYNAMICALLY_UNVERIFIABLE, incFinding.confidence());
  }

  @Test
  void testEvaluateDirectiveStandardVsSafeProfile() throws IOException {
    Path templateFile = tempDir.resolve("eval.vtl");
    Files.writeString(templateFile, "#evaluate($dynamicSnippet)\n");

    // Standard profile => WARNING (DYNAMICALLY_UNVERIFIABLE)
    TemplateMigrationRequest stdRequest =
        TemplateMigrationRequest.builder()
            .sourceDirectory(tempDir)
            .profile(VtlProfile.VTL_MIGRATION)
            .build();
    MigrationReport stdReport = analyzer.analyze(stdRequest);

    assertTrue(stdReport.success());
    assertEquals(MigrationReadinessStatus.ATTENTION_REQUIRED, stdReport.readinessStatus());
    MigrationFinding stdFinding = stdReport.allFindings().get(0);
    assertEquals(MigrationRuleRegistry.ID_EVALUATE_DYNAMIC, stdFinding.ruleId());
    assertEquals(MigrationSeverity.WARNING, stdFinding.severity());
    assertEquals(MigrationClassification.DYNAMICALLY_UNVERIFIABLE, stdFinding.classification());

    // VTL_SAFE profile => BLOCKER (SECURITY_RESTRICTED)
    TemplateMigrationRequest safeRequest =
        TemplateMigrationRequest.builder()
            .sourceDirectory(tempDir)
            .profile(VtlProfile.VTL_SAFE)
            .build();
    MigrationReport safeReport = analyzer.analyze(safeRequest);

    assertFalse(safeReport.success());
    assertEquals(MigrationReadinessStatus.BLOCKED, safeReport.readinessStatus());
    MigrationFinding safeFinding = safeReport.allFindings().get(0);
    assertEquals(MigrationRuleRegistry.ID_EVALUATE_DYNAMIC, safeFinding.ruleId());
    assertEquals(MigrationSeverity.BLOCKER, safeFinding.severity());
    assertEquals(MigrationClassification.SECURITY_RESTRICTED, safeFinding.classification());
    assertTrue(safeFinding.relatedDiagnosticCode().isPresent());
    assertEquals(
        "SECURITY:ACCESS_DENIED", safeFinding.relatedDiagnosticCode().get().qualifiedCode());
  }

  @Test
  void testStrictReferenceModeEnabledVsDisabled() throws IOException {
    Path templateFile = tempDir.resolve("strict_test.vtl");
    Files.writeString(templateFile, "Hello $undeclaredUser!\n");

    // Strict mode disabled => 0 strict findings
    TemplateMigrationRequest disabledRequest =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).strictReferences(false).build();
    MigrationReport disabledReport = analyzer.analyze(disabledRequest);

    assertTrue(disabledReport.success());
    assertEquals(0, disabledReport.summary().totalFindings());
    assertEquals(MigrationReadinessStatus.READY, disabledReport.readinessStatus());

    // Strict mode enabled => WARNING for undeclared reference
    TemplateMigrationRequest enabledRequest =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).strictReferences(true).build();
    MigrationReport enabledReport = analyzer.analyze(enabledRequest);

    assertEquals(MigrationReadinessStatus.READY_WITH_WARNINGS, enabledReport.readinessStatus());
    assertEquals(1, enabledReport.allFindings().size());
    MigrationFinding f = enabledReport.allFindings().get(0);
    assertEquals(MigrationRuleRegistry.ID_STRICT_REF, f.ruleId());
    assertEquals(MigrationSeverity.WARNING, f.severity());
    assertEquals(MigrationClassification.COMPATIBLE_WITH_CONFIGURATION, f.classification());
    assertTrue(f.relatedDiagnosticCode().isPresent());
    assertEquals("INTERPRETER:VARIABLE_UNDEFINED", f.relatedDiagnosticCode().get().qualifiedCode());
  }

  @Test
  void testInvalidSyntaxTemplateReportsDiagnosticsWithoutCrashing() throws IOException {
    Path invalidFile = tempDir.resolve("syntax_error.vtl");
    Files.writeString(invalidFile, "#if($unclosedCondition)\nSome content without end\n");

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).build();

    MigrationReport report = analyzer.analyze(request);

    assertFalse(report.success());
    assertEquals(MigrationReadinessStatus.BLOCKED, report.readinessStatus());
    assertEquals(1, report.summary().totalTemplates());
    assertEquals(0, report.summary().validTemplates());
    assertEquals(0, report.summary().compatibleTemplates());

    SingleTemplateMigration single = report.templates().get(0);
    assertFalse(single.valid());
    assertFalse(single.compatible());
    assertFalse(single.diagnostics().isEmpty());
  }

  @Test
  void testDeterministicTextAndJsonFormatters() throws IOException {
    Path templateFile = tempDir.resolve("sample.vtl");
    Files.writeString(
        templateFile, "#set($x = 10 / 0)\n" + "$user.getClass()\n" + "${name|'Guest'}\n");

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder().sourceDirectory(tempDir).build();

    MigrationReport report = analyzer.analyze(request);

    // Text format check
    String text1 = report.asText();
    String text2 = report.asText();
    assertEquals(text1, text2);
    assertTrue(text1.contains("=== Viet Template Migration Analysis Report ==="));
    assertTrue(text1.contains("Apache Velocity 2.4.1"));
    assertTrue(text1.contains("Viet Template 1.2.0-SNAPSHOT"));
    assertTrue(text1.contains("MIG-ARITH-DIV-ZERO"));
    assertTrue(text1.contains("MIG-SEC-CLASS-ACCESS"));
    assertTrue(text1.contains("MIG-EXT-ALT-VALUE"));

    // JSON format check
    String json1 = report.asJson();
    String json2 = report.asJson();
    assertEquals(json1, json2);
    assertTrue(json1.contains("\"formatVersion\": 1"));
    assertTrue(json1.contains("\"sourceEngine\": \"Apache Velocity\""));
    assertTrue(json1.contains("\"sourceVersion\": \"2.4.1\""));
    assertTrue(json1.contains("\"targetEngine\": \"Viet Template\""));
    assertTrue(json1.contains("\"targetVersion\": \"1.2.0-SNAPSHOT\""));
    assertTrue(json1.contains("\"templateId\": \"sample.vtl\""));
    assertTrue(json1.contains("\"relativePath\": \"sample.vtl\""));
    assertFalse(
        json1.contains(tempDir.toString()), "JSON must use logical paths, not machine paths");
  }

  @Test
  void testRequestAndResultImmutability() {
    List<Path> dirs = new ArrayList<>(List.of(tempDir));
    List<String> incs = new ArrayList<>(List.of("*.vtl"));
    List<String> excs = new ArrayList<>(List.of("*.txt"));
    Map<TemplateId, TemplateContract> contracts = new HashMap<>();
    TemplateId tid = TemplateId.of("foo.vtl");
    contracts.put(tid, TemplateContract.empty(tid));

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder()
            .sourceDirectories(dirs)
            .includes(incs)
            .excludes(excs)
            .contracts(contracts)
            .build();

    // Mutate source structures
    dirs.clear();
    incs.clear();
    excs.clear();
    contracts.clear();

    // Verify request remains unchanged
    assertEquals(1, request.sourceDirectories().size());
    assertEquals(1, request.includePatterns().size());
    assertEquals(1, request.excludePatterns().size());
    assertEquals(1, request.contracts().size());

    // Verify getters return unmodifiable collections
    assertThrows(UnsupportedOperationException.class, () -> request.sourceDirectories().clear());
    assertThrows(UnsupportedOperationException.class, () -> request.includePatterns().clear());
    assertThrows(UnsupportedOperationException.class, () -> request.excludePatterns().clear());
    assertThrows(UnsupportedOperationException.class, () -> request.contracts().clear());
  }

  @Test
  void testOutputFileGeneration() throws IOException {
    Path templateFile = tempDir.resolve("test.vtl");
    Files.writeString(templateFile, "Hello $name!\n");

    Path reportOutput = tempDir.resolve("reports/migration-report.json");

    TemplateMigrationRequest request =
        TemplateMigrationRequest.builder()
            .sourceDirectory(tempDir)
            .format("json")
            .outputFile(reportOutput)
            .build();

    MigrationReport report = analyzer.analyze(request);
    assertTrue(Files.isRegularFile(reportOutput));

    String written = Files.readString(reportOutput);
    assertEquals(report.asJson(), written);
  }
}
