package io.github.minh124199.viettemplate.api;

/** Thrown when layout execution encounters a cycle or exceeds maximum layout depth. */
public class TemplateLayoutException extends TemplateException {

  private static final long serialVersionUID = 1L;

  public TemplateLayoutException(
      String message, TemplateId templateId, SourceSpan span, DiagnosticCode diagnosticCode) {
    super(message, templateId, span, diagnosticCode, null);
  }

  public TemplateLayoutException(
      String message,
      TemplateId templateId,
      SourceSpan span,
      DiagnosticCode diagnosticCode,
      Throwable cause) {
    super(message, templateId, span, diagnosticCode, cause);
  }
}
