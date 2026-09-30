package io.github.minh124199.viettemplate.tooling.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateGenerateSchemasTaskTest {

  private static GradleRunner createRunner(Path projectDir) {
    GradleRunner runner = GradleRunner.create().withProjectDir(projectDir.toFile());
    if (VietTemplateGenerateSchemasTaskTest.class
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

  @Test
  @DisplayName("Generates canonical contract schemas (*.vt-schema.json) via GradleRunner")
  void testGenerateVietTemplateSchemas(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-schema-project\"\n",
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

    Path templateDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(templateDir);
    Files.writeString(
        templateDir.resolve("order-view.vtl"),
        "Order #$orderId for $customer",
        StandardCharsets.UTF_8);
    Files.writeString(
        templateDir.resolve("order-view.vtl.contract"),
        "orderId=long\ncustomer=String\n",
        StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.GENERATE_SCHEMAS_TASK_NAME)
            .build();

    assertThat(result.task(":" + VietTemplatePlugin.GENERATE_SCHEMAS_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.GENERATE_SCHEMAS_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    Path schemaFile =
        projectDir.resolve("build/generated/viet-template/schemas/order-view.vt-schema.json");
    assertThat(schemaFile).isRegularFile();
    String json = Files.readString(schemaFile, StandardCharsets.UTF_8);
    assertThat(json).contains("\"format\": \"viet-template-contract-schema/1\"");
    assertThat(json).contains("\"schemaVersion\": 1");
    assertThat(json).contains("\"orderId\"");
    assertThat(json).contains("\"customer\"");
  }

  @Test
  @DisplayName("Clean and regenerate removes stale schemas after contract removal")
  void testCleanAndRegenerateRemovesStaleSchemas(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-clean-project\"\n",
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

    Path templateDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(templateDir);
    Path oldTemplate = templateDir.resolve("old-template.vtl");
    Path oldContract = templateDir.resolve("old-template.vtl.contract");
    Files.writeString(oldTemplate, "Old template", StandardCharsets.UTF_8);
    Files.writeString(oldContract, "val=String\n", StandardCharsets.UTF_8);

    createRunner(projectDir).withArguments(VietTemplatePlugin.GENERATE_SCHEMAS_TASK_NAME).build();

    Path oldSchema =
        projectDir.resolve("build/generated/viet-template/schemas/old-template.vt-schema.json");
    assertThat(oldSchema).isRegularFile();

    // Remove old template & contract, add new one
    Files.delete(oldTemplate);
    Files.delete(oldContract);

    Files.writeString(templateDir.resolve("new-template.vtl"), "New", StandardCharsets.UTF_8);
    Files.writeString(
        templateDir.resolve("new-template.vtl.contract"), "id=int\n", StandardCharsets.UTF_8);

    createRunner(projectDir)
        .withArguments("clean", VietTemplatePlugin.GENERATE_SCHEMAS_TASK_NAME)
        .build();

    assertThat(oldSchema).doesNotExist();
    Path newSchema =
        projectDir.resolve("build/generated/viet-template/schemas/new-template.vt-schema.json");
    assertThat(newSchema).isRegularFile();
  }
}
