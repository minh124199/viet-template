package io.github.minh124199.viettemplate.schema;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import java.io.Serializable;
import java.util.Objects;

/** Diagnostic reporting validation, syntax, or resolution issues during schema import. */
public record SchemaDiagnostic(
    String sourcePath,
    int line,
    int column,
    SchemaFormat format,
    String code,
    DiagnosticSeverity severity,
    String message,
    String suggestedAction)
    implements Serializable {

  public SchemaDiagnostic {
    sourcePath = sourcePath == null ? "" : sourcePath;
    Objects.requireNonNull(format, "format must not be null");
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(severity, "severity must not be null");
    Objects.requireNonNull(message, "message must not be null");
    suggestedAction = suggestedAction == null ? "" : suggestedAction;
  }

  public static SchemaDiagnostic error(
      String sourcePath,
      int line,
      int column,
      SchemaFormat format,
      String code,
      String message,
      String suggestedAction) {
    return new SchemaDiagnostic(
        sourcePath, line, column, format, code, DiagnosticSeverity.ERROR, message, suggestedAction);
  }

  public static SchemaDiagnostic warning(
      String sourcePath,
      int line,
      int column,
      SchemaFormat format,
      String code,
      String message,
      String suggestedAction) {
    return new SchemaDiagnostic(
        sourcePath,
        line,
        column,
        format,
        code,
        DiagnosticSeverity.WARNING,
        message,
        suggestedAction);
  }

  public static SchemaDiagnostic info(
      String sourcePath,
      int line,
      int column,
      SchemaFormat format,
      String code,
      String message,
      String suggestedAction) {
    return new SchemaDiagnostic(
        sourcePath, line, column, format, code, DiagnosticSeverity.INFO, message, suggestedAction);
  }
}
