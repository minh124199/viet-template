package io.github.minh124199.viettemplate.schema;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Result produced by schema import containing imported schemas and diagnostics. */
public record SchemaImportResult(
    Map<String, CanonicalSchema> schemas,
    List<SchemaDiagnostic> diagnostics,
    boolean hasErrors,
    boolean isPartial)
    implements Serializable {

  public SchemaImportResult {
    schemas = schemas == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(schemas));
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  public static SchemaImportResult of(
      Map<String, CanonicalSchema> schemas, List<SchemaDiagnostic> diagnostics) {
    boolean hasErrors =
        diagnostics != null
            && diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    boolean isPartial = hasErrors && schemas != null && !schemas.isEmpty();
    return new SchemaImportResult(schemas, diagnostics, hasErrors, isPartial);
  }

  public static SchemaImportResult success(Map<String, CanonicalSchema> schemas) {
    return new SchemaImportResult(schemas, List.of(), false, false);
  }

  public static SchemaImportResult failure(List<SchemaDiagnostic> diagnostics) {
    return new SchemaImportResult(Map.of(), diagnostics, true, false);
  }
}
