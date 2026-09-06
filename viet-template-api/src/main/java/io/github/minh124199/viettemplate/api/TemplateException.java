package io.github.minh124199.viettemplate.api;

import java.io.Serial;
import java.util.Optional;

/** Base unchecked exception for all Viet Template errors. */
public abstract class TemplateException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final TemplateId templateId;
  private final SourceSpan span;
  private final DiagnosticCode code;

  protected TemplateException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode code,
      Throwable cause) {
    super(formatMessage(message, templateId, span, code), cause);
    this.templateId = templateId;
    this.span = span != null ? span : SourceSpan.UNKNOWN;
    this.code = code;
  }

  private static String formatMessage(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode code) {
    StringBuilder sb = new StringBuilder();
    if (code != null) {
      sb.append("[").append(code).append("] ");
    }
    if (templateId != null) {
      sb.append(templateId.value());
      if (span != null && span.isKnown()) {
        sb.append(":").append(span.startLine()).append(":").append(span.startColumn());
      }
      sb.append(" - ");
    }
    sb.append(message != null ? message : "Template exception");
    return sb.toString();
  }

  public Optional<TemplateId> templateId() {
    return Optional.ofNullable(templateId);
  }

  public SourceSpan span() {
    return span;
  }

  public Optional<DiagnosticCode> code() {
    return Optional.ofNullable(code);
  }
}
