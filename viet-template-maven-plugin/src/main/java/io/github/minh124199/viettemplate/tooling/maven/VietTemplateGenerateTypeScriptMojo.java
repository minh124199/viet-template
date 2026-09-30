package io.github.minh124199.viettemplate.tooling.maven;

import io.github.minh124199.viettemplate.aot.TypeScriptDeclarationProjector;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Maven Mojo to generate TypeScript declaration files (*.d.ts) for Viet Template contracts during
 * the {@code process-classes} phase by projecting canonical contract schemas (*.vt-schema.json).
 */
@Mojo(
    name = "generate-typescript",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class VietTemplateGenerateTypeScriptMojo extends AbstractMojo {

  @Parameter(
      defaultValue = "${project.build.directory}/generated-resources/viet-template/schemas",
      property = "viet-template.schemaDirectory")
  private File schemaDirectory;

  @Parameter(
      defaultValue = "${project.build.directory}/generated-sources/viet-template/typescript",
      property = "viet-template.typeScriptOutputDirectory")
  private File typeScriptOutputDirectory;

  @Parameter(defaultValue = "false", property = "viet-template.skip")
  private boolean skip = false;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping Viet Template TypeScript declaration generation (skip=true).");
      return;
    }

    if (schemaDirectory == null || !schemaDirectory.exists()) {
      getLog()
          .info(
              "Schema directory does not exist, skipping TypeScript declaration generation: "
                  + schemaDirectory);
      return;
    }

    if (!schemaDirectory.isDirectory()) {
      getLog().warn("Schema directory is not a directory, skipping: " + schemaDirectory);
      return;
    }

    if (typeScriptOutputDirectory == null) {
      throw new MojoExecutionException("typeScriptOutputDirectory must not be null");
    }

    List<Path> schemaFiles;
    try (Stream<Path> stream = Files.walk(schemaDirectory.toPath())) {
      schemaFiles =
          stream
              .filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(".vt-schema.json"))
              .sorted(Comparator.comparing(Path::toString))
              .toList();
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to scan schema directory: " + e.getMessage(), e);
    }

    if (schemaFiles.isEmpty()) {
      getLog().info("No schema files found in " + schemaDirectory.getAbsolutePath());
      return;
    }

    Path outDirPath = typeScriptOutputDirectory.toPath();
    try {
      Files.createDirectories(outDirPath);
      for (Path schemaFile : schemaFiles) {
        Path generatedFile = TypeScriptDeclarationProjector.projectToFile(schemaFile, outDirPath);
        getLog().debug("Generated TypeScript declaration: " + generatedFile);
      }
    } catch (IOException | RuntimeException e) {
      throw new MojoFailureException(
          "Viet Template TypeScript declaration projection failed: " + e.getMessage(), e);
    }

    getLog()
        .info(
            "Generated "
                + schemaFiles.size()
                + " TypeScript declaration file(s) in "
                + typeScriptOutputDirectory.getAbsolutePath());
  }

  public File getSchemaDirectory() {
    return schemaDirectory;
  }

  public void setSchemaDirectory(File schemaDirectory) {
    this.schemaDirectory = schemaDirectory;
  }

  public File getTypeScriptOutputDirectory() {
    return typeScriptOutputDirectory;
  }

  public void setTypeScriptOutputDirectory(File typeScriptOutputDirectory) {
    this.typeScriptOutputDirectory = typeScriptOutputDirectory;
  }

  public boolean isSkip() {
    return skip;
  }

  public void setSkip(boolean skip) {
    this.skip = skip;
  }

  public MavenProject getProject() {
    return project;
  }

  public void setProject(MavenProject project) {
    this.project = project;
  }
}
