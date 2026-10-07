package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Metadata capturing the origin, source location, and JVM binding proof of a schema element. */
record SchemaProvenance(
    SchemaFormat format,
    Optional<Path> sourcePath,
    String typeName,
    Optional<String> propertyName,
    Optional<SchemaSourceLocation> location,
    boolean jvmBound,
    Optional<String> jvmMemberName)
    implements Serializable {

  SchemaProvenance {
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(typeName, "typeName must not be null");
    sourcePath = sourcePath == null ? Optional.empty() : sourcePath;
    propertyName = propertyName == null ? Optional.empty() : propertyName;
    location = location == null ? Optional.empty() : location;
    jvmMemberName = jvmMemberName == null ? Optional.empty() : jvmMemberName;
  }
}
