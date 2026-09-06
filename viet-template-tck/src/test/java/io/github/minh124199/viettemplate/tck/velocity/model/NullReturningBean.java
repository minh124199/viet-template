package io.github.minh124199.viettemplate.tck.velocity.model;

/** Model exposing null properties and methods returning null. */
public class NullReturningBean {

  public Object getNullProperty() {
    return null;
  }

  public String returnNull() {
    return null;
  }

  public String getDefined() {
    return "present";
  }
}
