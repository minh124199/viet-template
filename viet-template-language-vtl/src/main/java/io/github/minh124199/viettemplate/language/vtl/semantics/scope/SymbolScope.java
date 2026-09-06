package io.github.minh124199.viettemplate.language.vtl.semantics.scope;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A single lexical scope containing symbols and a link to its parent scope. */
public final class SymbolScope {
  private final ScopeKind kind;
  private final SymbolScope parent;
  private final Map<String, Symbol> symbols = new LinkedHashMap<>();

  public SymbolScope(ScopeKind kind, SymbolScope parent) {
    this.kind = Objects.requireNonNull(kind, "kind must not be null");
    this.parent = parent;
  }

  public ScopeKind kind() {
    return kind;
  }

  public Optional<SymbolScope> parent() {
    return Optional.ofNullable(parent);
  }

  public void define(Symbol symbol) {
    Objects.requireNonNull(symbol, "symbol must not be null");
    symbols.put(symbol.name(), symbol);
  }

  public Optional<Symbol> resolve(String name) {
    Objects.requireNonNull(name, "name must not be null");
    Symbol local = symbols.get(name);
    if (local != null) {
      return Optional.of(local);
    }
    if (parent != null) {
      return parent.resolve(name);
    }
    return Optional.empty();
  }

  public Optional<Symbol> resolveCurrent(String name) {
    return Optional.ofNullable(symbols.get(name));
  }

  public Map<String, Symbol> symbols() {
    return Collections.unmodifiableMap(symbols);
  }
}
