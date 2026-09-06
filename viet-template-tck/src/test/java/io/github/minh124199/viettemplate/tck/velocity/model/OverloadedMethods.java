package io.github.minh124199.viettemplate.tck.velocity.model;

/** Model exposing overloaded methods to test method resolution and scoring. */
public class OverloadedMethods {

  public String choose(Integer x) {
    return "Integer:" + x;
  }

  public String choose(Number x) {
    return "Number:" + x;
  }

  public String choose(Object x) {
    return "Object:" + x;
  }

  public String choose(String s) {
    return "String:" + s;
  }

  public String pick(int i) {
    return "primitive_int:" + i;
  }

  public String pick(Integer i) {
    return "boxed_Integer:" + i;
  }

  public String identify(String a, Object b) {
    return "String_Object";
  }

  public String identify(Object a, String b) {
    return "Object_String";
  }

  public String identify(String a, String b) {
    return "String_String";
  }
}
