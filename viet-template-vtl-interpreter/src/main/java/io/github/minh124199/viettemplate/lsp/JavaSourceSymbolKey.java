package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

/**
 * Symbol key uniquely identifying a Java declaration target for cross-language definition.
 *
 * <p>Identifies either a type (class, record, interface, enum) or a member (record component,
 * method, field) within a given enclosing type.
 */
record JavaSourceSymbolKey(String typeName, Kind kind, String memberName, int parameterCount) {

  enum Kind {
    TYPE,
    RECORD_COMPONENT,
    METHOD,
    FIELD
  }

  JavaSourceSymbolKey {
    Objects.requireNonNull(typeName, "typeName must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    memberName = memberName != null ? memberName : "";
    if (parameterCount < 0) {
      throw new IllegalArgumentException("parameterCount must not be negative");
    }
  }

  static JavaSourceSymbolKey forType(String typeName) {
    return new JavaSourceSymbolKey(typeName, Kind.TYPE, "", 0);
  }

  static JavaSourceSymbolKey forRecordComponent(String typeName, String componentName) {
    return new JavaSourceSymbolKey(typeName, Kind.RECORD_COMPONENT, componentName, 0);
  }

  static JavaSourceSymbolKey forMethod(String typeName, String methodName, int parameterCount) {
    return new JavaSourceSymbolKey(typeName, Kind.METHOD, methodName, parameterCount);
  }

  static JavaSourceSymbolKey forField(String typeName, String fieldName) {
    return new JavaSourceSymbolKey(typeName, Kind.FIELD, fieldName, 0);
  }
}
