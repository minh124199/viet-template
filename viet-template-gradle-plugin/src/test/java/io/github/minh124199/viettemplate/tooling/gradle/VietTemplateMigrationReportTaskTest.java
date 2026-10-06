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

class VietTemplateMigrationReportTaskTest {

  private static GradleRunner createRunner(Path projectDir) {
    GradleRunner runner = GradleRunner.create().withProjectDir(projectDir.toFile());
    if (VietTemplateMigrationReportTaskTest.class
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
  @DisplayName("Registers migrationReport task with help group and not wired to check")
  void testMigrationReportTaskRegistration(@TempDir Path tempDir) {
    Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
    project.getPlugins().apply(JavaPlugin.class);
    project.getPlugins().apply("io.github.minh124199.viet-template");

    Task taskObj = project.getTasks().findByName(VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME);
    assertThat(taskObj).isInstanceOf(VietTemplateMigrationReportTask.class);
    VietTemplateMigrationReportTask reportTask = (VietTemplateMigrationReportTask) taskObj;

    assertThat(reportTask.getGroup()).isEqualTo("help");
    assertThat(reportTask.getDescription())
        .isEqualTo(
            "Analyzes Apache Velocity templates and generates a migration readiness report.");
    assertThat(reportTask.getEncoding().get()).isEqualTo("UTF-8");
    assertThat(reportTask.getTypeChecking().get()).isEqualTo("OFF");
    assertThat(reportTask.getProfile().get()).isEqualTo("VTL_MIGRATION");
    assertThat(reportTask.getFormat().get()).isEqualTo("text");
    assertThat(reportTask.getFailOnBlocker().get()).isFalse();
    assertThat(reportTask.getFailOnWarning().get()).isFalse();
    assertThat(reportTask.getStrictReferences().get()).isFalse();
    assertThat(reportTask.getMinimumSeverity().get()).isEqualTo("INFO");

    Task checkTask = project.getTasks().getByName("check");
    assertThat(checkTask.getDependsOn())
        .noneSatisfy(
            dep -> {
              if (dep instanceof org.gradle.api.tasks.TaskProvider<?> tp) {
                assertThat(tp.get()).isEqualTo(reportTask);
              } else {
                assertThat(dep).isEqualTo(reportTask);
              }
            });
  }

  @Test
  @DisplayName("Runs migrationReport task on exact-compatible template")
  void testRunMigrationReportExactCompatible(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-migration-report-exact\"\n",
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
        templateDir.resolve("welcome.vtl"),
        "#set($title = 'Welcome')\n<h1>$title</h1>\n<p>Hello $name</p>\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        templateDir.resolve("welcome.vtl.contract"), "name=String\n", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)
            .build();

    assertThat(result.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains("=== Viet Template Migration Analysis Report ===");
    assertThat(result.getOutput()).contains("Apache Velocity");
    assertThat(result.getOutput()).contains("Viet Template");
    assertThat(result.getOutput()).contains("Readiness Status: READY");
    assertThat(result.getOutput()).contains("Total Templates: 1");
    assertThat(result.getOutput()).contains("Compatible Templates: 1");
  }

  @Test
  @DisplayName("Fails migrationReport task when failOnBlocker is true and blockers exist")
  void testFailOnBlocker(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-migration-report-fail-blocker\"\n",
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
            + "    failOnBlocker.set(true)\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path templateDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(templateDir);
    Files.writeString(
        templateDir.resolve("divzero.vtl"), "#set($x = 10 / 0)\n", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)
            .buildAndFail();

    assertThat(result.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("blocker(s) with failOnBlocker enabled");
  }

  @Test
  @DisplayName("Generates JSON output to configured outputFile")
  void testJsonOutputToFile(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-migration-report-json\"\n",
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
            + "    outputFile.set(layout.buildDirectory.file(\"migration.json\"))\n"
            + "}\n",
        StandardCharsets.UTF_8);

    Path templateDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(templateDir);
    Files.writeString(templateDir.resolve("greeting.vtl"), "Hello, $name!", StandardCharsets.UTF_8);
    Files.writeString(
        templateDir.resolve("greeting.vtl.contract"), "name=String\n", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)
            .build();

    assertThat(result.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    Path jsonFile = projectDir.resolve("build/migration.json");
    assertThat(jsonFile).isRegularFile();
    String jsonContent = Files.readString(jsonFile, StandardCharsets.UTF_8);
    assertThat(jsonContent).contains("\"formatVersion\": 1");
    assertThat(jsonContent).contains("\"sourceEngine\": \"Apache Velocity\"");
    assertThat(jsonContent).contains("\"targetEngine\": \"Viet Template\"");
    assertThat(jsonContent).contains("\"totalTemplates\": 1");
    assertThat(jsonContent).contains("\"success\": true");
  }

  @Test
  @DisplayName("Handles non-existent source directory cleanly")
  void testNonExistentSourceDirectory(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-migration-report-nonexistent\"\n",
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
            .withArguments(VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)
            .build();

    assertThat(result.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.MIGRATION_REPORT_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }
}
