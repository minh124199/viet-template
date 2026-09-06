package io.github.minh124199.viettemplate.api;

import java.io.Serial;

/** Thrown when template source syntax is malformed. */
public class TemplateSyntaxException extends TemplateException {

  @Serial private static final long serialVersionUID = 1L;

  public TemplateSyntaxException(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode code) {
    super(message, templateId, span, code, null);
  }

  public TemplateSyntaxException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode code,
      Throwable cause) {
    super(message, templateId, span, code, cause);
  }
}
