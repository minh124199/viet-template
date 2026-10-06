package io.github.minh124199.viettemplate.tooling.maven;

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
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Maven Mojo to analyze Apache Velocity templates and produce migration readiness reports. */
@Mojo(name = "migration-report", requiresDependencyResolution = ResolutionScope.COMPILE)
public class VietTemplateMigrationReportMojo extends AbstractMojo {

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

  @Parameter(defaultValue = "text", property = "viet-template.format")
  private String format = "text";

  @Parameter(property = "viet-template.outputFile")
  private File outputFile;

  @Parameter(defaultValue = "false", property = "viet-template.skip")
  private boolean skip = false;

  @Parameter(defaultValue = "false", property = "viet-template.failOnBlocker")
  private boolean failOnBlocker = false;

  @Parameter(defaultValue = "false", property = "viet-template.failOnWarning")
  private boolean failOnWarning = false;

  @Parameter(defaultValue = "false", property = "viet-template.strictReferences")
  private boolean strictReferences = false;

  @Parameter(defaultValue = "INFO", property = "viet-template.minimumSeverity")
  private String minimumSeverity = "INFO";

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping Viet Template migration report (skip=true).");
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

    TemplateMigrationRequest.Builder requestBuilder =
        TemplateMigrationRequest.builder()
            .sourceDirectory(sourceDirectory.toPath())
            .encoding(charset)
            .strictReferences(strictReferences)
            .failOnBlocker(failOnBlocker)
            .failOnWarning(failOnWarning);

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

    MigrationSeverity minSeverity = MigrationSeverity.INFO;
    if (minimumSeverity != null && !minimumSeverity.isBlank()) {
      try {
        minSeverity = MigrationSeverity.valueOf(minimumSeverity.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new MojoExecutionException(
            "Invalid minimumSeverity configuration: '" + minimumSeverity + "'", e);
      }
    }
    requestBuilder.minimumSeverity(minSeverity);

    if (template != null && !template.isBlank()) {
      requestBuilder.template(template.trim());
    }
    if (format != null && !format.isBlank()) {
      requestBuilder.format(format.trim());
    }
    if (outputFile != null) {
      requestBuilder.outputFile(outputFile.toPath());
    }

    if (includes != null && !includes.isEmpty()) {
      requestBuilder.includes(includes);
    }
    if (excludes != null && !excludes.isEmpty()) {
      requestBuilder.excludes(excludes);
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
      TemplateMigrationRequest request;
      try {
        request = requestBuilder.build();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new MojoExecutionException(
            "Failed to build template migration request: " + e.getMessage(), e);
      }

      TemplateMigrationAnalyzer analyzer = TemplateMigrationAnalyzer.create();
      MigrationReport report = analyzer.analyze(request);

      for (TemplateAotDiagnostic diagnostic : report.validationDiagnostics()) {
        String message = diagnostic.formattedMessage();
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
          getLog().error(message);
        } else if (diagnostic.severity() == DiagnosticSeverity.WARNING) {
          getLog().warn(message);
        } else {
          getLog().info(message);
        }
      }

      String outputContent = "json".equalsIgnoreCase(format) ? report.asJson() : report.asText();
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

      int blockers =
          report.summary().findingsBySeverity().getOrDefault(MigrationSeverity.BLOCKER, 0);
      int warnings =
          report.summary().findingsBySeverity().getOrDefault(MigrationSeverity.WARNING, 0);

      if (failOnBlocker && blockers > 0) {
        throw new MojoFailureException(
            "Viet Template migration report detected "
                + blockers
                + " blocker(s) with failOnBlocker enabled.");
      }
      if (failOnWarning && warnings > 0) {
        throw new MojoFailureException(
            "Viet Template migration report detected "
                + warnings
                + " warning(s) with failOnWarning enabled.");
      }
      if (!report.success()) {
        throw new MojoFailureException(
            "Viet Template migration report failed due to template validation errors.");
      }
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (IOException e) {
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

  public boolean isFailOnBlocker() {
    return failOnBlocker;
  }

  public void setFailOnBlocker(boolean failOnBlocker) {
    this.failOnBlocker = failOnBlocker;
  }

  public boolean isFailOnWarning() {
    return failOnWarning;
  }

  public void setFailOnWarning(boolean failOnWarning) {
    this.failOnWarning = failOnWarning;
  }

  public boolean isStrictReferences() {
    return strictReferences;
  }

  public void setStrictReferences(boolean strictReferences) {
    this.strictReferences = strictReferences;
  }

  public String getMinimumSeverity() {
    return minimumSeverity;
  }

  public void setMinimumSeverity(String minimumSeverity) {
    this.minimumSeverity = minimumSeverity;
  }

  public MavenProject getProject() {
    return project;
  }

  public void setProject(MavenProject project) {
    this.project = project;
  }
}
