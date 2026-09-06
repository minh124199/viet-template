package io.github.minh124199.viettemplate.tck.velocity.model;

/** Model exposing multiple candidate property accessors for collision and precedence testing. */
public class IntrospectionTarget {
  public String name = "field_name";

  public String getName() {
    return "getter_name";
  }

  public String isName() {
    return "is_name";
  }

  public String name() {
    return "record_style_name";
  }

  public String get(String key) {
    return "generic_get:" + key;
  }
}
