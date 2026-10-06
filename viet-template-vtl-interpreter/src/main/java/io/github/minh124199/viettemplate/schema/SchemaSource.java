package io.github.minh124199.viettemplate.schema;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Source definition for schema import. */
public record SchemaSource(Path path, SchemaFormat format, Optional<String> templateId)
    implements Serializable {

  public SchemaSource {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(templateId, "templateId must not be null");
  }

  public SchemaSource(Path path, SchemaFormat format) {
    this(path, format, Optional.empty());
  }

  public SchemaSource(Path path, SchemaFormat format, String templateId) {
    this(path, format, Optional.ofNullable(templateId));
  }
}
