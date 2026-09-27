package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable declaration of a single typed template parameter.
 *
 * <p>Describes parameter identity, expected Java type, optional generic type arguments, and
 * nullability constraint for static specialization and compile-time validation.
 */
@SuppressWarnings("serial")
public final class TemplateParameter implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String name;
  private final Class<?> rawType;
  private final List<Class<?>> typeArguments;
  private final boolean nullable;

  public TemplateParameter(
      String name, Class<?> rawType, List<Class<?>> typeArguments, boolean nullable) {
    this.name = validateName(name);
    this.rawType = Objects.requireNonNull(rawType, "rawType must not be null");
    this.typeArguments =
        typeArguments == null
            ? List.of()
            : Collections.unmodifiableList(new ArrayList<>(typeArguments));
    this.nullable = rawType.isPrimitive() ? false : nullable;
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
    return new TemplateParameter(name, rawType, List.of(), !rawType.isPrimitive());
  }

  public static TemplateParameter of(String name, Class<?> rawType, boolean nullable) {
    return new TemplateParameter(name, rawType, List.of(), nullable);
  }

  public static TemplateParameter of(String name, Class<?> rawType, List<Class<?>> typeArguments) {
    return new TemplateParameter(name, rawType, typeArguments, !rawType.isPrimitive());
  }

  public static TemplateParameter of(
      String name, Class<?> rawType, List<Class<?>> typeArguments, boolean nullable) {
    return new TemplateParameter(name, rawType, typeArguments, nullable);
  }

  public static TemplateParameter fromGenericType(String name, Type genericType) {
    return fromGenericType(name, genericType, true);
  }

  public static TemplateParameter fromGenericType(String name, Type genericType, boolean nullable) {
    Objects.requireNonNull(genericType, "genericType must not be null");
    if (genericType instanceof Class<?> clazz) {
      return of(name, clazz, nullable);
    }
    if (genericType instanceof ParameterizedType pt) {
      Type raw = pt.getRawType();
      if (raw instanceof Class<?> rawClass) {
        List<Class<?>> args = new ArrayList<>();
        for (Type arg : pt.getActualTypeArguments()) {
          if (arg instanceof Class<?> argClass) {
            args.add(argClass);
          } else if (arg instanceof ParameterizedType argPt
              && argPt.getRawType() instanceof Class<?> argRawClass) {
            args.add(argRawClass);
          } else if (arg instanceof java.lang.reflect.WildcardType wt) {
            Type[] uppers = wt.getUpperBounds();
            if (uppers.length > 0 && uppers[0] instanceof Class<?> upperClass) {
              args.add(upperClass);
            } else if (uppers.length > 0
                && uppers[0] instanceof ParameterizedType upperPt
                && upperPt.getRawType() instanceof Class<?> upperRawClass) {
              args.add(upperRawClass);
            } else {
              args.add(Object.class);
            }
          } else {
            args.add(Object.class);
          }
        }
        return of(name, rawClass, args, nullable);
      }
    }
    return of(name, Object.class, nullable);
  }

  public String name() {
    return name;
  }

  public Class<?> rawType() {
    return rawType;
  }

  public List<Class<?>> typeArguments() {
    return typeArguments;
  }

  public boolean nullable() {
    return nullable;
  }

  public String fingerprintFragment() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(':').append(rawType.getName());
    if (!typeArguments.isEmpty()) {
      sb.append('<');
      for (int i = 0; i < typeArguments.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append(typeArguments.get(i).getName());
      }
      sb.append('>');
    }
    sb.append(nullable ? '?' : '!');
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TemplateParameter that)) return false;
    return nullable == that.nullable
        && name.equals(that.name)
        && rawType.equals(that.rawType)
        && typeArguments.equals(that.typeArguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, rawType, typeArguments, nullable);
  }

  @Override
  public String toString() {
    return fingerprintFragment();
  }
}
