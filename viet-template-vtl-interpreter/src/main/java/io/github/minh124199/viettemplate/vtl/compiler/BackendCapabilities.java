package io.github.minh124199.viettemplate.vtl.compiler;

/** Set of capabilities supported by a template backend. */
public record BackendCapabilities(
    boolean supportsStaticAot,
    boolean supportsDynamicSites,
    boolean supportsPrimitiveSpecialization,
    boolean supportsMethodSplitting,
    boolean supportsEvaluateDirective,
    boolean supportsDynamicMacros) {

  public static final BackendCapabilities AOT_DEFAULT =
      new BackendCapabilities(true, true, true, true, false, false);

  public static final BackendCapabilities INTERPRETER_DEFAULT =
      new BackendCapabilities(true, true, true, false, true, true);
}
