package io.github.minh124199.viettemplate.api;

import java.util.Objects;

/** Configuration settings for two-stage layout rendering. */
public record LayoutConfiguration(
    LayoutResolver resolver,
    String screenContentKey,
    LayoutContextScope contextScope,
    int maxLayoutDepth) {

  public static final String DEFAULT_SCREEN_CONTENT_KEY = "screen_content";
  public static final int DEFAULT_MAX_LAYOUT_DEPTH = 5;

  public LayoutConfiguration {
    Objects.requireNonNull(resolver, "resolver must not be null");
    Objects.requireNonNull(screenContentKey, "screenContentKey must not be null");
    Objects.requireNonNull(contextScope, "contextScope must not be null");
    if (maxLayoutDepth <= 0) {
      throw new IllegalArgumentException("maxLayoutDepth must be positive: " + maxLayoutDepth);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private LayoutResolver resolver = LayoutResolver.none();
    private String screenContentKey = DEFAULT_SCREEN_CONTENT_KEY;
    private LayoutContextScope contextScope = LayoutContextScope.SHARED_COMPATIBILITY_SCOPE;
    private int maxLayoutDepth = DEFAULT_MAX_LAYOUT_DEPTH;

    public Builder resolver(LayoutResolver resolver) {
      this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
      return this;
    }

    public Builder defaultLayout(TemplateId layoutId) {
      Objects.requireNonNull(layoutId, "layoutId must not be null");
      this.resolver = LayoutResolver.fromContextVariable("layout", layoutId);
      return this;
    }

    public Builder screenContentKey(String key) {
      this.screenContentKey = Objects.requireNonNull(key, "screenContentKey must not be null");
      return this;
    }

    public Builder contextScope(LayoutContextScope scope) {
      this.contextScope = Objects.requireNonNull(scope, "contextScope must not be null");
      return this;
    }

    public Builder maxLayoutDepth(int depth) {
      this.maxLayoutDepth = depth;
      return this;
    }

    public LayoutConfiguration build() {
      return new LayoutConfiguration(resolver, screenContentKey, contextScope, maxLayoutDepth);
    }
  }
}
