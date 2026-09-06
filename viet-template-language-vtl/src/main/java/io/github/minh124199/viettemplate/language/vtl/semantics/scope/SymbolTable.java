package io.github.minh124199.viettemplate.language.vtl.semantics.scope;

import java.util.Objects;
import java.util.Optional;

/**
 * Manages the stack of lexical symbol scopes during semantic analysis of a template.
 *
 * <p>Resolves identifiers through the lexical hierarchy: LOOP -> LOCAL / MACRO -> ROOT_MODEL ->
 * BUILTIN.
 */
public final class SymbolTable {
  private SymbolScope current;
  private final SymbolScope rootScope;
  private int depth;

  public SymbolTable() {
    this.rootScope = new SymbolScope(ScopeKind.ROOT_MODEL, null);
    this.current = rootScope;
    this.depth = 1;
  }

  public SymbolScope currentScope() {
    return current;
  }

  public SymbolScope rootScope() {
    return rootScope;
  }

  public int depth() {
    return depth;
  }

  public SymbolScope enterScope(ScopeKind kind) {
    Objects.requireNonNull(kind, "kind must not be null");
    current = new SymbolScope(kind, current);
    depth++;
    return current;
  }

  public SymbolScope exitScope() {
    if (current.parent().isEmpty()) {
      throw new IllegalStateException("Cannot exit the root model scope");
    }
    current = current.parent().get();
    depth--;
    return current;
  }

  public void define(Symbol symbol) {
    Objects.requireNonNull(symbol, "symbol must not be null");
    current.define(symbol);
  }

  public void defineInRoot(Symbol symbol) {
    Objects.requireNonNull(symbol, "symbol must not be null");
    rootScope.define(symbol);
  }

  public Optional<Symbol> resolve(String name) {
    Objects.requireNonNull(name, "name must not be null");
    return current.resolve(name);
  }

  public Optional<Symbol> resolveInCurrentScope(String name) {
    Objects.requireNonNull(name, "name must not be null");
    return current.resolveCurrent(name);
  }

  public boolean isDefined(String name) {
    return resolve(name).isPresent();
  }
}
