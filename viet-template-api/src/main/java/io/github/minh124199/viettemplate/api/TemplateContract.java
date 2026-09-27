package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Public, durable template contract defining the expected typed parameters for a template.
 *
 * <p>A {@code TemplateContract} provides compile-time specialization and call-site safety while
 * preserving 100% dynamic Velocity compatibility when no contract is declared.
 */
@SuppressWarnings("serial")
public final class TemplateContract implements Serializable {

  private static final long serialVersionUID = 1L;

  private final TemplateId templateId;
  private final List<TemplateParameter> parameters;
  private final Map<String, TemplateParameter> parameterMap;
  private final String fingerprint;

  private TemplateContract(TemplateId templateId, List<TemplateParameter> parameters) {
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(parameters, "parameters must not be null");

    Map<String, TemplateParameter> map = new LinkedHashMap<>();
    for (TemplateParameter param : parameters) {
      if (map.containsKey(param.name())) {
        throw new IllegalArgumentException(
            "Duplicate parameter name in template contract: '" + param.name() + "'");
      }
      map.put(param.name(), param);
    }
    this.parameterMap = Collections.unmodifiableMap(map);
    this.parameters = List.copyOf(map.values());
    this.fingerprint = computeFingerprint(this.templateId, this.parameters);
  }

  private static String computeFingerprint(
      TemplateId templateId, List<TemplateParameter> parameters) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(templateId.value().getBytes(StandardCharsets.UTF_8));
      md.update((byte) '\n');
      for (TemplateParameter param : parameters) {
        md.update(param.fingerprintFragment().getBytes(StandardCharsets.UTF_8));
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

  public static Builder builder(TemplateId templateId) {
    return new Builder(templateId);
  }

  public static Builder builder(String templateId) {
    return new Builder(TemplateId.of(templateId));
  }

  public static TemplateContract of(TemplateId templateId, TemplateParameter... parameters) {
    Objects.requireNonNull(parameters, "parameters must not be null");
    return new TemplateContract(templateId, List.of(parameters));
  }

  public static TemplateContract of(TemplateId templateId, List<TemplateParameter> parameters) {
    return new TemplateContract(templateId, parameters);
  }

  public static TemplateContract empty(TemplateId templateId) {
    return new TemplateContract(templateId, List.of());
  }

  public static TemplateContract fromRecord(TemplateId templateId, Class<?> recordClass) {
    Objects.requireNonNull(recordClass, "recordClass must not be null");
    if (!recordClass.isRecord()) {
      throw new IllegalArgumentException("Class is not a record: " + recordClass.getName());
    }
    List<TemplateParameter> params = new ArrayList<>();
    for (RecordComponent component : recordClass.getRecordComponents()) {
      params.add(
          TemplateParameter.fromGenericType(component.getName(), component.getGenericType()));
    }
    return new TemplateContract(templateId, params);
  }

  public static TemplateContract fromClass(TemplateId templateId, Class<?> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    if (clazz.isRecord()) {
      return fromRecord(templateId, clazz);
    }
    List<Method> candidateMethods = new ArrayList<>();
    for (Method method : clazz.getMethods()) {
      if (method.getParameterCount() != 0
          || Modifier.isStatic(method.getModifiers())
          || method.isBridge()
          || method.isSynthetic()
          || method.getDeclaringClass() == Object.class
          || method.getReturnType() == void.class) {
        continue;
      }
      String name = method.getName();
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is")
              && name.length() > 2
              && (method.getReturnType() == boolean.class
                  || method.getReturnType() == Boolean.class))) {
        candidateMethods.add(method);
      }
    }
    candidateMethods.sort(
        java.util.Comparator.comparing(Method::getName)
            .thenComparing(m -> m.getReturnType().getName()));

    Map<String, TemplateParameter> paramMap = new LinkedHashMap<>();
    for (Method method : candidateMethods) {
      String propName = extractPropertyName(method);
      TemplateParameter newParam =
          TemplateParameter.fromGenericType(propName, method.getGenericReturnType());
      TemplateParameter existing = paramMap.get(propName);
      if (existing == null) {
        paramMap.put(propName, newParam);
      } else if (existing.rawType().isAssignableFrom(newParam.rawType())) {
        paramMap.put(propName, newParam);
      }
    }
    List<TemplateParameter> params = new ArrayList<>(paramMap.values());
    params.sort(java.util.Comparator.comparing(TemplateParameter::name));
    return new TemplateContract(templateId, params);
  }

  private static String extractPropertyName(Method method) {
    String name = method.getName();
    if (name.startsWith("get") && name.length() > 3) {
      return Character.toLowerCase(name.charAt(3)) + name.substring(4);
    }
    if (name.startsWith("is") && name.length() > 2) {
      return Character.toLowerCase(name.charAt(2)) + name.substring(3);
    }
    return name;
  }

  public TemplateId templateId() {
    return templateId;
  }

  public List<TemplateParameter> parameters() {
    return parameters;
  }

  public Optional<TemplateParameter> parameter(String name) {
    return Optional.ofNullable(parameterMap.get(name));
  }

  public boolean hasParameter(String name) {
    return parameterMap.containsKey(name);
  }

  public String fingerprint() {
    return fingerprint;
  }

  public boolean isEmpty() {
    return parameters.isEmpty();
  }

  public int size() {
    return parameters.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TemplateContract that)) return false;
    return templateId.equals(that.templateId) && parameters.equals(that.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(templateId, parameters);
  }

  @Override
  public String toString() {
    return "TemplateContract["
        + "templateId="
        + templateId
        + ", parameters="
        + parameters
        + ", fingerprint="
        + fingerprint
        + "]";
  }

  /** Builder for constructing {@link TemplateContract} instances. */
  public static final class Builder {
    private final TemplateId templateId;
    private final List<TemplateParameter> params = new ArrayList<>();

    public Builder(TemplateId templateId) {
      this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    }

    public Builder parameter(TemplateParameter param) {
      params.add(Objects.requireNonNull(param, "param must not be null"));
      return this;
    }

    public Builder parameter(String name, Class<?> rawType) {
      return parameter(TemplateParameter.of(name, rawType));
    }

    public Builder parameter(String name, Class<?> rawType, boolean nullable) {
      return parameter(TemplateParameter.of(name, rawType, nullable));
    }

    public Builder parameter(String name, Class<?> rawType, List<Class<?>> typeArguments) {
      return parameter(TemplateParameter.of(name, rawType, typeArguments));
    }

    public Builder parameter(
        String name, Class<?> rawType, List<Class<?>> typeArguments, boolean nullable) {
      return parameter(TemplateParameter.of(name, rawType, typeArguments, nullable));
    }

    public TemplateContract build() {
      return new TemplateContract(templateId, params);
    }
  }
}
