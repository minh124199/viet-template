package io.github.minh124199.viettemplate.tooling.gradle;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.migration.MigrationReport;
import io.github.minh124199.viettemplate.migration.MigrationSeverity;
import io.github.minh124199.viettemplate.migration.TemplateMigrationAnalyzer;
import io.github.minh124199.viettemplate.migration.TemplateMigrationRequest;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

/**
 * Gradle task that analyzes Apache Velocity templates and produces a migration readiness report.
 */
@UntrackedTask(
    because =
        "Migration report is an informational/diagnostic report without persistent output cache"
            + " requirements")
public abstract class VietTemplateMigrationReportTask extends DefaultTask {

  @Inject
  @SuppressWarnings("this-escape")
  public VietTemplateMigrationReportTask() {
    getIncludes().convention(List.of("**/*.vtl", "**/*.vm"));
    getExcludes().convention(List.of());
    getEncoding().convention("UTF-8");
    getTypeChecking().convention("OFF");
    getProfile().convention("VTL_MIGRATION");
    getFormat().convention("text");
    getFailOnBlocker().convention(false);
    getFailOnWarning().convention(false);
    getStrictReferences().convention(false);
    getMinimumSeverity().convention("INFO");
  }

  @InputFiles
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
  public abstract Property<String> getTypeChecking();

  @Input
  @Optional
  public abstract Property<String> getProfile();

  @Input
  @Optional
  public abstract Property<String> getTemplate();

  @Input
  @Optional
  public abstract Property<String> getFormat();

  @OutputFile
  @Optional
  public abstract RegularFileProperty getOutputFile();

  @Input
  @Optional
  public abstract Property<Boolean> getFailOnBlocker();

  @Input
  @Optional
  public abstract Property<Boolean> getFailOnWarning();

  @Input
  @Optional
  public abstract Property<Boolean> getStrictReferences();

  @Input
  @Optional
  public abstract Property<String> getMinimumSeverity();

  @TaskAction
  public void runMigrationReport() {
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

    String typeChecking = getTypeChecking().getOrElse("OFF");
    TypeCheckingMode mode;
    try {
      mode = TypeCheckingConfigParser.parse(typeChecking);
    } catch (IllegalArgumentException e) {
      throw new GradleException(e.getMessage(), e);
    }

    String prof = getProfile().getOrElse("VTL_MIGRATION");
    VtlProfile vtlProfile = null;
    if (prof != null && !prof.isBlank()) {
      try {
        vtlProfile = VtlProfile.valueOf(prof.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new GradleException("Invalid profile configuration: '" + prof + "'", e);
      }
    }

    String minSevStr = getMinimumSeverity().getOrElse("INFO");
    MigrationSeverity minSeverity = MigrationSeverity.INFO;
    if (minSevStr != null && !minSevStr.isBlank()) {
      try {
        minSeverity = MigrationSeverity.valueOf(minSevStr.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new GradleException("Invalid minimumSeverity configuration: '" + minSevStr + "'", e);
      }
    }

    boolean failOnBlocker = getFailOnBlocker().getOrElse(false);
    boolean failOnWarning = getFailOnWarning().getOrElse(false);
    boolean strictReferences = getStrictReferences().getOrElse(false);

    TemplateMigrationRequest.Builder reqBuilder =
        TemplateMigrationRequest.builder()
            .sourceDirectory(srcDir.toPath())
            .encoding(charset)
            .typeCheckingMode(mode)
            .minimumSeverity(minSeverity)
            .failOnBlocker(failOnBlocker)
            .failOnWarning(failOnWarning)
            .strictReferences(strictReferences);

    if (vtlProfile != null) {
      reqBuilder.profile(vtlProfile);
    }

    String tpl = getTemplate().getOrNull();
    if (tpl != null && !tpl.isBlank()) {
      reqBuilder.template(tpl.trim());
    }

    String fmt = getFormat().getOrElse("text");
    if (fmt != null && !fmt.isBlank()) {
      reqBuilder.format(fmt.trim());
    }

    File outFile = getOutputFile().getAsFile().getOrNull();
    if (outFile != null) {
      reqBuilder.outputFile(outFile.toPath());
    }

    List<String> inc = getIncludes().getOrNull();
    if (inc != null && !inc.isEmpty()) {
      reqBuilder.includes(inc);
    }

    List<String> exc = getExcludes().getOrNull();
    if (exc != null && !exc.isEmpty()) {
      reqBuilder.excludes(exc);
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
      TemplateMigrationRequest request;
      try {
        request = reqBuilder.build();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new GradleException(
            "Failed to build template migration request: " + e.getMessage(), e);
      }

      TemplateMigrationAnalyzer analyzer = TemplateMigrationAnalyzer.create();
      MigrationReport report = analyzer.analyze(request);

      for (TemplateAotDiagnostic diagnostic : report.validationDiagnostics()) {
        String message = diagnostic.formattedMessage();
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
          getLogger().error("{}", message);
        } else if (diagnostic.severity() == DiagnosticSeverity.WARNING) {
          getLogger().warn("{}", message);
        } else {
          getLogger().info("{}", message);
        }
      }

      String outputContent = "json".equalsIgnoreCase(fmt) ? report.asJson() : report.asText();
      getLogger().lifecycle("{}", outputContent);

      if (outFile != null) {
        Path outPath = outFile.toPath().toAbsolutePath().normalize();
        try {
          if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
          }
          Files.writeString(outPath, outputContent, charset);
        } catch (IOException e) {
          throw new GradleException("Failed to write output file: " + outFile, e);
        }
      }

      int blockers =
          report.summary().findingsBySeverity().getOrDefault(MigrationSeverity.BLOCKER, 0);
      int warnings =
          report.summary().findingsBySeverity().getOrDefault(MigrationSeverity.WARNING, 0);

      if (failOnBlocker && blockers > 0) {
        throw new GradleException(
            "Viet Template migration report detected "
                + blockers
                + " blocker(s) with failOnBlocker enabled.");
      }
      if (failOnWarning && warnings > 0) {
        throw new GradleException(
            "Viet Template migration report detected "
                + warnings
                + " warning(s) with failOnWarning enabled.");
      }
      if (!report.success()) {
        throw new GradleException(
            "Viet Template migration report failed due to template validation errors.");
      }
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (IOException e) {
          getLogger().debug("Error closing compile classpath ClassLoader", e);
        }
      }
    }
  }
}
