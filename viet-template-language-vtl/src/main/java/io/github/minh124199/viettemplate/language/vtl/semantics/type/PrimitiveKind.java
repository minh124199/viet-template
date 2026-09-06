package io.github.minh124199.viettemplate.language.vtl.semantics.type;

import java.util.Objects;

/** Java primitive types supported by the Viet Template type system. */
public enum PrimitiveKind {
  BOOLEAN("boolean", "java.lang.Boolean", boolean.class, Boolean.class),
  BYTE("byte", "java.lang.Byte", byte.class, Byte.class),
  SHORT("short", "java.lang.Short", short.class, Short.class),
  INT("int", "java.lang.Integer", int.class, Integer.class),
  LONG("long", "java.lang.Long", long.class, Long.class),
  FLOAT("float", "java.lang.Float", float.class, Float.class),
  DOUBLE("double", "java.lang.Double", double.class, Double.class),
  CHAR("char", "java.lang.Character", char.class, Character.class);

  private final String keyword;
  private final String boxedClassName;
  private final Class<?> primitiveClass;
  private final Class<?> boxedClass;

  PrimitiveKind(
      String keyword, String boxedClassName, Class<?> primitiveClass, Class<?> boxedClass) {
    this.keyword = keyword;
    this.boxedClassName = boxedClassName;
    this.primitiveClass = primitiveClass;
    this.boxedClass = boxedClass;
  }

  public String keyword() {
    return keyword;
  }

  public String boxedClassName() {
    return boxedClassName;
  }

  public Class<?> primitiveClass() {
    return primitiveClass;
  }

  public Class<?> boxedClass() {
    return boxedClass;
  }

  public boolean isNumeric() {
    return this != BOOLEAN && this != CHAR;
  }

  public boolean isIntegral() {
    return this == BYTE || this == SHORT || this == INT || this == LONG || this == CHAR;
  }

  public boolean isFloatingPoint() {
    return this == FLOAT || this == DOUBLE;
  }

  public static PrimitiveKind fromClass(Class<?> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    for (PrimitiveKind kind : values()) {
      if (kind.primitiveClass == clazz || kind.boxedClass == clazz) {
        return kind;
      }
    }
    throw new IllegalArgumentException("Not a primitive or wrapper class: " + clazz.getName());
  }

  public static boolean isPrimitiveOrBoxed(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    for (PrimitiveKind kind : values()) {
      if (kind.primitiveClass == clazz || kind.boxedClass == clazz) {
        return true;
      }
    }
    return false;
  }
}
