package io.github.minh124199.viettemplate.language.vtl.semantics.resolve;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves properties on receiver types in safe typed mode.
 *
 * <p>Lookup order:
 *
 * <ol>
 *   <li>Record component exact name
 *   <li>JavaBean getter: {@code getX()}
 *   <li>Boolean getter: {@code isX()}
 *   <li>Public field
 *   <li>Registered extension property
 *   <li>Map key (for Map types)
 *   <li>Not found (with Levenshtein typo suggestion)
 * </ol>
 */
public final class MemberResolver {

  private MemberResolver() {}

  public static MemberResolution resolveProperty(VType receiverType, String propertyName) {
    Objects.requireNonNull(receiverType, "receiverType must not be null");
    Objects.requireNonNull(propertyName, "propertyName must not be null");

    if (receiverType instanceof VType.DynamicType) {
      return MemberResolution.dynamic(VTypes.DYNAMIC);
    }
    if (receiverType instanceof VType.ErrorType) {
      return MemberResolution.notFound(VTypes.ERROR, Optional.empty());
    }

    if (receiverType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> clazz = ct.javaClass().get();
      return resolveClassProperty(clazz, ct, propertyName);
    }

    return MemberResolution.notFound(VTypes.ERROR, Optional.empty());
  }

  private static MemberResolution resolveClassProperty(
      Class<?> clazz, VType.ClassType receiverType, String propertyName) {
    Set<String> candidates = new LinkedHashSet<>();

    // 1. Record component exact name
    if (clazz.isRecord()) {
      for (RecordComponent rc : clazz.getRecordComponents()) {
        candidates.add(rc.getName());
        if (rc.getName().equals(propertyName)) {
          VType type = VTypes.fromJavaType(rc.getGenericType(), Nullability.NULLABLE);
          return MemberResolution.of(
              MemberResolution.Kind.RECORD_COMPONENT, type, rc.getAccessor());
        }
      }
    }

    // 2. JavaBean getter: getX()
    String getterName = "get" + capitalize(propertyName);
    try {
      Method m = clazz.getMethod(getterName);
      if (Modifier.isPublic(m.getModifiers())
          && m.getParameterCount() == 0
          && m.getReturnType() != void.class) {
        VType type = VTypes.fromJavaType(m.getGenericReturnType(), Nullability.NULLABLE);
        return MemberResolution.of(MemberResolution.Kind.GETTER, type, m);
      }
    } catch (NoSuchMethodException ignored) {
    }

    // 3. Boolean getter: isX()
    String booleanGetterName = "is" + capitalize(propertyName);
    try {
      Method m = clazz.getMethod(booleanGetterName);
      if (Modifier.isPublic(m.getModifiers())
          && m.getParameterCount() == 0
          && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
        VType type = VTypes.fromJavaType(m.getGenericReturnType(), Nullability.NULLABLE);
        return MemberResolution.of(MemberResolution.Kind.BOOLEAN_GETTER, type, m);
      }
    } catch (NoSuchMethodException ignored) {
    }

    // 4. Exact zero-arg public method matching property name (e.g. interface or record-style
    // getter)
    try {
      Method m = clazz.getMethod(propertyName);
      if (Modifier.isPublic(m.getModifiers())
          && m.getParameterCount() == 0
          && m.getReturnType() != void.class
          && m.getDeclaringClass() != Object.class) {
        VType type = VTypes.fromJavaType(m.getGenericReturnType(), Nullability.NULLABLE);
        return MemberResolution.of(MemberResolution.Kind.GETTER, type, m);
      }
    } catch (NoSuchMethodException ignored) {
    }

    // 5. Public field
    try {
      Field f = clazz.getField(propertyName);
      if (Modifier.isPublic(f.getModifiers())) {
        VType type = VTypes.fromJavaType(f.getGenericType(), Nullability.NULLABLE);
        return MemberResolution.of(MemberResolution.Kind.FIELD, type, f);
      }
    } catch (NoSuchFieldException ignored) {
    }

    // 6. Map key lookup
    if (Map.class.isAssignableFrom(clazz)) {
      VType valueType = VTypes.elementType(receiverType);
      return MemberResolution.of(MemberResolution.Kind.MAP_ENTRY, valueType, null);
    }

    // Collect all available candidates for typo suggestions
    for (Method m : clazz.getMethods()) {
      if (m.getParameterCount() != 0
          || !Modifier.isPublic(m.getModifiers())
          || m.getDeclaringClass() == Object.class) {
        continue;
      }
      String name = m.getName();
      if (name.startsWith("get") && name.length() > 3) {
        candidates.add(decapitalize(name.substring(3)));
      } else if (name.startsWith("is") && name.length() > 2) {
        candidates.add(decapitalize(name.substring(2)));
      } else {
        candidates.add(name);
      }
    }
    for (Field f : clazz.getFields()) {
      if (Modifier.isPublic(f.getModifiers())) {
        candidates.add(f.getName());
      }
    }

    Optional<String> suggestion = LevenshteinDistance.findClosestMatch(propertyName, candidates);
    return MemberResolution.notFound(VTypes.ERROR, suggestion);
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  private static String decapitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toLowerCase(str.charAt(0)) + str.substring(1);
  }
}
