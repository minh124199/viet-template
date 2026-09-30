package io.github.minh124199.viettemplate.tooling.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.aot.TemplateAotCompiler;
import io.github.minh124199.viettemplate.aot.TemplateAotRequest;
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
}
