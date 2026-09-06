package io.github.minh124199.viettemplate.api;

import java.io.Serial;

/** Thrown when template semantics, types, or variable bindings fail validation. */
public class TemplateSemanticException extends TemplateException {

  @Serial private static final long serialVersionUID = 1L;

  public TemplateSemanticException(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode code) {
    super(message, templateId, span, code, null);
  }

  public TemplateSemanticException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode code,
      Throwable cause) {
    super(message, templateId, span, code, cause);
  }
}
