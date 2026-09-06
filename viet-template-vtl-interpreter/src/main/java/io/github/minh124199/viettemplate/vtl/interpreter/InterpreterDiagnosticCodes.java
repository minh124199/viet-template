package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.DiagnosticCode;

/** Standard diagnostic codes produced during template interpretation. */
public final class InterpreterDiagnosticCodes {

  public static final DiagnosticCode SECURITY_VIOLATION =
      DiagnosticCode.of("SECURITY", "VIOLATION");
  public static final DiagnosticCode SYNTAX_ERROR = DiagnosticCode.of("INTERPRETER", "ERROR");
  public static final DiagnosticCode VARIABLE_UNDEFINED =
      DiagnosticCode.of("INTERPRETER", "VARIABLE_UNDEFINED");
  public static final DiagnosticCode LIMIT_EXCEEDED = DiagnosticCode.of("LIMIT", "LIMIT_EXCEEDED");
  public static final DiagnosticCode RESOURCE_NOT_FOUND =
      DiagnosticCode.of("RESOURCE", "NOT_FOUND");
  public static final DiagnosticCode INVALID_PROPERTY =
      DiagnosticCode.of("INTERPRETER", "INVALID_PROPERTY");
  public static final DiagnosticCode INVALID_METHOD =
      DiagnosticCode.of("INTERPRETER", "INVALID_METHOD");

  private InterpreterDiagnosticCodes() {}
}
