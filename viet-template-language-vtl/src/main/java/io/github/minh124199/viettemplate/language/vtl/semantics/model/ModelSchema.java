package io.github.minh124199.viettemplate.language.vtl.semantics.model;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.model.ModelParameter;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable schema defining the declared root model parameters for a template.
 *
 * <p>Supports reflection-based introspection of Java records and interfaces annotated or intended
 * as template models.
 */
public final class ModelSchema {

  private static final ModelSchema EMPTY = new ModelSchema(Map.of());

  private final Map<String, ModelParameter> parameters;

  private ModelSchema(Map<String, ModelParameter> parameters) {
    this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
  }

  public static ModelSchema empty() {
    return EMPTY;
  }

  public static ModelSchema of(Map<String, VType> params) {
    Objects.requireNonNull(params, "params must not be null");
    Map<String, ModelParameter> map = new LinkedHashMap<>();
    for (Map.Entry<String, VType> entry : params.entrySet()) {
      map.put(entry.getKey(), ModelParameter.of(entry.getKey(), entry.getValue()));
    }
    return new ModelSchema(map);
  }

  public static ModelSchema of(ModelParameter... params) {
    Objects.requireNonNull(params, "params must not be null");
    Map<String, ModelParameter> map = new LinkedHashMap<>();
    for (ModelParameter param : params) {
      map.put(param.name(), param);
    }
    return new ModelSchema(map);
  }

  public static ModelSchema fromContract(TemplateContract contract) {
    Objects.requireNonNull(contract, "contract must not be null");
    Map<String, ModelParameter> map = new LinkedHashMap<>();
    for (TemplateParameter param : contract.parameters()) {
      Nullability nullability = param.nullable() ? Nullability.NULLABLE : Nullability.NON_NULL;
      VType type = convertTemplateType(param.type(), nullability);
      map.put(param.name(), ModelParameter.of(param.name(), type));
    }
    return new ModelSchema(map);
  }

  private static VType convertTemplateType(
      io.github.minh124199.viettemplate.api.TemplateType templateType, Nullability nullability) {
    if (templateType
        instanceof io.github.minh124199.viettemplate.api.TemplateType.PrimitiveType pt) {
      return new VType.PrimitiveType(
          io.github.minh124199.viettemplate.language.vtl.semantics.type.PrimitiveKind.fromClass(
              pt.primitiveClass()));
    }
    if (templateType instanceof io.github.minh124199.viettemplate.api.TemplateType.ArrayType at) {
      return new VType.ArrayType(
          convertTemplateType(at.componentType(), Nullability.UNKNOWN), nullability);
    }
    if (templateType
        instanceof io.github.minh124199.viettemplate.api.TemplateType.ParameterizedType pt) {
      List<VType> args = new ArrayList<>();
      for (io.github.minh124199.viettemplate.api.TemplateType arg : pt.typeArguments()) {
        args.add(convertTemplateType(arg, Nullability.UNKNOWN));
      }
      return VType.ClassType.of(pt.rawClass(), args, nullability);
    }
    if (templateType instanceof io.github.minh124199.viettemplate.api.TemplateType.ClassType ct) {
      return VType.ClassType.of(ct.rawClass(), nullability);
    }
    if (templateType
        instanceof io.github.minh124199.viettemplate.api.TemplateType.WildcardType wt) {
      if (wt.upperBound().isPresent()) {
        return convertTemplateType(wt.upperBound().get(), nullability);
      }
      return VTypes.DYNAMIC;
    }
    return VTypes.fromJavaClass(templateType.rawClass(), nullability);
  }

  public static ModelSchema fromRecord(Class<?> recordClass) {
    Objects.requireNonNull(recordClass, "recordClass must not be null");
    if (!recordClass.isRecord()) {
      throw new IllegalArgumentException("Class is not a record: " + recordClass.getName());
    }
    Map<String, ModelParameter> map = new LinkedHashMap<>();
    for (RecordComponent component : recordClass.getRecordComponents()) {
      VType type = VTypes.fromJavaType(component.getGenericType(), Nullability.NULLABLE);
      map.put(component.getName(), ModelParameter.of(component.getName(), type));
    }
    return new ModelSchema(map);
  }

  public static ModelSchema fromInterface(Class<?> interfaceClass) {
    Objects.requireNonNull(interfaceClass, "interfaceClass must not be null");
    if (!interfaceClass.isInterface()) {
      throw new IllegalArgumentException("Class is not an interface: " + interfaceClass.getName());
    }
    List<Method> methods = new ArrayList<>(List.of(interfaceClass.getMethods()));
    methods.sort(
        Comparator.comparing(Method::getName).thenComparing(m -> m.getReturnType().getName()));
    Map<String, ModelParameter> map = new LinkedHashMap<>();
    for (Method method : methods) {
      if (method.getParameterCount() != 0
          || Modifier.isStatic(method.getModifiers())
          || method.getDeclaringClass() == Object.class) {
        continue;
      }
      String propertyName = extractPropertyName(method);
      VType type = VTypes.fromJavaType(method.getGenericReturnType(), Nullability.NULLABLE);
      map.put(propertyName, ModelParameter.of(propertyName, type));
    }
    return new ModelSchema(map);
  }

  public static ModelSchema fromClass(Class<?> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    if (clazz.isRecord()) {
      return fromRecord(clazz);
    }
    if (clazz.isInterface()) {
      return fromInterface(clazz);
    }
    // For standard classes, inspect public zero-arg getters
    List<Method> methods = new ArrayList<>(List.of(clazz.getMethods()));
    methods.sort(
        Comparator.comparing(Method::getName).thenComparing(m -> m.getReturnType().getName()));
    Map<String, ModelParameter> map = new LinkedHashMap<>();
    for (Method method : methods) {
      if (method.getParameterCount() != 0
          || Modifier.isStatic(method.getModifiers())
          || method.getDeclaringClass() == Object.class) {
        continue;
      }
      String name = method.getName();
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is")
              && name.length() > 2
              && (method.getReturnType() == boolean.class
                  || method.getReturnType() == Boolean.class))) {
        String propName = extractPropertyName(method);
        VType type = VTypes.fromJavaType(method.getGenericReturnType(), Nullability.NULLABLE);
        map.put(propName, ModelParameter.of(propName, type));
      }
    }
    return new ModelSchema(map);
  }

  private static String extractPropertyName(Method method) {
    String name = method.getName();
    if (name.startsWith("get") && name.length() > 3) {
      return Character.toLowerCase(name.charAt(3)) + name.substring(4);
    }
    if (name.startsWith("is")
        && name.length() > 2
        && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
      return Character.toLowerCase(name.charAt(2)) + name.substring(3);
    }
    return name;
  }

  public Optional<ModelParameter> find(String name) {
    return Optional.ofNullable(parameters.get(name));
  }

  public boolean contains(String name) {
    return parameters.containsKey(name);
  }

  public Map<String, ModelParameter> parameters() {
    return Collections.unmodifiableMap(parameters);
  }

  public Set<String> parameterNames() {
    return parameters.keySet();
  }

  public boolean isEmpty() {
    return parameters.isEmpty();
  }

  public int size() {
    return parameters.size();
  }

  public String fingerprint() {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      for (Map.Entry<String, ModelParameter> entry : parameters.entrySet()) {
        md.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
        md.update((byte) ':');
        md.update(entry.getValue().type().toString().getBytes(StandardCharsets.UTF_8));
        md.update((byte) '\n');
      }
      byte[] digest = md.digest();
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModelSchema that)) return false;
    return parameters.equals(that.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parameters);
  }

  static Builder builder() {
    return new Builder();
  }

  static final class Builder {
    private final Map<String, ModelParameter> params = new LinkedHashMap<>();

    Builder() {}

    public Builder add(String name, VType type) {
      params.put(name, ModelParameter.of(name, type));
      return this;
    }

    public Builder add(ModelParameter parameter) {
      Objects.requireNonNull(parameter, "parameter must not be null");
      params.put(parameter.name(), parameter);
      return this;
    }

    public Builder addAll(ModelSchema schema) {
      Objects.requireNonNull(schema, "schema must not be null");
      params.putAll(schema.parameters());
      return this;
    }

    public ModelSchema build() {
      return new ModelSchema(params);
    }
  }
}
