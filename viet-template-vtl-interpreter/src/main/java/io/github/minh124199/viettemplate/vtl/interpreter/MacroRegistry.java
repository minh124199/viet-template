package io.github.minh124199.viettemplate.vtl.interpreter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Render-local macro registry holding macro definitions. */
public final class MacroRegistry {

  private final Map<String, MacroDefinition> macros = new HashMap<>();

  public void register(MacroDefinition macro) {
    Objects.requireNonNull(macro, "macro must not be null");
    macros.put(macro.name().toLowerCase(java.util.Locale.ROOT), macro);
  }

  public MacroDefinition lookup(String name) {
    if (name == null) {
      return null;
    }
    return macros.get(name.toLowerCase(java.util.Locale.ROOT));
  }

  public boolean contains(String name) {
    if (name == null) {
      return false;
    }
    return macros.containsKey(name.toLowerCase(java.util.Locale.ROOT));
  }
}
