package io.github.minh124199.viettemplate.vtl.interpreter;

/** Signal to break out of a #foreach loop. */
public final class BreakSignal extends ControlSignal {
  @java.io.Serial private static final long serialVersionUID = 1L;

  public static final BreakSignal INSTANCE = new BreakSignal();

  private BreakSignal() {}
}
