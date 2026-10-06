package io.github.minh124199.viettemplate.schema;

import java.util.List;
import java.util.Objects;

/** Contract for importing external schemas into canonical schema models. */
public interface SchemaImporter {

  /**
   * Creates a default composite schema importer supporting all built-in formats.
   *
   * @return a composite schema importer instance
   */
  static SchemaImporter create() {
    return new CompositeSchemaImporter();
  }

  /**
   * Imports schemas from sources defined in the request.
   *
   * @param request the import request
   * @return the import result containing canonical schemas and any diagnostics
   */
  SchemaImportResult importSchemas(SchemaImportRequest request);

  /**
   * Convenience helper to import a single schema source.
   *
   * @param source the schema source
   * @return the import result
   */
  default SchemaImportResult importSource(SchemaSource source) {
    Objects.requireNonNull(source, "source must not be null");
    return importSchemas(new SchemaImportRequest(List.of(source)));
  }
}
