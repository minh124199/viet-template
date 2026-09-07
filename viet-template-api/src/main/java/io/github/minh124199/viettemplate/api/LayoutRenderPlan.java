package io.github.minh124199.viettemplate.api;

import java.io.IOException;
import java.util.Optional;

/** Executable plan for rendering a screen template within an optional layout. */
public interface LayoutRenderPlan {

  /** Identifies the screen template being rendered. */
  TemplateId screenId();

  /** Identifies the layout template wrapping the screen, or empty if rendering standalone. */
  Optional<TemplateId> layoutId();

  /** Returns the active layout configuration governing this plan. */
  LayoutConfiguration configuration();

  /**
   * Executes the render plan, rendering screen and layout to the target {@link TemplateOutput}.
   *
   * @param context evaluation context
   * @param output destination template output
   * @throws IOException on I/O write failures
   */
  void render(RenderContext context, TemplateOutput output) throws IOException;
}
