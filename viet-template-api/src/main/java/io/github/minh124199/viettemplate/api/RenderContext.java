package io.github.minh124199.viettemplate.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only evaluation context supplied during template rendering.
 *
 * <p>Variable keys must not be {@code null}. Context entries may have {@code null} values; a {@code
 * null} value represents a defined-null variable ({@code DEFINED_NULL}), distinguishing an
 * explicitly defined {@code null} from an absent / undefined variable.
 */
public interface RenderContext {

  Object get(String name);

  boolean contains(String name);

  default Set<String> keys() {
    return Set.of();
  }

  default Optional<Object> find(String name) {
    return contains(name) ? Optional.ofNullable(get(name)) : Optional.empty();
  }

  static RenderContext empty() {
    return EmptyRenderContext.INSTANCE;
  }

  /**
   * Creates an immutable {@link RenderContext} backed by the provided map.
   *
   * @param map map of variable names to values; keys must not be null, null values represent
   *     defined-null variables
   * @return read-only render context
   * @throws NullPointerException if map is null or any key in map is null
   */
  static RenderContext of(Map<String, Object> map) {
    Objects.requireNonNull(map, "map must not be null");
    return new MapBackedRenderContext(map);
  }

  /**
   * Creates an immutable single-variable {@link RenderContext}.
   *
   * @param key variable name (must not be null)
   * @param value variable value (may be null, representing a defined-null variable)
   * @return read-only render context
   * @throws NullPointerException if key is null
   */
  static RenderContext of(String key, Object value) {
    Objects.requireNonNull(key, "key must not be null");
    return new SingleVariableRenderContext(key, value);
  }

  /**
   * Creates an immutable two-variable {@link RenderContext} without map allocation.
   *
   * @param k1 first variable name
   * @param v1 first variable value
   * @param k2 second variable name
   * @param v2 second variable value
   * @return read-only render context
   */
  static RenderContext of(String k1, Object v1, String k2, Object v2) {
    Objects.requireNonNull(k1, "k1 must not be null");
    Objects.requireNonNull(k2, "k2 must not be null");
    return new ArrayBackedRenderContext(new String[] {k1, k2}, new Object[] {v1, v2});
  }

  /**
   * Creates an immutable three-variable {@link RenderContext} without map allocation.
   *
   * @param k1 first variable name
   * @param v1 first variable value
   * @param k2 second variable name
   * @param v2 second variable value
   * @param k3 third variable name
   * @param v3 third variable value
   * @return read-only render context
   */
  static RenderContext of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
    Objects.requireNonNull(k1, "k1 must not be null");
    Objects.requireNonNull(k2, "k2 must not be null");
    Objects.requireNonNull(k3, "k3 must not be null");
    return new ArrayBackedRenderContext(new String[] {k1, k2, k3}, new Object[] {v1, v2, v3});
  }

  /**
   * Creates an immutable four-variable {@link RenderContext} without map allocation.
   *
   * @param k1 first variable name
   * @param v1 first variable value
   * @param k2 second variable name
   * @param v2 second variable value
   * @param k3 third variable name
   * @param v3 third variable value
   * @param k4 fourth variable name
   * @param v4 fourth variable value
   * @return read-only render context
   */
  static RenderContext of(
      String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
    Objects.requireNonNull(k1, "k1 must not be null");
    Objects.requireNonNull(k2, "k2 must not be null");
    Objects.requireNonNull(k3, "k3 must not be null");
    Objects.requireNonNull(k4, "k4 must not be null");
    return new ArrayBackedRenderContext(
        new String[] {k1, k2, k3, k4}, new Object[] {v1, v2, v3, v4});
  }

  /**
   * Creates an immutable {@link RenderContext} backed by parallel arrays of keys and values.
   *
   * @param keys array of variable names (must not be null, elements must not be null)
   * @param values array of variable values (must not be null, length must match keys)
   * @return read-only render context
   */
  static RenderContext of(String[] keys, Object[] values) {
    Objects.requireNonNull(keys, "keys must not be null");
    Objects.requireNonNull(values, "values must not be null");
    if (keys.length != values.length) {
      throw new IllegalArgumentException(
          "Keys length (" + keys.length + ") does not match values length (" + values.length + ")");
    }
    for (String key : keys) {
      Objects.requireNonNull(key, "variable key must not be null");
    }
    return new ArrayBackedRenderContext(keys.clone(), values.clone());
  }

  static Builder builder() {
    return new Builder();
  }

  /** Fluent builder for constructing immutable {@link RenderContext} instances. */
  final class Builder {
    private final Map<String, Object> map = new LinkedHashMap<>();

    public Builder put(String key, Object value) {
      Objects.requireNonNull(key, "key must not be null");
      map.put(key, value);
      return this;
    }

    public Builder putAll(Map<String, ?> entries) {
      if (entries != null) {
        entries.forEach(this::put);
      }
      return this;
    }

    public RenderContext build() {
      return new MapBackedRenderContext(map);
    }
  }
}

final class EmptyRenderContext implements RenderContext {
  static final EmptyRenderContext INSTANCE = new EmptyRenderContext();

  private EmptyRenderContext() {}

  @Override
  public Object get(String name) {
    return null;
  }

  @Override
  public boolean contains(String name) {
    return false;
  }
}

final class SingleVariableRenderContext implements RenderContext {
  private final String key;
  private final Object value;

  SingleVariableRenderContext(String key, Object value) {
    this.key = Objects.requireNonNull(key, "variable key must not be null");
    this.value = value;
  }

  @Override
  public Object get(String name) {
    return key.equals(name) ? value : null;
  }

  @Override
  public boolean contains(String name) {
    return key.equals(name);
  }

  @Override
  public Set<String> keys() {
    return Set.of(key);
  }
}

final class MapBackedRenderContext implements RenderContext {
  private final Map<String, Object> map;

  MapBackedRenderContext(Map<String, Object> map) {
    Objects.requireNonNull(map, "map must not be null");
    Map<String, Object> copy = new LinkedHashMap<>(map);
    for (String key : copy.keySet()) {
      Objects.requireNonNull(key, "variable key must not be null");
    }
    this.map = Collections.unmodifiableMap(copy);
  }

  @Override
  public Object get(String name) {
    return map.get(name);
  }

  @Override
  public boolean contains(String name) {
    return map.containsKey(name);
  }

  @Override
  public Set<String> keys() {
    return map.keySet();
  }
}

final class ArrayBackedRenderContext implements RenderContext {
  private final String[] keys;
  private final Object[] values;

  ArrayBackedRenderContext(String[] keys, Object[] values) {
    this.keys = keys;
    this.values = values;
  }

  @Override
  public Object get(String name) {
    if (name == null) {
      return null;
    }
    for (int i = 0; i < keys.length; i++) {
      if (keys[i].equals(name)) {
        return values[i];
      }
    }
    return null;
  }

  @Override
  public boolean contains(String name) {
    if (name == null) {
      return false;
    }
    for (String key : keys) {
      if (key.equals(name)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<String> keys() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(keys)));
  }
}
