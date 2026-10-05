package io.github.minh124199.viettemplate.tooling.gradle;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.validation.TemplateValidationRequest;
import io.github.minh124199.viettemplate.validation.TemplateValidationResult;
import io.github.minh124199.viettemplate.validation.TemplateValidator;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/** Gradle task that validates Viet Template files at build time without template execution. */
@UntrackedTask(
    because = "Validation is an in-memory verification task without persistent file outputs")
public abstract class VietTemplateValidateTask extends DefaultTask {

  @Inject
  @SuppressWarnings("this-escape")
  public VietTemplateValidateTask() {
    getIncludes().convention(List.of("**/*.vtl", "**/*.vm"));
    getExcludes().convention(List.of());
    getEncoding().convention("UTF-8");
    getFailOnWarning().convention(false);
    getTypeChecking().convention("OFF");
    getProfile().convention("VTL_MIGRATION");
  }

  @InputDirectory
  @Optional
  @PathSensitive(PathSensitivity.RELATIVE)
  @IgnoreEmptyDirectories
  public abstract DirectoryProperty getSourceDirectory();

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
  public abstract Property<Boolean> getFailOnWarning();

  @Input
  @Optional
  public abstract Property<String> getTypeChecking();

  @Input
  @Optional
  public abstract Property<String> getProfile();

  @TaskAction
  public void validateTemplates() {
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

    String enc = getEncoding().getOrElse("UTF-8");
    Charset charset;
    try {
      charset = Charset.forName(enc);
    } catch (IllegalArgumentException e) {
      throw new GradleException("Invalid encoding: " + enc, e);
    }

    boolean failWarn = getFailOnWarning().getOrElse(false);
    String typeChecking = getTypeChecking().getOrElse("OFF");

    TemplateValidationRequest.Builder reqBuilder =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir.toPath())
            .encoding(charset)
            .failOnWarning(failWarn)
            .validateDependencies(true);

    TypeCheckingMode mode;
    try {
      mode = TypeCheckingConfigParser.parse(typeChecking);
    } catch (IllegalArgumentException e) {
      throw new GradleException(e.getMessage(), e);
    }
    reqBuilder.typeCheckingMode(mode);

    String prof = getProfile().getOrElse("VTL_MIGRATION");
    if (prof != null && !prof.isBlank()) {
      try {
        VtlProfile vtlProfile = VtlProfile.valueOf(prof.trim().toUpperCase(Locale.ROOT));
        reqBuilder.profile(vtlProfile);
      } catch (IllegalArgumentException e) {
        throw new GradleException("Invalid profile configuration: '" + prof + "'", e);
      }
    }

    List<String> inc = getIncludes().getOrNull();
    if (inc != null && !inc.isEmpty()) {
      reqBuilder.includePatterns(inc);
    }

    List<String> exc = getExcludes().getOrNull();
    if (exc != null && !exc.isEmpty()) {
      reqBuilder.excludePatterns(exc);
    }

    URLClassLoader urlClassLoader = null;
    ConfigurableFileCollection cp = getClasspath();
    if (cp != null && !cp.isEmpty()) {
      List<URL> urls = new ArrayList<>();
      for (File f : cp.getFiles()) {
        try {
          urls.add(f.toURI().toURL());
        } catch (MalformedURLException e) {
          getLogger().debug("Ignoring malformed classpath entry: {}", f, e);
        }
      }
      urlClassLoader =
          new URLClassLoader(
              urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
      reqBuilder.classLoader(urlClassLoader);
    }

    try {
      TemplateValidationRequest request;
      try {
        request = reqBuilder.build();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new GradleException(
            "Failed to build template validation request: " + e.getMessage(), e);
      }

      TemplateValidator validator = TemplateValidator.create();
      TemplateValidationResult result = validator.validate(request);

      for (TemplateAotDiagnostic diagnostic : result.diagnostics()) {
        String message = diagnostic.formattedMessage();
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
          getLogger().error("{}", message);
        } else if (diagnostic.severity() == DiagnosticSeverity.WARNING) {
          getLogger().warn("{}", message);
        } else {
          getLogger().info("{}", message);
        }
      }

      if (!result.success()) {
        throw new GradleException(
            "Viet Template validation failed with "
                + result.errorCount()
                + " error(s)"
                + (result.warningCount() > 0
                    ? " and " + result.warningCount() + " warning(s)."
                    : "."));
      }

      getLogger().info("Validated {} Viet Template(s) successfully.", result.validatedCount());
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (java.io.IOException e) {
          getLogger().debug("Error closing compile classpath ClassLoader", e);
        }
      }
    }
  }
}
