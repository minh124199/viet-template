package io.github.minh124199.viettemplate.vtl.compiler;

/** Status of template compilation reported by execution backends. */
public enum CompilationStatus {
  /** Template compiled successfully to static ahead-of-time bytecode with direct access. */
  AOT_OK,

  /** Template compiled successfully to bytecode with dynamic call sites linked via dynamic linker. */
  AOT_OK_WITH_DYNAMIC_SITES,

  /** Compilation requires interpreter fallback due to dynamic #evaluate directive. */
  INTERPRETER_REQUIRED_EVALUATE,

  /** Compilation requires interpreter fallback due to runtime-discovered macro. */
  INTERPRETER_REQUIRED_RUNTIME_MACRO,

  /** Compilation denied by security policy. */
  DENIED_SECURITY,

  /** Template contains an unsupported language feature. */
  UNSUPPORTED_LANGUAGE_FEATURE;

  public boolean isSuccess() {
    return this == AOT_OK || this == AOT_OK_WITH_DYNAMIC_SITES;
  }
}
