package io.github.minh124199.viettemplate.vtl.interpreter;

/**
 * Internal control flow signal for non-local jumps (#break, #stop) within the interpreter. Not
 * exposed as a user error.
 */
public abstract sealed class ControlSignal extends RuntimeException
    permits BreakSignal, StopSignal {

  @java.io.Serial private static final long serialVersionUID = 1L;

  protected ControlSignal() {
    super(null, null, false, false);
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
