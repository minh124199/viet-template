package io.github.minh124199.viettemplate.quarkus.deployment;

import io.github.minh124199.viettemplate.aot.TemplateAotArtifact;
import io.github.minh124199.viettemplate.aot.TemplateAotCompiler;
import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.aot.TemplateAotRequest;
import io.github.minh124199.viettemplate.aot.TemplateAotResult;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.quarkus.VietTemplateConfig;
import io.github.minh124199.viettemplate.quarkus.VietTemplateProducer;
import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.ConfigMappingBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.LaunchMode;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Quarkus deployment processor for Viet Template.
 *
 * <p>Handles extension feature registration, CDI bean registration, build-time ahead-of-time (AOT)
 * template compilation, native-image resource/reflection registration, and dev-mode hot reload file
 * watching.
 */
public class VietTemplateProcessor {

  private static final String FEATURE = "viet-template";
  private static final String TEMPLATES_INDEX_PATH = "META-INF/viet-template/templates.idx";
  private static final String DEFAULT_PACKAGE_PREFIX =
      "io.github.minh124199.viettemplate.generated";

  @BuildStep
  public FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  @BuildStep
  public ConfigMappingBuildItem configMapping() {
    return new ConfigMappingBuildItem(VietTemplateConfig.class, "quarkus.viet-template");
  }

  @BuildStep
  public IndexDependencyBuildItem indexRuntime() {
    return new IndexDependencyBuildItem("io.github.minh124199", "viet-template-quarkus");
  }

  @BuildStep
  public AdditionalBeanBuildItem createBeans() {
    return AdditionalBeanBuildItem.builder()
        .addBeanClasses(VietTemplateProducer.class, VietTemplateRenderer.class)
        .setUnremovable()
        .build();
  }

  @BuildStep
  public void compileTemplates(
      LaunchModeBuildItem launchMode,
      ApplicationArchivesBuildItem applicationArchivesBuildItem,
      BuildProducer<GeneratedClassBuildItem> generatedClasses,
      BuildProducer<GeneratedResourceBuildItem> generatedResources,
      BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
      BuildProducer<NativeImageResourceBuildItem> nativeImageResources,
      BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles)
      throws Exception {

    Config cfg = ConfigProvider.getConfig();
    String path =
        cfg.getOptionalValue("quarkus.viet-template.path", String.class).orElse("templates").trim();

    ApplicationArchive rootArchive = applicationArchivesBuildItem.getRootArchive();

    Path templatesDir = null;
    if (!path.isEmpty()) {
      templatesDir = rootArchive.getChildPath(path);
    }
    if (templatesDir == null || !Files.exists(templatesDir)) {
      for (Path root : rootArchive.getRootDirectories()) {
        Path candidate = path.isEmpty() ? root : root.resolve(path);
        if (Files.exists(candidate) && Files.isDirectory(candidate)) {
          templatesDir = candidate;
          break;
        }
      }
    }

    if (templatesDir == null || !Files.exists(templatesDir) || !Files.isDirectory(templatesDir)) {
      return;
    }

    List<String> allSuffixes = new ArrayList<>();
    String suffix =
        cfg.getOptionalValue("quarkus.viet-template.suffix", String.class).orElse(".vtl").trim();
    if (!suffix.isBlank()) {
      allSuffixes.add(suffix);
    }
    List<String> additionalSuffixes =
        cfg.getOptionalValues("quarkus.viet-template.additional-suffixes", String.class)
            .orElse(List.of());
    for (String s : additionalSuffixes) {
      if (s != null && !s.isBlank()) {
        allSuffixes.add(s.trim());
      }
    }
    if (allSuffixes.isEmpty()) {
      allSuffixes.add(".vtl");
    }

    List<Path> templateFiles = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(templatesDir)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(
              file -> {
                String fileName = file.getFileName().toString();
                for (String s : allSuffixes) {
                  String suffixWithDot = s.startsWith(".") ? s : "." + s;
                  if (fileName.endsWith(suffixWithDot)) {
                    templateFiles.add(file);
                    break;
                  }
                }
              });
    }

    if (templateFiles.isEmpty()) {
      return;
    }

    // Register dev-mode watched files
    if (!path.isEmpty()) {
      watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(path));
    }
    for (Path file : templateFiles) {
      Path rel = templatesDir.relativize(file);
      String relStr = rel.toString().replace('\\', '/');
      String watchedPath = path.isEmpty() ? relStr : path + "/" + relStr;
      watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(watchedPath));
    }

    boolean runtimeCompilationEnabled =
        cfg.getOptionalValue("quarkus.viet-template.runtime-compilation-enabled", Boolean.class)
            .orElse(false);
    boolean isDevOrTest =
        launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT
            || launchMode.getLaunchMode() == LaunchMode.TEST;
    boolean runtimeAllowed = runtimeCompilationEnabled || isDevOrTest;

    Charset charset;
    try {
      String encoding =
          cfg.getOptionalValue("quarkus.viet-template.encoding", String.class)
              .orElse("UTF-8")
              .trim();
      charset = Charset.forName(encoding);
    } catch (IllegalArgumentException e) {
      charset = StandardCharsets.UTF_8;
    }

    Path effectiveTemplatesDir = templatesDir;
    templateFiles.sort(
        Comparator.comparing(
            f -> effectiveTemplatesDir.relativize(f).toString().replace('\\', '/')));

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    Map<String, String> compiledIndexEntries = new LinkedHashMap<>();

    for (Path file : templateFiles) {
      Path rel = templatesDir.relativize(file);
      String relStr = rel.toString().replace('\\', '/');

      Path tempWorkDir = Files.createTempDirectory("viet-template-quarkus-single");
      try {
        Path tempSourceDir = tempWorkDir.resolve("src");
        Path tempOutputDir = tempWorkDir.resolve("classes");
        Path tempResourceOutputDir = tempWorkDir.resolve("res");
        Files.createDirectories(tempSourceDir);
        Files.createDirectories(tempOutputDir);
        Files.createDirectories(tempResourceOutputDir);

        Path target = tempSourceDir.resolve(rel);
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);

        TemplateAotRequest aotRequest =
            TemplateAotRequest.builder()
                .sourceDirectory(tempSourceDir)
                .outputDirectory(tempOutputDir)
                .resourceOutputDirectory(tempResourceOutputDir)
                .encoding(charset)
                .packagePrefix(DEFAULT_PACKAGE_PREFIX)
                .includePatterns(List.of(relStr))
                .build();

        TemplateAotResult result = compiler.compile(aotRequest);

        // Fail with IllegalStateException on syntax or semantic errors
        boolean hasErrorDiagnostics =
            result.diagnostics().stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
        if (hasErrorDiagnostics) {
          StringBuilder sb =
              new StringBuilder("Viet Template AOT compilation syntax/semantic error in '")
                  .append(relStr)
                  .append("':\n");
          for (TemplateAotDiagnostic diag : result.diagnostics()) {
            if (diag.severity() == DiagnosticSeverity.ERROR) {
              sb.append("  [")
                  .append(diag.severity())
                  .append("] ")
                  .append(diag.formattedMessage())
                  .append("\n");
            }
          }
          throw new IllegalStateException(sb.toString());
        }

        // Check for dynamic directives (#evaluate VTLAOT:1101, #parse VTLAOT:1102)
        boolean hasDynamicDirective =
            result.diagnostics().stream()
                .anyMatch(
                    d -> {
                      String code = d.code().qualifiedCode();
                      return "VTLAOT:1101".equals(code) || "VTLAOT:1102".equals(code);
                    });

        if (hasDynamicDirective) {
          if (!runtimeAllowed) {
            StringBuilder sb =
                new StringBuilder("Viet Template AOT compilation failed for '")
                    .append(relStr)
                    .append(
                        "': dynamic directives (#parse/#evaluate) require runtime interpreter, but"
                            + " runtime compilation is disabled (e.g. native image build):\n");
            for (TemplateAotDiagnostic diag : result.diagnostics()) {
              sb.append("  [")
                  .append(diag.severity())
                  .append("] ")
                  .append(diag.formattedMessage())
                  .append("\n");
            }
            throw new IllegalStateException(sb.toString());
          }
          // Dynamic template in dev/test/runtimeCompilationEnabled mode:
          // Skip AOT compilation and fall back to runtime interpreter
          continue;
        }

        if (!result.success()) {
          StringBuilder sb =
              new StringBuilder("Viet Template AOT compilation failed for '")
                  .append(relStr)
                  .append("':\n");
          for (TemplateAotDiagnostic diag : result.diagnostics()) {
            sb.append("  [")
                .append(diag.severity())
                .append("] ")
                .append(diag.formattedMessage())
                .append("\n");
          }
          throw new IllegalStateException(sb.toString());
        }

        for (TemplateAotArtifact artifact : result.artifacts()) {
          byte[] classBytes = Files.readAllBytes(artifact.outputFile());
          generatedClasses.produce(
              new GeneratedClassBuildItem(true, artifact.className(), classBytes));
          reflectiveClasses.produce(
              ReflectiveClassBuildItem.builder(artifact.className())
                  .constructors()
                  .methods()
                  .build());
          compiledIndexEntries.put(artifact.templateId().value(), artifact.className());
        }
      } finally {
        deleteRecursively(tempWorkDir);
      }
    }

    if (!compiledIndexEntries.isEmpty()) {
      StringBuilder finalIdx = new StringBuilder();
      Set<String> writtenKeys = new LinkedHashSet<>();
      List<Map.Entry<String, String>> sortedEntries =
          new ArrayList<>(compiledIndexEntries.entrySet());
      sortedEntries.sort(Map.Entry.comparingByKey());

      for (Map.Entry<String, String> entry : sortedEntries) {
        String key = entry.getKey();
        String val = entry.getValue();
        if (writtenKeys.add(key)) {
          finalIdx.append(key).append('=').append(val).append('\n');
        }
        if (!path.isEmpty() && !key.startsWith(path + "/")) {
          String withPath = path + "/" + key;
          if (writtenKeys.add(withPath)) {
            finalIdx.append(withPath).append('=').append(val).append('\n');
          }
        }
        if (!path.isEmpty() && key.startsWith(path + "/")) {
          String withoutPath = key.substring(path.length() + 1);
          if (writtenKeys.add(withoutPath)) {
            finalIdx.append(withoutPath).append('=').append(val).append('\n');
          }
        }
      }
      byte[] idxBytes = finalIdx.toString().getBytes(StandardCharsets.UTF_8);
      generatedResources.produce(new GeneratedResourceBuildItem(TEMPLATES_INDEX_PATH, idxBytes));
      nativeImageResources.produce(new NativeImageResourceBuildItem(TEMPLATES_INDEX_PATH));
    }
  }

  private static void deleteRecursively(Path dir) {
    if (dir == null || !Files.exists(dir)) {
      return;
    }
    try {
      Files.walkFileTree(
          dir,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.deleteIfExists(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
              Files.deleteIfExists(d);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException ignored) {
    }
  }
}
