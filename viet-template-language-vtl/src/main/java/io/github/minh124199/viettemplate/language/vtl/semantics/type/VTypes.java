package io.github.minh124199.viettemplate.language.vtl.semantics.type;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Factory and utility functions for constructing and inspecting {@link VType} instances. */
public final class VTypes {
  public static final VType.PrimitiveType BOOLEAN = new VType.PrimitiveType(PrimitiveKind.BOOLEAN);
  public static final VType.PrimitiveType BYTE = new VType.PrimitiveType(PrimitiveKind.BYTE);
  public static final VType.PrimitiveType SHORT = new VType.PrimitiveType(PrimitiveKind.SHORT);
  public static final VType.PrimitiveType INT = new VType.PrimitiveType(PrimitiveKind.INT);
  public static final VType.PrimitiveType LONG = new VType.PrimitiveType(PrimitiveKind.LONG);
  public static final VType.PrimitiveType FLOAT = new VType.PrimitiveType(PrimitiveKind.FLOAT);
  public static final VType.PrimitiveType DOUBLE = new VType.PrimitiveType(PrimitiveKind.DOUBLE);
  public static final VType.PrimitiveType CHAR = new VType.PrimitiveType(PrimitiveKind.CHAR);

  public static final VType.ClassType STRING =
      VType.ClassType.of(String.class, Nullability.NON_NULL);
  public static final VType.ClassType OBJECT =
      VType.ClassType.of(Object.class, Nullability.NULLABLE);
  public static final VType.ClassType BIG_DECIMAL =
      VType.ClassType.of(BigDecimal.class, Nullability.NULLABLE);
  public static final VType.ClassType BIG_INTEGER =
      VType.ClassType.of(BigInteger.class, Nullability.NULLABLE);

  public static final VType.DynamicType DYNAMIC = new VType.DynamicType(Nullability.UNKNOWN);
  public static final VType.NullType NULL = new VType.NullType();
  public static final VType.ErrorType ERROR = new VType.ErrorType();

  private VTypes() {}

  public static VType fromJavaClass(Class<?> clazz) {
    return fromJavaClass(clazz, Nullability.NULLABLE);
  }

  public static VType fromJavaClass(Class<?> clazz, Nullability nullability) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    if (clazz == void.class || clazz == Void.class) {
      return VTypes.NULL;
    }
    if (clazz.isPrimitive()) {
      return new VType.PrimitiveType(PrimitiveKind.fromClass(clazz));
    }
    if (clazz.isArray()) {
      return new VType.ArrayType(
          fromJavaClass(clazz.getComponentType(), Nullability.NULLABLE), nullability);
    }
    return VType.ClassType.of(clazz, nullability);
  }

  public static VType fromJavaType(Type type) {
    return fromJavaType(type, Nullability.NULLABLE);
  }

  public static VType fromJavaType(Type type, Nullability nullability) {
    Objects.requireNonNull(type, "type must not be null");
    if (type instanceof Class<?> c) {
      return fromJavaClass(c, nullability);
    }
    if (type instanceof ParameterizedType pt) {
      Type raw = pt.getRawType();
      if (raw instanceof Class<?> rawClass) {
        List<VType> args = new ArrayList<>();
        for (Type arg : pt.getActualTypeArguments()) {
          args.add(fromJavaType(arg, Nullability.NULLABLE));
        }
        return VType.ClassType.of(rawClass, args, nullability);
      }
    }
    if (type instanceof GenericArrayType gat) {
      return new VType.ArrayType(
          fromJavaType(gat.getGenericComponentType(), Nullability.NULLABLE), nullability);
    }
    if (type instanceof WildcardType wt) {
      Type[] upperBounds = wt.getUpperBounds();
      if (upperBounds.length > 0) {
        return fromJavaType(upperBounds[0], nullability);
      }
    }
    return DYNAMIC;
  }

  public static boolean isNumeric(VType type) {
    if (type instanceof VType.PrimitiveType pt) {
      return pt.kind().isNumeric();
    }
    if (type instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> c = ct.javaClass().get();
      return Number.class.isAssignableFrom(c) || PrimitiveKind.isPrimitiveOrBoxed(c);
    }
    return false;
  }

  public static boolean isBoolean(VType type) {
    if (type instanceof VType.PrimitiveType pt) {
      return pt.kind() == PrimitiveKind.BOOLEAN;
    }
    if (type instanceof VType.ClassType ct) {
      return Boolean.class.getName().equals(ct.className());
    }
    return false;
  }

  public static boolean isIterable(VType type) {
    if (type instanceof VType.ArrayType) {
      return true;
    }
    if (type instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      return Iterable.class.isAssignableFrom(ct.javaClass().get())
          || Map.class.isAssignableFrom(ct.javaClass().get());
    }
    return false;
  }

  public static boolean isMap(VType type) {
    if (type instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      return Map.class.isAssignableFrom(ct.javaClass().get());
    }
    return false;
  }

  public static boolean isCollection(VType type) {
    if (type instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      return Collection.class.isAssignableFrom(ct.javaClass().get());
    }
    return false;
  }

  public static VType elementType(VType iterableType) {
    if (iterableType instanceof VType.ArrayType at) {
      return at.componentType();
    }
    if (iterableType instanceof VType.ClassType ct) {
      if (ct.javaClass().isPresent()) {
        Class<?> c = ct.javaClass().get();
        if (Iterable.class.isAssignableFrom(c)) {
          if (!ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
          }
          List<Type> args = findGenericArguments(c, Iterable.class);
          if (!args.isEmpty()) {
            return fromJavaType(args.get(0), Nullability.NULLABLE);
          }
          return DYNAMIC;
        }
        if (Map.class.isAssignableFrom(c)) {
          // In VTL #foreach($item in $map), Velocity iterates over the map values
          if (ct.typeArguments().size() >= 2) {
            return ct.typeArguments().get(1);
          }
          List<Type> args = findGenericArguments(c, Map.class);
          if (args.size() >= 2) {
            return fromJavaType(args.get(1), Nullability.NULLABLE);
          }
          return DYNAMIC;
        }
      }
    }
    if (iterableType instanceof VType.DynamicType) {
      return DYNAMIC;
    }
    return DYNAMIC;
  }

  private static List<Type> findGenericArguments(Type current, Class<?> target) {
    if (current == null) {
      return List.of();
    }
    if (current instanceof ParameterizedType pt) {
      Type raw = pt.getRawType();
      if (raw instanceof Class<?> rawClass) {
        if (rawClass == target) {
          return List.of(pt.getActualTypeArguments());
        }
        for (Type iface : rawClass.getGenericInterfaces()) {
          List<Type> res =
              findGenericArguments(
                  resolveTypeArguments(iface, rawClass, pt.getActualTypeArguments()), target);
          if (!res.isEmpty()) {
            return res;
          }
        }
        Type sc = rawClass.getGenericSuperclass();
        if (sc != null) {
          List<Type> res =
              findGenericArguments(
                  resolveTypeArguments(sc, rawClass, pt.getActualTypeArguments()), target);
          if (!res.isEmpty()) {
            return res;
          }
        }
      }
    } else if (current instanceof Class<?> clazz) {
      if (clazz == target) {
        return List.of();
      }
      for (Type iface : clazz.getGenericInterfaces()) {
        List<Type> res = findGenericArguments(iface, target);
        if (!res.isEmpty()) {
          return res;
        }
      }
      Type sc = clazz.getGenericSuperclass();
      if (sc != null) {
        List<Type> res = findGenericArguments(sc, target);
        if (!res.isEmpty()) {
          return res;
        }
      }
    }
    return List.of();
  }

  private static Type resolveTypeArguments(
      Type targetType, Class<?> declaringClass, Type[] actualArgs) {
    if (!(targetType instanceof ParameterizedType pt)) {
      return targetType;
    }
    Type[] targetArgs = pt.getActualTypeArguments();
    Type[] resolvedArgs = new Type[targetArgs.length];
    java.lang.reflect.TypeVariable<?>[] typeParams = declaringClass.getTypeParameters();
    for (int i = 0; i < targetArgs.length; i++) {
      resolvedArgs[i] = targetArgs[i];
      if (targetArgs[i] instanceof java.lang.reflect.TypeVariable<?> tv) {
        for (int j = 0; j < typeParams.length; j++) {
          if (typeParams[j].getName().equals(tv.getName()) && j < actualArgs.length) {
            resolvedArgs[i] = actualArgs[j];
            break;
          }
        }
      }
    }
    return new ParameterizedTypeImpl(pt.getRawType(), resolvedArgs, pt.getOwnerType());
  }

  private record ParameterizedTypeImpl(Type rawType, Type[] actualTypeArguments, Type ownerType)
      implements ParameterizedType {
    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments.clone();
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }
  }
}
