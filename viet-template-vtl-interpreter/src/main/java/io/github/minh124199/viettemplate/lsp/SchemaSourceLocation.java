package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;

/** 1-indexed source location pointing to a declaration in a schema file. */
record SchemaSourceLocation(
    Path filePath, int startLine, int startColumn, int endLine, int endColumn)
    implements Serializable, Comparable<SchemaSourceLocation> {

  SchemaSourceLocation {
    Objects.requireNonNull(filePath, "filePath must not be null");
    if (startLine < 1 || startColumn < 1 || endLine < 1 || endColumn < 1) {
      throw new IllegalArgumentException(
          String.format(
              "Lines and columns must be 1-indexed (>= 1): %d:%d -> %d:%d",
              startLine, startColumn, endLine, endColumn));
    }
  }

  public static SchemaSourceLocation of(
      Path filePath, int startLine, int startColumn, int endLine, int endColumn) {
    return new SchemaSourceLocation(filePath, startLine, startColumn, endLine, endColumn);
  }

  public String format() {
    String fileName =
        filePath.getFileName() != null ? filePath.getFileName().toString() : filePath.toString();
    return fileName + ":" + startLine + ":" + startColumn;
  }

  @Override
  public int compareTo(SchemaSourceLocation o) {
    Objects.requireNonNull(o, "o must not be null");
    int fileCmp = this.filePath.compareTo(o.filePath);
    if (fileCmp != 0) {
      return fileCmp;
    }
    int lineCmp = Integer.compare(this.startLine, o.startLine);
    if (lineCmp != 0) {
      return lineCmp;
    }
    return Integer.compare(this.startColumn, o.startColumn);
  }
}
