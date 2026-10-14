package io.github.minh124199.viettemplate.tooling.maven;

import io.github.minh124199.viettemplate.aot.TypeScriptContractProjector;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Maven Mojo to generate deterministic TypeScript contract declaration files (*.vtl.d.ts)
 * Ahead-Of-Time by projecting #param directives, companion contracts, Java models, and canonical
 * schemas during the {@code process-classes} phase.
 */
@Mojo(
    name = "generate-typescript-contracts",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.COMPILE)
public class VietTemplateGenerateTypeScriptContractsMojo extends AbstractMojo {

  @Parameter(
      defaultValue = "${project.basedir}/src/main/viet-template",
      property = "viet-template.sourceDirectory")
  private File sourceDirectory;

  @Parameter(
      defaultValue = "${project.build.directory}/generated-sources/viet-template/typescript",
      property = "viet-template.typeScriptOutputDirectory")
  private File typeScriptOutputDirectory;

  @Parameter(
      defaultValue = "${project.build.directory}/generated-resources/viet-template/schemas",
      property = "viet-template.schemaDirectory")
  private File schemaDirectory;

  @Parameter(property = "viet-template.includes")
  private List<String> includes = new ArrayList<>(List.of("**/*.vtl", "**/*.vm", "**/*.vtl.html"));

  @Parameter(property = "viet-template.excludes")
  private List<String> excludes = new ArrayList<>();

  @Parameter(defaultValue = "UTF-8", property = "viet-template.encoding")
  private String encoding = "UTF-8";

  @Parameter(defaultValue = "true", property = "viet-template.cleanStaleOutputs")
  private boolean cleanStaleOutputs = true;

  @Parameter(defaultValue = "false", property = "viet-template.skip")
  private boolean skip = false;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  public VietTemplateGenerateTypeScriptContractsMojo() {}

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping Viet Template TypeScript contract generation (skip=true).");
      return;
    }

    if (sourceDirectory == null || !sourceDirectory.exists()) {
      getLog()
          .info(
              "Viet Template source directory does not exist, skipping TypeScript contract"
                  + " generation: "
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

    if (typeScriptOutputDirectory == null) {
      throw new MojoExecutionException("typeScriptOutputDirectory must not be null");
    }

    Charset charset;
    try {
      charset = Charset.forName(encoding);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException("Invalid encoding: " + encoding, e);
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
        }
      } catch (org.apache.maven.artifact.DependencyResolutionRequiredException
          | MalformedURLException
          | RuntimeException e) {
        getLog().debug("Could not build compile classpath ClassLoader: " + e.getMessage());
      }
    }

    try {
      TypeScriptContractProjector projector = new TypeScriptContractProjector();
      Map<String, Integer> summary =
          projector.projectTemplates(
              sourceDirectory.toPath(),
              typeScriptOutputDirectory.toPath(),
              includes,
              excludes,
              charset,
              urlClassLoader != null
                  ? urlClassLoader
                  : Thread.currentThread().getContextClassLoader(),
              cleanStaleOutputs,
              null,
              null,
              (schemaDirectory != null && schemaDirectory.isDirectory())
                  ? schemaDirectory.toPath()
                  : null);

      getLog()
          .info(
              String.format(
                  "Generated %d TypeScript contract(s) (%d written, %d unchanged, %d stale cleaned)"
                      + " in %s",
                  summary.getOrDefault("generated", 0),
                  summary.getOrDefault("written", 0),
                  summary.getOrDefault("skipped", 0),
                  summary.getOrDefault("deleted", 0),
                  typeScriptOutputDirectory.getAbsolutePath()));
    } catch (IOException | RuntimeException e) {
      throw new MojoFailureException(
          "Viet Template TypeScript contract generation failed: " + e.getMessage(), e);
    } finally {
      if (urlClassLoader != null) {
        try {
          urlClassLoader.close();
        } catch (IOException ignored) {
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

  public File getTypeScriptOutputDirectory() {
    return typeScriptOutputDirectory;
  }

  public void setTypeScriptOutputDirectory(File typeScriptOutputDirectory) {
    this.typeScriptOutputDirectory = typeScriptOutputDirectory;
  }

  public File getSchemaDirectory() {
    return schemaDirectory;
  }

  public void setSchemaDirectory(File schemaDirectory) {
    this.schemaDirectory = schemaDirectory;
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

  public boolean isCleanStaleOutputs() {
    return cleanStaleOutputs;
  }

  public void setCleanStaleOutputs(boolean cleanStaleOutputs) {
    this.cleanStaleOutputs = cleanStaleOutputs;
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
