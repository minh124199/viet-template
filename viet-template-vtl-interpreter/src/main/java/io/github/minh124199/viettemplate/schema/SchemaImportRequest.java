package io.github.minh124199.viettemplate.schema;

import java.io.Serializable;
import java.util.List;

/** Request specification for importing one or more schema sources. */
public record SchemaImportRequest(
    List<SchemaSource> sources, boolean failOnWarning, boolean strictMode) implements Serializable {

  public SchemaImportRequest {
    sources = sources == null ? List.of() : List.copyOf(sources);
  }

  public SchemaImportRequest(List<SchemaSource> sources) {
    this(sources, false, false);
  }

  public SchemaImportRequest(SchemaSource source) {
    this(List.of(source), false, false);
  }
}
