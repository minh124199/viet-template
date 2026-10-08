package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

/**
 * Exception raised when a requested rename refactoring violates safety, uniqueness, or ownership
 * invariants.
 */
class RenameConflictException extends RuntimeException {

  @java.io.Serial private static final long serialVersionUID = 1L;

  enum Reason {
    INVALID_IDENTIFIER,
    NAME_COLLISION,
    UNSUPPORTED_JVM_MEMBER,
    UNSUPPORTED_DYNAMIC_SYMBOL,
    UNSUPPORTED_DENIED_MEMBER,
    UNSUPPORTED_METHOD,
    UNSUPPORTED_ROOT_PARAMETER,
    UNSUPPORTED_SCHEMA_FORMAT,
    DECLARATION_NOT_EDITABLE,
    OVERLAPPING_EDITS,
    STALE_OR_MALFORMED_DOCUMENT
  }

  private final Reason reason;

  RenameConflictException(Reason reason, String message) {
    super(Objects.requireNonNull(message, "message must not be null"));
    this.reason = Objects.requireNonNull(reason, "reason must not be null");
  }

  Reason reason() {
    return reason;
  }
}
