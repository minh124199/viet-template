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

class VietTemplateValidateTaskTest {

  private static GradleRunner createRunner(Path projectDir) {
    GradleRunner runner = GradleRunner.create().withProjectDir(projectDir.toFile());
    if (VietTemplateValidateTaskTest.class
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
  @DisplayName("Valid templates pass validation via validateVietTemplates task")
  void testValidateTemplatesSuccess(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-validate-success\"\n",
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
        createRunner(projectDir).withArguments(VietTemplatePlugin.VALIDATE_TASK_NAME).build();

    assertThat(result.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  @DisplayName("Template with syntax error causes validateVietTemplates task to fail")
  void testValidateTemplatesSyntaxErrorFails(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-validate-syntax-fail\"\n",
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
        templateDir.resolve("broken.vtl"), "#if($user) Unclosed block", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.VALIDATE_TASK_NAME)
            .buildAndFail();

    assertThat(result.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("Viet Template validation failed");
  }

  @Test
  @DisplayName("Missing static dependency causes validateVietTemplates task to fail")
  void testValidateTemplatesMissingDependencyFails(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-validate-missing-dep-fail\"\n",
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
        templateDir.resolve("main.vtl"),
        "<html>#parse('missing.vtl')</html>",
        StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir)
            .withArguments(VietTemplatePlugin.VALIDATE_TASK_NAME)
            .buildAndFail();

    assertThat(result.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput())
        .contains("Referenced template dependency not found: missing.vtl");
  }

  @Test
  @DisplayName("Executing Gradle check task triggers validateVietTemplates")
  void testCheckTaskDependsOnValidateVietTemplates(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-check-depends-validate\"\n",
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

    BuildResult result = createRunner(projectDir).withArguments("check").build();

    assertThat(result.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.VALIDATE_TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }
}
