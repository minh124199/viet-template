package io.github.minh124199.viettemplate.api;

import java.io.Serial;

/** Thrown when template source resources, includes, or assets cannot be resolved or accessed. */
public class TemplateResourceException extends TemplateException {

  @Serial private static final long serialVersionUID = 1L;

  public TemplateResourceException(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode code) {
    super(message, templateId, span, code, null);
  }

  public TemplateResourceException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode code,
      Throwable cause) {
    super(message, templateId, span, code, cause);
  }
}
