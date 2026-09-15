package io.github.minh124199.viettemplate.tooling.gradle;

import io.github.minh124199.viettemplate.aot.TemplateAotCompiler;
import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.aot.TemplateAotRequest;
import io.github.minh124199.viettemplate.aot.TemplateAotResult;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Gradle task that compiles Viet Template files into JVM bytecode Ahead-Of-Time (AOT). */
@CacheableTask
public abstract class VietTemplateCompileTask extends DefaultTask {

  @Inject
  @SuppressWarnings("this-escape")
  public VietTemplateCompileTask() {
    getIncludes().convention(List.of("**/*.vtl", "**/*.vm"));
    getExcludes().convention(List.of());
    getEncoding().convention("UTF-8");
    getPackagePrefix().convention("io.github.minh124199.viettemplate.generated");
    getFailOnWarning().convention(false);
    getIncremental().convention(true);
  }

  @InputDirectory
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  @IgnoreEmptyDirectories
  public abstract DirectoryProperty getSourceDirectory();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @OutputDirectory
  public abstract DirectoryProperty getResourceOutputDirectory();

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

  @Input
  @Optional
  public abstract Property<Boolean> getIncremental();

  @TaskAction
  public void compileTemplates() {
    File srcDir = getSourceDirectory().getAsFile().getOrNull();
    if (srcDir == null || !srcDir.exists()) {
      getLogger().info("Viet Template source directory does not exist, skipping: {}", srcDir);
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

    File outDir = getOutputDirectory().getAsFile().get();
    File resDir = getResourceOutputDirectory().getAsFile().get();

    String enc = getEncoding().getOrElse("UTF-8");
    Charset charset;
    try {
      charset = Charset.forName(enc);
    } catch (Exception e) {
      throw new GradleException("Invalid encoding: " + enc, e);
    }

    String pkg = getPackagePrefix().getOrElse("io.github.minh124199.viettemplate.generated");
    boolean failWarn = getFailOnWarning().getOrElse(false);
    boolean incr = getIncremental().getOrElse(true);

    TemplateAotRequest.Builder reqBuilder =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir.toPath())
            .outputDirectory(outDir.toPath())
            .resourceOutputDirectory(resDir.toPath())
            .encoding(charset)
            .packagePrefix(pkg)
            .failOnWarning(failWarn)
            .incremental(incr);

    List<String> incl = getIncludes().getOrNull();
    if (incl != null && !incl.isEmpty()) {
      reqBuilder.includePatterns(incl);
    }
    List<String> excl = getExcludes().getOrNull();
    if (excl != null && !excl.isEmpty()) {
      reqBuilder.excludePatterns(excl);
    }

    TemplateAotRequest request;
    try {
      request = reqBuilder.build();
    } catch (Exception e) {
      throw new GradleException("Failed to build AOT compilation request: " + e.getMessage(), e);
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
          "Viet Template AOT compilation failed with " + errorCount + " error(s).");
    }

    getLogger()
        .info(
            "Compiled {} Viet Template(s) ({} skipped, {} deleted).",
            result.compiledCount(),
            result.skippedCount(),
            result.deletedCount());
  }
}
