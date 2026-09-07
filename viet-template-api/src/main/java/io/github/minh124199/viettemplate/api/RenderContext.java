package io.github.minh124199.viettemplate.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Read-only evaluation context supplied during template rendering. */
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

  static RenderContext of(Map<String, Object> map) {
    Objects.requireNonNull(map, "map must not be null");
    return new MapBackedRenderContext(map);
  }

  static RenderContext of(String key, Object value) {
    Objects.requireNonNull(key, "key must not be null");
    return new MapBackedRenderContext(Map.of(key, value));
  }

  static Builder builder() {
    return new Builder();
  }

  /** Fluent builder for constructing immutable {@link RenderContext} instances. */
  final class Builder {
    private final Map<String, Object> map = new HashMap<>();

    public Builder put(String key, Object value) {
      Objects.requireNonNull(key, "key must not be null");
      map.put(key, value);
      return this;
    }

    public Builder putAll(Map<String, ?> entries) {
      if (entries != null) {
        map.putAll(entries);
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

final class MapBackedRenderContext implements RenderContext {
  private final Map<String, Object> map;

  MapBackedRenderContext(Map<String, Object> map) {
    this.map = Map.copyOf(map);
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
