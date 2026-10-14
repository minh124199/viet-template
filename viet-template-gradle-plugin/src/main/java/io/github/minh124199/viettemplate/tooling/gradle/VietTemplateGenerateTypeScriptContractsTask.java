package io.github.minh124199.viettemplate.tooling.gradle;

import io.github.minh124199.viettemplate.aot.TypeScriptContractProjector;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that generates deterministic TypeScript contract declaration files (*.vtl.d.ts)
 * Ahead-Of-Time by projecting #param directives, companion contracts, Java models, and canonical
 * schemas.
 */
@CacheableTask
public abstract class VietTemplateGenerateTypeScriptContractsTask extends DefaultTask {

  @Inject
  @SuppressWarnings("this-escape")
  public VietTemplateGenerateTypeScriptContractsTask() {
    getIncludes().convention(List.of("**/*.vtl", "**/*.vm", "**/*.vtl.html"));
    getExcludes().convention(List.of());
    getEncoding().convention("UTF-8");
    getCleanStaleOutputs().convention(true);
  }

  @InputFiles
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  @IgnoreEmptyDirectories
  public abstract DirectoryProperty getSourceDirectory();

  @OutputDirectory
  public abstract DirectoryProperty getTypeScriptOutputDirectory();

  @InputFiles
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  @IgnoreEmptyDirectories
  public abstract DirectoryProperty getSchemaDirectory();

  @InputFiles
  @Classpath
  @Optional
  public abstract ConfigurableFileCollection getClasspath();

  @Input
  @Optional
  public abstract ListProperty<String> getIncludes();

  @Input
  @Optional
  public abstract ListProperty<String> getExcludes();

  @Input
  @Optional
  public abstract Property<String> getEncoding();

  @Input
  @Optional
  public abstract Property<Boolean> getCleanStaleOutputs();

  @TaskAction
  public void generateTypeScriptContracts() {
    File srcDir = getSourceDirectory().getAsFile().getOrNull();
    if (srcDir == null || !srcDir.exists()) {
      getLogger()
          .info(
              "Viet Template source directory does not exist, skipping TypeScript contract"
                  + " generation: {}",
              srcDir);
      return;
    }

    if (!srcDir.isDirectory()) {
      getLogger().warn("Viet Template source directory is not a directory, skipping: {}", srcDir);
      return;
    }

    File outDir = getTypeScriptOutputDirectory().getAsFile().get();
    File schemaDir = getSchemaDirectory().getAsFile().getOrNull();

    Charset charset;
    try {
      charset = Charset.forName(getEncoding().getOrElse("UTF-8"));
    } catch (IllegalArgumentException e) {
      throw new GradleException("Invalid encoding: " + getEncoding().getOrNull(), e);
    }

    URLClassLoader urlClassLoader = null;
    Set<File> cpFiles = getClasspath().getFiles();
    if (!cpFiles.isEmpty()) {
      List<URL> urls = new ArrayList<>();
      for (File file : cpFiles) {
        try {
          urls.add(file.toURI().toURL());
        } catch (MalformedURLException e) {
          getLogger().debug("Ignoring unresolvable classpath element: {}", file, e);
        }
      }
      urlClassLoader =
          new URLClassLoader(
              urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
    }

    try {
      TypeScriptContractProjector projector = new TypeScriptContractProjector();
      Map<String, Integer> summary =
          projector.projectTemplates(
              srcDir.toPath(),
              outDir.toPath(),
              getIncludes().getOrElse(List.of("**/*.vtl", "**/*.vm", "**/*.vtl.html")),
              getExcludes().getOrElse(List.of()),
              charset,
              urlClassLoader != null
                  ? urlClassLoader
                  : Thread.currentThread().getContextClassLoader(),
              getCleanStaleOutputs().getOrElse(true),
              null,
              null,
              (schemaDir != null && schemaDir.isDirectory()) ? schemaDir.toPath() : null);

      getLogger()
          .info(
              "Generated {} TypeScript contract(s) ({} written, {} unchanged, {} stale cleaned) in"
                  + " {}",
              summary.getOrDefault("generated", 0),
              summary.getOrDefault("written", 0),
              summary.getOrDefault("skipped", 0),
              summary.getOrDefault("deleted", 0),
              outDir.getAbsolutePath());
    } catch (IOException | RuntimeException e) {
      throw new GradleException(
          "Viet Template TypeScript contract generation failed: " + e.getMessage(), e);
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (IOException ignored) {
        }
      }
    }
  }
}
