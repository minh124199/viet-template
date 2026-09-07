package io.github.minh124199.viettemplate.api;

import java.util.Objects;
import java.util.Optional;

/** Strategy for determining the layout template to apply to a screen template. */
@FunctionalInterface
public interface LayoutResolver {

  /**
   * Resolves the layout template for the specified screen template and context.
   *
   * @param screenTemplateId screen template identifier
   * @param context current evaluation context
   * @return optional layout template identifier, or empty if no layout should be applied
   */
  Optional<TemplateId> resolveLayout(TemplateId screenTemplateId, RenderContext context);

  /** Returns a resolver that applies no layout. */
  static LayoutResolver none() {
    return (screen, ctx) -> Optional.empty();
  }

  /** Returns a resolver that unconditionally applies the specified layout. */
  static LayoutResolver constant(TemplateId layoutId) {
    Objects.requireNonNull(layoutId, "layoutId must not be null");
    return (screen, ctx) -> Optional.of(layoutId);
  }

  /**
   * Returns a resolver that checks a context variable for a per-screen layout override, falling
   * back to a default layout if not set, and bypassing layout if set to "none" or {@code false}.
   */
  static LayoutResolver fromContextVariable(String variableName, TemplateId defaultLayoutId) {
    Objects.requireNonNull(variableName, "variableName must not be null");
    return (screen, ctx) -> {
      if (ctx != null && ctx.contains(variableName)) {
        Object val = ctx.get(variableName);
        if (val == null
            || Boolean.FALSE.equals(val)
            || "none".equalsIgnoreCase(String.valueOf(val))) {
          return Optional.empty();
        }
        return Optional.of(TemplateId.normalize(String.valueOf(val)));
      }
      return Optional.ofNullable(defaultLayoutId);
    };
  }
}
