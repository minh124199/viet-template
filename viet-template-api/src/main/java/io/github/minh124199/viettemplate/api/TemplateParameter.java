package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable declaration of a single typed template parameter.
 *
 * <p>Describes parameter identity, expected Java type (as a recursive {@link TemplateType}), and
 * nullability constraint for static specialization and compile-time validation.
 */
@SuppressWarnings("serial")
public final class TemplateParameter implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String name;
  private final TemplateType type;
  private final boolean nullable;

  public TemplateParameter(String name, TemplateType type, boolean nullable) {
    this.name = validateName(name);
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.nullable = type.isPrimitive() ? false : nullable;
  }

  public TemplateParameter(
      String name, Class<?> rawType, List<Class<?>> typeArguments, boolean nullable) {
    this(name, buildTemplateType(rawType, typeArguments), nullable);
  }

  private static TemplateType buildTemplateType(Class<?> rawType, List<Class<?>> typeArguments) {
    Objects.requireNonNull(rawType, "rawType must not be null");
    if (typeArguments == null || typeArguments.isEmpty()) {
      return TemplateType.of(rawType);
    }
    List<TemplateType> args = new ArrayList<>();
    for (Class<?> arg : typeArguments) {
      args.add(TemplateType.of(arg));
    }
    return TemplateType.parameterized(rawType, args);
  }

  private static String validateName(String name) {
    Objects.requireNonNull(name, "name must not be null");
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Parameter name must not be empty");
    }
    if (!Character.isJavaIdentifierStart(trimmed.charAt(0))) {
      throw new IllegalArgumentException("Invalid parameter identifier start: '" + trimmed + "'");
    }
    for (int i = 1; i < trimmed.length(); i++) {
      if (!Character.isJavaIdentifierPart(trimmed.charAt(i))) {
        throw new IllegalArgumentException(
            "Invalid character '"
                + trimmed.charAt(i)
                + "' in parameter identifier: '"
                + trimmed
                + "'");
      }
    }
    return trimmed;
  }

  public static TemplateParameter of(String name, Class<?> rawType) {
    Objects.requireNonNull(rawType, "rawType must not be null");
    return new TemplateParameter(name, TemplateType.of(rawType), !rawType.isPrimitive());
  }

  public static TemplateParameter of(String name, Class<?> rawType, boolean nullable) {
    return new TemplateParameter(name, TemplateType.of(rawType), nullable);
  }

  public static TemplateParameter of(String name, Class<?> rawType, List<Class<?>> typeArguments) {
    Objects.requireNonNull(rawType, "rawType must not be null");
    return new TemplateParameter(
        name, buildTemplateType(rawType, typeArguments), !rawType.isPrimitive());
  }

  public static TemplateParameter of(
      String name, Class<?> rawType, List<Class<?>> typeArguments, boolean nullable) {
    return new TemplateParameter(name, buildTemplateType(rawType, typeArguments), nullable);
  }

  public static TemplateParameter of(String name, TemplateType type) {
    Objects.requireNonNull(type, "type must not be null");
    return new TemplateParameter(name, type, !type.isPrimitive());
  }

  public static TemplateParameter of(String name, TemplateType type, boolean nullable) {
    return new TemplateParameter(name, type, nullable);
  }

  public static TemplateParameter fromGenericType(String name, Type genericType) {
    return fromGenericType(name, genericType, true);
  }

  public static TemplateParameter fromGenericType(String name, Type genericType, boolean nullable) {
    Objects.requireNonNull(genericType, "genericType must not be null");
    TemplateType tt = TemplateType.fromGenericType(genericType);
    return new TemplateParameter(name, tt, nullable);
  }

  public String name() {
    return name;
  }

  public TemplateType type() {
    return type;
  }

  public Class<?> rawType() {
    return type.rawClass();
  }

  public List<Class<?>> typeArguments() {
    List<TemplateType> args = type.typeArguments();
    if (args.isEmpty()) {
      return List.of();
    }
    List<Class<?>> rawArgs = new ArrayList<>(args.size());
    for (TemplateType arg : args) {
      rawArgs.add(arg.rawClass());
    }
    return Collections.unmodifiableList(rawArgs);
  }

  public boolean nullable() {
    return nullable;
  }

  public String fingerprintFragment() {
    return name + ":" + type.fingerprintFragment() + (nullable ? "?" : "!");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TemplateParameter that)) return false;
    return nullable == that.nullable && name.equals(that.name) && type.equals(that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, nullable);
  }

  @Override
  public String toString() {
    return "TemplateParameter["
        + "name='"
        + name
        + '\''
        + ", type="
        + type.typeName()
        + ", nullable="
        + nullable
        + ']';
  }
}
