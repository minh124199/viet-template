package io.github.minh124199.viettemplate.api;

import java.util.Objects;

/**
 * Thrown when an illegal variable name collision occurs under {@link
 * ContextCollisionPolicy#ERROR_ON_COLLISION} or when a reserved engine variable is overwritten.
 */
public class ContextCollisionException extends TemplateException {

  private static final long serialVersionUID = 1L;

  private final String variableName;
  private final ValueOrigin existingOrigin;
  private final ValueOrigin collidingOrigin;

  public ContextCollisionException(
      String message,
      String variableName,
      ValueOrigin existingOrigin,
      ValueOrigin collidingOrigin) {
    super(
        message,
        TemplateId.of("<context>"),
        SourceSpan.UNKNOWN,
        DiagnosticCode.of("CONTEXT", "COLLISION"),
        null);
    this.variableName = Objects.requireNonNull(variableName, "variableName must not be null");
    this.existingOrigin = Objects.requireNonNull(existingOrigin, "existingOrigin must not be null");
    this.collidingOrigin =
        Objects.requireNonNull(collidingOrigin, "collidingOrigin must not be null");
  }

  public String variableName() {
    return variableName;
  }

  public ValueOrigin existingOrigin() {
    return existingOrigin;
  }

  public ValueOrigin collidingOrigin() {
    return collidingOrigin;
  }
}
