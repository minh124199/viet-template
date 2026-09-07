package io.github.minh124199.viettemplate.api;

/** Identifies the origin of a variable registered in the evaluation context. */
public enum ValueOrigin {
  /** Value supplied directly in the caller's model. */
  MODEL,

  /** Value contributed by a {@link RenderContextContributor}. */
  CONTRIBUTOR,

  /** Reserved value managed by the engine runtime (e.g. loop metadata, captured layout content). */
  ENGINE_INTERNAL
}
