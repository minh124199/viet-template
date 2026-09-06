package io.github.minh124199.viettemplate.tck.velocity.result;

/**
 * Normalized observation of an exception thrown during template compilation or rendering.
 *
 * @param category Normalized semantic classification.
 * @param rawExceptionClass Fully-qualified name of the thrown exception class.
 * @param message Exception message string.
 * @param causeClass Fully-qualified name of the cause class, if present.
 */
public record ExceptionObservation(
    ExceptionCategory category, String rawExceptionClass, String message, String causeClass) {

  public static ExceptionObservation of(ExceptionCategory category, Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    String cause = throwable.getCause() != null ? throwable.getCause().getClass().getName() : null;
    return new ExceptionObservation(
        category,
        throwable.getClass().getName(),
        throwable.getMessage() != null ? throwable.getMessage() : "",
        cause);
  }
}
