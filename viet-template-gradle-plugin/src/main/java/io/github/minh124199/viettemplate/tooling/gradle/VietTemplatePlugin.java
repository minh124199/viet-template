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
  public static final String GENERATE_FACADES_TASK_NAME = "generateVietTemplateFacades";
  public static final String GENERATE_SCHEMAS_TASK_NAME = "generateVietTemplateSchemas";
  public static final String GENERATE_TYPESCRIPT_TASK_NAME = "generateVietTemplateTypeScript";
  public static final String VALIDATE_TASK_NAME = "validateVietTemplates";
  public static final String EXPLAIN_TASK_NAME = "explainVietTemplates";

  @Override
  public void apply(Project project) {
    VietTemplateExtension extension =
        project.getExtensions().create(EXTENSION_NAME, VietTemplateExtension.class);

    // Wires defaults:
    // sourceDirectory: src/main/viet-template
    // outputDirectory: layout.buildDirectory.dir("generated/viet-template/classes")
    // resourceOutputDirectory: layout.buildDirectory.dir("generated/viet-template/resources")
    // generatedSourcesDirectory: layout.buildDirectory.dir("generated/viet-template/sources")
    // schemaOutputDirectory: layout.buildDirectory.dir("generated/viet-template/schemas")
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
    extension
        .getGeneratedSourcesDirectory()
        .convention(project.getLayout().getBuildDirectory().dir("generated/viet-template/sources"));
    extension
        .getSchemaOutputDirectory()
        .convention(project.getLayout().getBuildDirectory().dir("generated/viet-template/schemas"));
    extension
        .getTypeScriptOutputDirectory()
        .convention(
            project.getLayout().getBuildDirectory().dir("generated/viet-template/typescript"));

    TaskProvider<VietTemplateGenerateFacadesTask> generateFacadesTask =
        project
            .getTasks()
            .register(
                GENERATE_FACADES_TASK_NAME,
                VietTemplateGenerateFacadesTask.class,
                facadeTask -> {
                  facadeTask.setDescription(
                      "Generates typed Java facades for Viet Template contracts Ahead-Of-Time.");
                  facadeTask.setGroup("build");

                  facadeTask.getSourceDirectory().convention(extension.getSourceDirectory());
                  facadeTask
                      .getGeneratedSourcesDirectory()
                      .convention(extension.getGeneratedSourcesDirectory());
                  facadeTask.getIncludes().convention(extension.getIncludes());
                  facadeTask.getExcludes().convention(extension.getExcludes());
                  facadeTask.getEncoding().convention(extension.getEncoding());
                  facadeTask.getPackagePrefix().convention(extension.getPackagePrefix());
                  facadeTask.getFailOnWarning().convention(extension.getFailOnWarning());
                  facadeTask
                      .getGenerateTypedFacades()
                      .convention(extension.getGenerateTypedFacades());
                  facadeTask.getTypeChecking().convention(extension.getTypeChecking());
                  facadeTask.getProfile().convention(extension.getProfile());
                  facadeTask.onlyIf(t -> facadeTask.getGenerateTypedFacades().getOrElse(false));
                });

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
                  compileTask
                      .getGeneratedSourcesDirectory()
                      .convention(extension.getGeneratedSourcesDirectory());
                  compileTask.getIncludes().convention(extension.getIncludes());
                  compileTask.getExcludes().convention(extension.getExcludes());
                  compileTask.getEncoding().convention(extension.getEncoding());
                  compileTask.getPackagePrefix().convention(extension.getPackagePrefix());
                  compileTask.getFailOnWarning().convention(extension.getFailOnWarning());
                  compileTask.getIncremental().convention(extension.getIncremental());
                  compileTask
                      .getGenerateTypedFacades()
                      .convention(extension.getGenerateTypedFacades());
                  compileTask.getTypeChecking().convention(extension.getTypeChecking());
                  compileTask.getProfile().convention(extension.getProfile());
                });

    TaskProvider<VietTemplateGenerateSchemasTask> generateSchemasTask =
        project
            .getTasks()
            .register(
                GENERATE_SCHEMAS_TASK_NAME,
                VietTemplateGenerateSchemasTask.class,
                schemasTask -> {
                  schemasTask.setDescription(
                      "Generates canonical contract schemas (*.vt-schema.json) for Viet Template"
                          + " contracts Ahead-Of-Time.");
                  schemasTask.setGroup("build");

                  schemasTask.getSourceDirectory().convention(extension.getSourceDirectory());
                  schemasTask
                      .getSchemaOutputDirectory()
                      .convention(extension.getSchemaOutputDirectory());
                  schemasTask.getIncludes().convention(extension.getIncludes());
                  schemasTask.getExcludes().convention(extension.getExcludes());
                  schemasTask.getEncoding().convention(extension.getEncoding());
                  schemasTask.getPackagePrefix().convention(extension.getPackagePrefix());
                  schemasTask.getFailOnWarning().convention(extension.getFailOnWarning());
                });

    TaskProvider<VietTemplateGenerateTypeScriptTask> generateTypeScriptTask =
        project
            .getTasks()
            .register(
                GENERATE_TYPESCRIPT_TASK_NAME,
                VietTemplateGenerateTypeScriptTask.class,
                typeScriptTask -> {
                  typeScriptTask.setDescription(
                      "Generates TypeScript declaration files (*.d.ts) for Viet Template contracts"
                          + " Ahead-Of-Time.");
                  typeScriptTask.setGroup("build");

                  typeScriptTask
                      .getSchemaDirectory()
                      .convention(extension.getSchemaOutputDirectory());
                  typeScriptTask
                      .getTypeScriptOutputDirectory()
                      .convention(extension.getTypeScriptOutputDirectory());
                  typeScriptTask.dependsOn(generateSchemasTask);
                });

    TaskProvider<VietTemplateValidateTask> validateTask =
        project
            .getTasks()
            .register(
                VALIDATE_TASK_NAME,
                VietTemplateValidateTask.class,
                vTask -> {
                  vTask.setDescription(
                      "Validates Viet Template files at build time without template execution.");
                  vTask.setGroup("verification");

                  vTask.getSourceDirectory().convention(extension.getSourceDirectory());
                  vTask.getIncludes().convention(extension.getIncludes());
                  vTask.getExcludes().convention(extension.getExcludes());
                  vTask.getEncoding().convention(extension.getEncoding());
                  vTask.getFailOnWarning().convention(extension.getFailOnWarning());
                  vTask.getTypeChecking().convention(extension.getTypeChecking());
                  vTask.getProfile().convention(extension.getProfile());
                });

    TaskProvider<VietTemplateExplainTask> explainTask =
        project
            .getTasks()
            .register(
                EXPLAIN_TASK_NAME,
                VietTemplateExplainTask.class,
                eTask -> {
                  eTask.setDescription(
                      "Explains Viet Template compiler optimization and code generation"
                          + " decisions.");
                  eTask.setGroup("help");

                  eTask.getSourceDirectory().convention(extension.getSourceDirectory());
                  eTask.getIncludes().convention(extension.getIncludes());
                  eTask.getExcludes().convention(extension.getExcludes());
                  eTask.getEncoding().convention(extension.getEncoding());
                  eTask.getTypeChecking().convention(extension.getTypeChecking());
                  eTask.getProfile().convention(extension.getProfile());
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

              VietTemplateGenerateFacadesTask facadeTask = generateFacadesTask.get();
              facadeTask.getClasspath().from(mainSourceSet.getCompileClasspath());
              mainSourceSet.getJava().srcDir(facadeTask.getGeneratedSourcesDirectory());
              project
                  .getTasks()
                  .named(JavaPlugin.COMPILE_JAVA_TASK_NAME)
                  .configure(t -> t.dependsOn(facadeTask));

              VietTemplateCompileTask compileTask = task.get();
              mainSourceSet.getOutput().dir(compileTask.getOutputDirectory());
              mainSourceSet.getResources().srcDir(compileTask.getResourceOutputDirectory());
              compileTask
                  .getClasspath()
                  .from(
                      mainSourceSet.getCompileClasspath(),
                      mainSourceSet.getOutput().getClassesDirs());
              compileTask.dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME);
              project
                  .getTasks()
                  .named(JavaPlugin.CLASSES_TASK_NAME)
                  .configure(t -> t.dependsOn(compileTask));

              VietTemplateGenerateSchemasTask schemasTask = generateSchemasTask.get();
              schemasTask
                  .getClasspath()
                  .from(
                      mainSourceSet.getCompileClasspath(),
                      mainSourceSet.getOutput().getClassesDirs());
              schemasTask.dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME);
              mainSourceSet.getOutput().dir(schemasTask.getSchemaOutputDirectory());
              project
                  .getTasks()
                  .named(JavaPlugin.CLASSES_TASK_NAME)
                  .configure(t -> t.dependsOn(schemasTask));

              VietTemplateGenerateTypeScriptTask typeScriptTask = generateTypeScriptTask.get();
              typeScriptTask.dependsOn(schemasTask);
              project
                  .getTasks()
                  .named(JavaPlugin.CLASSES_TASK_NAME)
                  .configure(t -> t.dependsOn(typeScriptTask));

              VietTemplateValidateTask vTask = validateTask.get();
              vTask
                  .getClasspath()
                  .from(
                      mainSourceSet.getCompileClasspath(),
                      mainSourceSet.getOutput().getClassesDirs());
              project.getTasks().named("check").configure(t -> t.dependsOn(validateTask));

              VietTemplateExplainTask eTask = explainTask.get();
              eTask
                  .getClasspath()
                  .from(
                      mainSourceSet.getCompileClasspath(),
                      mainSourceSet.getOutput().getClassesDirs());
            });
  }
}
