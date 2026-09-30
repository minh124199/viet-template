package io.github.minh124199.viettemplate.tooling.gradle;

import io.github.minh124199.viettemplate.aot.TemplateAotCompiler;
import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.aot.TemplateAotRequest;
import io.github.minh124199.viettemplate.aot.TemplateAotResult;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
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
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that generates canonical tooling contract schemas (*.vt-schema.json) for Viet
 * Template contracts Ahead-Of-Time.
 */
@CacheableTask
public abstract class VietTemplateGenerateSchemasTask extends DefaultTask {

  @Inject
  @SuppressWarnings("this-escape")
  public VietTemplateGenerateSchemasTask() {
    getIncludes().convention(List.of("**/*.vtl", "**/*.vm"));
    getExcludes().convention(List.of());
    getEncoding().convention("UTF-8");
    getPackagePrefix().convention("io.github.minh124199.viettemplate.generated");
    getFailOnWarning().convention(false);
  }

  @InputDirectory
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  @IgnoreEmptyDirectories
  public abstract DirectoryProperty getSourceDirectory();

  @OutputDirectory
  public abstract DirectoryProperty getSchemaOutputDirectory();

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
  public abstract Property<String> getPackagePrefix();

  @Input
  @Optional
  public abstract Property<Boolean> getFailOnWarning();

  @TaskAction
  public void generateSchemas() {
    File srcDir = getSourceDirectory().getAsFile().getOrNull();
    if (srcDir == null || !srcDir.exists()) {
      getLogger()
          .info(
              "Viet Template source directory does not exist, skipping schema generation: {}",
              srcDir);
      return;
    }

    if (!srcDir.isDirectory()) {
      getLogger().warn("Viet Template source directory is not a directory, skipping: {}", srcDir);
      return;
    }

    File[] files = srcDir.listFiles();
    if (files == null || files.length == 0) {
      getLogger().info("Viet Template source directory is empty, skipping: {}", srcDir);
      return;
    }

    File schemaDir = getSchemaOutputDirectory().getAsFile().get();

    String enc = getEncoding().getOrElse("UTF-8");
    Charset charset;
    try {
      charset = Charset.forName(enc);
    } catch (IllegalArgumentException e) {
      throw new GradleException("Invalid encoding: " + enc, e);
    }

    String pkg = getPackagePrefix().getOrElse("io.github.minh124199.viettemplate.generated");
    boolean failWarn = getFailOnWarning().getOrElse(false);

    TemplateAotRequest.Builder reqBuilder =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir.toPath())
            .schemaOutputDirectory(schemaDir.toPath())
            .generateSchemas(true)
            .compileBytecode(false)
            .encoding(charset)
            .packagePrefix(pkg)
            .failOnWarning(failWarn);

    List<String> incl = getIncludes().getOrNull();
    if (incl != null && !incl.isEmpty()) {
      reqBuilder.includePatterns(incl);
    }
    List<String> excl = getExcludes().getOrNull();
    if (excl != null && !excl.isEmpty()) {
      reqBuilder.excludePatterns(excl);
    }

    URLClassLoader urlClassLoader = null;
    if (!getClasspath().isEmpty()) {
      try {
        List<URL> urls = new ArrayList<>();
        for (File f : getClasspath().getFiles()) {
          urls.add(f.toURI().toURL());
        }
        urlClassLoader =
            new URLClassLoader(
                urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
        reqBuilder.classLoader(urlClassLoader);
      } catch (MalformedURLException e) {
        throw new GradleException("Failed to construct classpath URL: " + e.getMessage(), e);
      }
    }

    try {
      TemplateAotRequest request;
      try {
        request = reqBuilder.build();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new GradleException(
            "Failed to build schema generation request: " + e.getMessage(), e);
      }

      TemplateAotCompiler compiler = TemplateAotCompiler.create();
      TemplateAotResult result = compiler.compile(request);

      for (TemplateAotDiagnostic diagnostic : result.diagnostics()) {
        String message = diagnostic.formattedMessage();
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
          getLogger().error(message);
        } else if (diagnostic.severity() == DiagnosticSeverity.WARNING) {
          getLogger().warn(message);
        } else {
          getLogger().info(message);
        }
      }

      if (!result.success()) {
        long errorCount =
            result.diagnostics().stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .count();
        throw new GradleException(
            "Viet Template schema generation failed with " + errorCount + " error(s).");
      }

      getLogger().info("Generated canonical contract schemas in {}", schemaDir);
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (IOException e) {
          getLogger().debug("Failed to close URLClassLoader: {}", e.getMessage());
        }
      }
    }
  }
}
