package io.github.minh124199.viettemplate.schema;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.api.TemplateType;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Normalizes Java domain models (records, JavaBeans, interfaces, enums, collections) and {@link
 * TemplateContract} instances into canonical schemas.
 */
public final class JavaModelSchemaImporter implements SchemaImporter {

  public static final String CODE_CLASS_NOT_FOUND = "JAVA_SCHEMA_CLASS_NOT_FOUND";
  public static final String CODE_REFLECTION_ERROR = "JAVA_SCHEMA_REFLECTION_ERROR";
  private static final int MAX_EXPANSION_DEPTH = 32;

  @Override
  public SchemaImportResult importSchemas(SchemaImportRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Map<String, CanonicalSchema> schemas = new TreeMap<>();
    List<SchemaDiagnostic> allDiagnostics = new ArrayList<>();

    for (SchemaSource source : request.sources()) {
      String sourceStr = source.path().toString();
      String className = sourceStr;
      if (className.endsWith(".class")) {
        className =
            className
                .substring(0, className.length() - ".class".length())
                .replace('/', '.')
                .replace('\\', '.');
      }

      try {
        Class<?> clazz = Class.forName(className);
        String templateId = source.templateId().orElseGet(() -> clazz.getSimpleName() + ".vtl");
        SchemaImportResult res = importClass(clazz, templateId, MemberAccessPolicy.standard());
        schemas.putAll(res.schemas());
        allDiagnostics.addAll(res.diagnostics());
      } catch (ClassNotFoundException e) {
        allDiagnostics.add(
            SchemaDiagnostic.error(
                sourceStr,
                1,
                1,
                source.format(),
                CODE_CLASS_NOT_FOUND,
                "Class not found on classpath: " + className,
                "Verify class name and ensure it is available on classpath"));
      } catch (LinkageError | RuntimeException e) {
        allDiagnostics.add(
            SchemaDiagnostic.error(
                sourceStr,
                1,
                1,
                source.format(),
                CODE_REFLECTION_ERROR,
                "Error introspecting Java model: " + e.getMessage(),
                "Check access modifiers and security policies"));
      }
    }

    boolean hasErrors =
        allDiagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    if (request.failOnWarning()
        && allDiagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.WARNING)) {
      hasErrors = true;
    }
    boolean isPartial = hasErrors && !schemas.isEmpty();
    return new SchemaImportResult(schemas, allDiagnostics, hasErrors, isPartial);
  }

  /** Imports canonical schema from a {@link TemplateContract}. */
  public SchemaImportResult importContract(TemplateContract contract) {
    return importContract(contract, MemberAccessPolicy.standard());
  }

  /** Imports canonical schema from a {@link TemplateContract} with custom policy. */
  public SchemaImportResult importContract(TemplateContract contract, MemberAccessPolicy policy) {
    Objects.requireNonNull(contract, "contract must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    String templateId = contract.templateId().value();
    String fingerprint = contract.fingerprint();

    Map<String, ParameterDef> parameters = new TreeMap<>();
    Set<Type> referencedTypes = new HashSet<>();

    List<TemplateParameter> sortedParams = new ArrayList<>(contract.parameters());
    sortedParams.sort(Comparator.comparing(TemplateParameter::name));

    for (TemplateParameter param : sortedParams) {
      TypeRef typeRef = mapTemplateType(param.type(), policy);
      parameters.put(
          param.name(), new ParameterDef(param.name(), typeRef, param.nullable(), false, ""));
      collectReferencedTypes(param.type(), referencedTypes);
    }

    Map<String, TypeDef> types = new TreeMap<>();
    Set<String> visited = new HashSet<>();

    for (Type rt : referencedTypes) {
      if (rt instanceof Class<?> c) {
        expandClass(c, types, visited, policy, 0);
      }
    }

    CanonicalSchema schema =
        new CanonicalSchema(
            templateId,
            SchemaFormat.CONTRACT,
            fingerprint,
            parameters,
            types,
            "TemplateContract(" + templateId + ")");

    return SchemaImportResult.success(Map.of(templateId, schema));
  }

  /** Introspects a Java class (record, bean, enum) as a template contract model. */
  public SchemaImportResult importClass(Class<?> clazz) {
    return importClass(clazz, clazz.getSimpleName() + ".vtl", MemberAccessPolicy.standard());
  }

  /** Introspects a Java class with explicit template ID. */
  public SchemaImportResult importClass(Class<?> clazz, String templateId) {
    return importClass(clazz, templateId, MemberAccessPolicy.standard());
  }

  /** Introspects a Java class with explicit template ID and member policy. */
  public SchemaImportResult importClass(
      Class<?> clazz, String templateId, MemberAccessPolicy policy) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    String resolvedTemplateId =
        (templateId == null || templateId.isBlank()) ? clazz.getSimpleName() + ".vtl" : templateId;

    Map<String, ParameterDef> parameters = new TreeMap<>();
    Map<String, TypeDef> types = new TreeMap<>();
    Set<String> visited = new HashSet<>();

    if (clazz.isEnum()) {
      return importEnum(clazz, resolvedTemplateId);
    }

    if (clazz.isRecord()) {
      return importRecord(clazz, resolvedTemplateId, policy);
    }

    // JavaBean / interface
    return importBean(clazz, resolvedTemplateId, policy);
  }

  /** Imports a Java record where each component becomes a template parameter. */
  public SchemaImportResult importRecord(Class<?> recordClass, String templateId) {
    return importRecord(recordClass, templateId, MemberAccessPolicy.standard());
  }

  /** Imports a Java record with explicit policy. */
  public SchemaImportResult importRecord(
      Class<?> recordClass, String templateId, MemberAccessPolicy policy) {
    Objects.requireNonNull(recordClass, "recordClass must not be null");
    if (!recordClass.isRecord()) {
      throw new IllegalArgumentException("Class is not a record: " + recordClass.getName());
    }

    String resolvedTemplateId =
        (templateId == null || templateId.isBlank())
            ? recordClass.getSimpleName() + ".vtl"
            : templateId;

    Map<String, ParameterDef> parameters = new TreeMap<>();
    Map<String, TypeDef> types = new TreeMap<>();
    Set<String> visited = new HashSet<>();

    RecordComponent[] components = recordClass.getRecordComponents();
    if (components != null) {
      for (RecordComponent comp : components) {
        String name = comp.getName();
        if (!policy.isPropertyPermitted(recordClass, name)) {
          continue;
        }
        Type compType = comp.getGenericType();
        TypeRef typeRef = mapJavaType(compType, policy);
        boolean nullable = comp.getType().isPrimitive() ? false : isNullable(comp, comp.getType());
        parameters.put(name, new ParameterDef(name, typeRef, nullable, false, ""));

        expandType(compType, types, visited, policy, 0);
      }
    }

    // Also register record itself in types
    expandClass(recordClass, types, visited, policy, 0);

    String fingerprint = computeClassFingerprint(recordClass, parameters, types);
    CanonicalSchema schema =
        new CanonicalSchema(
            resolvedTemplateId,
            SchemaFormat.JAVA,
            fingerprint,
            parameters,
            types,
            "JavaRecord(" + recordClass.getName() + ")");

    return SchemaImportResult.success(Map.of(resolvedTemplateId, schema));
  }

  /** Imports a JavaBean where each getter property becomes a template parameter. */
  public SchemaImportResult importBean(Class<?> beanClass, String templateId) {
    return importBean(beanClass, templateId, MemberAccessPolicy.standard());
  }

  /** Imports a JavaBean with explicit policy. */
  public SchemaImportResult importBean(
      Class<?> beanClass, String templateId, MemberAccessPolicy policy) {
    Objects.requireNonNull(beanClass, "beanClass must not be null");

    String resolvedTemplateId =
        (templateId == null || templateId.isBlank())
            ? beanClass.getSimpleName() + ".vtl"
            : templateId;

    Map<String, ParameterDef> parameters = new TreeMap<>();
    Map<String, TypeDef> types = new TreeMap<>();
    Set<String> visited = new HashSet<>();

    Map<String, Method> getters = extractGetters(beanClass, policy);
    for (Map.Entry<String, Method> entry : getters.entrySet()) {
      String propName = entry.getKey();
      Method method = entry.getValue();
      Type retType = method.getGenericReturnType();
      TypeRef typeRef = mapJavaType(retType, policy);
      boolean nullable =
          method.getReturnType().isPrimitive() ? false : isNullable(method, method.getReturnType());
      parameters.put(propName, new ParameterDef(propName, typeRef, nullable, false, ""));

      expandType(retType, types, visited, policy, 0);
    }

    // Register bean itself in types
    expandClass(beanClass, types, visited, policy, 0);

    String fingerprint = computeClassFingerprint(beanClass, parameters, types);
    CanonicalSchema schema =
        new CanonicalSchema(
            resolvedTemplateId,
            SchemaFormat.JAVA,
            fingerprint,
            parameters,
            types,
            "JavaBean(" + beanClass.getName() + ")");

    return SchemaImportResult.success(Map.of(resolvedTemplateId, schema));
  }

  /** Imports an Enum type into canonical schema. */
  public SchemaImportResult importEnum(Class<?> enumClass, String templateId) {
    Objects.requireNonNull(enumClass, "enumClass must not be null");
    if (!enumClass.isEnum()) {
      throw new IllegalArgumentException("Class is not an enum: " + enumClass.getName());
    }

    String resolvedTemplateId =
        (templateId == null || templateId.isBlank())
            ? enumClass.getSimpleName() + ".vtl"
            : templateId;

    List<String> symbols =
        Arrays.stream(enumClass.getEnumConstants()).map(Object::toString).toList();

    TypeDef enumDef =
        new TypeDef(enumClass.getSimpleName(), "enum", Map.of(), symbols, Optional.empty(), "");
    Map<String, TypeDef> types = new TreeMap<>();
    types.put(enumClass.getName(), enumDef);
    types.put(enumClass.getSimpleName(), enumDef);

    String paramName = decapitalize(enumClass.getSimpleName());
    EnumTypeRef enumTypeRef = new EnumTypeRef(enumClass.getName(), symbols);
    ParameterDef param = new ParameterDef(paramName, enumTypeRef, false, false, "");

    Map<String, ParameterDef> parameters = new TreeMap<>();
    parameters.put(paramName, param);

    String fingerprint = computeClassFingerprint(enumClass, parameters, types);
    CanonicalSchema schema =
        new CanonicalSchema(
            resolvedTemplateId,
            SchemaFormat.JAVA,
            fingerprint,
            parameters,
            types,
            "JavaEnum(" + enumClass.getName() + ")");

    return SchemaImportResult.success(Map.of(resolvedTemplateId, schema));
  }

  /**
   * Imports a single class model where the model itself is passed as a named template parameter
   * (e.g. order: SimpleRecord).
   */
  public SchemaImportResult importModel(Class<?> clazz, String parameterName, String templateId) {
    return importModel(clazz, parameterName, templateId, MemberAccessPolicy.standard());
  }

  /** Imports a single class model with explicit policy. */
  public SchemaImportResult importModel(
      Class<?> clazz, String parameterName, String templateId, MemberAccessPolicy policy) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    String pName =
        (parameterName == null || parameterName.isBlank())
            ? decapitalize(clazz.getSimpleName())
            : parameterName;
    String resolvedTemplateId =
        (templateId == null || templateId.isBlank()) ? clazz.getSimpleName() + ".vtl" : templateId;

    Map<String, ParameterDef> parameters = new TreeMap<>();
    Map<String, TypeDef> types = new TreeMap<>();
    Set<String> visited = new HashSet<>();

    TypeRef typeRef = mapJavaType(clazz, policy);
    parameters.put(pName, new ParameterDef(pName, typeRef, false, false, ""));

    expandClass(clazz, types, visited, policy, 0);

    String fingerprint = computeClassFingerprint(clazz, parameters, types);
    CanonicalSchema schema =
        new CanonicalSchema(
            resolvedTemplateId,
            SchemaFormat.JAVA,
            fingerprint,
            parameters,
            types,
            "JavaModel(" + clazz.getName() + ")");

    return SchemaImportResult.success(Map.of(resolvedTemplateId, schema));
  }

  // --- Type Mapping ---

  /** Maps {@link TemplateType} to canonical {@link TypeRef}. */
  public TypeRef mapTemplateType(TemplateType type, MemberAccessPolicy policy) {
    Objects.requireNonNull(type, "type must not be null");
    if (type instanceof TemplateType.PrimitiveType pt) {
      return new PrimitiveTypeRef(pt.primitiveClass().getName());
    }
    if (type instanceof TemplateType.ClassType ct) {
      Class<?> raw = ct.rawClass();
      if (raw == String.class) {
        return new ClassTypeRef("java.lang.String");
      }
      if (raw.isEnum()) {
        List<String> symbols = Arrays.stream(raw.getEnumConstants()).map(Object::toString).toList();
        return new EnumTypeRef(raw.getName(), symbols);
      }
      if (raw.isArray()) {
        return new ArrayTypeRef(mapJavaType(raw.getComponentType(), policy));
      }
      if (Collection.class.isAssignableFrom(raw)) {
        return new ArrayTypeRef(new DynamicTypeRef());
      }
      if (Map.class.isAssignableFrom(raw)) {
        return new MapTypeRef(new ClassTypeRef("java.lang.String"), new DynamicTypeRef());
      }
      return new ClassTypeRef(raw.getName());
    }
    if (type instanceof TemplateType.ParameterizedType pt) {
      Class<?> raw = pt.rawClass();
      if (Collection.class.isAssignableFrom(raw) && !pt.typeArguments().isEmpty()) {
        return new ArrayTypeRef(mapTemplateType(pt.typeArguments().get(0), policy));
      }
      if (Map.class.isAssignableFrom(raw) && pt.typeArguments().size() >= 2) {
        return new MapTypeRef(
            mapTemplateType(pt.typeArguments().get(0), policy),
            mapTemplateType(pt.typeArguments().get(1), policy));
      }
      List<TypeRef> args = new ArrayList<>(pt.typeArguments().size());
      for (TemplateType arg : pt.typeArguments()) {
        args.add(mapTemplateType(arg, policy));
      }
      return new ParameterizedTypeRef(raw.getName(), args);
    }
    if (type instanceof TemplateType.ArrayType at) {
      return new ArrayTypeRef(mapTemplateType(at.componentType(), policy));
    }
    if (type instanceof TemplateType.WildcardType wt) {
      if (wt.lowerBound().isPresent()) {
        return new WildcardTypeRef(
            "super", Optional.of(mapTemplateType(wt.lowerBound().get(), policy)));
      }
      if (wt.upperBound().isPresent()) {
        return new WildcardTypeRef(
            "extends", Optional.of(mapTemplateType(wt.upperBound().get(), policy)));
      }
      return new WildcardTypeRef("unbounded", Optional.empty());
    }
    if (type instanceof TemplateType.NamedType nt) {
      List<TypeRef> args = new ArrayList<>(nt.typeArguments().size());
      for (TemplateType arg : nt.typeArguments()) {
        args.add(mapTemplateType(arg, policy));
      }
      return new NamedTypeRef(nt.name(), args);
    }
    return new DynamicTypeRef();
  }

  /** Maps standard Java {@link Type} to canonical {@link TypeRef}. */
  public TypeRef mapJavaType(Type type, MemberAccessPolicy policy) {
    Objects.requireNonNull(type, "type must not be null");

    if (type instanceof Class<?> c) {
      if (c.isPrimitive()) {
        return new PrimitiveTypeRef(c.getName());
      }
      if (c == String.class) {
        return new ClassTypeRef("java.lang.String");
      }
      if (c.isEnum()) {
        List<String> symbols = Arrays.stream(c.getEnumConstants()).map(Object::toString).toList();
        return new EnumTypeRef(c.getName(), symbols);
      }
      if (c.isArray()) {
        return new ArrayTypeRef(mapJavaType(c.getComponentType(), policy));
      }
      if (Collection.class.isAssignableFrom(c)) {
        return new ArrayTypeRef(new DynamicTypeRef());
      }
      if (Map.class.isAssignableFrom(c)) {
        return new MapTypeRef(new ClassTypeRef("java.lang.String"), new DynamicTypeRef());
      }
      return new ClassTypeRef(c.getName());
    }

    if (type instanceof ParameterizedType pt) {
      Type raw = pt.getRawType();
      if (raw instanceof Class<?> rawClass) {
        Type[] actualArgs = pt.getActualTypeArguments();
        if (Collection.class.isAssignableFrom(rawClass) && actualArgs.length >= 1) {
          return new ArrayTypeRef(mapJavaType(actualArgs[0], policy));
        }
        if (Map.class.isAssignableFrom(rawClass) && actualArgs.length >= 2) {
          return new MapTypeRef(
              mapJavaType(actualArgs[0], policy), mapJavaType(actualArgs[1], policy));
        }
        List<TypeRef> args = new ArrayList<>(actualArgs.length);
        for (Type a : actualArgs) {
          args.add(mapJavaType(a, policy));
        }
        return new ParameterizedTypeRef(rawClass.getName(), args);
      }
    }

    if (type instanceof GenericArrayType gat) {
      return new ArrayTypeRef(mapJavaType(gat.getGenericComponentType(), policy));
    }

    if (type instanceof WildcardType wt) {
      Type[] lower = wt.getLowerBounds();
      if (lower != null && lower.length > 0) {
        return new WildcardTypeRef("super", Optional.of(mapJavaType(lower[0], policy)));
      }
      Type[] upper = wt.getUpperBounds();
      if (upper != null && upper.length > 0 && upper[0] != Object.class) {
        return new WildcardTypeRef("extends", Optional.of(mapJavaType(upper[0], policy)));
      }
      return new WildcardTypeRef("unbounded", Optional.empty());
    }

    if (type instanceof TypeVariable<?> tv) {
      return new NamedTypeRef(tv.getName());
    }

    return new DynamicTypeRef();
  }

  // --- Type Catalog Expansion ---

  private void expandType(
      Type type,
      Map<String, TypeDef> types,
      Set<String> visited,
      MemberAccessPolicy policy,
      int depth) {
    if (depth > MAX_EXPANSION_DEPTH || type == null) {
      return;
    }
    if (type instanceof Class<?> c) {
      expandClass(c, types, visited, policy, depth);
    } else if (type instanceof ParameterizedType pt) {
      for (Type arg : pt.getActualTypeArguments()) {
        expandType(arg, types, visited, policy, depth + 1);
      }
    } else if (type instanceof GenericArrayType gat) {
      expandType(gat.getGenericComponentType(), types, visited, policy, depth + 1);
    } else if (type instanceof WildcardType wt) {
      for (Type ub : wt.getUpperBounds()) {
        expandType(ub, types, visited, policy, depth + 1);
      }
    }
  }

  private void expandClass(
      Class<?> clazz,
      Map<String, TypeDef> types,
      Set<String> visited,
      MemberAccessPolicy policy,
      int depth) {
    if (depth > MAX_EXPANSION_DEPTH || clazz == null) {
      return;
    }
    String fqcn = clazz.getName();
    if (visited.contains(fqcn)) {
      return;
    }
    visited.add(fqcn);

    if (!shouldExpandClass(clazz, policy)) {
      return;
    }

    if (clazz.isEnum()) {
      List<String> symbols = Arrays.stream(clazz.getEnumConstants()).map(Object::toString).toList();
      TypeDef enumDef =
          new TypeDef(clazz.getSimpleName(), "enum", Map.of(), symbols, Optional.empty(), "");
      types.put(clazz.getName(), enumDef);
      types.put(clazz.getSimpleName(), enumDef);
      return;
    }

    Map<String, PropertyDef> properties = new TreeMap<>();
    List<Type> nestedReferenced = new ArrayList<>();

    if (clazz.isRecord()) {
      RecordComponent[] components = clazz.getRecordComponents();
      if (components != null) {
        for (RecordComponent comp : components) {
          String name = comp.getName();
          if (!policy.isPropertyPermitted(clazz, name)) {
            continue;
          }
          if (!policy.isClassPermitted(comp.getType())) {
            continue;
          }
          Method accessor = comp.getAccessor();
          if (accessor != null && !policy.isMethodPermitted(clazz, accessor)) {
            continue;
          }

          Type genericType = comp.getGenericType();
          TypeRef typeRef = mapJavaType(genericType, policy);
          boolean nullable =
              comp.getType().isPrimitive() ? false : isNullable(comp, comp.getType());
          properties.put(name, new PropertyDef(name, typeRef, nullable, false, ""));
          nestedReferenced.add(genericType);
        }
      }
      TypeDef recordDef = new TypeDef(clazz.getSimpleName(), "record", properties);
      types.put(clazz.getName(), recordDef);
      types.put(clazz.getSimpleName(), recordDef);
    } else {
      String kind = clazz.isInterface() ? "interface" : "bean";
      Map<String, Method> getters = extractGetters(clazz, policy);
      for (Map.Entry<String, Method> entry : getters.entrySet()) {
        String propName = entry.getKey();
        Method method = entry.getValue();
        Type retType = method.getGenericReturnType();
        TypeRef typeRef = mapJavaType(retType, policy);
        boolean nullable =
            method.getReturnType().isPrimitive()
                ? false
                : isNullable(method, method.getReturnType());
        properties.put(propName, new PropertyDef(propName, typeRef, nullable, false, ""));
        nestedReferenced.add(retType);
      }
      TypeDef beanDef = new TypeDef(clazz.getSimpleName(), kind, properties);
      types.put(clazz.getName(), beanDef);
      types.put(clazz.getSimpleName(), beanDef);
    }

    for (Type nested : nestedReferenced) {
      expandType(nested, types, visited, policy, depth + 1);
    }
  }

  private static boolean shouldExpandClass(Class<?> clazz, MemberAccessPolicy policy) {
    if (clazz == null || clazz.isPrimitive() || clazz.isArray()) {
      return false;
    }
    if (clazz == Object.class
        || clazz == void.class
        || clazz == Void.class
        || clazz == String.class) {
      return false;
    }
    String name = clazz.getName();
    if (name.startsWith("java.")
        || name.startsWith("javax.")
        || name.startsWith("jakarta.")
        || name.startsWith("sun.")
        || name.startsWith("com.sun.")
        || name.startsWith("jdk.")) {
      return false;
    }
    return policy.isClassPermitted(clazz);
  }

  private static Map<String, Method> extractGetters(Class<?> clazz, MemberAccessPolicy policy) {
    Method[] methods = clazz.getMethods();
    List<Method> candidates = new ArrayList<>();
    for (Method m : methods) {
      if (m.getParameterCount() != 0
          || Modifier.isStatic(m.getModifiers())
          || m.isBridge()
          || m.isSynthetic()
          || m.getDeclaringClass() == Object.class
          || m.getReturnType() == void.class
          || !policy.isClassPermitted(m.getDeclaringClass())
          || !policy.isMethodPermitted(clazz, m)
          || !policy.isClassPermitted(m.getReturnType())) {
        continue;
      }
      String name = m.getName();
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is")
              && name.length() > 2
              && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class))) {
        candidates.add(m);
      }
    }

    candidates.sort(
        Comparator.comparing(Method::getName).thenComparing(m -> m.getReturnType().getName()));

    Map<String, Method> propertyToMethod = new LinkedHashMap<>();
    for (Method m : candidates) {
      String propName = extractPropertyName(m);
      if (!policy.isPropertyPermitted(clazz, propName)) {
        continue;
      }
      Method existing = propertyToMethod.get(propName);
      if (existing == null) {
        propertyToMethod.put(propName, m);
      } else {
        boolean existingIsGet = existing.getName().startsWith("get");
        boolean currentIsGet = m.getName().startsWith("get");
        if (!existingIsGet && currentIsGet) {
          propertyToMethod.put(propName, m);
        } else if (existing.getReturnType().isAssignableFrom(m.getReturnType())) {
          propertyToMethod.put(propName, m);
        }
      }
    }
    return propertyToMethod;
  }

  private static String extractPropertyName(Method method) {
    String name = method.getName();
    if (name.startsWith("get") && name.length() > 3) {
      return decapitalize(name.substring(3));
    }
    if (name.startsWith("is") && name.length() > 2) {
      return decapitalize(name.substring(2));
    }
    return name;
  }

  private static boolean isNullable(AnnotatedElement element, Class<?> rawType) {
    if (rawType.isPrimitive()) {
      return false;
    }
    if (element != null) {
      try {
        for (Annotation ann : element.getAnnotations()) {
          String simple = ann.annotationType().getSimpleName();
          if ("NotNull".equalsIgnoreCase(simple) || "NonNull".equalsIgnoreCase(simple)) {
            return false;
          }
          if ("Nullable".equalsIgnoreCase(simple)) {
            return true;
          }
        }
      } catch (SecurityException | LinkageError | TypeNotPresentException ignored) {
      }
    }
    return true;
  }

  private static void collectReferencedTypes(TemplateType type, Set<Type> collected) {
    if (type instanceof TemplateType.ClassType ct) {
      collected.add(ct.rawClass());
    } else if (type instanceof TemplateType.ParameterizedType pt) {
      collected.add(pt.rawClass());
      for (TemplateType arg : pt.typeArguments()) {
        collectReferencedTypes(arg, collected);
      }
    } else if (type instanceof TemplateType.ArrayType at) {
      collectReferencedTypes(at.componentType(), collected);
    } else if (type instanceof TemplateType.WildcardType wt) {
      wt.upperBound().ifPresent(ub -> collectReferencedTypes(ub, collected));
      wt.lowerBound().ifPresent(lb -> collectReferencedTypes(lb, collected));
    } else if (type instanceof TemplateType.NamedType nt) {
      Class<?> resolved = nt.rawClass();
      if (resolved != Object.class) {
        collected.add(resolved);
      }
      for (TemplateType arg : nt.typeArguments()) {
        collectReferencedTypes(arg, collected);
      }
    }
  }

  private static String decapitalize(String str) {
    if (str == null || str.isEmpty()) {
      return "";
    }
    return Character.toLowerCase(str.charAt(0)) + str.substring(1);
  }

  private static String computeClassFingerprint(
      Class<?> clazz, Map<String, ParameterDef> params, Map<String, TypeDef> types) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      StringBuilder sb = new StringBuilder(clazz.getName());
      for (ParameterDef p : params.values()) {
        sb.append(";p:")
            .append(p.name())
            .append(":")
            .append(p.type().displayName())
            .append(":")
            .append(p.nullable());
      }
      for (Map.Entry<String, TypeDef> e : types.entrySet()) {
        sb.append(";t:").append(e.getKey()).append(":").append(e.getValue().kind());
        for (PropertyDef prop : e.getValue().properties().values()) {
          sb.append(";pr:")
              .append(prop.name())
              .append(":")
              .append(prop.type().displayName())
              .append(":")
              .append(prop.nullable());
        }
      }
      byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder("java-model:v1:");
      for (byte b : hash) {
        out.append(String.format("%02x", b));
      }
      return out.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm missing", e);
    }
  }
}
