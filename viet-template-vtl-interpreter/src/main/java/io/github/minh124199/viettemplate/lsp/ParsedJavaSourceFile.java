package io.github.minh124199.viettemplate.lsp;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed representation of a Java source file caching its declared types and members for definition
 * lookups.
 */
record ParsedJavaSourceFile(
    Path path,
    long lastModified,
    long fileSize,
    Map<String, JavaSourceDeclaration> typeDeclarations,
    Map<String, List<JavaSourceDeclaration>> memberDeclarations) {

  ParsedJavaSourceFile {
    Objects.requireNonNull(path, "path must not be null");
    typeDeclarations =
        typeDeclarations == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(typeDeclarations);
    memberDeclarations =
        memberDeclarations == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(memberDeclarations);
  }

  static ParsedJavaSourceFile empty(Path path, long lastModified, long fileSize) {
    return new ParsedJavaSourceFile(
        path, lastModified, fileSize, Collections.emptyMap(), Collections.emptyMap());
  }
}
