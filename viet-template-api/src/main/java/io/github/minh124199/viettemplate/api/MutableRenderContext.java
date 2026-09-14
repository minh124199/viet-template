package io.github.minh124199.viettemplate.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable evaluation context supporting dynamic variable updates during template rendering.
 *
 * <p><strong>Thread Confinement:</strong> {@link MutableRenderContext} instances are render-scoped
 * and designed for single-threaded or thread-confined evaluation within an active render request.
 * While internal storage uses concurrent structures for defensive memory consistency, sharing a
 * mutable context across concurrent, distinct template renderings is not supported.
 *
 * <p><strong>Protected Keys Enforcement:</strong> Any keys designated as protected during
 * construction cannot be overwritten via {@link #put(String, Object)} or removed via {@link
 * #remove(String)}. Any violation immediately throws a {@link TemplateSecurityException} with
 * diagnostic code {@code SECURITY:PROTECTED_VARIABLE}.
 *
 * <p><strong>Defined Null Semantics:</strong> Storing {@code null} via {@code put(key, null)}
 * preserves the key as a defined-null variable ({@link #contains(String)} returns {@code true}, and
 * {@link #get(String)} returns {@code null}). To completely erase or undefined a variable, invoke
 * {@link #remove(String)}.
 */
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

  private static final Object NULL_SENTINEL = new Object();

  private final Map<String, Object> variables = new ConcurrentHashMap<>();
  private final Set<String> protectedKeys;

  DefaultMutableRenderContext() {
    this(Map.of(), Set.of());
  }

  DefaultMutableRenderContext(Map<String, Object> initial, Set<String> protectedKeys) {
    this.protectedKeys = protectedKeys != null ? Set.copyOf(protectedKeys) : Set.of();
    if (initial != null) {
      for (Map.Entry<String, Object> e : initial.entrySet()) {
        if (e.getKey() != null) {
          variables.put(e.getKey(), e.getValue() != null ? e.getValue() : NULL_SENTINEL);
        }
      }
    }
  }

  @Override
  public Object get(String name) {
    Object val = variables.get(name);
    return val == NULL_SENTINEL ? null : val;
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
    variables.put(key, value != null ? value : NULL_SENTINEL);
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
    Map<String, Object> snapshot = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      snapshot.put(entry.getKey(), entry.getValue() == NULL_SENTINEL ? null : entry.getValue());
    }
    return Collections.unmodifiableMap(snapshot);
  }
}
