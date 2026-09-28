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
import java.util.Comparator;
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
  private static final String FINGERPRINT_PREFIX = "vt-contract:v1:";

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
    this.fingerprint = computeCanonicalFingerprint(this.templateId, this.parameters);
  }

  /**
   * Computes a deterministic canonical fingerprint for this contract.
   *
   * <p>Parameters are canonically sorted by name so that two contracts with identical parameter
   * sets declared in different insertion orders yield the exact same fingerprint.
   */
  private static String computeCanonicalFingerprint(
      TemplateId templateId, List<TemplateParameter> parameters) {
    List<TemplateParameter> sorted = new ArrayList<>(parameters);
    sorted.sort(Comparator.comparing(TemplateParameter::name));

    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(templateId.value().getBytes(StandardCharsets.UTF_8));
      md.update((byte) '\n');
      for (TemplateParameter param : sorted) {
        md.update(param.fingerprintFragment().getBytes(StandardCharsets.UTF_8));
        md.update((byte) '\n');
      }
      byte[] digest = md.digest();
      StringBuilder sb = new StringBuilder(FINGERPRINT_PREFIX.length() + digest.length * 2);
      sb.append(FINGERPRINT_PREFIX);
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

  /**
   * Creates a contract by introspecting public record components as individual template parameters.
   *
   * @param templateId template identifier
   * @param recordClass the record class to inspect
   * @return contract where each record component maps to a template parameter
   */
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

  /**
   * Creates a contract by introspecting JavaBean properties or record components of {@code clazz}
   * as individual template parameters ($prop1, $prop2).
   */
  public static TemplateContract fromProperties(TemplateId templateId, Class<?> beanOrRecordClass) {
    Objects.requireNonNull(beanOrRecordClass, "beanOrRecordClass must not be null");
    if (beanOrRecordClass.isRecord()) {
      return fromRecord(templateId, beanOrRecordClass);
    }
    MemberAccessPolicy policy = MemberAccessPolicy.standard();
    List<Method> candidateMethods = new ArrayList<>();
    for (Method method : beanOrRecordClass.getMethods()) {
      if (method.getParameterCount() != 0
          || Modifier.isStatic(method.getModifiers())
          || method.isBridge()
          || method.isSynthetic()
          || method.getDeclaringClass() == Object.class
          || method.getReturnType() == void.class
          || !policy.isMethodPermitted(beanOrRecordClass, method)) {
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

    // Sort candidate methods deterministically
    candidateMethods.sort(
        Comparator.comparing(Method::getName).thenComparing(m -> m.getReturnType().getName()));

    Map<String, Method> propertyToMethod = new LinkedHashMap<>();
    for (Method method : candidateMethods) {
      String propName = extractPropertyName(method);
      Method existing = propertyToMethod.get(propName);
      if (existing == null) {
        propertyToMethod.put(propName, method);
      } else {
        // If conflicting getter vs is-getter, prefer 'get'
        boolean existingIsGet = existing.getName().startsWith("get");
        boolean currentIsGet = method.getName().startsWith("get");
        if (!existingIsGet && currentIsGet) {
          propertyToMethod.put(propName, method);
        } else if (existing.getReturnType().isAssignableFrom(method.getReturnType())) {
          // Covariant specialization: more specific return type wins
          propertyToMethod.put(propName, method);
        }
      }
    }

    List<TemplateParameter> params = new ArrayList<>();
    for (Map.Entry<String, Method> entry : propertyToMethod.entrySet()) {
      params.add(
          TemplateParameter.fromGenericType(
              entry.getKey(), entry.getValue().getGenericReturnType()));
    }
    params.sort(Comparator.comparing(TemplateParameter::name));
    return new TemplateContract(templateId, params);
  }

  /**
   * Introspects public properties/record components of {@code clazz} as individual template
   * parameters ($prop1, $prop2).
   */
  public static TemplateContract fromClass(TemplateId templateId, Class<?> clazz) {
    return fromProperties(templateId, clazz);
  }

  /**
   * Creates a contract declaring a single root model parameter (e.g. {@code $user}).
   *
   * @param templateId template identifier
   * @param parameterName the root parameter name (e.g. "user")
   * @param clazz the expected root model class
   * @return contract with a single root parameter
   */
  public static TemplateContract fromRoot(
      TemplateId templateId, String parameterName, Class<?> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    return of(templateId, TemplateParameter.of(parameterName, clazz));
  }

  /** Creates a contract declaring a single root model parameter with a {@link TemplateType}. */
  public static TemplateContract fromRoot(
      TemplateId templateId, String parameterName, TemplateType type, boolean nullable) {
    Objects.requireNonNull(type, "type must not be null");
    return of(templateId, TemplateParameter.of(parameterName, type, nullable));
  }

  /** Standard JavaBean property name extraction following decapitalization rules. */
  public static String extractPropertyName(Method method) {
    String name = method.getName();
    String rawProp;
    if (name.startsWith("get") && name.length() > 3) {
      rawProp = name.substring(3);
    } else if (name.startsWith("is") && name.length() > 2) {
      rawProp = name.substring(2);
    } else {
      return name;
    }

    // Standard JavaBeans decapitalization: if first two chars are uppercase (e.g. "URL"), keep it
    if (rawProp.length() > 1
        && Character.isUpperCase(rawProp.charAt(0))
        && Character.isUpperCase(rawProp.charAt(1))) {
      return rawProp;
    }
    return Character.toLowerCase(rawProp.charAt(0)) + rawProp.substring(1);
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

    public Builder parameter(String name, TemplateType type) {
      return parameter(TemplateParameter.of(name, type));
    }

    public Builder parameter(String name, TemplateType type, boolean nullable) {
      return parameter(TemplateParameter.of(name, type, nullable));
    }

    public TemplateContract build() {
      return new TemplateContract(templateId, params);
    }
  }
}
