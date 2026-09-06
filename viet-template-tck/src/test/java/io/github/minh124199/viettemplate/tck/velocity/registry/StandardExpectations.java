package io.github.minh124199.viettemplate.tck.velocity.registry;

import io.github.minh124199.viettemplate.tck.velocity.result.CompatibilityClassification;

/** Standard registry defining registered architectural deviations from Velocity 2.4.1. */
public final class StandardExpectations {

  private StandardExpectations() {}

  public static ExpectationRegistry create() {
    ExpectationRegistry.Builder builder = ExpectationRegistry.builder();

    // Security expectations: Viet Template safe profile intentionally blocks reflection and Class
    // access
    builder.expect(
        "security.denial.get-class-method",
        CompatibilityClassification.EXPECTED_DIFFERENCE,
        "Viet Template safe profile intentionally blocks getClass() to prevent reflection escapes.",
        "0.1.0",
        "ADR-0004");

    builder.expect(
        "security.denial.class-property",
        CompatibilityClassification.EXPECTED_DIFFERENCE,
        "Viet Template safe profile intentionally blocks .class property access to prevent"
            + " reflection escapes.",
        "0.1.0",
        "ADR-0004");

    builder.expect(
        "arithmetic.divide-by-zero",
        CompatibilityClassification.EXPECTED_DIFFERENCE,
        "Viet Template deliberately fails fast with a TemplateRenderException on division by zero"
            + " rather than silently producing null.",
        "0.1.0",
        "ADR-0005");

    builder.expect(
        "foreach.control.stop-method",
        CompatibilityClassification.VIET_EXTENSION,
        "Viet Template exposes $foreach.stop() for programmatic loop termination in addition to"
            + " standard #break.",
        "0.1.0",
        "ADR-0006");

    builder.expect(
        "set.null-rhs.legacy-preserved.undefined",
        CompatibilityClassification.EXPECTED_DIFFERENCE,
        "Viet Template provides legacy Velocity 1.x setNullAllowed=false compatibility mode where"
            + " undefined RHS preserves previous value, whereas Velocity 2.x removed this option.",
        "0.1.0",
        "ADR-0007");

    builder.expect(
        "set.null-rhs.legacy-preserved.method-null",
        CompatibilityClassification.EXPECTED_DIFFERENCE,
        "Viet Template provides legacy Velocity 1.x setNullAllowed=false compatibility mode where"
            + " null-returning method RHS preserves previous value, whereas Velocity 2.x removed"
            + " this option.",
        "0.1.0",
        "ADR-0007");

    return builder.build();
  }
}
