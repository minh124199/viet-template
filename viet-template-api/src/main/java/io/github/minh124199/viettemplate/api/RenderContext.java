package io.github.minh124199.viettemplate.api;

import java.util.Optional;

/** Read-only evaluation context supplied during template rendering. */
public interface RenderContext {

  Object get(String name);

  boolean contains(String name);

  default Optional<Object> find(String name) {
    return contains(name) ? Optional.ofNullable(get(name)) : Optional.empty();
  }

  static RenderContext empty() {
    return EmptyRenderContext.INSTANCE;
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
