package io.github.minh124199.viettemplate.api;

/** Policy governing how variable name collisions are resolved between models and contributors. */
public enum ContextCollisionPolicy {
  /** User-supplied model values take precedence over contributor-supplied values. */
  MODEL_WINS,

  /** Contributor-supplied values overwrite user-supplied model values. */
  CONTRIBUTOR_WINS,

  /** A {@link ContextCollisionException} is thrown whenever a collision occurs. */
  ERROR_ON_COLLISION
}
