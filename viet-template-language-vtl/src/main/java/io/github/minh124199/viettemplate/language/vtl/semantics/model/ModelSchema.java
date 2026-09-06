package io.github.minh124199.viettemplate.language.vtl.semantics.model;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    this.parameters = Map.copyOf(parameters);
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
    Map<String, ModelParameter> map = new LinkedHashMap<>();
    for (Method method : interfaceClass.getMethods()) {
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
    Map<String, ModelParameter> map = new LinkedHashMap<>();
    for (Method method : clazz.getMethods()) {
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

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final Map<String, ModelParameter> params = new LinkedHashMap<>();

    private Builder() {}

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
