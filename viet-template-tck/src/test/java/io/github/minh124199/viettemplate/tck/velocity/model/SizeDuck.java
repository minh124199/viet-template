package io.github.minh124199.viettemplate.tck.velocity.model;

/** Model for duck-typing truthiness tests with size and length. */
public class SizeDuck {
  private final int sizeVal;
  private final int lengthVal;

  public SizeDuck(int sizeVal, int lengthVal) {
    this.sizeVal = sizeVal;
    this.lengthVal = lengthVal;
  }

  public int size() {
    return sizeVal;
  }

  public int length() {
    return lengthVal;
  }
}
