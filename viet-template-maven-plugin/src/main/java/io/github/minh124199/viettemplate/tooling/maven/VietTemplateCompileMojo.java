package io.github.minh124199.viettemplate.tooling.maven;

import io.github.minh124199.viettemplate.aot.TemplateAotCompiler;
import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.aot.TemplateAotRequest;
import io.github.minh124199.viettemplate.aot.TemplateAotResult;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Maven Mojo to compile Viet Template files Ahead-Of-Time (AOT) into bytecode. */
@Mojo(
    name = "compile",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class VietTemplateCompileMojo extends AbstractMojo {

  @Parameter(
      defaultValue = "${project.basedir}/src/main/viet-template",
      property = "viet-template.sourceDirectory")
  private File sourceDirectory;

  @Parameter(
      defaultValue = "${project.build.outputDirectory}",
      property = "viet-template.outputDirectory")
  private File outputDirectory;

  @Parameter(
      defaultValue = "${project.build.outputDirectory}",
      property = "viet-template.resourceOutputDirectory")
  private File resourceOutputDirectory;

  @Parameter(property = "viet-template.includes")
  private List<String> includes = new ArrayList<>(List.of("**/*.vtl", "**/*.vm"));

  @Parameter(property = "viet-template.excludes")
  private List<String> excludes = new ArrayList<>();

  @Parameter(defaultValue = "UTF-8", property = "viet-template.encoding")
  private String encoding = "UTF-8";

  @Parameter(
      defaultValue = "io.github.minh124199.viettemplate.generated",
      property = "viet-template.packagePrefix")
  private String packagePrefix = "io.github.minh124199.viettemplate.generated";

  @Parameter(defaultValue = "false", property = "viet-template.failOnWarning")
  private boolean failOnWarning = false;

  @Parameter(defaultValue = "true", property = "viet-template.incremental")
  private boolean incremental = true;

  @Parameter(defaultValue = "false", property = "viet-template.skip")
  private boolean skip = false;

  @Parameter(defaultValue = "false", property = "viet-template.generateTypedFacades")
  private boolean generateTypedFacades = false;

  @Parameter(defaultValue = "OFF", property = "viet-template.typeChecking")
  private String typeChecking = "OFF";

  @Parameter(defaultValue = "VTL_MIGRATION", property = "viet-template.profile")
  private String profile = "VTL_MIGRATION";

  @Parameter(
      defaultValue = "${project.build.directory}/generated-sources/viet-template",
      property = "viet-template.generatedSourcesDirectory")
  private File generatedSourcesDirectory;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping Viet Template compilation (skip=true).");
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

    if (outputDirectory == null) {
      throw new MojoExecutionException("outputDirectory must not be null");
    }

    Charset charset;
    try {
      charset = Charset.forName(encoding);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException("Invalid encoding: " + encoding, e);
    }

    TemplateAotRequest.Builder requestBuilder =
        TemplateAotRequest.builder()
            .sourceDirectory(sourceDirectory.toPath())
            .outputDirectory(outputDirectory.toPath())
            .resourceOutputDirectory(
                resourceOutputDirectory != null
                    ? resourceOutputDirectory.toPath()
                    : outputDirectory.toPath())
            .encoding(charset)
            .packagePrefix(packagePrefix)
            .failOnWarning(failOnWarning)
            .incremental(incremental);

    TypeCheckingMode mode;
    try {
      mode = TypeCheckingConfigParser.parse(typeChecking);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    requestBuilder.typeCheckingMode(mode);

    if (profile != null && !profile.isBlank()) {
      try {
        io.github.minh124199.viettemplate.language.vtl.VtlProfile vtlProfile =
            io.github.minh124199.viettemplate.language.vtl.VtlProfile.valueOf(
                profile.trim().toUpperCase(java.util.Locale.ROOT));
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

    if (generateTypedFacades) {
      requestBuilder.generateTypedFacades(true);
      if (generatedSourcesDirectory != null) {
        requestBuilder.generatedSourcesDirectory(generatedSourcesDirectory.toPath());
      }
    }

    java.net.URLClassLoader urlClassLoader = null;
    if (project != null) {
      try {
        List<String> classpathElements = project.getCompileClasspathElements();
        if (classpathElements != null && !classpathElements.isEmpty()) {
          List<java.net.URL> urls = new ArrayList<>();
          for (String element : classpathElements) {
            urls.add(new File(element).toURI().toURL());
          }
          urlClassLoader =
              new java.net.URLClassLoader(
                  urls.toArray(new java.net.URL[0]),
                  Thread.currentThread().getContextClassLoader());
          requestBuilder.classLoader(urlClassLoader);
        }
      } catch (org.apache.maven.artifact.DependencyResolutionRequiredException
          | java.net.MalformedURLException
          | RuntimeException e) {
        getLog().debug("Could not build compile classpath ClassLoader: " + e.getMessage());
      }
    }

    try {
      TemplateAotRequest request;
      try {
        request = requestBuilder.build();
      } catch (IllegalArgumentException | IllegalStateException e) {
        throw new MojoExecutionException(
            "Failed to build AOT compilation request: " + e.getMessage(), e);
      }

      TemplateAotCompiler compiler = TemplateAotCompiler.create();
      TemplateAotResult result = compiler.compile(request);

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
        long errorCount =
            result.diagnostics().stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .count();
        throw new MojoFailureException(
            "Viet Template AOT compilation failed with " + errorCount + " error(s).");
      }

      getLog()
          .info(
              String.format(
                  "Compiled %d Viet Template(s) (%d skipped, %d deleted).",
                  result.compiledCount(), result.skippedCount(), result.deletedCount()));

      if (generateTypedFacades && generatedSourcesDirectory != null && project != null) {
        project.addCompileSourceRoot(generatedSourcesDirectory.getAbsolutePath());
      }
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (java.io.IOException e) {
          getLog().debug("Failed to close URLClassLoader: " + e.getMessage());
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

  public File getOutputDirectory() {
    return outputDirectory;
  }

  public void setOutputDirectory(File outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  public File getResourceOutputDirectory() {
    return resourceOutputDirectory;
  }

  public void setResourceOutputDirectory(File resourceOutputDirectory) {
    this.resourceOutputDirectory = resourceOutputDirectory;
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

  public String getPackagePrefix() {
    return packagePrefix;
  }

  public void setPackagePrefix(String packagePrefix) {
    this.packagePrefix = packagePrefix;
  }

  public boolean isFailOnWarning() {
    return failOnWarning;
  }

  public void setFailOnWarning(boolean failOnWarning) {
    this.failOnWarning = failOnWarning;
  }

  public boolean isIncremental() {
    return incremental;
  }

  public void setIncremental(boolean incremental) {
    this.incremental = incremental;
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

  public boolean isGenerateTypedFacades() {
    return generateTypedFacades;
  }

  public void setGenerateTypedFacades(boolean generateTypedFacades) {
    this.generateTypedFacades = generateTypedFacades;
  }

  public File getGeneratedSourcesDirectory() {
    return generatedSourcesDirectory;
  }

  public void setGeneratedSourcesDirectory(File generatedSourcesDirectory) {
    this.generatedSourcesDirectory = generatedSourcesDirectory;
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
}
