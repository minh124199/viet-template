package io.github.minh124199.viettemplate.language.vtl.semantics.type;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Root of the Viet Template semantic type hierarchy.
 *
 * <p>Models compile-time types for variables, properties, method returns, and expressions.
 */
public sealed interface VType
    permits VType.PrimitiveType,
        VType.ClassType,
        VType.ArrayType,
        VType.DynamicType,
        VType.NullType,
        VType.UnionType,
        VType.ErrorType {

  Nullability nullability();

  VType withNullability(Nullability nullability);

  boolean isAssignableTo(VType target);

  String typeName();

  default boolean isPrimitive() {
    return this instanceof PrimitiveType;
  }

  default boolean isDynamic() {
    return this instanceof DynamicType;
  }

  default boolean isError() {
    return this instanceof ErrorType;
  }

  default boolean isNull() {
    return this instanceof NullType;
  }

  /** Represents a primitive Java type (always non-null). */
  record PrimitiveType(PrimitiveKind kind) implements VType {
    public PrimitiveType {
      Objects.requireNonNull(kind, "kind must not be null");
    }

    @Override
    public Nullability nullability() {
      return Nullability.NON_NULL;
    }

    @Override
    public VType withNullability(Nullability nullability) {
      return this;
    }

    @Override
    public boolean isAssignableTo(VType target) {
      if (target instanceof PrimitiveType pt) {
        if (this.kind == pt.kind) {
          return true;
        }
        if (this.kind.isNumeric() && pt.kind.isNumeric()) {
          return isNumericWidenable(this.kind, pt.kind);
        }
        return false;
      }
      if (target instanceof ClassType ct) {
        if ("java.lang.Object".equals(ct.className())) {
          return true;
        }
        if (this.kind.isNumeric() && "java.lang.Number".equals(ct.className())) {
          return true;
        }
        return this.kind.boxedClassName().equals(ct.className());
      }
      if (target instanceof DynamicType) {
        return true;
      }
      if (target instanceof UnionType ut) {
        return ut.options().stream().anyMatch(this::isAssignableTo);
      }
      return false;
    }

    private static boolean isNumericWidenable(PrimitiveKind from, PrimitiveKind to) {
      return switch (from) {
        case BYTE ->
            to == PrimitiveKind.SHORT
                || to == PrimitiveKind.INT
                || to == PrimitiveKind.LONG
                || to == PrimitiveKind.FLOAT
                || to == PrimitiveKind.DOUBLE;
        case SHORT, CHAR ->
            to == PrimitiveKind.INT
                || to == PrimitiveKind.LONG
                || to == PrimitiveKind.FLOAT
                || to == PrimitiveKind.DOUBLE;
        case INT ->
            to == PrimitiveKind.LONG || to == PrimitiveKind.FLOAT || to == PrimitiveKind.DOUBLE;
        case LONG -> to == PrimitiveKind.FLOAT || to == PrimitiveKind.DOUBLE;
        case FLOAT -> to == PrimitiveKind.DOUBLE;
        default -> false;
      };
    }

    @Override
    public String typeName() {
      return kind.keyword();
    }
  }

  /** Represents an object / class type, with optional generic arguments and Java class binding. */
  record ClassType(
      String className,
      List<VType> typeArguments,
      Nullability nullability,
      Optional<Class<?>> javaClass)
      implements VType {

    public ClassType {
      Objects.requireNonNull(className, "className must not be null");
      typeArguments =
          List.copyOf(Objects.requireNonNull(typeArguments, "typeArguments must not be null"));
      Objects.requireNonNull(nullability, "nullability must not be null");
      Objects.requireNonNull(javaClass, "javaClass must not be null");
    }

    public static ClassType of(Class<?> clazz) {
      return of(clazz, Nullability.NULLABLE);
    }

    public static ClassType of(Class<?> clazz, Nullability nullability) {
      Objects.requireNonNull(clazz, "clazz must not be null");
      return new ClassType(clazz.getName(), List.of(), nullability, Optional.of(clazz));
    }

    public static ClassType of(Class<?> clazz, List<VType> typeArguments, Nullability nullability) {
      Objects.requireNonNull(clazz, "clazz must not be null");
      return new ClassType(clazz.getName(), typeArguments, nullability, Optional.of(clazz));
    }

    public static ClassType of(
        String className, List<VType> typeArguments, Nullability nullability) {
      return new ClassType(className, typeArguments, nullability, Optional.empty());
    }

    @Override
    public VType withNullability(Nullability nullability) {
      return new ClassType(className, typeArguments, nullability, javaClass);
    }

    @Override
    public boolean isAssignableTo(VType target) {
      if (target instanceof DynamicType) {
        return true;
      }
      if (target instanceof ClassType ct) {
        if (this.className.equals(ct.className)) {
          return true;
        }
        if (javaClass.isPresent() && ct.javaClass.isPresent()) {
          return ct.javaClass.get().isAssignableFrom(javaClass.get());
        }
        return "java.lang.Object".equals(ct.className);
      }
      if (target instanceof PrimitiveType pt) {
        return pt.kind().boxedClassName().equals(this.className);
      }
      if (target instanceof UnionType ut) {
        return ut.options().stream().anyMatch(this::isAssignableTo);
      }
      return false;
    }

    @Override
    public String typeName() {
      if (typeArguments.isEmpty()) {
        return className;
      }
      StringBuilder sb = new StringBuilder(className).append("<");
      for (int i = 0; i < typeArguments.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(typeArguments.get(i).typeName());
      }
      return sb.append(">").toString();
    }
  }

  /** Represents a Java array type. */
  record ArrayType(VType componentType, Nullability nullability) implements VType {
    public ArrayType {
      Objects.requireNonNull(componentType, "componentType must not be null");
      Objects.requireNonNull(nullability, "nullability must not be null");
    }

    @Override
    public VType withNullability(Nullability nullability) {
      return new ArrayType(componentType, nullability);
    }

    @Override
    public boolean isAssignableTo(VType target) {
      if (target instanceof DynamicType) {
        return true;
      }
      if (target instanceof ArrayType at) {
        return componentType.isAssignableTo(at.componentType);
      }
      if (target instanceof ClassType ct) {
        return "java.lang.Object".equals(ct.className())
            || "java.lang.Cloneable".equals(ct.className())
            || "java.io.Serializable".equals(ct.className());
      }
      if (target instanceof UnionType ut) {
        return ut.options().stream().anyMatch(this::isAssignableTo);
      }
      return false;
    }

    @Override
    public String typeName() {
      return componentType.typeName() + "[]";
    }
  }

  /** Represents dynamically typed expressions or references with runtime-deferred resolution. */
  record DynamicType(Nullability nullability) implements VType {
    public DynamicType {
      Objects.requireNonNull(nullability, "nullability must not be null");
    }

    @Override
    public VType withNullability(Nullability nullability) {
      return new DynamicType(nullability);
    }

    @Override
    public boolean isAssignableTo(VType target) {
      return true;
    }

    @Override
    public String typeName() {
      return "Dynamic";
    }
  }

  /** Represents the literal null value. */
  record NullType() implements VType {
    @Override
    public Nullability nullability() {
      return Nullability.NULLABLE;
    }

    @Override
    public VType withNullability(Nullability nullability) {
      return this;
    }

    @Override
    public boolean isAssignableTo(VType target) {
      return !(target instanceof PrimitiveType);
    }

    @Override
    public String typeName() {
      return "Null";
    }
  }

  /** Represents a union of possible types (e.g. from branching). */
  record UnionType(List<VType> options, Nullability nullability) implements VType {
    public UnionType {
      options = List.copyOf(Objects.requireNonNull(options, "options must not be null"));
      Objects.requireNonNull(nullability, "nullability must not be null");
    }

    @Override
    public VType withNullability(Nullability nullability) {
      return new UnionType(options, nullability);
    }

    @Override
    public boolean isAssignableTo(VType target) {
      return options.stream().allMatch(o -> o.isAssignableTo(target));
    }

    @Override
    public String typeName() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < options.size(); i++) {
        if (i > 0) {
          sb.append(" | ");
        }
        sb.append(options.get(i).typeName());
      }
      return sb.toString();
    }
  }

  /**
   * Represents an erroneous type produced after a diagnostic failure, preventing cascading errors.
   */
  record ErrorType() implements VType {
    @Override
    public Nullability nullability() {
      return Nullability.UNKNOWN;
    }

    @Override
    public VType withNullability(Nullability nullability) {
      return this;
    }

    @Override
    public boolean isAssignableTo(VType target) {
      return true;
    }

    @Override
    public String typeName() {
      return "<error>";
    }
  }
}
