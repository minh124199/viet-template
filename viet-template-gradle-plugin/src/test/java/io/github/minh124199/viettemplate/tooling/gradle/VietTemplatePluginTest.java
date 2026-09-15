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

class VietTemplatePluginTest {

  private static GradleRunner createRunner(Path projectDir) {
    GradleRunner runner = GradleRunner.create().withProjectDir(projectDir.toFile());
    if (VietTemplatePluginTest.class
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
  @DisplayName("Applies plugin and registers extension and task with correct conventions")
  void testPluginApplicationWithProjectBuilder(@TempDir Path tempDir) {
    Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
    project.getPlugins().apply(JavaPlugin.class);
    project.getPlugins().apply("io.github.minh124199.viet-template");

    // Verify extension
    Object extObj = project.getExtensions().findByName(VietTemplatePlugin.EXTENSION_NAME);
    assertThat(extObj).isInstanceOf(VietTemplateExtension.class);
    VietTemplateExtension extension = (VietTemplateExtension) extObj;
    assertThat(extension.getEncoding().get()).isEqualTo("UTF-8");
    assertThat(extension.getPackagePrefix().get())
        .isEqualTo("io.github.minh124199.viettemplate.generated");
    assertThat(extension.getFailOnWarning().get()).isFalse();
    assertThat(extension.getIncremental().get()).isTrue();

    // Verify task
    Task taskObj = project.getTasks().findByName(VietTemplatePlugin.TASK_NAME);
    assertThat(taskObj).isInstanceOf(VietTemplateCompileTask.class);
    VietTemplateCompileTask task = (VietTemplateCompileTask) taskObj;

    assertThat(task.getSourceDirectory().get().getAsFile())
        .isEqualTo(tempDir.resolve("src/main/viet-template").toFile());
    assertThat(task.getOutputDirectory().get().getAsFile())
        .isEqualTo(tempDir.resolve("build/generated/viet-template/classes").toFile());
    assertThat(task.getResourceOutputDirectory().get().getAsFile())
        .isEqualTo(tempDir.resolve("build/generated/viet-template/resources").toFile());

    // Verify task dependencies
    assertThat(task.getDependsOn()).contains(JavaPlugin.COMPILE_JAVA_TASK_NAME);
    Task classesTask = project.getTasks().getByName(JavaPlugin.CLASSES_TASK_NAME);
    assertThat(classesTask.getDependsOn()).contains(task);
  }

  @Test
  @DisplayName("Executes compileVietTemplates task successfully via GradleRunner")
  void testTaskExecutionWithGradleRunner(@TempDir Path projectDir) throws Exception {
    // settings.gradle.kts
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-aot-project\"\n",
        StandardCharsets.UTF_8);

    // build.gradle.kts
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

    // Template source
    Path templateDir = projectDir.resolve("src/main/viet-template");
    Files.createDirectories(templateDir);
    Files.writeString(
        templateDir.resolve("greeting.vtl"), "Hello, $name! Rendered AOT.", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir).withArguments(VietTemplatePlugin.TASK_NAME).build();

    assertThat(result.task(":" + VietTemplatePlugin.TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    // Verify generated index
    Path idxFile =
        projectDir.resolve(
            "build/generated/viet-template/resources/META-INF/viet-template/templates.idx");
    assertThat(idxFile).isRegularFile();
    String content = Files.readString(idxFile, StandardCharsets.UTF_8);
    assertThat(content).contains("greeting.vtl=");

    // Verify generated class
    String fqcn =
        content
            .lines()
            .filter(l -> l.startsWith("greeting.vtl="))
            .findFirst()
            .map(l -> l.substring("greeting.vtl=".length()).trim())
            .orElseThrow();
    Path classFile =
        projectDir.resolve(
            "build/generated/viet-template/classes/" + fqcn.replace('.', '/') + ".class");
    assertThat(classFile).isRegularFile();
  }

  @Test
  @DisplayName("Fails build when template has syntax error via GradleRunner")
  void testTaskFailureWithGradleRunner(@TempDir Path projectDir) throws Exception {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        "rootProject.name = \"test-fail-project\"\n",
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
    Files.writeString(templateDir.resolve("broken.vtl"), "#if($unclosed)", StandardCharsets.UTF_8);

    BuildResult result =
        createRunner(projectDir).withArguments(VietTemplatePlugin.TASK_NAME).buildAndFail();

    assertThat(result.task(":" + VietTemplatePlugin.TASK_NAME)).isNotNull();
    assertThat(result.task(":" + VietTemplatePlugin.TASK_NAME).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("Viet Template AOT compilation failed");
  }
}
