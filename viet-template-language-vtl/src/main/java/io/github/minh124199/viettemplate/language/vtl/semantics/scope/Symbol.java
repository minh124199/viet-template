package io.github.minh124199.viettemplate.language.vtl.semantics.scope;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;
import java.util.Optional;

/** Represents a named symbol in the template lexical scope hierarchy. */
public record Symbol(
    String name,
    VType type,
    ScopeKind scopeKind,
    Optional<SourceSpan> declaredSpan,
    boolean isReadOnly) {

  public Symbol {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(scopeKind, "scopeKind must not be null");
    Objects.requireNonNull(declaredSpan, "declaredSpan must not be null");
  }

  public static Symbol rootModel(String name, VType type) {
    return new Symbol(name, type, ScopeKind.ROOT_MODEL, Optional.empty(), true);
  }

  public static Symbol local(String name, VType type, SourceSpan span) {
    return new Symbol(name, type, ScopeKind.LOCAL, Optional.of(span), false);
  }

  public static Symbol loopVariable(String name, VType type, SourceSpan span) {
    return new Symbol(name, type, ScopeKind.LOOP, Optional.of(span), true);
  }

  public static Symbol loopMetadata(String name, VType type, SourceSpan span) {
    return new Symbol(name, type, ScopeKind.LOOP, Optional.of(span), true);
  }

  public static Symbol macroParameter(String name, VType type, SourceSpan span) {
    return new Symbol(name, type, ScopeKind.MACRO, Optional.of(span), false);
  }

  public static Symbol builtin(String name, VType type) {
    return new Symbol(name, type, ScopeKind.BUILTIN, Optional.empty(), true);
  }

  public Symbol withType(VType newType) {
    return new Symbol(name, newType, scopeKind, declaredSpan, isReadOnly);
  }
}
