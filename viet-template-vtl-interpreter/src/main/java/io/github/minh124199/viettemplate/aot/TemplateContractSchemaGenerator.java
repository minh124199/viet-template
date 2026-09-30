package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.api.TemplateType;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * Package-private canonical tooling schema generator for Viet Template contracts.
 *
 * <p>Produces deterministic {@code *.vt-schema.json} artifacts adhering to schema specification
 * {@code format = viet-template-contract-schema/1} and {@code schemaVersion = 1}.
 */
final class TemplateContractSchemaGenerator {

  public static final String SCHEMA_URL =
      "https://viet-template.github.io/schemas/contract-v1.json";
  public static final String FORMAT = "viet-template-contract-schema/1";
  public static final int SCHEMA_VERSION = 1;
  public static final String SCHEMA_FILE_EXTENSION = ".vt-schema.json";
  private static final int MAX_EXPANSION_DEPTH = 32;

  private TemplateContractSchemaGenerator() {}

  // --- Domain Model ---

  sealed interface TypeRef
      permits PrimitiveTypeRef,
          ClassTypeRef,
          ParameterizedTypeRef,
          ArrayTypeRef,
          WildcardTypeRef,
          NamedTypeRef {}

  record PrimitiveTypeRef(String name) implements TypeRef {}

  record ClassTypeRef(String name) implements TypeRef {}

  record ParameterizedTypeRef(String rawType, List<TypeRef> arguments) implements TypeRef {
    public ParameterizedTypeRef {
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
  }

  record ArrayTypeRef(TypeRef componentType) implements TypeRef {}

  record WildcardTypeRef(String boundKind, Optional<TypeRef> bound) implements TypeRef {}

  record NamedTypeRef(String name, List<TypeRef> arguments) implements TypeRef {
    public NamedTypeRef {
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
  }

  record PropertyDef(String name, TypeRef type, boolean nullable) {}

  record TypeDef(String kind, List<PropertyDef> properties) {
    public TypeDef {
      properties = properties == null ? List.of() : List.copyOf(properties);
    }
  }

  record ParameterDef(String name, TypeRef type, boolean nullable, boolean optional) {}

  record SchemaEnvelope(
      String schema,
      String format,
      int schemaVersion,
      String templateId,
      String contractFingerprint,
      List<ParameterDef> parameters,
      Map<String, TypeDef> types) {
    public SchemaEnvelope {
      parameters = parameters == null ? List.of() : List.copyOf(parameters);
      types = types == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(types));
    }
  }

  // --- Schema Extraction ---

  public static SchemaEnvelope extractSchema(TemplateContract contract) {
    return extractSchema(contract, MemberAccessPolicy.standard());
  }

  public static SchemaEnvelope extractSchema(TemplateContract contract, MemberAccessPolicy policy) {
    Objects.requireNonNull(contract, "contract must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    String templateId = contract.templateId().value();
    String fingerprint = contract.fingerprint();

    List<ParameterDef> params = new ArrayList<>();
    Set<Class<?>> rootReferencedClasses = new HashSet<>();

    List<TemplateParameter> sortedParams = new ArrayList<>(contract.parameters());
    sortedParams.sort(Comparator.comparing(TemplateParameter::name));

    for (TemplateParameter param : sortedParams) {
      TypeRef typeRef = mapType(param.type());
      params.add(new ParameterDef(param.name(), typeRef, param.nullable(), false));
      collectReferencedClasses(param.type(), rootReferencedClasses);
    }

    Map<String, TypeDef> types = new TreeMap<>();
    Set<String> visited = new HashSet<>();

    List<Class<?>> sortedRoots = new ArrayList<>(rootReferencedClasses);
    sortedRoots.sort(Comparator.comparing(Class::getName));

    for (Class<?> rootClass : sortedRoots) {
      expandType(rootClass, types, visited, policy, 0);
    }

    return new SchemaEnvelope(
        SCHEMA_URL, FORMAT, SCHEMA_VERSION, templateId, fingerprint, params, types);
  }

  public static String generateJson(TemplateContract contract) {
    return serialize(extractSchema(contract));
  }

  public static String generateJson(TemplateContract contract, MemberAccessPolicy policy) {
    return serialize(extractSchema(contract, policy));
  }

  public static Path deriveSchemaFilePath(Path baseOutputDir, String templateIdValue) {
    Objects.requireNonNull(baseOutputDir, "baseOutputDir must not be null");
    Objects.requireNonNull(templateIdValue, "templateIdValue must not be null");

    String normalized = templateIdValue.replace('\\', '/').trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }

    int dot = normalized.lastIndexOf('.');
    String baseName = (dot > 0) ? normalized.substring(0, dot) : normalized;
    String schemaRelativePath = baseName + SCHEMA_FILE_EXTENSION;
    return baseOutputDir.resolve(schemaRelativePath).normalize();
  }

  public static Path generateSchemaFile(TemplateContract contract, Path baseOutputDir)
      throws IOException {
    return generateSchemaFile(contract, baseOutputDir, MemberAccessPolicy.standard());
  }

  public static Path generateSchemaFile(
      TemplateContract contract, Path baseOutputDir, MemberAccessPolicy policy) throws IOException {
    Path targetFile = deriveSchemaFilePath(baseOutputDir, contract.templateId().value());
    Path parent = targetFile.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    String json = generateJson(contract, policy);
    Files.writeString(targetFile, json, StandardCharsets.UTF_8);
    return targetFile;
  }

  // --- Type Mapping ---

  static TypeRef mapType(TemplateType type) {
    Objects.requireNonNull(type, "type must not be null");
    if (type instanceof TemplateType.PrimitiveType pt) {
      return new PrimitiveTypeRef(pt.primitiveClass().getName());
    }
    if (type instanceof TemplateType.ClassType ct) {
      return new ClassTypeRef(ct.rawClass().getName());
    }
    if (type instanceof TemplateType.ParameterizedType pt) {
      List<TypeRef> args = new ArrayList<>(pt.typeArguments().size());
      for (TemplateType arg : pt.typeArguments()) {
        args.add(mapType(arg));
      }
      return new ParameterizedTypeRef(pt.rawClass().getName(), args);
    }
    if (type instanceof TemplateType.ArrayType at) {
      return new ArrayTypeRef(mapType(at.componentType()));
    }
    if (type instanceof TemplateType.WildcardType wt) {
      if (wt.lowerBound().isPresent()) {
        return new WildcardTypeRef("super", Optional.of(mapType(wt.lowerBound().get())));
      }
      if (wt.upperBound().isPresent()) {
        return new WildcardTypeRef("extends", Optional.of(mapType(wt.upperBound().get())));
      }
      return new WildcardTypeRef("unbounded", Optional.empty());
    }
    if (type instanceof TemplateType.NamedType nt) {
      List<TypeRef> args = new ArrayList<>(nt.typeArguments().size());
      for (TemplateType arg : nt.typeArguments()) {
        args.add(mapType(arg));
      }
      return new NamedTypeRef(nt.name(), args);
    }
    throw new IllegalArgumentException(
        "Unsupported TemplateType variant: " + type.getClass().getName());
  }

  // --- Bounded Type Discovery ---

  private static void collectReferencedClasses(TemplateType type, Set<Class<?>> classes) {
    if (type instanceof TemplateType.ClassType ct) {
      classes.add(ct.rawClass());
    } else if (type instanceof TemplateType.ParameterizedType pt) {
      classes.add(pt.rawClass());
      for (TemplateType arg : pt.typeArguments()) {
        collectReferencedClasses(arg, classes);
      }
    } else if (type instanceof TemplateType.ArrayType at) {
      collectReferencedClasses(at.componentType(), classes);
    } else if (type instanceof TemplateType.WildcardType wt) {
      wt.upperBound().ifPresent(ub -> collectReferencedClasses(ub, classes));
      wt.lowerBound().ifPresent(lb -> collectReferencedClasses(lb, classes));
    } else if (type instanceof TemplateType.NamedType nt) {
      Class<?> resolved = nt.rawClass();
      if (resolved != Object.class) {
        classes.add(resolved);
      }
      for (TemplateType arg : nt.typeArguments()) {
        collectReferencedClasses(arg, classes);
      }
    }
  }

  private static boolean shouldExpandClass(Class<?> clazz, MemberAccessPolicy policy) {
    if (clazz == null || clazz.isPrimitive() || clazz.isArray()) {
      return false;
    }
    if (clazz == Object.class || clazz == void.class || clazz == Void.class) {
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

  private static void expandType(
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

    Set<Class<?>> nestedReferencedClasses = new HashSet<>();
    List<PropertyDef> properties = new ArrayList<>();
    String kind;

    if (clazz.isRecord()) {
      kind = "record";
      RecordComponent[] components = clazz.getRecordComponents();
      if (components != null) {
        List<RecordComponent> sorted = new ArrayList<>(Arrays.asList(components));
        sorted.sort(Comparator.comparing(RecordComponent::getName));

        for (RecordComponent comp : sorted) {
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
          TemplateType tt = TemplateType.fromGenericType(genericType);
          TypeRef typeRef = mapType(tt);
          boolean nullable =
              comp.getType().isPrimitive() ? false : isNullable(comp, comp.getType());
          properties.add(new PropertyDef(name, typeRef, nullable));
          collectReferencedClasses(tt, nestedReferencedClasses);
        }
      }
    } else {
      kind = clazz.isInterface() ? "interface" : "bean";
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
        String propName = TemplateContract.extractPropertyName(m);
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

      for (Map.Entry<String, Method> entry : propertyToMethod.entrySet()) {
        String propName = entry.getKey();
        Method method = entry.getValue();
        Type genericType = method.getGenericReturnType();
        TemplateType tt = TemplateType.fromGenericType(genericType);
        TypeRef typeRef = mapType(tt);
        boolean nullable =
            method.getReturnType().isPrimitive()
                ? false
                : isNullable(method, method.getReturnType());
        properties.add(new PropertyDef(propName, typeRef, nullable));
        collectReferencedClasses(tt, nestedReferencedClasses);
      }
    }

    properties.sort(Comparator.comparing(PropertyDef::name));
    types.put(fqcn, new TypeDef(kind, properties));

    List<Class<?>> sortedNested = new ArrayList<>(nestedReferencedClasses);
    sortedNested.sort(Comparator.comparing(Class::getName));

    for (Class<?> nested : sortedNested) {
      expandType(nested, types, visited, policy, depth + 1);
    }
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
      } catch (Throwable ignored) {
      }
    }
    return true;
  }

  // --- Deterministic JSON Serializer ---

  public static String serialize(SchemaEnvelope envelope) {
    StringBuilder sb = new StringBuilder(1024);
    sb.append("{\n");
    sb.append("  \"$schema\": \"").append(escapeJson(envelope.schema())).append("\",\n");
    sb.append("  \"format\": \"").append(escapeJson(envelope.format())).append("\",\n");
    sb.append("  \"schemaVersion\": ").append(envelope.schemaVersion()).append(",\n");
    sb.append("  \"templateId\": \"").append(escapeJson(envelope.templateId())).append("\",\n");
    sb.append("  \"contractFingerprint\": \"")
        .append(escapeJson(envelope.contractFingerprint()))
        .append("\",\n");

    // Parameters
    sb.append("  \"parameters\": ");
    if (envelope.parameters().isEmpty()) {
      sb.append("[],\n");
    } else {
      sb.append("[\n");
      for (int i = 0; i < envelope.parameters().size(); i++) {
        ParameterDef param = envelope.parameters().get(i);
        sb.append("    {\n");
        sb.append("      \"name\": \"").append(escapeJson(param.name())).append("\",\n");
        sb.append("      \"type\": ");
        serializeTypeRef(param.type(), sb, 3);
        sb.append(",\n");
        sb.append("      \"nullable\": ").append(param.nullable()).append(",\n");
        sb.append("      \"optional\": ").append(param.optional()).append("\n");
        sb.append("    }");
        if (i < envelope.parameters().size() - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  ],\n");
    }

    // Types
    sb.append("  \"types\": ");
    if (envelope.types().isEmpty()) {
      sb.append("{}\n");
    } else {
      sb.append("{\n");
      List<Map.Entry<String, TypeDef>> typeEntries = new ArrayList<>(envelope.types().entrySet());
      for (int i = 0; i < typeEntries.size(); i++) {
        Map.Entry<String, TypeDef> entry = typeEntries.get(i);
        sb.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
        TypeDef def = entry.getValue();
        sb.append("      \"kind\": \"").append(escapeJson(def.kind())).append("\",\n");
        sb.append("      \"properties\": ");
        if (def.properties().isEmpty()) {
          sb.append("[]\n");
        } else {
          sb.append("[\n");
          for (int j = 0; j < def.properties().size(); j++) {
            PropertyDef prop = def.properties().get(j);
            sb.append("        {\n");
            sb.append("          \"name\": \"").append(escapeJson(prop.name())).append("\",\n");
            sb.append("          \"type\": ");
            serializeTypeRef(prop.type(), sb, 5);
            sb.append(",\n");
            sb.append("          \"nullable\": ").append(prop.nullable()).append("\n");
            sb.append("        }");
            if (j < def.properties().size() - 1) {
              sb.append(",");
            }
            sb.append("\n");
          }
          sb.append("      ]\n");
        }
        sb.append("    }");
        if (i < typeEntries.size() - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
      sb.append("  }\n");
    }

    sb.append("}\n");
    return sb.toString();
  }

  private static void serializeTypeRef(TypeRef type, StringBuilder sb, int indentLevel) {
    String indent = "  ".repeat(indentLevel);
    String innerIndent = "  ".repeat(indentLevel + 1);

    if (type instanceof PrimitiveTypeRef pt) {
      sb.append("{\n");
      sb.append(innerIndent).append("\"kind\": \"primitive\",\n");
      sb.append(innerIndent).append("\"name\": \"").append(escapeJson(pt.name())).append("\"\n");
      sb.append(indent).append("}");
    } else if (type instanceof ClassTypeRef ct) {
      sb.append("{\n");
      sb.append(innerIndent).append("\"kind\": \"class\",\n");
      sb.append(innerIndent).append("\"name\": \"").append(escapeJson(ct.name())).append("\"\n");
      sb.append(indent).append("}");
    } else if (type instanceof ParameterizedTypeRef pt) {
      sb.append("{\n");
      sb.append(innerIndent).append("\"kind\": \"parameterized\",\n");
      sb.append(innerIndent)
          .append("\"rawType\": \"")
          .append(escapeJson(pt.rawType()))
          .append("\",\n");
      sb.append(innerIndent).append("\"arguments\": ");
      if (pt.arguments().isEmpty()) {
        sb.append("[]\n");
      } else {
        sb.append("[\n");
        for (int i = 0; i < pt.arguments().size(); i++) {
          sb.append(innerIndent).append("  ");
          serializeTypeRef(pt.arguments().get(i), sb, indentLevel + 2);
          if (i < pt.arguments().size() - 1) {
            sb.append(",");
          }
          sb.append("\n");
        }
        sb.append(innerIndent).append("]\n");
      }
      sb.append(indent).append("}");
    } else if (type instanceof ArrayTypeRef at) {
      sb.append("{\n");
      sb.append(innerIndent).append("\"kind\": \"array\",\n");
      sb.append(innerIndent).append("\"componentType\": ");
      serializeTypeRef(at.componentType(), sb, indentLevel + 1);
      sb.append("\n");
      sb.append(indent).append("}");
    } else if (type instanceof WildcardTypeRef wt) {
      sb.append("{\n");
      sb.append(innerIndent).append("\"kind\": \"wildcard\",\n");
      sb.append(innerIndent)
          .append("\"boundKind\": \"")
          .append(escapeJson(wt.boundKind()))
          .append("\"");
      if (wt.bound().isPresent()) {
        sb.append(",\n");
        sb.append(innerIndent).append("\"bound\": ");
        serializeTypeRef(wt.bound().get(), sb, indentLevel + 1);
        sb.append("\n");
      } else {
        sb.append("\n");
      }
      sb.append(indent).append("}");
    } else if (type instanceof NamedTypeRef nt) {
      sb.append("{\n");
      sb.append(innerIndent).append("\"kind\": \"named\",\n");
      sb.append(innerIndent).append("\"name\": \"").append(escapeJson(nt.name())).append("\",\n");
      sb.append(innerIndent).append("\"arguments\": ");
      if (nt.arguments().isEmpty()) {
        sb.append("[]\n");
      } else {
        sb.append("[\n");
        for (int i = 0; i < nt.arguments().size(); i++) {
          sb.append(innerIndent).append("  ");
          serializeTypeRef(nt.arguments().get(i), sb, indentLevel + 2);
          if (i < nt.arguments().size() - 1) {
            sb.append(",");
          }
          sb.append("\n");
        }
        sb.append(innerIndent).append("]\n");
      }
      sb.append(indent).append("}");
    } else {
      throw new IllegalArgumentException("Unknown TypeRef variant: " + type.getClass().getName());
    }
  }

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  static String escapeJson(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c <= 0x1F) {
            sb.append("\\u00").append(HEX_DIGITS[(c >> 4) & 0x0F]).append(HEX_DIGITS[c & 0x0F]);
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
