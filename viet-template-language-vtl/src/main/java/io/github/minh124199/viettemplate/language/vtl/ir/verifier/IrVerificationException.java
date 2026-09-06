package io.github.minh124199.viettemplate.language.vtl.ir.verifier;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Exception thrown when Template IR fails invariant verification. */
public final class IrVerificationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final SourceSpan span;

  public IrVerificationException(String message, SourceSpan span) {
    super(message);
    this.span = Objects.requireNonNull(span, "span must not be null");
  }

  public SourceSpan span() {
    return span;
  }
}
