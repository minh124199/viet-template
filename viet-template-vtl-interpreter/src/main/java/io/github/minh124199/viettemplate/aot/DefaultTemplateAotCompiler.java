package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.CompilationStatus;
import io.github.minh124199.viettemplate.vtl.internal.compiler.CompiledArtifact;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Package-private default implementation of {@link TemplateAotCompiler}. */
class DefaultTemplateAotCompiler implements TemplateAotCompiler {

  record DiscoveredTemplate(TemplateId templateId, String relPath, Path file) {}

  record StateEntry(
      TemplateId templateId,
      String sourceHash,
      String relativeClassPath,
      String fqcn,
      String fingerprint) {}

  record PendingClassWrite(Path outputFile, byte[] classBytes) {}

  @Override
  public TemplateAotResult compile(TemplateAotRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    Path outputDir = request.outputDirectory().toAbsolutePath().normalize();
    Path resourceOutputDir = request.resourceOutputDirectory().toAbsolutePath().normalize();

    String packagePrefix = request.packagePrefix().trim();
    validatePackagePrefix(packagePrefix);

    Path packageSubdir =
        packagePrefix.isEmpty()
            ? outputDir
            : outputDir.resolve(packagePrefix.replace('.', '/')).normalize();
    if (!packageSubdir.startsWith(outputDir)) {
      throw new SecurityException(
          "Package prefix causes path traversal outside output directory: " + packageSubdir);
    }

    try {
      Files.createDirectories(outputDir);
      Files.createDirectories(resourceOutputDir);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create output directories", e);
    }

    Path stateFilePath =
        request
            .stateFile()
            .map(p -> p.toAbsolutePath().normalize())
            .orElseGet(() -> outputDir.resolve("META-INF/viet-template/aot-state"));

    Map<TemplateId, StateEntry> previousState = loadState(stateFilePath, outputDir);

    Map<TemplateId, DiscoveredTemplate> discovered = scanTemplates(request);
    List<DiscoveredTemplate> sortedTemplates = new ArrayList<>(discovered.values());
    sortedTemplates.sort(Comparator.comparing(d -> d.templateId().value()));

    List<StateEntry> staleEntries = new ArrayList<>();
    for (Map.Entry<TemplateId, StateEntry> entry : previousState.entrySet()) {
      if (!discovered.containsKey(entry.getKey())) {
        staleEntries.add(entry.getValue());
      }
    }

    int compiledCount = 0;
    int skippedCount = 0;
    List<TemplateAotArtifact> artifacts = new ArrayList<>();
    List<TemplateAotDiagnostic> allDiagnostics = new ArrayList<>();
    Map<TemplateId, StateEntry> newState = new LinkedHashMap<>();
    List<PendingClassWrite> pendingWrites = new ArrayList<>();
    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendOptions backendOptions = BackendOptions.builder().packagePrefix(packagePrefix).build();
    boolean compilationFailed = false;

    for (DiscoveredTemplate dt : sortedTemplates) {
      TemplateId templateId = dt.templateId();
      Path sourceFile = dt.file();

      String sourceText;
      try {
        sourceText = Files.readString(sourceFile, request.encoding());
      } catch (IOException e) {
        allDiagnostics.add(
            new TemplateAotDiagnostic(
                templateId,
                dt.relPath(),
                DiagnosticSeverity.ERROR,
                DiagnosticCode.of("VTLAOT", "1001"),
                "Failed to read template source: " + e.getMessage(),
                -1,
                -1,
                -1,
                -1));
        compilationFailed = true;
        continue;
      }

      String contentHash = sha256Hex(sourceText);
      StateEntry prev = previousState.get(templateId);

      boolean isUpToDate = false;
      if (request.incremental() && prev != null) {
        if (contentHash.equals(prev.sourceHash())) {
          Path classPath = outputDir.resolve(prev.relativeClassPath()).normalize();
          if (Files.isRegularFile(classPath) && classPath.startsWith(outputDir)) {
            isUpToDate = true;
          }
        }
      }

      if (isUpToDate) {
        skippedCount++;
        newState.put(templateId, prev);
        artifacts.add(
            new TemplateAotArtifact(
                templateId,
                prev.fqcn(),
                outputDir.resolve(prev.relativeClassPath()),
                prev.fingerprint()));
        continue;
      }

      SourceText source = SourceText.of(templateId, sourceText);
      VtlParseResult parseResult = VtlParser.parse(source);
      for (Diagnostic diag : parseResult.diagnostics()) {
        allDiagnostics.add(TemplateAotDiagnostic.from(templateId, dt.relPath(), diag));
      }
      if (parseResult.hasErrors()) {
        compilationFailed = true;
        continue;
      }

      VtlSemanticOptions semanticOptions =
          VtlSemanticOptions.builder()
              .profile(VtlProfile.VTL_CORE)
              .allowArbitraryMethods(true)
              .build();
      SemanticAnalysisResult analysis =
          VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
      for (Diagnostic diag : analysis.diagnostics()) {
        allDiagnostics.add(TemplateAotDiagnostic.from(templateId, dt.relPath(), diag));
      }
      if (analysis.hasErrors()) {
        compilationFailed = true;
        continue;
      }

      IrTemplate ir =
          AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);
      BackendResult result = compiler.compile(ir, backendOptions);
      for (Diagnostic diag : result.diagnostics()) {
        allDiagnostics.add(TemplateAotDiagnostic.from(templateId, dt.relPath(), diag));
      }

      if (result.status() == CompilationStatus.AOT_OK_WITH_DYNAMIC_SITES) {
        allDiagnostics.add(
            TemplateAotDiagnostic.from(
                templateId,
                dt.relPath(),
                Diagnostic.warning(
                    DiagnosticCode.of("VTLAOT", "1201"),
                    "Template requires dynamic call site dispatch: " + templateId.value(),
                    SourceSpan.UNKNOWN)));
      }

      if (!result.isSuccess() || result.artifact() == null) {
        compilationFailed = true;
        continue;
      }

      CompiledArtifact ca = result.artifact();
      String fqcn = ca.generatedClassName();
      int dotIdx = fqcn.lastIndexOf('.');
      String simpleClassName = (dotIdx >= 0) ? fqcn.substring(dotIdx + 1) : fqcn;

      Path outputFile = packageSubdir.resolve(simpleClassName + ".class").normalize();
      if (!outputFile.startsWith(outputDir)) {
        throw new SecurityException(
            "Output file path traversal outside output directory: " + outputFile);
      }
      String relativeClassPath = outputDir.relativize(outputFile).toString().replace('\\', '/');
      String fingerprint = ca.fingerprint();

      StateEntry entry =
          new StateEntry(templateId, contentHash, relativeClassPath, fqcn, fingerprint);
      newState.put(templateId, entry);

      TemplateAotArtifact artifact =
          new TemplateAotArtifact(templateId, fqcn, outputFile, fingerprint);
      artifacts.add(artifact);
      pendingWrites.add(new PendingClassWrite(outputFile, ca.classBytes()));
      compiledCount++;
    }

    boolean hasErrors =
        compilationFailed
            || allDiagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    boolean hasWarnings =
        allDiagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.WARNING);

    if (hasErrors || (request.failOnWarning() && hasWarnings)) {
      return TemplateAotResult.failure(allDiagnostics, artifacts, compiledCount, skippedCount, 0);
    }

    // Compilation succeeded without fatal issues: write outputs and delete stale files
    try {
      for (PendingClassWrite pw : pendingWrites) {
        Files.createDirectories(pw.outputFile().getParent());
        Files.write(pw.outputFile(), pw.classBytes());
      }

      int deletedCount = 0;
      for (StateEntry stale : staleEntries) {
        Path classFile = outputDir.resolve(stale.relativeClassPath()).normalize();
        if (classFile.startsWith(outputDir)
            && classFile.getFileName().toString().endsWith(".class")) {
          Files.deleteIfExists(classFile);
        }
        deletedCount++;
      }

      // Write templates.idx
      Path idxDir = resourceOutputDir.resolve("META-INF/viet-template");
      Files.createDirectories(idxDir);
      Path idxFile = idxDir.resolve("templates.idx");
      StringBuilder idxContent = new StringBuilder();
      List<StateEntry> sortedEntries = new ArrayList<>(newState.values());
      sortedEntries.sort(Comparator.comparing(e -> e.templateId().value()));
      for (StateEntry e : sortedEntries) {
        idxContent.append(e.templateId().value()).append('=').append(e.fqcn()).append('\n');
      }
      Files.writeString(idxFile, idxContent.toString(), StandardCharsets.UTF_8);

      // Write aot-state
      Files.createDirectories(stateFilePath.getParent());
      StringBuilder stateContent = new StringBuilder();
      for (StateEntry e : sortedEntries) {
        stateContent
            .append(e.templateId().value())
            .append('|')
            .append(e.sourceHash())
            .append('|')
            .append(e.relativeClassPath())
            .append('|')
            .append(e.fqcn())
            .append('|')
            .append(e.fingerprint())
            .append('\n');
      }
      Files.writeString(stateFilePath, stateContent.toString(), StandardCharsets.UTF_8);

      return TemplateAotResult.success(
          compiledCount, skippedCount, deletedCount, artifacts, allDiagnostics);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write compilation artifacts or state", e);
    }
  }

  private Map<TemplateId, DiscoveredTemplate> scanTemplates(TemplateAotRequest request) {
    Map<TemplateId, DiscoveredTemplate> discovered = new LinkedHashMap<>();
    List<String> includes = request.includePatterns();
    List<String> excludes = request.excludePatterns();

    for (Path srcDir : request.sourceDirectories()) {
      Path absSrc = srcDir.toAbsolutePath().normalize();
      if (!Files.exists(absSrc) || !Files.isDirectory(absSrc)) {
        continue;
      }

      try (Stream<Path> stream = Files.walk(absSrc)) {
        List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
        for (Path file : files) {
          Path rel = absSrc.relativize(file);
          if (rel.startsWith("..") || !file.toAbsolutePath().normalize().startsWith(absSrc)) {
            throw new SecurityException("Path traversal outside source directory: " + file);
          }
          String relStr = rel.toString().replace('\\', '/');
          if (!TemplateId.isTraversalSafe(relStr)) {
            throw new SecurityException("Path traversal detected in template path: " + relStr);
          }

          if (matchesAny(rel, includes) && !matchesAny(rel, excludes)) {
            TemplateId templateId = TemplateId.normalize(relStr);
            discovered.putIfAbsent(templateId, new DiscoveredTemplate(templateId, relStr, file));
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to scan source directory: " + absSrc, e);
      }
    }

    return discovered;
  }

  private static boolean matchesAny(Path path, List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return false;
    }
    for (String pattern : patterns) {
      if (matchesGlob(path, pattern)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesGlob(Path relativePath, String pattern) {
    FileSystem fs = FileSystems.getDefault();
    if (fs.getPathMatcher("glob:" + pattern).matches(relativePath)) {
      return true;
    }
    if (pattern.startsWith("**/")) {
      String subPattern = pattern.substring(3);
      if (fs.getPathMatcher("glob:" + subPattern).matches(relativePath)) {
        return true;
      }
      if (subPattern.contains("/**/")) {
        if (fs.getPathMatcher("glob:" + subPattern.replace("/**/", "/")).matches(relativePath)) {
          return true;
        }
      }
    }
    if (pattern.contains("/**/")) {
      if (fs.getPathMatcher("glob:" + pattern.replace("/**/", "/")).matches(relativePath)) {
        return true;
      }
    }
    return false;
  }

  private Map<TemplateId, StateEntry> loadState(Path stateFilePath, Path outputDir) {
    if (!Files.exists(stateFilePath) || !Files.isRegularFile(stateFilePath)) {
      return Collections.emptyMap();
    }

    Map<TemplateId, StateEntry> state = new LinkedHashMap<>();
    try (BufferedReader reader = Files.newBufferedReader(stateFilePath, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length >= 5) {
          String rawId = parts[0].trim();
          String sourceHash = parts[1].trim();
          String relativeClassPath = parts[2].trim();
          String fqcn = parts[3].trim();
          String fingerprint = parts[4].trim();

          if (!TemplateId.isTraversalSafe(rawId)) {
            continue;
          }
          Path resolvedClass = outputDir.resolve(relativeClassPath).normalize();
          if (!resolvedClass.startsWith(outputDir)
              || !resolvedClass.getFileName().toString().endsWith(".class")) {
            throw new SecurityException(
                "Path traversal detected in stored state relativeClassPath: " + relativeClassPath);
          }

          TemplateId templateId = TemplateId.normalize(rawId);
          state.put(
              templateId,
              new StateEntry(templateId, sourceHash, relativeClassPath, fqcn, fingerprint));
        }
      }
    } catch (IOException e) {
      // In case of corrupt state file, return empty map to recompile cleanly
      return Collections.emptyMap();
    }
    return state;
  }

  private static void validatePackagePrefix(String packagePrefix) {
    if (packagePrefix.isEmpty()) {
      return;
    }
    if (packagePrefix.contains("..")
        || packagePrefix.contains("/")
        || packagePrefix.contains("\\")) {
      throw new IllegalArgumentException(
          "Invalid packagePrefix contains illegal characters or path traversal: " + packagePrefix);
    }
    for (String segment : packagePrefix.split("\\.", -1)) {
      if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
        throw new IllegalArgumentException("Invalid packagePrefix identifier segment: " + segment);
      }
      for (int i = 1; i < segment.length(); i++) {
        if (!Character.isJavaIdentifierPart(segment.charAt(i))) {
          throw new IllegalArgumentException(
              "Invalid packagePrefix identifier segment: " + segment);
        }
      }
    }
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm unavailable", e);
    }
  }
}
