package io.github.minh124199.viettemplate.tooling.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/** Gradle plugin that configures Ahead-Of-Time (AOT) template compilation for Viet Template. */
public class VietTemplatePlugin implements Plugin<Project> {

  public static final String EXTENSION_NAME = "vietTemplate";
  public static final String TASK_NAME = "compileVietTemplates";

  @Override
  public void apply(Project project) {
    VietTemplateExtension extension =
        project.getExtensions().create(EXTENSION_NAME, VietTemplateExtension.class);

    // Wires defaults:
    // sourceDirectory: src/main/viet-template
    // outputDirectory: layout.buildDirectory.dir("generated/viet-template/classes")
    // resourceOutputDirectory: layout.buildDirectory.dir("generated/viet-template/resources")
    extension
        .getSourceDirectory()
        .convention(project.getLayout().getProjectDirectory().dir("src/main/viet-template"));
    extension
        .getOutputDirectory()
        .convention(project.getLayout().getBuildDirectory().dir("generated/viet-template/classes"));
    extension
        .getResourceOutputDirectory()
        .convention(
            project.getLayout().getBuildDirectory().dir("generated/viet-template/resources"));

    TaskProvider<VietTemplateCompileTask> task =
        project
            .getTasks()
            .register(
                TASK_NAME,
                VietTemplateCompileTask.class,
                compileTask -> {
                  compileTask.setDescription("Compiles Viet Template files Ahead-Of-Time (AOT).");
                  compileTask.setGroup("build");

                  compileTask.getSourceDirectory().convention(extension.getSourceDirectory());
                  compileTask.getOutputDirectory().convention(extension.getOutputDirectory());
                  compileTask
                      .getResourceOutputDirectory()
                      .convention(extension.getResourceOutputDirectory());
                  compileTask.getIncludes().convention(extension.getIncludes());
                  compileTask.getExcludes().convention(extension.getExcludes());
                  compileTask.getEncoding().convention(extension.getEncoding());
                  compileTask.getPackagePrefix().convention(extension.getPackagePrefix());
                  compileTask.getFailOnWarning().convention(extension.getFailOnWarning());
                  compileTask.getIncremental().convention(extension.getIncremental());
                });

    // When java plugin applied:
    // sourceSets.named("main").get().getOutput().dir(task.getOutputDirectory())
    // sourceSets.named("main").get().getResources().srcDir(task.getResourceOutputDirectory())
    // task.dependsOn("compileJava")
    // tasks.named("classes").configure(t -> t.dependsOn(task))
    project
        .getPlugins()
        .withType(
            JavaPlugin.class,
            javaPlugin -> {
              JavaPluginExtension javaExt =
                  project.getExtensions().getByType(JavaPluginExtension.class);
              SourceSet mainSourceSet =
                  javaExt.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME).get();

              VietTemplateCompileTask compileTask = task.get();
              mainSourceSet.getOutput().dir(compileTask.getOutputDirectory());
              mainSourceSet.getResources().srcDir(compileTask.getResourceOutputDirectory());
              compileTask.dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME);
              project
                  .getTasks()
                  .named(JavaPlugin.CLASSES_TASK_NAME)
                  .configure(t -> t.dependsOn(compileTask));
            });
  }
}
