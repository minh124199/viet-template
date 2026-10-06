package io.github.minh124199.viettemplate.schema;

import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Composite schema importer capable of dispatching schema sources based on {@link SchemaFormat}.
 */
public final class CompositeSchemaImporter implements SchemaImporter {

  private final Map<SchemaFormat, SchemaImporter> importers = new EnumMap<>(SchemaFormat.class);

  public CompositeSchemaImporter() {
    register(SchemaFormat.JSON_SCHEMA, new JsonSchemaImporter());
    register(SchemaFormat.TYPESCRIPT, new TypeScriptSchemaImporter());
    JavaModelSchemaImporter javaImporter = new JavaModelSchemaImporter();
    register(SchemaFormat.JAVA, javaImporter);
    register(SchemaFormat.CONTRACT, javaImporter);
  }

  /**
   * Registers or overrides a format-specific schema importer.
   *
   * @param format the schema format
   * @param importer the importer instance
   * @return this composite importer instance
   */
  public CompositeSchemaImporter register(SchemaFormat format, SchemaImporter importer) {
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(importer, "importer must not be null");
    importers.put(format, importer);
    return this;
  }

  @Override
  public SchemaImportResult importSchemas(SchemaImportRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Map<String, CanonicalSchema> allSchemas = new TreeMap<>();
    List<SchemaDiagnostic> allDiagnostics = new ArrayList<>();

    // Group sources by format to dispatch in batches
    Map<SchemaFormat, List<SchemaSource>> byFormat = new EnumMap<>(SchemaFormat.class);
    for (SchemaSource source : request.sources()) {
      byFormat.computeIfAbsent(source.format(), k -> new ArrayList<>()).add(source);
    }

    for (Map.Entry<SchemaFormat, List<SchemaSource>> entry : byFormat.entrySet()) {
      SchemaFormat format = entry.getKey();
      List<SchemaSource> sources = entry.getValue();
      SchemaImporter importer = importers.get(format);

      if (importer == null) {
        for (SchemaSource s : sources) {
          allDiagnostics.add(
              SchemaDiagnostic.error(
                  s.path().toString(),
                  1,
                  1,
                  format,
                  "SCHEMA_UNSUPPORTED_FORMAT",
                  "No importer registered for format: " + format,
                  "Register an importer supporting " + format));
        }
        continue;
      }

      SchemaImportResult res =
          importer.importSchemas(
              new SchemaImportRequest(sources, request.failOnWarning(), request.strictMode()));
      allSchemas.putAll(res.schemas());
      allDiagnostics.addAll(res.diagnostics());
    }

    return SchemaImportResult.of(allSchemas, allDiagnostics);
  }
}
