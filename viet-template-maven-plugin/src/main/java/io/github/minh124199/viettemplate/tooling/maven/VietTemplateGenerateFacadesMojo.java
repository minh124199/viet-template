package io.github.minh124199.viettemplate.tooling.maven;

import io.github.minh124199.viettemplate.aot.TemplateAotCompiler;
import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.aot.TemplateAotRequest;
import io.github.minh124199.viettemplate.aot.TemplateAotResult;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
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

/**
 * Maven Mojo to generate typed Java facades for Viet Template contracts during the {@code
 * generate-sources} phase. This ensures generated {@code <Template>View.java} source files are
 * compiled by {@code javac} alongside application code in a single-pass clean build.
 */
@Mojo(
    name = "generate-facades",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class VietTemplateGenerateFacadesMojo extends AbstractMojo {

  @Parameter(
      defaultValue = "${project.basedir}/src/main/viet-template",
      property = "viet-template.sourceDirectory")
  private File sourceDirectory;

  @Parameter(
      defaultValue = "${project.build.directory}/generated-sources/viet-template",
      property = "viet-template.generatedSourcesDirectory")
  private File generatedSourcesDirectory;

  @Parameter(
      defaultValue = "${project.build.directory}/generated-classes/viet-template-facades",
      property = "viet-template.facadeClassOutputDirectory")
  private File facadeClassOutputDirectory;

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

  @Parameter(defaultValue = "OFF", property = "viet-template.typeChecking")
  private String typeChecking = "OFF";

  @Parameter(defaultValue = "false", property = "viet-template.skip")
  private boolean skip = false;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping Viet Template facade generation (skip=true).");
      return;
    }

    if (sourceDirectory == null || !sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
      return;
    }

    String[] files = sourceDirectory.list();
    if (files == null || files.length == 0) {
      return;
    }

    if (generatedSourcesDirectory == null) {
      throw new MojoExecutionException("generatedSourcesDirectory must not be null");
    }

    if (project != null) {
      project.addCompileSourceRoot(generatedSourcesDirectory.getAbsolutePath());
    }

    Charset charset;
    try {
      charset = Charset.forName(encoding);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException("Invalid encoding: " + encoding, e);
    }

    File tempOutput =
        facadeClassOutputDirectory != null
            ? facadeClassOutputDirectory
            : new File(generatedSourcesDirectory.getParentFile(), "facade-classes");

    TemplateAotRequest.Builder requestBuilder =
        TemplateAotRequest.builder()
            .sourceDirectory(sourceDirectory.toPath())
            .outputDirectory(tempOutput.toPath())
            .encoding(charset)
            .packagePrefix(packagePrefix)
            .failOnWarning(failOnWarning)
            .generateTypedFacades(true)
            .generatedSourcesDirectory(generatedSourcesDirectory.toPath());

    TypeCheckingMode mode;
    try {
      mode = TypeCheckingConfigParser.parse(typeChecking);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    requestBuilder.typeCheckingMode(mode);

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
      } catch (org.apache.maven.artifact.DependencyResolutionRequiredException
          | MalformedURLException
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
            "Failed to build facade generation request: " + e.getMessage(), e);
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
            "Viet Template facade generation failed with " + errorCount + " error(s).");
      }

      getLog()
          .info("Generated typed Java facades in " + generatedSourcesDirectory.getAbsolutePath());
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (IOException e) {
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

  public File getGeneratedSourcesDirectory() {
    return generatedSourcesDirectory;
  }

  public void setGeneratedSourcesDirectory(File generatedSourcesDirectory) {
    this.generatedSourcesDirectory = generatedSourcesDirectory;
  }

  public File getFacadeClassOutputDirectory() {
    return facadeClassOutputDirectory;
  }

  public void setFacadeClassOutputDirectory(File facadeClassOutputDirectory) {
    this.facadeClassOutputDirectory = facadeClassOutputDirectory;
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

  public String getTypeChecking() {
    return typeChecking;
  }

  public void setTypeChecking(String typeChecking) {
    this.typeChecking = typeChecking;
  }
}
