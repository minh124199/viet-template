package io.github.minh124199.viettemplate.api;

import java.io.Serial;

/** Thrown when an unrecoverable runtime failure occurs during template rendering. */
public class TemplateRenderException extends TemplateException {

  @Serial private static final long serialVersionUID = 1L;

  public TemplateRenderException(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode code) {
    super(message, templateId, span, code, null);
  }

  public TemplateRenderException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode code,
      Throwable cause) {
    super(message, templateId, span, code, cause);
  }
}
