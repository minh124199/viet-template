package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.RenderContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable thread-safe implementation of {@link RenderContext} backed by a Map. */
public final class MapRenderContext implements RenderContext {

  private final Map<String, Object> variables;

  private MapRenderContext(Map<String, Object> map) {
    this.variables = Collections.unmodifiableMap(new HashMap<>(map));
  }

  public static MapRenderContext of(Map<String, Object> map) {
    Objects.requireNonNull(map, "map must not be null");
    return new MapRenderContext(map);
  }

  public static MapRenderContext of(String key, Object value) {
    Objects.requireNonNull(key, "key must not be null");
    return new MapRenderContext(Map.of(key, value));
  }

  public static Builder builder() {
    return new Builder();
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

  public Map<String, Object> asMap() {
    return variables;
  }

  public static final class Builder {
    private final Map<String, Object> map = new HashMap<>();

    private Builder() {}

    public Builder put(String key, Object value) {
      Objects.requireNonNull(key, "key must not be null");
      map.put(key, value);
      return this;
    }

    public Builder putAll(Map<String, ?> entries) {
      Objects.requireNonNull(entries, "entries must not be null");
      map.putAll(entries);
      return this;
    }

    public MapRenderContext build() {
      return new MapRenderContext(map);
    }
  }
}
