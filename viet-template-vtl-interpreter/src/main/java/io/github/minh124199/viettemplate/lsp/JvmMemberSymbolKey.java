package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

record JvmMemberSymbolKey(
    String declaringClassName,
    Kind memberKind,
    String memberName,
    String descriptor,
    int parameterCount)
    implements WorkspaceSymbolKey {

  enum Kind {
    RECORD_COMPONENT,
    GETTER,
    BOOLEAN_GETTER,
    FIELD,
    METHOD
  }

  public JvmMemberSymbolKey {
    Objects.requireNonNull(declaringClassName, "declaringClassName must not be null");
    Objects.requireNonNull(memberKind, "memberKind must not be null");
    Objects.requireNonNull(memberName, "memberName must not be null");
    descriptor = descriptor != null ? descriptor : "";
    if (parameterCount < 0) {
      throw new IllegalArgumentException("parameterCount must not be negative");
    }
  }

  static JvmMemberSymbolKey of(String declaringClassName, Kind memberKind, String memberName) {
    return new JvmMemberSymbolKey(declaringClassName, memberKind, memberName, "", 0);
  }

  static JvmMemberSymbolKey of(
      String declaringClassName,
      Kind memberKind,
      String memberName,
      String descriptor,
      int parameterCount) {
    return new JvmMemberSymbolKey(
        declaringClassName, memberKind, memberName, descriptor, parameterCount);
  }
}
