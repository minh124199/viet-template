package io.github.minh124199.viettemplate.tck.velocity.engine;

/** Identifies an engine participating in differential testing. */
public enum EngineIdentity {
  APACHE_VELOCITY_2_4_1("Apache Velocity 2.4.1"),
  VIET_TEMPLATE_REFERENCE("Viet Template Reference Interpreter");

  private final String displayName;

  EngineIdentity(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
