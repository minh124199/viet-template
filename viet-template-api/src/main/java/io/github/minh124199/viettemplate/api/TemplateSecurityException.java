package io.github.minh124199.viettemplate.api;

import java.io.Serial;

/**
 * Thrown when a template operation attempts an unauthorized capability or violates security policy.
 */
public class TemplateSecurityException extends TemplateException {

  @Serial private static final long serialVersionUID = 1L;

  public TemplateSecurityException(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode code) {
    super(message, templateId, span, code, null);
  }

  public TemplateSecurityException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode code,
      Throwable cause) {
    super(message, templateId, span, code, cause);
  }
}
