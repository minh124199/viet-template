package io.github.minh124199.viettemplate.api;

import java.io.Serial;

/** Thrown when bytecode generation or ahead-of-time compilation fails. */
public class TemplateCompilationException extends TemplateException {

  @Serial private static final long serialVersionUID = 1L;

  public TemplateCompilationException(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode code) {
    super(message, templateId, span, code, null);
  }

  public TemplateCompilationException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode code,
      Throwable cause) {
    super(message, templateId, span, code, cause);
  }
}
