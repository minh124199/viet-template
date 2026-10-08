package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

/**
 * Representation of a declared Java symbol discovered in source code.
 *
 * <p>Captures the precise identifier span (for landing the cursor) and the enclosing declaration
 * span, along with symbol kind and signature metadata.
 */
record JavaSourceDeclaration(
    Range identifierRange,
    Range declarationRange,
    String name,
    JavaSourceSymbolKey.Kind kind,
    int parameterCount) {

  JavaSourceDeclaration {
    Objects.requireNonNull(identifierRange, "identifierRange must not be null");
    Objects.requireNonNull(declarationRange, "declarationRange must not be null");
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    if (parameterCount < 0) {
      throw new IllegalArgumentException("parameterCount must not be negative");
    }
  }
}
