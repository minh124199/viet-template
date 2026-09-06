package io.github.minh124199.viettemplate.vtl.interpreter;

/** Signal to stop execution of the current template. */
public final class StopSignal extends ControlSignal {
  @java.io.Serial private static final long serialVersionUID = 1L;

  public static final StopSignal INSTANCE = new StopSignal();

  private final String message;

  public StopSignal() {
    this.message = null;
  }

  public StopSignal(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }
}
