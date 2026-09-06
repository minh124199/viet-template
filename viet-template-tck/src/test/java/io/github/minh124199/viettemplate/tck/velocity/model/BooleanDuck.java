package io.github.minh124199.viettemplate.tck.velocity.model;

/** Model for duck-typing truthiness tests with getAsBoolean and isEmpty. */
public class BooleanDuck {
  private final boolean boolVal;
  private final boolean emptyVal;

  public BooleanDuck(boolean boolVal) {
    this(boolVal, false);
  }

  public BooleanDuck(boolean boolVal, boolean emptyVal) {
    this.boolVal = boolVal;
    this.emptyVal = emptyVal;
  }

  public boolean getAsBoolean() {
    return boolVal;
  }

  public boolean isEmpty() {
    return emptyVal;
  }
}
