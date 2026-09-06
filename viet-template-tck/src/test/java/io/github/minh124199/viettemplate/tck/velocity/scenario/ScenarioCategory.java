package io.github.minh124199.viettemplate.tck.velocity.scenario;

/** Stable functional categories for grouping compatibility scenarios. */
public enum ScenarioCategory {
  LEXICAL("Lexical & Comments"),
  REFERENCE("References & Property Access"),
  ESCAPING("Backslash & Reference Escaping"),
  TRUTHINESS("Truthiness & Duck Typing"),
  EXPRESSION("Expressions & Operations"),
  ASSIGNMENT("#set Directive & Scoping"),
  CONDITIONAL("#if / #elseif / #else Conditionals"),
  FOREACH("#foreach Iteration & Metadata"),
  MACRO("#macro Directives & Defaults"),
  BLOCK_MACRO("Block Macros (#@macro) & Body"),
  DEFINE("#define Directives & Blocks"),
  RESOURCE("#include & #parse Directives"),
  EVALUATE("#evaluate Dynamic Execution"),
  STRICT_MODE("Strict Reference Checking"),
  WHITESPACE("Space Gobbling & Whitespace"),
  INTROSPECTION("Bean & Property Introspection"),
  METHOD_OVERLOAD("Method Overload Resolution"),
  SECURITY("Security Policy & Sandbox"),
  ERROR("Error Handling & Diagnostics"),
  LITERAL("Literal Collections & Ranges");

  private final String description;

  ScenarioCategory(String description) {
    this.description = description;
  }

  public String description() {
    return description;
  }
}
