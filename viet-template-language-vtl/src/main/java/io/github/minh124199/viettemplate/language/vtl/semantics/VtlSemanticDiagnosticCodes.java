package io.github.minh124199.viettemplate.language.vtl.semantics;

import io.github.minh124199.viettemplate.api.DiagnosticCode;

/** Standard diagnostic codes emitted during VTL semantic analysis. */
final class VtlSemanticDiagnosticCodes {

  public static final DiagnosticCode PROPERTY_NOT_FOUND = DiagnosticCode.of("VTLS", "2104");
  public static final DiagnosticCode METHOD_NOT_FOUND = DiagnosticCode.of("VTLS", "2105");
  public static final DiagnosticCode SECURITY_DENIED = DiagnosticCode.of("VTLSEC", "2401");
  public static final DiagnosticCode UNRESOLVED_ROOT = DiagnosticCode.of("VTLS", "2101");
  public static final DiagnosticCode INVALID_ASSIGNMENT = DiagnosticCode.of("VTLS", "2102");
  public static final DiagnosticCode TYPE_MISMATCH = DiagnosticCode.of("VTLS", "2103");
  public static final DiagnosticCode INVALID_ITERABLE = DiagnosticCode.of("VTLS", "2106");
  public static final DiagnosticCode NULLABLE_DEREFERENCE = DiagnosticCode.of("VTLS", "2107");
  public static final DiagnosticCode DUPLICATE_PARAM = DiagnosticCode.of("VTLS", "2108");
  public static final DiagnosticCode UNKNOWN_TYPE = DiagnosticCode.of("VTLS", "2109");
  public static final DiagnosticCode INVALID_PARAM_SYNTAX = DiagnosticCode.of("VTLS", "2110");
  public static final DiagnosticCode PARAM_AFTER_PROLOGUE = DiagnosticCode.of("VTLS", "2111");

  private VtlSemanticDiagnosticCodes() {}
}
