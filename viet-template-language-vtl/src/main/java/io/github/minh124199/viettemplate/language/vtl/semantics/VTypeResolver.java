package io.github.minh124199.viettemplate.language.vtl.semantics;

import io.github.minh124199.viettemplate.language.vtl.internal.semantics.model.ModelParameter;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.PrimitiveKind;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves declared type expressions into canonical {@link VType} instances.
 *
 * <p>Supports Java primitives, boxed and standard type aliases, generic type arguments (e.g. {@code
 * List<Employee>}), array types (e.g. {@code int[]}), wildcard bounds (e.g. {@code ? extends
 * User}), nullable markers, companion schema definitions, and fully-qualified classpath types.
 */
final class VTypeResolver {

  private VTypeResolver() {}

  public static Optional<VType> resolve(String typeText, boolean nullable) {
    return resolve(typeText, nullable, null, null);
  }

  public static Optional<VType> resolve(
      String typeText, boolean nullable, ModelSchema modelSchema) {
    return resolve(typeText, nullable, modelSchema, null);
  }

  public static Optional<VType> resolve(
      String typeText, boolean isNullable, ModelSchema modelSchema, ClassLoader classLoader) {
    Objects.requireNonNull(typeText, "typeText must not be null");
    String trimmed = typeText.trim();
    if (trimmed.isEmpty()) {
      return Optional.empty();
    }

    boolean nullable = isNullable;
    if (trimmed.endsWith("?")) {
      nullable = true;
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
      if (trimmed.isEmpty()) {
        return Optional.empty();
      }
    }

    Nullability nullability = nullable ? Nullability.NULLABLE : Nullability.NON_NULL;

    // 1. Wildcards: ? extends T, ? super T, ?
    if (trimmed.startsWith("?")) {
      String afterQ = trimmed.substring(1).trim();
      if (afterQ.startsWith("extends ") || afterQ.startsWith("extends\t")) {
        String bound = afterQ.substring(8).trim();
        Optional<VType> boundType = resolve(bound, false, modelSchema, classLoader);
        return boundType.map(b -> b.withNullability(nullability));
      }
      if (afterQ.startsWith("super ") || afterQ.startsWith("super\t")) {
        return Optional.of(VTypes.OBJECT.withNullability(nullability));
      }
      if (afterQ.isEmpty()) {
        return Optional.of(VTypes.DYNAMIC.withNullability(nullability));
      }
      return Optional.empty();
    }

    // 2. Arrays: Type[]
    if (trimmed.endsWith("[]")) {
      String compText = trimmed.substring(0, trimmed.length() - 2).trim();
      Optional<VType> compType = resolve(compText, false, modelSchema, classLoader);
      return compType.map(c -> new VType.ArrayType(c, nullability));
    }

    // 3. Generics: Raw<Arg1, Arg2>
    int angleIdx = trimmed.indexOf('<');
    if (angleIdx != -1 && trimmed.endsWith(">")) {
      String rawName = trimmed.substring(0, angleIdx).trim();
      String argsStr = trimmed.substring(angleIdx + 1, trimmed.length() - 1).trim();
      List<String> argStrings = splitGenericArgs(argsStr);
      List<VType> typeArgs = new ArrayList<>(argStrings.size());
      for (String argStr : argStrings) {
        Optional<VType> argType = resolve(argStr, false, modelSchema, classLoader);
        if (argType.isEmpty()) {
          return Optional.empty();
        }
        typeArgs.add(argType.get());
      }

      Class<?> rawClass = resolveStandardClass(rawName);
      if (rawClass == null) {
        rawClass = loadClass(rawName, classLoader);
      }
      if (rawClass != null) {
        return Optional.of(VType.ClassType.of(rawClass, typeArgs, nullability));
      }
      Optional<VType> schemaRaw = findSchemaType(modelSchema, rawName, nullability);
      if (schemaRaw.isPresent()) {
        VType sType = schemaRaw.get();
        if (sType instanceof VType.ClassType ct) {
          if (ct.javaClass().isPresent()) {
            return Optional.of(VType.ClassType.of(ct.javaClass().get(), typeArgs, nullability));
          }
          return Optional.of(VType.ClassType.of(ct.className(), typeArgs, nullability));
        }
      }
      return Optional.empty();
    }

    // 4. Primitives
    for (PrimitiveKind pk : PrimitiveKind.values()) {
      if (pk.keyword().equals(trimmed)) {
        if (nullable) {
          return Optional.of(VType.ClassType.of(pk.boxedClass(), Nullability.NULLABLE));
        }
        return Optional.of(new VType.PrimitiveType(pk));
      }
    }

    // 5. Standard type aliases & java.lang / java.util / java.math types
    Optional<VType> standardType = resolveStandardAlias(trimmed, nullability);
    if (standardType.isPresent()) {
      return standardType;
    }

    // 6. Type variables (T, E, K, V, etc.)
    if (trimmed.length() == 1 && Character.isUpperCase(trimmed.charAt(0))) {
      return Optional.of(VTypes.OBJECT.withNullability(nullability));
    }

    // 7. Companion schema types
    Optional<VType> schemaType = findSchemaType(modelSchema, trimmed, nullability);
    if (schemaType.isPresent()) {
      return schemaType;
    }

    // 8. Class loading (fully qualified or available on classpath)
    Class<?> clazz = loadClass(trimmed, classLoader);
    if (clazz != null) {
      return Optional.of(VType.ClassType.of(clazz, nullability));
    }

    return Optional.empty();
  }

  private static Optional<VType> resolveStandardAlias(String name, Nullability nullability) {
    return switch (name) {
      case "String", "java.lang.String" -> Optional.of(VTypes.STRING.withNullability(nullability));
      case "Object", "java.lang.Object" -> Optional.of(VTypes.OBJECT.withNullability(nullability));
      case "Boolean", "java.lang.Boolean" ->
          Optional.of(VType.ClassType.of(Boolean.class, nullability));
      case "Integer", "java.lang.Integer" ->
          Optional.of(VType.ClassType.of(Integer.class, nullability));
      case "Long", "java.lang.Long" -> Optional.of(VType.ClassType.of(Long.class, nullability));
      case "Double", "java.lang.Double" ->
          Optional.of(VType.ClassType.of(Double.class, nullability));
      case "Float", "java.lang.Float" -> Optional.of(VType.ClassType.of(Float.class, nullability));
      case "Byte", "java.lang.Byte" -> Optional.of(VType.ClassType.of(Byte.class, nullability));
      case "Short", "java.lang.Short" -> Optional.of(VType.ClassType.of(Short.class, nullability));
      case "Character", "java.lang.Character" ->
          Optional.of(VType.ClassType.of(Character.class, nullability));
      case "BigDecimal", "java.math.BigDecimal" ->
          Optional.of(VTypes.BIG_DECIMAL.withNullability(nullability));
      case "BigInteger", "java.math.BigInteger" ->
          Optional.of(VTypes.BIG_INTEGER.withNullability(nullability));
      case "List", "java.util.List" ->
          Optional.of(VType.ClassType.of(List.class, List.of(VTypes.DYNAMIC), nullability));
      case "Map", "java.util.Map" ->
          Optional.of(
              VType.ClassType.of(Map.class, List.of(VTypes.DYNAMIC, VTypes.DYNAMIC), nullability));
      case "Set", "java.util.Set" ->
          Optional.of(VType.ClassType.of(Set.class, List.of(VTypes.DYNAMIC), nullability));
      case "Collection", "java.util.Collection" ->
          Optional.of(VType.ClassType.of(Collection.class, List.of(VTypes.DYNAMIC), nullability));
      case "Iterable", "java.lang.Iterable" ->
          Optional.of(VType.ClassType.of(Iterable.class, List.of(VTypes.DYNAMIC), nullability));
      default -> Optional.empty();
    };
  }

  private static Class<?> resolveStandardClass(String name) {
    return switch (name) {
      case "List", "java.util.List" -> List.class;
      case "Map", "java.util.Map" -> Map.class;
      case "Set", "java.util.Set" -> Set.class;
      case "Collection", "java.util.Collection" -> Collection.class;
      case "Iterable", "java.lang.Iterable" -> Iterable.class;
      case "Optional", "java.util.Optional" -> Optional.class;
      case "Class", "java.lang.Class" -> Class.class;
      case "String", "java.lang.String" -> String.class;
      case "Object", "java.lang.Object" -> Object.class;
      default -> null;
    };
  }

  private static List<String> splitGenericArgs(String args) {
    List<String> list = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < args.length(); i++) {
      char c = args.charAt(i);
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
      } else if (c == ',' && depth == 0) {
        String piece = args.substring(start, i).trim();
        if (!piece.isEmpty()) {
          list.add(piece);
        }
        start = i + 1;
      }
    }
    if (start < args.length()) {
      String piece = args.substring(start).trim();
      if (!piece.isEmpty()) {
        list.add(piece);
      }
    }
    return list;
  }

  private static Class<?> loadClass(String name, ClassLoader classLoader) {
    Class<?> loaded = doLoadClass(name, classLoader);
    if (loaded != null) {
      return loaded;
    }
    // Handle nested classes e.g. Outer.Inner -> Outer$Inner, Outer.Middle.Inner ->
    // Outer$Middle$Inner
    int dot = name.indexOf('.');
    while (dot > 0) {
      String prefix = name.substring(0, dot);
      Class<?> prefixClass = doLoadClass(prefix, classLoader);
      if (prefixClass != null) {
        String suffix = name.substring(dot + 1).replace('.', '$');
        loaded = doLoadClass(prefix + "$" + suffix, classLoader);
        if (loaded != null) {
          return loaded;
        }
      }
      dot = name.indexOf('.', dot + 1);
    }
    // Fallback: replace dot from the right
    int lastDot = name.lastIndexOf('.');
    while (lastDot > 0) {
      String candidate = name.substring(0, lastDot) + "$" + name.substring(lastDot + 1);
      loaded = doLoadClass(candidate, classLoader);
      if (loaded != null) {
        return loaded;
      }
      lastDot = name.lastIndexOf('.', lastDot - 1);
    }
    return null;
  }

  private static Class<?> doLoadClass(String name, ClassLoader classLoader) {
    if (classLoader != null) {
      try {
        return Class.forName(name, false, classLoader);
      } catch (ClassNotFoundException | LinkageError ignored) {
      }
    }
    ClassLoader ccl = Thread.currentThread().getContextClassLoader();
    if (ccl != null && ccl != classLoader) {
      try {
        return Class.forName(name, false, ccl);
      } catch (ClassNotFoundException | LinkageError ignored) {
      }
    }
    try {
      return Class.forName(name, false, VTypeResolver.class.getClassLoader());
    } catch (ClassNotFoundException | LinkageError ignored) {
    }
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException | LinkageError ignored) {
      return null;
    }
  }

  private static Optional<VType> findSchemaType(
      ModelSchema modelSchema, String name, Nullability nullability) {
    if (modelSchema == null) {
      return Optional.empty();
    }
    if (modelSchema.hasType(name)) {
      return Optional.of(VType.ClassType.of(name, List.of(), nullability));
    }
    for (ModelParameter p : modelSchema.parameters().values()) {
      Optional<VType> match = matchTypeByName(p.type(), name, nullability);
      if (match.isPresent()) {
        return match;
      }
    }
    return Optional.empty();
  }

  private static Optional<VType> matchTypeByName(VType type, String name, Nullability nullability) {
    if (type instanceof VType.ClassType ct) {
      if (ct.javaClass().isPresent() && ct.javaClass().get().getSimpleName().equals(name)) {
        return Optional.of(VType.ClassType.of(ct.javaClass().get(), nullability));
      }
      if (ct.className().equals(name) || ct.className().endsWith("." + name)) {
        return Optional.of(ct.withNullability(nullability));
      }
      for (VType arg : ct.typeArguments()) {
        Optional<VType> match = matchTypeByName(arg, name, nullability);
        if (match.isPresent()) {
          return match;
        }
      }
    } else if (type instanceof VType.ArrayType at) {
      return matchTypeByName(at.componentType(), name, nullability);
    }
    return Optional.empty();
  }
}
