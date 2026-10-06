package io.github.minh124199.viettemplate.tooling.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.aot.TemplateAotCompiler;
import io.github.minh124199.viettemplate.aot.TemplateAotRequest;
import io.github.minh124199.viettemplate.aot.TypeScriptDeclarationProjector;
import io.github.minh124199.viettemplate.explanation.ExpressionExplanation;
import io.github.minh124199.viettemplate.explanation.SingleTemplateExplanation;
import io.github.minh124199.viettemplate.explanation.TemplateExplainRequest;
import io.github.minh124199.viettemplate.explanation.TemplateExplainer;
import io.github.minh124199.viettemplate.explanation.TemplateExplanation;
import io.github.minh124199.viettemplate.migration.MigrationReport;
import io.github.minh124199.viettemplate.migration.MigrationRuleRegistry;
import io.github.minh124199.viettemplate.migration.MigrationSeverity;
import io.github.minh124199.viettemplate.migration.TemplateMigrationAnalyzer;
import io.github.minh124199.viettemplate.migration.TemplateMigrationRequest;
import io.github.minh124199.viettemplate.validation.TemplateValidationRequest;
import io.github.minh124199.viettemplate.validation.TemplateValidationResult;
import io.github.minh124199.viettemplate.validation.TemplateValidator;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateMavenGradleParityTest {

  private static GradleRunner createRunner(Path projectDir) {
    GradleRunner runner = GradleRunner.create().withProjectDir(projectDir.toFile());
    if (VietTemplateMavenGradleParityTest.class
            .getClassLoader()
            .getResource("plugin-under-test-metadata.properties")
        != null) {
      return runner.withPluginClasspath();
    }
    List<File> cp =
        Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(File::new)
            .filter(File::exists)
            .collect(Collectors.toList());
    return runner.withGradleVersion("9.7.1").withPluginClasspath(cp);
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("Maven and Gradle generate byte-for-byte and SHA-256 identical schema artifacts")
  void testMavenGradleSchemaParity(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"parity-test-project\"\n",
        StandardCharsets.UTF_8);

    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"io.github.minh124199.viet-template\")\n"
            + "}\n"
            + "repositories {\n"
            + "    mavenCentral()\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path srcDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("order-view.vtl"),
        "Order #$orderId for $customer ($total USD)",
        StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("order-view.vtl.contract"),
        "customer=String\norderId=long\ntotal=double\n",
        StandardCharsets.UTF_8);

    // 1. Run Gradle generation
    BuildResult gradleResult =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.GENERATE_SCHEMAS_TASK_NAME)
            .build();

    assertThat(gradleResult.task(":" + VietTemplatePlugin.GENERATE_SCHEMAS_TASK_NAME)).isNotNull();
    assertThat(gradleResult.task(":" + VietTemplatePlugin.GENERATE_SCHEMAS_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    Path gradleSchemaFile =
        projectDir.resolve("build/generated/viet-template/schemas/order-view.vt-schema.json");
    assertThat(gradleSchemaFile).isRegularFile();
    byte[] gradleBytes = Files.readAllBytes(gradleSchemaFile);
    String gradleSha256 = sha256Hex(gradleBytes);

    // 2. Run Maven generation logic on identical input source directory
    Path mavenOutputDir = projectDir.resolve("target/generated-resources/viet-template/schemas");
    TemplateAotRequest mavenRequest =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .schemaOutputDirectory(mavenOutputDir)
            .generateSchemas(true)
            .compileBytecode(false)
            .encoding(StandardCharsets.UTF_8)
            .packagePrefix("io.github.minh124199.viettemplate.generated")
            .failOnWarning(false)
            .build();

    TemplateAotCompiler.create().compile(mavenRequest);

    Path mavenSchemaFile = mavenOutputDir.resolve("order-view.vt-schema.json");
    assertThat(mavenSchemaFile).isRegularFile();
    byte[] mavenBytes = Files.readAllBytes(mavenSchemaFile);
    String mavenSha256 = sha256Hex(mavenBytes);

    // 3. Verify exact byte-for-byte and SHA-256 parity
    assertThat(gradleBytes)
        .as("Maven and Gradle schema outputs must be byte-for-byte identical")
        .isEqualTo(mavenBytes);
    assertThat(gradleSha256)
        .as("Maven and Gradle schema outputs must have identical SHA-256 digests")
        .isEqualTo(mavenSha256);
  }

  @Test
  @DisplayName(
      "Maven and Gradle generate byte-for-byte and SHA-256 identical TypeScript declaration"
          + " artifacts")
  void testMavenGradleTypeScriptParity(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"parity-ts-project\"\n",
        StandardCharsets.UTF_8);

    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"io.github.minh124199.viet-template\")\n"
            + "}\n"
            + "repositories {\n"
            + "    mavenCentral()\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path srcDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("order-view.vtl"),
        "Order #$orderId for $customer ($total USD)",
        StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("order-view.vtl.contract"),
        "customer=String\norderId=long\ntotal=double\n",
        StandardCharsets.UTF_8);

    // 1. Run Gradle TypeScript generation
    BuildResult gradleResult =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.GENERATE_TYPESCRIPT_TASK_NAME)
            .build();

    assertThat(gradleResult.task(":" + VietTemplatePlugin.GENERATE_TYPESCRIPT_TASK_NAME))
        .isNotNull();
    assertThat(
            gradleResult.task(":" + VietTemplatePlugin.GENERATE_TYPESCRIPT_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    Path gradleDtsFile =
        projectDir.resolve("build/generated/viet-template/typescript/order-view.d.ts");
    assertThat(gradleDtsFile).isRegularFile();
    byte[] gradleBytes = Files.readAllBytes(gradleDtsFile);
    String gradleSha256 = sha256Hex(gradleBytes);

    // 2. Run Maven generation logic on identical input source directory
    Path mavenOutputDir = projectDir.resolve("target/generated-resources/viet-template/schemas");
    TemplateAotRequest mavenRequest =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .schemaOutputDirectory(mavenOutputDir)
            .generateSchemas(true)
            .compileBytecode(false)
            .encoding(StandardCharsets.UTF_8)
            .packagePrefix("io.github.minh124199.viettemplate.generated")
            .failOnWarning(false)
            .build();

    TemplateAotCompiler.create().compile(mavenRequest);

    Path mavenSchemaFile = mavenOutputDir.resolve("order-view.vt-schema.json");
    assertThat(mavenSchemaFile).isRegularFile();

    Path mavenTsOutputDir = projectDir.resolve("target/generated-sources/viet-template/typescript");
    Path mavenDtsFile =
        TypeScriptDeclarationProjector.projectToFile(mavenSchemaFile, mavenTsOutputDir);
    assertThat(mavenDtsFile).isRegularFile();
    byte[] mavenBytes = Files.readAllBytes(mavenDtsFile);
    String mavenSha256 = sha256Hex(mavenBytes);

    // 3. Verify exact byte-for-byte and SHA-256 parity
    assertThat(gradleBytes)
        .as("Maven and Gradle TypeScript outputs must be byte-for-byte identical")
        .isEqualTo(mavenBytes);
    assertThat(gradleSha256)
        .as("Maven and Gradle TypeScript outputs must have identical SHA-256 digests")
        .isEqualTo(mavenSha256);
  }

  @Test
  @DisplayName("Maven and Gradle validation exhibit 100% diagnostic and exit parity")
  void testMavenGradleValidationParity(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"parity-val-project\"\n",
        StandardCharsets.UTF_8);

    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"io.github.minh124199.viet-template\")\n"
            + "}\n"
            + "repositories {\n"
            + "    mavenCentral()\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path srcDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);

    // 1. Success parity test
    Files.writeString(
        srcDir.resolve("valid.vtl"), "Valid template with $name", StandardCharsets.UTF_8);

    // Gradle execution
    BuildResult gradleSuccessResult =
        createRunner(projectDir).withArguments(VietTemplatePlugin.VALIDATE_TASK_NAME).build();
    assertThat(gradleSuccessResult.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME)).isNotNull();
    assertThat(gradleSuccessResult.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    // Maven validation core execution on identical source
    TemplateValidationRequest mavenRequest =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .encoding(StandardCharsets.UTF_8)
            .build();
    TemplateValidationResult mavenResult = TemplateValidator.create().validate(mavenRequest);
    assertThat(mavenResult.isSuccess()).isTrue();
    assertThat(mavenResult.errorCount()).isZero();

    // 2. Syntax error failure parity test
    Files.writeString(
        srcDir.resolve("broken-syntax.vtl"), "#if($condition) unclosed", StandardCharsets.UTF_8);

    BuildResult gradleSyntaxFail =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.VALIDATE_TASK_NAME)
            .buildAndFail();
    assertThat(gradleSyntaxFail.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME)).isNotNull();
    assertThat(gradleSyntaxFail.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(gradleSyntaxFail.getOutput()).contains("[SYNTAX:PARSE_ERROR]");

    TemplateValidationResult mavenSyntaxResult = TemplateValidator.create().validate(mavenRequest);
    assertThat(mavenSyntaxResult.isSuccess()).isFalse();
    assertThat(mavenSyntaxResult.diagnostics())
        .anyMatch(d -> d.code().qualifiedCode().equals("SYNTAX:PARSE_ERROR"));

    // 3. Missing dependency failure parity test
    Files.delete(srcDir.resolve("broken-syntax.vtl"));
    Files.writeString(
        srcDir.resolve("missing-dep.vtl"), "#parse('non-existent.vtl')", StandardCharsets.UTF_8);

    BuildResult gradleDepFail =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.VALIDATE_TASK_NAME)
            .buildAndFail();
    assertThat(gradleDepFail.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME)).isNotNull();
    assertThat(gradleDepFail.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(gradleDepFail.getOutput()).contains("[RESOURCE:NOT_FOUND]");

    TemplateValidationResult mavenDepResult = TemplateValidator.create().validate(mavenRequest);
    assertThat(mavenDepResult.isSuccess()).isFalse();
    assertThat(mavenDepResult.diagnostics())
        .anyMatch(d -> d.code().qualifiedCode().equals("RESOURCE:NOT_FOUND"));
  }

  @Test
  @DisplayName(
      "Maven and Gradle explanation produce byte-for-byte identical structured JSON and identical"
          + " decisions")
  void testMavenGradleExplainParity(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"parity-explain-project\"\n",
        StandardCharsets.UTF_8);

    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"io.github.minh124199.viet-template\")\n"
            + "}\n"
            + "repositories {\n"
            + "    mavenCentral()\n"
            + "}\n"
            + "tasks.named<io.github.minh124199.viettemplate.tooling.gradle.VietTemplateExplainTask>(\"explainVietTemplates\")"
            + " {\n"
            + "    format.set(\"json\")\n"
            + "    outputFile.set(layout.buildDirectory.file(\"explanation.json\"))\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path srcDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);

    // 1. Template with String specialization and Integer specialization
    Files.writeString(
        srcDir.resolve("typed.vtl"), "User: $username, Level: $level", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("typed.vtl.contract"),
        "username=String\nlevel=int\n",
        StandardCharsets.UTF_8);

    // 2. Template requiring dynamic fallback
    Files.writeString(
        srcDir.resolve("dynamic.vtl"), "Dynamic: $unknownField", StandardCharsets.UTF_8);

    // Execute Gradle explain task
    BuildResult gradleResult =
        createRunner(projectDir).withArguments(VietTemplatePlugin.EXPLAIN_TASK_NAME).build();

    assertThat(gradleResult.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME)).isNotNull();
    assertThat(gradleResult.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    Path gradleOutputFile = projectDir.resolve("build/explanation.json");
    assertThat(gradleOutputFile).isRegularFile();
    byte[] gradleBytes = Files.readAllBytes(gradleOutputFile);
    String gradleSha256 = sha256Hex(gradleBytes);

    // Execute Maven explanation logic on identical input source directory
    Path mavenOutputDir = projectDir.resolve("target");
    Path mavenOutputFile = mavenOutputDir.resolve("explanation.json");
    TemplateExplainRequest mavenRequest =
        TemplateExplainRequest.builder()
            .sourceDirectory(srcDir)
            .format("json")
            .outputFile(mavenOutputFile)
            .build();

    TemplateExplainer explainer = TemplateExplainer.create();
    TemplateExplanation mavenExplanation = explainer.explain(mavenRequest);
    String mavenJson = mavenExplanation.asJson();
    Files.createDirectories(mavenOutputDir);
    Files.writeString(mavenOutputFile, mavenJson, StandardCharsets.UTF_8);

    byte[] mavenBytes = Files.readAllBytes(mavenOutputFile);
    String mavenSha256 = sha256Hex(mavenBytes);

    // Verify exact byte-for-byte and SHA-256 parity
    assertThat(gradleBytes)
        .as("Maven and Gradle explanation JSON outputs must be byte-for-byte identical")
        .isEqualTo(mavenBytes);
    assertThat(gradleSha256)
        .as("Maven and Gradle explanation JSON outputs must have identical SHA-256 digests")
        .isEqualTo(mavenSha256);

    // Verify structured decisions on the explanation
    assertThat(mavenExplanation.success()).isTrue();
    assertThat(mavenExplanation.totalTemplates()).isEqualTo(2);

    SingleTemplateExplanation typedExplanation =
        mavenExplanation.templates().stream()
            .filter(t -> t.templateId().value().equals("typed.vtl"))
            .findFirst()
            .orElseThrow();
    assertThat(typedExplanation.typed()).isTrue();
    assertThat(typedExplanation.aotEligible()).isTrue();
    assertThat(typedExplanation.compilationStatus()).isEqualTo("AOT_OK");

    ExpressionExplanation strExpr =
        typedExplanation.expressions().stream()
            .filter(e -> e.sourceText().equals("$username"))
            .findFirst()
            .orElseThrow();
    assertThat(strExpr.inferredType()).isEqualTo("java.lang.String");
    assertThat(strExpr.resolutionStrategy()).isEqualTo("ROOT_PARAMETER");
    assertThat(strExpr.outputDispatch()).contains("WRITE_STRING_SPECIALIZED");
    assertThat(strExpr.outputMethod()).contains("BytecodeRuntimeBridge.writeString");
    assertThat(strExpr.aotEligible()).isTrue();

    ExpressionExplanation intExpr =
        typedExplanation.expressions().stream()
            .filter(e -> e.sourceText().equals("$level"))
            .findFirst()
            .orElseThrow();
    assertThat(intExpr.inferredType()).isEqualTo("int");
    assertThat(intExpr.resolutionStrategy()).isEqualTo("ROOT_PARAMETER");
    assertThat(intExpr.outputDispatch()).contains("WRITE_INTEGER_SPECIALIZED");
    assertThat(intExpr.outputMethod()).contains("BytecodeRuntimeBridge.writeInteger");
    assertThat(intExpr.aotEligible()).isTrue();

    SingleTemplateExplanation dynamicExplanation =
        mavenExplanation.templates().stream()
            .filter(t -> t.templateId().value().equals("dynamic.vtl"))
            .findFirst()
            .orElseThrow();
    assertThat(dynamicExplanation.typed()).isFalse();
    assertThat(dynamicExplanation.aotEligible()).isFalse();
    assertThat(dynamicExplanation.compilationStatus()).isEqualTo("AOT_OK_WITH_DYNAMIC_SITES");
    assertThat(dynamicExplanation.aotRejectionReasons()).contains("UNTYPED_TEMPLATE");

    ExpressionExplanation dynamicExpr =
        dynamicExplanation.expressions().stream()
            .filter(e -> e.sourceText().equals("$unknownField"))
            .findFirst()
            .orElseThrow();
    assertThat(dynamicExpr.aotEligible()).isFalse();
    assertThat(dynamicExpr.aotRejectionReasons()).contains("UNTYPED_TEMPLATE");
  }

  @Test
  @DisplayName(
      "Maven and Gradle migration report produce byte-for-byte identical structured JSON and"
          + " identical findings")
  void testMavenGradleMigrationReportParity(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"parity-migration-project\"\n",
        StandardCharsets.UTF_8);

    Files.writeString(
        projectDir.resolve("build.gradle.kts"),
        "plugins {\n"
            + "    java\n"
            + "    id(\"io.github.minh124199.viet-template\")\n"
            + "}\n"
            + "repositories {\n"
            + "    mavenCentral()\n"
            + "}\n"
            + "tasks.named<io.github.minh124199.viettemplate.tooling.gradle.VietTemplateMigrationReportTask>(\"migrationReport\")"
            + " {\n"
            + "    format.set(\"json\")\n"
            + "    outputFile.set(layout.buildDirectory.file(\"migration-report.json\"))\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path srcDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);

    // 1. Exact compatible template
    Files.writeString(
        srcDir.resolve("1_exact.vtl"),
        "#set($title = 'Welcome')\n<h1>$title</h1>\n#if($user)\n<p>Hello $user.name</p>\n#end\n",
        StandardCharsets.UTF_8);

    // 2. Division by zero template
    Files.writeString(
        srcDir.resolve("2_divzero.vtl"), "#set($x = 10 / 0)\n", StandardCharsets.UTF_8);

    // 3. Dynamic parse template
    Files.writeString(
        srcDir.resolve("3_dynamic_parse.vtl"), "#parse($dynamicPage)\n", StandardCharsets.UTF_8);

    // 4. Security class access template
    Files.writeString(
        srcDir.resolve("4_security.vtl"),
        "$user.class\n$user.getClass()\n",
        StandardCharsets.UTF_8);

    // Execute Gradle migrationReport task
    BuildResult gradleResult =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)
            .build();

    assertThat(gradleResult.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)).isNotNull();
    assertThat(gradleResult.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    Path gradleOutputFile = projectDir.resolve("build/migration-report.json");
    assertThat(gradleOutputFile).isRegularFile();
    byte[] gradleBytes = Files.readAllBytes(gradleOutputFile);
    String gradleSha256 = sha256Hex(gradleBytes);

    // Execute Maven migration report logic on identical input source directory
    Path mavenOutputDir = projectDir.resolve("target");
    Path mavenOutputFile = mavenOutputDir.resolve("migration-report.json");
    TemplateMigrationRequest mavenRequest =
        TemplateMigrationRequest.builder()
            .sourceDirectory(srcDir)
            .format("json")
            .outputFile(mavenOutputFile)
            .failOnBlocker(false)
            .failOnWarning(false)
            .build();

    TemplateMigrationAnalyzer analyzer = TemplateMigrationAnalyzer.create();
    MigrationReport mavenReport = analyzer.analyze(mavenRequest);
    String mavenJson = mavenReport.asJson();
    Files.createDirectories(mavenOutputDir);
    Files.writeString(mavenOutputFile, mavenJson, StandardCharsets.UTF_8);

    byte[] mavenBytes = Files.readAllBytes(mavenOutputFile);
    String mavenSha256 = sha256Hex(mavenBytes);

    // Verify exact byte-for-byte and SHA-256 parity
    assertThat(gradleBytes)
        .as("Maven and Gradle migration report JSON outputs must be byte-for-byte identical")
        .isEqualTo(mavenBytes);
    assertThat(gradleSha256)
        .as("Maven and Gradle migration report JSON outputs must have identical SHA-256 digests")
        .isEqualTo(mavenSha256);

    // Verify summary counts and findings
    assertThat(mavenReport.summary().totalTemplates()).isEqualTo(4);
    assertThat(mavenReport.summary().findingsBySeverity().get(MigrationSeverity.BLOCKER))
        .isGreaterThan(0);
    assertThat(mavenReport.summary().findingsBySeverity().get(MigrationSeverity.WARNING))
        .isGreaterThan(0);

    // Check specific rule findings are present
    assertThat(mavenReport.allFindings())
        .anyMatch(f -> f.ruleId().equals(MigrationRuleRegistry.ID_ARITH_DIV_ZERO))
        .anyMatch(f -> f.ruleId().equals(MigrationRuleRegistry.ID_PARSE_DYNAMIC))
        .anyMatch(f -> f.ruleId().equals(MigrationRuleRegistry.ID_SEC_CLASS_ACCESS));
  }
}
