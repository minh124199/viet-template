package io.github.minh124199.viettemplate.api;

import java.io.Serial;

/**
 * Thrown when rendering exceeds resource limits such as maximum iterations, output size, or
 * recursion depth.
 */
public class TemplateLimitException extends TemplateException {

  @Serial private static final long serialVersionUID = 1L;

  public TemplateLimitException(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode code) {
    super(message, templateId, span, code, null);
  }

  public TemplateLimitException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode code,
      Throwable cause) {
    super(message, templateId, span, code, cause);
  }
}
