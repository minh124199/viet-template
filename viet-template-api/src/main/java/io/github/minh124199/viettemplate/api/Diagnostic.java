package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.util.Objects;

/**
 * Diagnostic event produced during template parsing, semantic analysis, compilation, or validation.
 */
public record Diagnostic(
    DiagnosticSeverity severity, DiagnosticCode code, String message, SourceSpan primarySpan)
    implements Serializable {

  public Diagnostic {
    Objects.requireNonNull(severity, "severity must not be null");
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(primarySpan, "primarySpan must not be null");
  }

  public static Diagnostic error(DiagnosticCode code, String message, SourceSpan primarySpan) {
    return new Diagnostic(DiagnosticSeverity.ERROR, code, message, primarySpan);
  }

  public static Diagnostic warning(DiagnosticCode code, String message, SourceSpan primarySpan) {
    return new Diagnostic(DiagnosticSeverity.WARNING, code, message, primarySpan);
  }

  public static Diagnostic info(DiagnosticCode code, String message, SourceSpan primarySpan) {
    return new Diagnostic(DiagnosticSeverity.INFO, code, message, primarySpan);
  }
}
