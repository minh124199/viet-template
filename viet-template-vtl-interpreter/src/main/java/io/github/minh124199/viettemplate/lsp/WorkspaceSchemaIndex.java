package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workspace-level index managing template-to-schema dependencies, provenance metadata, and
 * incremental cache invalidation for language server operations.
 */
final class WorkspaceSchemaIndex {

  private final CanonicalSchemaResolver resolver;
  private final ClassLoader classLoader;
  private final Map<Path, Set<String>> schemaToTemplates = new ConcurrentHashMap<>();
  private final Map<String, Path> templateToSchema = new ConcurrentHashMap<>();
  private final Map<String, Map<String, SchemaProvenance>> provenanceCache =
      new ConcurrentHashMap<>();

  WorkspaceSchemaIndex(CanonicalSchemaResolver resolver) {
    this(resolver, null);
  }

  WorkspaceSchemaIndex(CanonicalSchemaResolver resolver, ClassLoader classLoader) {
    this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    this.classLoader = classLoader;
  }

  public CanonicalSchemaResolver resolver() {
    return resolver;
  }

  public void recordDependency(String templateUri, Path schemaPath) {
    if (templateUri == null || schemaPath == null) {
      return;
    }
    Path norm = schemaPath.toAbsolutePath().normalize();
    String normUri = TemplateDocumentStore.normalizeUri(templateUri);
    schemaToTemplates.computeIfAbsent(norm, k -> ConcurrentHashMap.newKeySet()).add(normUri);
    templateToSchema.put(normUri, norm);
    resolver
        .resolveSchema(norm.toUri().toString())
        .ifPresent(
            s -> {
              resolver.registerSchema(normUri, s);
            });
  }

  public Optional<Path> getAssociatedSchemaPath(String templateUri) {
    if (templateUri == null) {
      return Optional.empty();
    }
    String normUri = TemplateDocumentStore.normalizeUri(templateUri);
    Path p = templateToSchema.get(normUri);
    if (p != null) {
      return Optional.of(p);
    }
    return resolver.getSchemaFilePath(templateUri);
  }

  public Optional<SchemaProvenance> getParameterProvenance(
      String templateIdOrUri, String paramName) {
    Map<String, SchemaProvenance> index = getOrComputeProvenance(templateIdOrUri);
    return SchemaProvenanceExtractor.findParameter(index, paramName);
  }

  public Optional<SchemaProvenance> getPropertyProvenance(
      String templateIdOrUri, String typeName, String propName) {
    Map<String, SchemaProvenance> index = getOrComputeProvenance(templateIdOrUri);
    return SchemaProvenanceExtractor.findProperty(index, typeName, propName);
  }

  public Optional<SchemaProvenance> getTypeProvenance(String templateIdOrUri, String typeName) {
    Map<String, SchemaProvenance> index = getOrComputeProvenance(templateIdOrUri);
    return SchemaProvenanceExtractor.findType(index, typeName);
  }

  public Map<String, SchemaProvenance> getOrComputeProvenance(String templateIdOrUri) {
    if (templateIdOrUri == null || templateIdOrUri.isBlank()) {
      return Map.of();
    }
    String key = CanonicalSchemaResolver.normalizeKey(templateIdOrUri);
    Map<String, SchemaProvenance> cached = provenanceCache.get(key);
    if (cached != null) {
      return cached;
    }

    Optional<CanonicalSchema> schemaOpt = resolver.resolveSchema(templateIdOrUri);
    if (schemaOpt.isEmpty()) {
      return Map.of();
    }

    CanonicalSchema schema = schemaOpt.get();
    Optional<Path> pathOpt = getAssociatedSchemaPath(templateIdOrUri);
    if (pathOpt.isPresent()) {
      recordDependency(templateIdOrUri, pathOpt.get());
    }

    ClassLoader cl = classLoader != null ? classLoader : resolver.classLoader().orElse(null);
    Map<String, SchemaProvenance> extracted =
        SchemaProvenanceExtractor.extractAll(schema, pathOpt, cl);

    provenanceCache.put(key, extracted);
    if (!schema.templateId().isBlank()) {
      provenanceCache.put(CanonicalSchemaResolver.normalizeKey(schema.templateId()), extracted);
    }
    if (pathOpt.isPresent()) {
      provenanceCache.put(
          CanonicalSchemaResolver.normalizeKey(pathOpt.get().toUri().toString()), extracted);
    }

    return extracted;
  }

  public List<String> onSchemaChanged(Path path) {
    if (path == null) {
      return List.of();
    }
    Path norm = path.toAbsolutePath().normalize();
    resolver.invalidateSchema(norm);
    clearProvenanceFor(norm);

    if (Files.isRegularFile(norm)) {
      try {
        resolver.registerSchemaFile(norm);
        // Pre-warm provenance for updated file
        String uriKey = norm.toUri().toString();
        getOrComputeProvenance(uriKey);
      } catch (IOException ignored) {
      }
    }

    List<String> affected = findDependentTemplates(norm);
    for (String dep : affected) {
      resolver
          .resolveSchema(norm.toUri().toString())
          .ifPresent(s -> resolver.registerSchema(dep, s));
    }
    return affected;
  }

  public List<String> onSchemaDeleted(Path path) {
    if (path == null) {
      return List.of();
    }
    Path norm = path.toAbsolutePath().normalize();
    resolver.invalidateSchema(norm);
    clearProvenanceFor(norm);

    List<String> dependents = findDependentTemplates(norm);
    schemaToTemplates.remove(norm);
    for (String dep : dependents) {
      templateToSchema.remove(dep);
      resolver.removeSchema(dep);
    }
    return dependents;
  }

  private void clearProvenanceFor(Path normPath) {
    String uriKey = CanonicalSchemaResolver.normalizeKey(normPath.toUri().toString());
    provenanceCache.remove(uriKey);
    String fileName = normPath.getFileName().toString();
    int dot = fileName.indexOf('.');
    String base = dot > 0 ? fileName.substring(0, dot) : fileName;
    provenanceCache.remove(CanonicalSchemaResolver.normalizeKey(base));
    provenanceCache.remove(CanonicalSchemaResolver.normalizeKey(base + ".vtl"));
    provenanceCache.remove(CanonicalSchemaResolver.normalizeKey(base + ".vt"));
  }

  private List<String> findDependentTemplates(Path normPath) {
    Set<String> affected = new LinkedHashSet<>();
    Set<String> registered = schemaToTemplates.get(normPath);
    if (registered != null) {
      affected.addAll(registered);
    }

    // Also infer sibling template convention: if foo.schema.json, foo.vtl/foo.vt
    String fileName = normPath.getFileName().toString();
    int dot = fileName.indexOf('.');
    String base = dot > 0 ? fileName.substring(0, dot) : fileName;
    for (String ext : List.of(".vtl", ".vt", ".vm")) {
      Path sibling = normPath.resolveSibling(base + ext);
      String siblingUri = TemplateDocumentStore.normalizeUri(sibling.toUri().toString());
      affected.add(siblingUri);
    }

    return new ArrayList<>(affected);
  }
}
