package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.io.Serializable;
import java.util.Objects;

/** Diagnostic event reported during AOT compilation (error, warning, or informational). */
public record TemplateAotDiagnostic(
    TemplateId templateId,
    String sourcePath,
    DiagnosticSeverity severity,
    DiagnosticCode code,
    String message,
    int startLine,
    int startColumn,
    int endLine,
    int endColumn)
    implements Serializable {

  public TemplateAotDiagnostic {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(sourcePath, "sourcePath must not be null");
    Objects.requireNonNull(severity, "severity must not be null");
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
  }

  /**
   * Formats this diagnostic in standard compiler report style: {@code
   * <sourcePath>:<startLine>:<startColumn> [<code.qualifiedCode()>] <message>} or {@code
   * <sourcePath> [<code.qualifiedCode()>] <message>} when span coordinates are unknown.
   */
  public String formattedMessage() {
    if (startLine > 0 && startColumn > 0) {
      return String.format(
          "%s:%d:%d [%s] %s", sourcePath, startLine, startColumn, code.qualifiedCode(), message);
    } else {
      return String.format("%s [%s] %s", sourcePath, code.qualifiedCode(), message);
    }
  }

  @Override
  public String toString() {
    return formattedMessage();
  }

  /** Constructs a {@link TemplateAotDiagnostic} from an internal compiler {@link Diagnostic}. */
  public static TemplateAotDiagnostic from(
      TemplateId templateId, String sourcePath, Diagnostic diagnostic) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(sourcePath, "sourcePath must not be null");
    Objects.requireNonNull(diagnostic, "diagnostic must not be null");
    SourceSpan span = diagnostic.primarySpan();
    int startLine = -1;
    int startColumn = -1;
    int endLine = -1;
    int endColumn = -1;
    if (span != null && span.isKnown()) {
      startLine = span.startLine();
      startColumn = span.startColumn();
      endLine = span.endLine();
      endColumn = span.endColumn();
    }
    DiagnosticCode code = diagnostic.code();
    if (code != null && "PARSER".equalsIgnoreCase(code.category())) {
      code = DiagnosticCode.of("SYNTAX", "PARSE_ERROR");
    }
    return new TemplateAotDiagnostic(
        templateId,
        sourcePath,
        diagnostic.severity(),
        code,
        diagnostic.message(),
        startLine,
        startColumn,
        endLine,
        endColumn);
  }
}
