package io.github.minh124199.viettemplate.tooling.maven;

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
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Maven Mojo to validate Viet Template files Ahead-Of-Time at build time. */
@Mojo(
    name = "validate",
    defaultPhase = LifecyclePhase.VALIDATE,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class VietTemplateValidateMojo extends AbstractMojo {

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

  @Parameter(defaultValue = "false", property = "viet-template.failOnWarning")
  private boolean failOnWarning = false;

  @Parameter(defaultValue = "OFF", property = "viet-template.typeChecking")
  private String typeChecking = "OFF";

  @Parameter(defaultValue = "VTL_MIGRATION", property = "viet-template.profile")
  private String profile = "VTL_MIGRATION";

  @Parameter(defaultValue = "false", property = "viet-template.skip")
  private boolean skip = false;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping Viet Template validation (skip=true).");
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

    TemplateValidationRequest.Builder requestBuilder =
        TemplateValidationRequest.builder()
            .sourceDirectory(sourceDirectory.toPath())
            .encoding(charset)
            .failOnWarning(failOnWarning)
            .validateDependencies(true);

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
      TemplateValidationRequest request;
      try {
        request = requestBuilder.build();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new MojoExecutionException(
            "Failed to build template validation request: " + e.getMessage(), e);
      }

      TemplateValidator validator = TemplateValidator.create();
      TemplateValidationResult result = validator.validate(request);

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

      if (!result.success()) {
        throw new MojoFailureException(
            "Viet Template validation failed with "
                + result.errorCount()
                + " error(s)"
                + (result.warningCount() > 0
                    ? " and " + result.warningCount() + " warning(s)."
                    : "."));
      }

      getLog()
          .info(
              String.format(
                  "Validated %d Viet Template(s) successfully.", result.validatedCount()));
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

  public boolean isFailOnWarning() {
    return failOnWarning;
  }

  public void setFailOnWarning(boolean failOnWarning) {
    this.failOnWarning = failOnWarning;
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
