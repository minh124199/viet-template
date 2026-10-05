package io.github.minh124199.viettemplate.tooling.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.aot.TemplateAotCompiler;
import io.github.minh124199.viettemplate.aot.TemplateAotRequest;
import io.github.minh124199.viettemplate.aot.TypeScriptDeclarationProjector;
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
}
