package io.github.minh124199.viettemplate.tooling.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateExplainTaskTest {

  private static GradleRunner createRunner(Path projectDir) {
    GradleRunner runner = GradleRunner.create().withProjectDir(projectDir.toFile());
    if (VietTemplateExplainTaskTest.class
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
  @DisplayName("Registers explainVietTemplates task with help group and not wired to check")
  void testExplainTaskRegistration(@TempDir Path tempDir) {
    Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
    project.getPlugins().apply(JavaPlugin.class);
    project.getPlugins().apply("io.github.minh124199.viet-template");

    Task taskObj = project.getTasks().findByName(VietTemplatePlugin.EXPLAIN_TASK_NAME);
    assertThat(taskObj).isInstanceOf(VietTemplateExplainTask.class);
    VietTemplateExplainTask explainTask = (VietTemplateExplainTask) taskObj;

    assertThat(explainTask.getGroup()).isEqualTo("help");
    assertThat(explainTask.getDescription())
        .isEqualTo("Explains Viet Template compiler optimization and code generation decisions.");
    assertThat(explainTask.getEncoding().get()).isEqualTo("UTF-8");
    assertThat(explainTask.getTypeChecking().get()).isEqualTo("OFF");
    assertThat(explainTask.getProfile().get()).isEqualTo("VTL_MIGRATION");
    assertThat(explainTask.getFormat().get()).isEqualTo("text");
    assertThat(explainTask.getFailOnDynamicFallback().get()).isFalse();
    assertThat(explainTask.getStrictReferences().get()).isFalse();

    Task checkTask = project.getTasks().getByName("check");
    assertThat(checkTask.getDependsOn())
        .noneSatisfy(
            dep -> {
              if (dep instanceof org.gradle.api.tasks.TaskProvider<?> tp) {
                assertThat(tp.get()).isEqualTo(explainTask);
              } else {
                assertThat(dep).isEqualTo(explainTask);
              }
            });
  }

  @Test
  @DisplayName("Explains typed template with String and Integer specialization")
  void testExplainTypedTemplateSpecialization(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-explain-typed\"\n",
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
        templateDir.resolve("user.vtl"), "Hello, $name! Age: $age.", StandardCharsets.UTF_8);
    Files.writeString(
        templateDir.resolve("user.vtl.contract"), "name=String\nage=int\n", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir).withArguments(VietTemplatePlugin.EXPLAIN_TASK_NAME).build();

    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains("=== Viet Template Compiler Explanation ===");
    assertThat(result.getOutput()).contains("Output Dispatch: WRITE_STRING_SPECIALIZED");
    assertThat(result.getOutput()).contains("Output Dispatch: WRITE_INTEGER_SPECIALIZED");
    assertThat(result.getOutput()).contains("BytecodeRuntimeBridge.writeString");
    assertThat(result.getOutput()).contains("BytecodeRuntimeBridge.writeInteger");
    assertThat(result.getOutput()).contains("Typed: true");
    assertThat(result.getOutput()).contains("Compilation Status: AOT_OK (AOT Eligible: true)");
  }

  @Test
  @DisplayName("Explains dynamic template and reports dynamic sites fallback")
  void testExplainDynamicTemplate(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-explain-dynamic\"\n",
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
        templateDir.resolve("dynamic.vtl"), "Dynamic value: $dynamicVar", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir).withArguments(VietTemplatePlugin.EXPLAIN_TASK_NAME).build();

    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains("Typed: false");
    assertThat(result.getOutput()).contains("UNTYPED_TEMPLATE");
    assertThat(result.getOutput()).contains("Compilation Status: AOT_OK_WITH_DYNAMIC_SITES");
  }

  @Test
  @DisplayName("Fails explain task when failOnDynamicFallback is true and fallback occurs")
  void testExplainFailOnDynamicFallback(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-explain-fail-fallback\"\n",
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
            + "    failOnDynamicFallback.set(true)\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path templateDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(templateDir);
    Files.writeString(
        templateDir.resolve("dynamic.vtl"), "Dynamic value: $dynamicVar", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir).withArguments(VietTemplatePlugin.EXPLAIN_TASK_NAME).buildAndFail();

    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput())
        .contains(
            "one or more templates require dynamic fallback with failOnDynamicFallback enabled");
  }

  @Test
  @DisplayName("Generates deterministic JSON explanation report to outputFile")
  void testExplainJsonOutputFileGeneration(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-explain-json\"\n",
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
            + "    outputFile.set(layout.buildDirectory.file(\"reports/explanation.json\"))\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path templateDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(templateDir);
    Files.writeString(templateDir.resolve("greeting.vtl"), "Hello, $name!", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir).withArguments(VietTemplatePlugin.EXPLAIN_TASK_NAME).build();

    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    Path jsonFile = projectDir.resolve("build/reports/explanation.json");
    assertThat(jsonFile).isRegularFile();
    String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
    assertThat(content).contains("\"formatVersion\": 1");
    assertThat(content).contains("\"templateId\": \"greeting.vtl\"");
    assertThat(content).contains("\"success\": true");
  }

  @Test
  @DisplayName("Non-existent template directory skips gracefully during explainVietTemplates")
  void testExplainNonExistentSourceDirectorySucceeds(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-explain-missing-dir\"\n",
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

    BuildResult result =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.EXPLAIN_TASK_NAME, "--info")
            .build();

    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains("source directory does not exist, skipping");
  }

  @Test
  @DisplayName("explainVietTemplates task is compatible with Gradle Configuration Cache")
  void testExplainConfigurationCacheCompatible(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-explain-config-cache\"\n",
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
        templateDir.resolve("hello.vtl"), "Hello, $name! Welcome.", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.EXPLAIN_TASK_NAME, "--configuration-cache")
            .build();

    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.EXPLAIN_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }
}
