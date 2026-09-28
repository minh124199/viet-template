package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Public, durable, recursive descriptor for typed template parameter types.
 *
 * <p>Preserves full nested generic type arguments (e.g. {@code Map<String, List<User>>}), wildcard
 * bounds ({@code List<? extends User>}), arrays, primitives, and named symbolic types for
 * compile-time specialization and code generation.
 */
public sealed interface TemplateType extends Serializable
    permits TemplateType.PrimitiveType,
        TemplateType.ClassType,
        TemplateType.ParameterizedType,
        TemplateType.ArrayType,
        TemplateType.WildcardType,
        TemplateType.NamedType {

  /** Returns the full canonical type name suitable for Java code generation or diagnostics. */
  String typeName();

  /** Returns the resolved raw Java class if available, or {@code Object.class} as fallback. */
  Class<?> rawClass();

  /** Returns the nested type arguments if parameterized, or an empty list. */
  default List<TemplateType> typeArguments() {
    return List.of();
  }

  /** Checks whether this type is a primitive Java type. */
  default boolean isPrimitive() {
    return false;
  }

  /** Checks whether this type is an array type. */
  default boolean isArray() {
    return false;
  }

  /** Returns a deterministic fingerprint fragment representing this type in contract hashes. */
  String fingerprintFragment();

  // --- Factory Methods ---

  static TemplateType of(Class<?> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    if (clazz.isPrimitive()) {
      return new PrimitiveType(clazz);
    }
    if (clazz.isArray()) {
      return new ArrayType(of(clazz.getComponentType()));
    }
    return new ClassType(clazz);
  }

  static TemplateType primitive(Class<?> primitiveClass) {
    return new PrimitiveType(primitiveClass);
  }

  static TemplateType classType(Class<?> clazz) {
    return of(clazz);
  }

  static TemplateType parameterized(Class<?> rawClass, TemplateType... typeArguments) {
    Objects.requireNonNull(rawClass, "rawClass must not be null");
    Objects.requireNonNull(typeArguments, "typeArguments must not be null");
    return new ParameterizedType(rawClass, List.of(typeArguments));
  }

  static TemplateType parameterized(Class<?> rawClass, List<TemplateType> typeArguments) {
    return new ParameterizedType(rawClass, typeArguments);
  }

  static TemplateType array(TemplateType componentType) {
    return new ArrayType(componentType);
  }

  static TemplateType wildcardExtends(TemplateType upperBound) {
    return new WildcardType(Optional.of(upperBound), Optional.empty());
  }

  static TemplateType wildcardSuper(TemplateType lowerBound) {
    return new WildcardType(Optional.empty(), Optional.of(lowerBound));
  }

  static TemplateType wildcard() {
    return WildcardType.UNBOUNDED;
  }

  static TemplateType named(String typeName) {
    return new NamedType(typeName, List.of());
  }

  static TemplateType named(String typeName, List<TemplateType> typeArguments) {
    return new NamedType(typeName, typeArguments);
  }

  /**
   * Recursively inspects a {@link Type} (including parameterized, array, and wildcard types) and
   * produces an immutable {@link TemplateType}.
   */
  static TemplateType fromGenericType(Type type) {
    Objects.requireNonNull(type, "type must not be null");
    if (type instanceof Class<?> c) {
      return of(c);
    }
    if (type instanceof java.lang.reflect.ParameterizedType pt) {
      Type raw = pt.getRawType();
      Class<?> rawClass = (raw instanceof Class<?> rc) ? rc : Object.class;
      List<TemplateType> args = new ArrayList<>();
      for (Type arg : pt.getActualTypeArguments()) {
        args.add(fromGenericType(arg));
      }
      return new ParameterizedType(rawClass, args);
    }
    if (type instanceof GenericArrayType gat) {
      return new ArrayType(fromGenericType(gat.getGenericComponentType()));
    }
    if (type instanceof java.lang.reflect.WildcardType wt) {
      Type[] upper = wt.getUpperBounds();
      Type[] lower = wt.getLowerBounds();
      if (lower != null && lower.length > 0 && lower[0] != null) {
        return wildcardSuper(fromGenericType(lower[0]));
      }
      if (upper != null && upper.length > 0 && upper[0] != null && upper[0] != Object.class) {
        return wildcardExtends(fromGenericType(upper[0]));
      }
      return wildcard();
    }
    if (type instanceof java.lang.reflect.TypeVariable<?> tv) {
      return named(tv.getName());
    }
    return of(Object.class);
  }

  // --- Permitted Record Implementations ---

  record PrimitiveType(Class<?> primitiveClass) implements TemplateType {
    public PrimitiveType {
      Objects.requireNonNull(primitiveClass, "primitiveClass must not be null");
      if (!primitiveClass.isPrimitive()) {
        throw new IllegalArgumentException("Class is not primitive: " + primitiveClass.getName());
      }
    }

    @Override
    public String typeName() {
      return primitiveClass.getName();
    }

    @Override
    public Class<?> rawClass() {
      return primitiveClass;
    }

    @Override
    public boolean isPrimitive() {
      return true;
    }

    @Override
    public String fingerprintFragment() {
      return primitiveClass.getName();
    }
  }

  record ClassType(Class<?> rawClass) implements TemplateType {
    public ClassType {
      Objects.requireNonNull(rawClass, "rawClass must not be null");
      if (rawClass.isPrimitive()) {
        throw new IllegalArgumentException(
            "Use PrimitiveType for primitive classes: " + rawClass.getName());
      }
      if (rawClass.isArray()) {
        throw new IllegalArgumentException(
            "Use ArrayType for array classes: " + rawClass.getName());
      }
    }

    @Override
    public String typeName() {
      return rawClass.getName();
    }

    @Override
    public String fingerprintFragment() {
      return rawClass.getName();
    }
  }

  record ParameterizedType(Class<?> rawClass, List<TemplateType> typeArguments)
      implements TemplateType {
    public ParameterizedType {
      Objects.requireNonNull(rawClass, "rawClass must not be null");
      typeArguments =
          typeArguments == null
              ? List.of()
              : Collections.unmodifiableList(new ArrayList<>(typeArguments));
    }

    @Override
    public String typeName() {
      StringBuilder sb = new StringBuilder(rawClass.getName());
      if (!typeArguments.isEmpty()) {
        sb.append('<');
        for (int i = 0; i < typeArguments.size(); i++) {
          if (i > 0) sb.append(", ");
          sb.append(typeArguments.get(i).typeName());
        }
        sb.append('>');
      }
      return sb.toString();
    }

    @Override
    public String fingerprintFragment() {
      StringBuilder sb = new StringBuilder(rawClass.getName());
      if (!typeArguments.isEmpty()) {
        sb.append('<');
        for (int i = 0; i < typeArguments.size(); i++) {
          if (i > 0) sb.append(',');
          sb.append(typeArguments.get(i).fingerprintFragment());
        }
        sb.append('>');
      }
      return sb.toString();
    }
  }

  record ArrayType(TemplateType componentType) implements TemplateType {
    public ArrayType {
      Objects.requireNonNull(componentType, "componentType must not be null");
    }

    @Override
    public String typeName() {
      return componentType.typeName() + "[]";
    }

    @Override
    public Class<?> rawClass() {
      Class<?> comp = componentType.rawClass();
      return comp.arrayType();
    }

    @Override
    public boolean isArray() {
      return true;
    }

    @Override
    public String fingerprintFragment() {
      return componentType.fingerprintFragment() + "[]";
    }
  }

  record WildcardType(Optional<TemplateType> upperBound, Optional<TemplateType> lowerBound)
      implements TemplateType {
    public static final WildcardType UNBOUNDED =
        new WildcardType(Optional.empty(), Optional.empty());

    public WildcardType {
      Objects.requireNonNull(upperBound, "upperBound must not be null");
      Objects.requireNonNull(lowerBound, "lowerBound must not be null");
    }

    @Override
    public String typeName() {
      if (lowerBound.isPresent()) {
        return "? super " + lowerBound.get().typeName();
      }
      if (upperBound.isPresent()) {
        return "? extends " + upperBound.get().typeName();
      }
      return "?";
    }

    @Override
    public Class<?> rawClass() {
      return upperBound.map(TemplateType::rawClass).orElse(Object.class);
    }

    @Override
    public String fingerprintFragment() {
      if (lowerBound.isPresent()) {
        return "?-" + lowerBound.get().fingerprintFragment();
      }
      if (upperBound.isPresent()) {
        return "?+" + upperBound.get().fingerprintFragment();
      }
      return "?";
    }
  }

  record NamedType(String name, List<TemplateType> typeArguments) implements TemplateType {
    public NamedType {
      Objects.requireNonNull(name, "name must not be null");
      typeArguments =
          typeArguments == null
              ? List.of()
              : Collections.unmodifiableList(new ArrayList<>(typeArguments));
    }

    @Override
    public String typeName() {
      StringBuilder sb = new StringBuilder(name);
      if (!typeArguments.isEmpty()) {
        sb.append('<');
        for (int i = 0; i < typeArguments.size(); i++) {
          if (i > 0) sb.append(", ");
          sb.append(typeArguments.get(i).typeName());
        }
        sb.append('>');
      }
      return sb.toString();
    }

    @Override
    public Class<?> rawClass() {
      try {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
          return Class.forName(name, false, cl);
        }
      } catch (ClassNotFoundException ignored) {
      }
      return Object.class;
    }

    @Override
    public String fingerprintFragment() {
      StringBuilder sb = new StringBuilder(name);
      if (!typeArguments.isEmpty()) {
        sb.append('<');
        for (int i = 0; i < typeArguments.size(); i++) {
          if (i > 0) sb.append(',');
          sb.append(typeArguments.get(i).fingerprintFragment());
        }
        sb.append('>');
      }
      return sb.toString();
    }
  }
}
