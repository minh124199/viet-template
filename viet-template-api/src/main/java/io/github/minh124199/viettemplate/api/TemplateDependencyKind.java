package io.github.minh124199.viettemplate.api;

/** Kind of dependency relationship between templates. */
public enum TemplateDependencyKind {
  /** Statically knowable {@code #parse} directive target. */
  STATIC_PARSE,

  /** Statically knowable {@code #include} directive target. */
  STATIC_INCLUDE,

  /** Configured global macro library dependency. */
  GLOBAL_MACRO_LIBRARY,

  /** Layout template wrapping a screen template. */
  LAYOUT
}
