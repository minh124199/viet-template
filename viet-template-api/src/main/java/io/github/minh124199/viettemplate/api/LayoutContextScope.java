package io.github.minh124199.viettemplate.api;

/** Defines how context changes from the screen template propagate to the layout template. */
public enum LayoutContextScope {
  /**
   * Screen template mutations (e.g. {@code #set($page_title = "...")}) are visible to the layout.
   *
   * <p>Standard mode for historical Apache Velocity and Spring MVC {@code VelocityLayoutView}.
   */
  SHARED_COMPATIBILITY_SCOPE,

  /** Screen template mutations are strictly isolated and discarded before rendering the layout. */
  ISOLATED_SCREEN_SCOPE
}
