package io.github.minh124199.viettemplate.validation;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.SchemaSource;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.internal.engine.dependency.StaticDependencyExtractor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Package-private default implementation of {@link TemplateValidator}. */
class DefaultTemplateValidator implements TemplateValidator {

  record DiscoveredTemplate(TemplateId templateId, String relPath, Path file) {}

  @Override
  public TemplateValidationResult validate(TemplateValidationRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    DefaultTemplateDependencyGraph dependencyGraph = new DefaultTemplateDependencyGraph();
    Map<TemplateId, DiscoveredTemplate> discovered = scanTemplates(request);
    List<DiscoveredTemplate> sortedTemplates = new ArrayList<>(discovered.values());
    sortedTemplates.sort(Comparator.comparing(d -> d.templateId().value()));

    List<TemplateAotDiagnostic> allDiagnostics = new ArrayList<>();

    CanonicalSchemaResolver schemaResolver =
        new CanonicalSchemaResolver(request.classLoader().orElse(null));
    for (SchemaSource source : request.schemaSources()) {
      schemaResolver.registerSource(source);
    }
    for (Map.Entry<TemplateId, CanonicalSchema> entry : request.canonicalSchemas().entrySet()) {
      schemaResolver.registerSchema(entry.getKey(), entry.getValue());
    }

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
        continue;
      }

      TemplateContract contract = request.contracts().get(templateId);
      if (contract == null) {
        contract =
            ValidationContractReader.findCompanion(
                    sourceFile, templateId, request.classLoader().orElse(null))
                .orElse(null);
      }

      CanonicalSchema canonicalSchema = null;
      if (contract == null) {
        canonicalSchema = request.canonicalSchemas().get(templateId);
        if (canonicalSchema == null) {
          canonicalSchema = schemaResolver.resolveSchema(templateId, sourceFile).orElse(null);
        }
      }

      SourceText source = SourceText.of(templateId, sourceText);
      VtlParseResult parseResult = VtlParser.parse(source);
      for (Diagnostic diag : parseResult.diagnostics()) {
        allDiagnostics.add(TemplateAotDiagnostic.from(templateId, dt.relPath(), diag));
      }
      if (parseResult.hasErrors()) {
        continue;
      }

      ModelSchema modelSchema = null;
      if (contract != null) {
        modelSchema = ModelSchema.fromContract(contract);
      } else if (canonicalSchema != null) {
        modelSchema =
            io.github.minh124199.viettemplate.schema.internal.CanonicalModelSchemaConverter
                .toModelSchema(canonicalSchema, request.classLoader().orElse(null));
      }

      ModelSchema finalModelSchema = modelSchema;
      VtlSemanticOptions semanticOptions =
          request
              .semanticOptions()
              .map(
                  options -> {
                    if (finalModelSchema != null
                        && (options.modelSchema() == null || options.modelSchema().isEmpty())) {
                      return options.toBuilder().modelSchema(finalModelSchema).build();
                    }
                    return options;
                  })
              .orElseGet(
                  () -> {
                    VtlProfile profile = request.profile().orElse(VtlProfile.VTL_MIGRATION);
                    VtlSemanticOptions.Builder semanticOptionsBuilder =
                        VtlSemanticOptions.builder()
                            .profile(profile)
                            .allowArbitraryMethods(profile.isArbitraryMethodsAllowed())
                            .typeCheckingMode(request.typeCheckingMode());
                    if (finalModelSchema != null) {
                      semanticOptionsBuilder.modelSchema(finalModelSchema);
                    }
                    return semanticOptionsBuilder.build();
                  });

      SemanticAnalysisResult analysis =
          VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
      for (Diagnostic diag : analysis.diagnostics()) {
        allDiagnostics.add(TemplateAotDiagnostic.from(templateId, dt.relPath(), diag));
      }

      if (request.validateDependencies()) {
        IrTemplate ir =
            AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);
        Set<TemplateDependency> deps =
            StaticDependencyExtractor.extract(
                ir, request.globalMacroLibraries(), request.layoutId());
        dependencyGraph.replaceDependencies(templateId, deps);
        for (TemplateDependency dep : deps) {
          TemplateId targetId = dep.target();
          if (!existsTarget(targetId, dt, request.sourceDirectories(), discovered)) {
            allDiagnostics.add(
                new TemplateAotDiagnostic(
                    templateId,
                    dt.relPath(),
                    DiagnosticSeverity.ERROR,
                    DiagnosticCode.of("RESOURCE", "NOT_FOUND"),
                    "Referenced template dependency not found: " + targetId.value(),
                    -1,
                    -1,
                    -1,
                    -1));
          }
        }
      }
    }

    List<TemplateAotDiagnostic> sortedDiagnostics =
        allDiagnostics.stream()
            .distinct()
            .sorted(
                Comparator.comparing((TemplateAotDiagnostic d) -> d.templateId().value())
                    .thenComparingInt(TemplateAotDiagnostic::startLine)
                    .thenComparingInt(TemplateAotDiagnostic::startColumn)
                    .thenComparing(d -> d.code().qualifiedCode())
                    .thenComparing(TemplateAotDiagnostic::message))
            .toList();

    int errorCount =
        (int)
            sortedDiagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .count();
    int warningCount =
        (int)
            sortedDiagnostics.stream()
                .filter(d -> d.severity() == DiagnosticSeverity.WARNING)
                .count();
    boolean success = errorCount == 0 && (!request.failOnWarning() || warningCount == 0);

    return new TemplateValidationResult(
        success,
        sortedTemplates.size(),
        errorCount,
        warningCount,
        sortedDiagnostics,
        dependencyGraph);
  }

  private static boolean existsTarget(
      TemplateId targetId,
      DiscoveredTemplate current,
      List<Path> sourceDirectories,
      Map<TemplateId, DiscoveredTemplate> discovered) {
    if (discovered.containsKey(targetId)) {
      return true;
    }
    for (Path srcDir : sourceDirectories) {
      Path absSrc = srcDir.toAbsolutePath().normalize();
      if (!Files.isDirectory(absSrc)) {
        continue;
      }
      Path candidate = absSrc.resolve(targetId.value()).normalize();
      if (candidate.startsWith(absSrc) && Files.isRegularFile(candidate)) {
        return true;
      }
    }
    if (current != null && current.file() != null) {
      Path parent = current.file().getParent();
      if (parent != null) {
        Path candidate = parent.resolve(targetId.value()).normalize();
        for (Path srcDir : sourceDirectories) {
          Path absSrc = srcDir.toAbsolutePath().normalize();
          if (candidate.startsWith(absSrc) && Files.isRegularFile(candidate)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static Map<TemplateId, DiscoveredTemplate> scanTemplates(
      TemplateValidationRequest request) {
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
}
