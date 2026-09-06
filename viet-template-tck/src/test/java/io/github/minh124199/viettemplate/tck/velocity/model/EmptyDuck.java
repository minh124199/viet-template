package io.github.minh124199.viettemplate.tck.velocity.model;

/** Model for duck-typing truthiness tests with isEmpty and size. */
public class EmptyDuck {
  private final boolean emptyVal;
  private final int sizeVal;

  public EmptyDuck(boolean emptyVal, int sizeVal) {
    this.emptyVal = emptyVal;
    this.sizeVal = sizeVal;
  }

  public boolean isEmpty() {
    return emptyVal;
  }

  public int size() {
    return sizeVal;
  }
}
