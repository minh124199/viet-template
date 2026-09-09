package io.github.minh124199.viettemplate.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Mutable evaluation context supporting dynamic variable updates during template rendering. */
public interface MutableRenderContext extends RenderContext {

  void put(String key, Object value);

  default void putAll(Map<String, ?> entries) {
    if (entries != null) {
      entries.forEach(this::put);
    }
  }

  void remove(String key);

  Map<String, Object> asMap();

  static MutableRenderContext of() {
    return new DefaultMutableRenderContext();
  }

  static MutableRenderContext of(Map<String, Object> initial) {
    return new DefaultMutableRenderContext(initial, Set.of());
  }

  static MutableRenderContext of(Map<String, Object> initial, Set<String> protectedKeys) {
    return new DefaultMutableRenderContext(initial, protectedKeys);
  }
}

final class DefaultMutableRenderContext implements MutableRenderContext {

  private final Map<String, Object> variables = new ConcurrentHashMap<>();
  private final Set<String> protectedKeys;

  DefaultMutableRenderContext() {
    this(Map.of(), Set.of());
  }

  DefaultMutableRenderContext(Map<String, Object> initial, Set<String> protectedKeys) {
    this.protectedKeys = protectedKeys != null ? Set.copyOf(protectedKeys) : Set.of();
    if (initial != null) {
      for (Map.Entry<String, Object> e : initial.entrySet()) {
        if (e.getKey() != null && e.getValue() != null) {
          variables.put(e.getKey(), e.getValue());
        }
      }
    }
  }

  @Override
  public Object get(String name) {
    return variables.get(name);
  }

  @Override
  public boolean contains(String name) {
    return variables.containsKey(name);
  }

  @Override
  public Set<String> keys() {
    return variables.keySet();
  }

  @Override
  public void put(String key, Object value) {
    Objects.requireNonNull(key, "key must not be null");
    if (protectedKeys.contains(key)) {
      throw new TemplateSecurityException(
          "Cannot overwrite protected context variable: " + key,
          TemplateId.of("unknown"),
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("SECURITY", "PROTECTED_VARIABLE"));
    }
    if (value == null) {
      variables.remove(key);
    } else {
      variables.put(key, value);
    }
  }

  @Override
  public void remove(String key) {
    Objects.requireNonNull(key, "key must not be null");
    if (protectedKeys.contains(key)) {
      throw new TemplateSecurityException(
          "Cannot remove protected context variable: " + key,
          TemplateId.of("unknown"),
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("SECURITY", "PROTECTED_VARIABLE"));
    }
    variables.remove(key);
  }

  @Override
  public Map<String, Object> asMap() {
    return Collections.unmodifiableMap(new HashMap<>(variables));
  }
}
