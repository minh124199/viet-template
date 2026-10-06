package io.github.minh124199.viettemplate.tooling.maven;

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
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Maven Mojo to explain Viet Template compiler optimization and code generation decisions. */
@Mojo(name = "explain", requiresDependencyResolution = ResolutionScope.COMPILE)
public class VietTemplateExplainMojo extends AbstractMojo {

  @Parameter(
      defaultValue = "${project.basedir}/src/main/viet-template",
      property = "viet-template.sourceDirectory")
  private File sourceDirectory;

  @Parameter(property = "viet-template.includes")
  private List<String> includes = new ArrayList<>(List.of("**/*.vtl", "**/*.vm"));

  @Parameter(property = "viet-template.excludes")
  private List<String> excludes = new ArrayList<>();

  @Parameter(defaultValue = "UTF-8", property = "viet-template.encoding")
  private String encoding = "UTF-8";

  @Parameter(defaultValue = "OFF", property = "viet-template.typeChecking")
  private String typeChecking = "OFF";

  @Parameter(defaultValue = "VTL_MIGRATION", property = "viet-template.profile")
  private String profile = "VTL_MIGRATION";

  @Parameter(property = "viet-template.template")
  private String template;

  @Parameter(property = "viet-template.line")
  private Integer line;

  @Parameter(property = "viet-template.column")
  private Integer column;

  @Parameter(defaultValue = "text", property = "viet-template.format")
  private String format = "text";

  @Parameter(property = "viet-template.outputFile")
  private File outputFile;

  @Parameter(defaultValue = "false", property = "viet-template.skip")
  private boolean skip = false;

  @Parameter(defaultValue = "false", property = "viet-template.failOnDynamicFallback")
  private boolean failOnDynamicFallback = false;

  @Parameter(defaultValue = "false", property = "viet-template.strictReferences")
  private boolean strictReferences = false;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping Viet Template explanation (skip=true).");
      return;
    }

    if (sourceDirectory == null || !sourceDirectory.exists()) {
      getLog()
          .info(
              "Viet Template source directory does not exist, skipping: "
                  + (sourceDirectory != null ? sourceDirectory.getAbsolutePath() : "null"));
      return;
    }

    if (!sourceDirectory.isDirectory()) {
      getLog()
          .warn(
              "Viet Template source directory is not a directory, skipping: "
                  + sourceDirectory.getAbsolutePath());
      return;
    }

    String[] files = sourceDirectory.list();
    if (files == null || files.length == 0) {
      getLog()
          .info(
              "Viet Template source directory is empty, skipping: "
                  + sourceDirectory.getAbsolutePath());
      return;
    }

    Charset charset;
    try {
      charset = Charset.forName(encoding);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException("Invalid encoding: " + encoding, e);
    }

    TemplateExplainRequest.Builder requestBuilder =
        TemplateExplainRequest.builder()
            .sourceDirectory(sourceDirectory.toPath())
            .encoding(charset)
            .failOnDynamicFallback(failOnDynamicFallback)
            .strictReferences(strictReferences);

    TypeCheckingMode mode;
    try {
      mode = TypeCheckingConfigParser.parse(typeChecking);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    requestBuilder.typeCheckingMode(mode);

    if (profile != null && !profile.isBlank()) {
      try {
        VtlProfile vtlProfile = VtlProfile.valueOf(profile.trim().toUpperCase(Locale.ROOT));
        requestBuilder.profile(vtlProfile);
      } catch (IllegalArgumentException e) {
        throw new MojoExecutionException("Invalid profile configuration: '" + profile + "'", e);
      }
    }

    if (template != null && !template.isBlank()) {
      requestBuilder.template(template.trim());
    }
    if (line != null && line > 0) {
      requestBuilder.line(line);
    }
    if (column != null && column > 0) {
      requestBuilder.column(column);
    }
    if (format != null && !format.isBlank()) {
      requestBuilder.format(format.trim());
    }
    if (outputFile != null) {
      requestBuilder.outputFile(outputFile.toPath());
    }

    if (includes != null && !includes.isEmpty()) {
      requestBuilder.includePatterns(includes);
    }
    if (excludes != null && !excludes.isEmpty()) {
      requestBuilder.excludePatterns(excludes);
    }

    URLClassLoader urlClassLoader = null;
    if (project != null) {
      try {
        List<String> classpathElements = project.getCompileClasspathElements();
        if (classpathElements != null && !classpathElements.isEmpty()) {
          List<URL> urls = new ArrayList<>();
          for (String element : classpathElements) {
            urls.add(new File(element).toURI().toURL());
          }
          urlClassLoader =
              new URLClassLoader(
                  urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
          requestBuilder.classLoader(urlClassLoader);
        }
      } catch (DependencyResolutionRequiredException | MalformedURLException | RuntimeException e) {
        getLog().debug("Could not build compile classpath ClassLoader: " + e.getMessage());
      }
    }

    try {
      TemplateExplainRequest request;
      try {
        request = requestBuilder.build();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new MojoExecutionException(
            "Failed to build template explain request: " + e.getMessage(), e);
      }

      TemplateExplainer explainer = TemplateExplainer.create();
      TemplateExplanation result = explainer.explain(request);

      for (TemplateAotDiagnostic diagnostic : result.diagnostics()) {
        String message = diagnostic.formattedMessage();
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
          getLog().error(message);
        } else if (diagnostic.severity() == DiagnosticSeverity.WARNING) {
          getLog().warn(message);
        } else {
          getLog().info(message);
        }
      }

      String outputContent = "json".equalsIgnoreCase(format) ? result.asJson() : result.asText();
      getLog().info(outputContent);

      if (outputFile != null) {
        Path outPath = outputFile.toPath().toAbsolutePath().normalize();
        try {
          if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
          }
          Files.writeString(outPath, outputContent, charset);
        } catch (IOException e) {
          throw new MojoExecutionException("Failed to write output file: " + outputFile, e);
        }
      }

      if (failOnDynamicFallback) {
        boolean hasFallback =
            result.templates().stream()
                .anyMatch(t -> !t.aotEligible() || !"AOT_OK".equals(t.compilationStatus()));
        if (hasFallback || !result.success()) {
          throw new MojoFailureException(
              "Viet Template explanation failed: one or more templates require dynamic fallback"
                  + " with failOnDynamicFallback enabled.");
        }
      }

      if (!result.success()) {
        long errorCount =
            result.diagnostics().stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .count();
        throw new MojoFailureException(
            "Viet Template explanation failed with " + errorCount + " error(s).");
      }

      getLog()
          .info(
              String.format(
                  "Explained %d Viet Template(s) successfully.", result.totalTemplates()));
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (java.io.IOException e) {
          getLog().debug("Error closing compile classpath ClassLoader", e);
        }
      }
    }
  }

  public File getSourceDirectory() {
    return sourceDirectory;
  }

  public void setSourceDirectory(File sourceDirectory) {
    this.sourceDirectory = sourceDirectory;
  }

  public List<String> getIncludes() {
    return includes;
  }

  public void setIncludes(List<String> includes) {
    this.includes = includes;
  }

  public List<String> getExcludes() {
    return excludes;
  }

  public void setExcludes(List<String> excludes) {
    this.excludes = excludes;
  }

  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  public String getTypeChecking() {
    return typeChecking;
  }

  public void setTypeChecking(String typeChecking) {
    this.typeChecking = typeChecking;
  }

  public String getProfile() {
    return profile;
  }

  public void setProfile(String profile) {
    this.profile = profile;
  }

  public String getTemplate() {
    return template;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public Integer getLine() {
    return line;
  }

  public void setLine(Integer line) {
    this.line = line;
  }

  public Integer getColumn() {
    return column;
  }

  public void setColumn(Integer column) {
    this.column = column;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public File getOutputFile() {
    return outputFile;
  }

  public void setOutputFile(File outputFile) {
    this.outputFile = outputFile;
  }

  public boolean isSkip() {
    return skip;
  }

  public void setSkip(boolean skip) {
    this.skip = skip;
  }

  public boolean isFailOnDynamicFallback() {
    return failOnDynamicFallback;
  }

  public void setFailOnDynamicFallback(boolean failOnDynamicFallback) {
    this.failOnDynamicFallback = failOnDynamicFallback;
  }

  public boolean isStrictReferences() {
    return strictReferences;
  }

  public void setStrictReferences(boolean strictReferences) {
    this.strictReferences = strictReferences;
  }

  public MavenProject getProject() {
    return project;
  }

  public void setProject(MavenProject project) {
    this.project = project;
  }
}
