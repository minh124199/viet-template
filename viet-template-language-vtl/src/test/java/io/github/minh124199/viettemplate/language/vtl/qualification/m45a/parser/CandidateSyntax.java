package io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser;

/** Candidate declaration syntax models evaluated in M45A qualification. */
public enum CandidateSyntax {
  /** Candidate A: #param(Employee $employee) or #param(List<String> $roles) */
  CANDIDATE_A_PAREN_TYPE_VAR,

  /** Candidate B: #param($employee: Employee) or #param($roles: List<String>) */
  CANDIDATE_B_PAREN_VAR_COLON_TYPE,

  /** Candidate C: #param Employee $employee */
  CANDIDATE_C_BARE_TYPE_VAR,

  /** Candidate D: Companion schema only (*.vt-schema.json), no inline syntax */
  CANDIDATE_D_SCHEMA_ONLY,

  /** Candidate E: Hybrid inline declaration + companion schema fallback */
  CANDIDATE_E_HYBRID,

  /** Candidate F: Metadata / comment-style declaration (*# @param Type $var #*) */
  CANDIDATE_F_COMMENT_METADATA
}
