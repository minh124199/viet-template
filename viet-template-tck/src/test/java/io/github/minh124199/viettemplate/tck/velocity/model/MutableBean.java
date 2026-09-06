package io.github.minh124199.viettemplate.tck.velocity.model;

/** Simple mutable bean to observe property mutations in #set. */
public class MutableBean {
  private String text = "initial";
  private int count = 0;

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }
}
