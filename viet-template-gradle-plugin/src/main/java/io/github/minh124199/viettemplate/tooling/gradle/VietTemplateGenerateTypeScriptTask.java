package io.github.minh124199.viettemplate.tooling.gradle;

import io.github.minh124199.viettemplate.aot.TypeScriptDeclarationProjector;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that generates TypeScript declaration files (*.d.ts) for Viet Template contracts
 * Ahead-Of-Time by projecting canonical contract schemas (*.vt-schema.json).
 */
@CacheableTask
public abstract class VietTemplateGenerateTypeScriptTask extends DefaultTask {

  @Inject
  public VietTemplateGenerateTypeScriptTask() {}

  @InputDirectory
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  @IgnoreEmptyDirectories
  public abstract DirectoryProperty getSchemaDirectory();

  @OutputDirectory
  public abstract DirectoryProperty getTypeScriptOutputDirectory();

  @TaskAction
  public void generateTypeScript() {
    File schemaDir = getSchemaDirectory().getAsFile().getOrNull();
    if (schemaDir == null || !schemaDir.exists()) {
      getLogger()
          .info(
              "Schema directory does not exist, skipping TypeScript declaration generation: {}",
              schemaDir);
      return;
    }

    if (!schemaDir.isDirectory()) {
      getLogger().warn("Schema directory is not a directory, skipping: {}", schemaDir);
      return;
    }

    File outDir = getTypeScriptOutputDirectory().getAsFile().get();
    Path outDirPath = outDir.toPath();

    List<Path> schemaFiles;
    try (Stream<Path> stream = Files.walk(schemaDir.toPath())) {
      schemaFiles =
          stream
              .filter(Files::isRegularFile)
              .filter(
                  p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".vt-schema.json") || name.endsWith(".schema.json");
                  })
              .sorted(Comparator.comparing(Path::toString))
              .toList();
    } catch (IOException e) {
      throw new GradleException("Failed to scan schema directory: " + e.getMessage(), e);
    }

    if (schemaFiles.isEmpty()) {
      getLogger().info("No schema files found in {}", schemaDir);
      return;
    }

    try {
      Files.createDirectories(outDirPath);
      java.util.Set<Path> generatedFiles = new java.util.HashSet<>();
      io.github.minh124199.viettemplate.schema.JsonSchemaImporter jsonSchemaImporter =
          new io.github.minh124199.viettemplate.schema.JsonSchemaImporter();
      for (Path schemaFile : schemaFiles) {
        Path generatedFile;
        if (schemaFile.getFileName().toString().endsWith(".schema.json")) {
          io.github.minh124199.viettemplate.schema.SchemaImportResult importResult =
              jsonSchemaImporter.importSchemas(
                  new io.github.minh124199.viettemplate.schema.SchemaImportRequest(
                      List.of(
                          new io.github.minh124199.viettemplate.schema.SchemaSource(
                              schemaFile,
                              io.github.minh124199.viettemplate.schema.SchemaFormat.JSON_SCHEMA,
                              java.util.Optional.empty())),
                      false,
                      false));
          if (importResult.hasErrors() || importResult.schemas().isEmpty()) {
            throw new GradleException(
                "Failed to import JSON schema " + schemaFile + ": " + importResult.diagnostics());
          }
          generatedFile =
              TypeScriptDeclarationProjector.projectToFile(
                  importResult.schemas().values().iterator().next(), outDirPath);
        } else {
          generatedFile = TypeScriptDeclarationProjector.projectToFile(schemaFile, outDirPath);
        }
        generatedFiles.add(generatedFile.toAbsolutePath().normalize());
        getLogger().debug("Generated TypeScript declaration: {}", generatedFile);
      }

      // Clean up stale generated .d.ts files
      try (Stream<Path> existing = Files.walk(outDirPath)) {
        existing
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().endsWith(".d.ts"))
            .filter(p -> !generatedFiles.contains(p.toAbsolutePath().normalize()))
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                    getLogger().debug("Cleaned up stale TypeScript declaration: {}", p);
                  } catch (IOException ignored) {
                  }
                });
      }
    } catch (IOException | RuntimeException e) {
      throw new GradleException(
          "Viet Template TypeScript declaration projection failed: " + e.getMessage(), e);
    }

    getLogger()
        .info(
            "Generated {} TypeScript declaration file(s) in {}",
            schemaFiles.size(),
            outDir.getAbsolutePath());
  }
}
