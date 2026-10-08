package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Objects;

/** Symbol key uniquely identifying a template macro (#macro) declaration. */
record TemplateMacroSymbolKey(String templateUri, String macroName) implements Serializable {

  public TemplateMacroSymbolKey {
    Objects.requireNonNull(templateUri, "templateUri must not be null");
    Objects.requireNonNull(macroName, "macroName must not be null");
  }

  static TemplateMacroSymbolKey of(String templateUri, String macroName) {
    return new TemplateMacroSymbolKey(templateUri, macroName);
  }
}
