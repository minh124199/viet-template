package io.github.minh124199.viettemplate.tooling.gradle;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.explanation.TemplateExplainRequest;
import io.github.minh124199.viettemplate.explanation.TemplateExplainer;
import io.github.minh124199.viettemplate.explanation.TemplateExplanation;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
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

/** Gradle task that explains Viet Template compiler optimization and code generation decisions. */
@UntrackedTask(
    because =
        "Explain is an informational/diagnostic task without persistent output cache requirements")
public abstract class VietTemplateExplainTask extends DefaultTask {

  @Inject
  @SuppressWarnings("this-escape")
  public VietTemplateExplainTask() {
    getIncludes().convention(List.of("**/*.vtl", "**/*.vm"));
    getExcludes().convention(List.of());
    getEncoding().convention("UTF-8");
    getTypeChecking().convention("OFF");
    getProfile().convention("VTL_MIGRATION");
    getFormat().convention("text");
    getFailOnDynamicFallback().convention(false);
    getStrictReferences().convention(false);
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
  public abstract Property<Integer> getLine();

  @Input
  @Optional
  public abstract Property<Integer> getColumn();

  @Input
  @Optional
  public abstract Property<String> getFormat();

  @Input
  @Optional
  public abstract Property<Boolean> getFailOnDynamicFallback();

  @Input
  @Optional
  public abstract Property<Boolean> getStrictReferences();

  @OutputFile
  @Optional
  public abstract RegularFileProperty getOutputFile();

  @TaskAction
  public void explainTemplates() {
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

    boolean failOnDynamicFallback = getFailOnDynamicFallback().getOrElse(false);
    boolean strictReferences = getStrictReferences().getOrElse(false);

    TemplateExplainRequest.Builder reqBuilder =
        TemplateExplainRequest.builder()
            .sourceDirectory(srcDir.toPath())
            .encoding(charset)
            .typeCheckingMode(mode)
            .failOnDynamicFallback(failOnDynamicFallback)
            .strictReferences(strictReferences);

    if (vtlProfile != null) {
      reqBuilder.profile(vtlProfile);
    }

    String tpl = getTemplate().getOrNull();
    if (tpl != null && !tpl.isBlank()) {
      reqBuilder.template(tpl.trim());
    }

    Integer lineVal = getLine().getOrNull();
    if (lineVal != null && lineVal > 0) {
      reqBuilder.line(lineVal);
    }

    Integer colVal = getColumn().getOrNull();
    if (colVal != null && colVal > 0) {
      reqBuilder.column(colVal);
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
      TemplateExplainRequest request;
      try {
        request = reqBuilder.build();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new GradleException("Failed to build template explain request: " + e.getMessage(), e);
      }

      TemplateExplainer explainer = TemplateExplainer.create();
      TemplateExplanation result = explainer.explain(request);

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

      String outputContent = "json".equalsIgnoreCase(fmt) ? result.asJson() : result.asText();
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

      if (failOnDynamicFallback) {
        boolean hasFallback =
            result.templates().stream()
                .anyMatch(t -> !t.aotEligible() || !"AOT_OK".equals(t.compilationStatus()));
        if (hasFallback || !result.success()) {
          throw new GradleException(
              "Viet Template explanation failed: one or more templates require dynamic fallback"
                  + " with failOnDynamicFallback enabled.");
        }
      }

      if (!result.success()) {
        long errorCount =
            result.diagnostics().stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .count();
        throw new GradleException(
            "Viet Template explanation failed with " + errorCount + " error(s).");
      }

      getLogger().info("Explained {} Viet Template(s) successfully.", result.totalTemplates());
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
