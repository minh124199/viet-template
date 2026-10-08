package io.github.minh124199.viettemplate.lsp;

import java.util.Objects;

/** Type symbol key uniquely identifying a JVM class declaration. */
record JvmTypeSymbolKey(String className) implements WorkspaceTypeSymbolKey {

  public JvmTypeSymbolKey {
    Objects.requireNonNull(className, "className must not be null");
  }

  static JvmTypeSymbolKey of(String className) {
    return new JvmTypeSymbolKey(className);
  }
}
