package io.github.minh124199.viettemplate.language.vtl.semantics.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SymbolTableTest {

  private static final SourceSpan DUMMY_SPAN = SourceSpan.of(0, 5, 1, 1, 1, 6);

  @Test
  @DisplayName("Define and resolve root model symbols")
  void rootModelSymbols() {
    SymbolTable table = new SymbolTable();
    table.defineInRoot(Symbol.rootModel("user", VTypes.STRING));

    assertThat(table.isDefined("user")).isTrue();
    assertThat(table.resolve("user").orElseThrow().type()).isEqualTo(VTypes.STRING);
    assertThat(table.resolve("user").orElseThrow().isReadOnly()).isTrue();
  }

  @Test
  @DisplayName("Nested scopes shadow outer symbols and revert on exit")
  void scopeShadowing() {
    SymbolTable table = new SymbolTable();
    table.defineInRoot(Symbol.rootModel("item", VTypes.STRING));

    table.enterScope(ScopeKind.LOOP);
    table.define(Symbol.loopVariable("item", VTypes.INT, DUMMY_SPAN));

    assertThat(table.resolve("item").orElseThrow().type()).isEqualTo(VTypes.INT);
    assertThat(table.depth()).isEqualTo(2);

    table.exitScope();
    assertThat(table.resolve("item").orElseThrow().type()).isEqualTo(VTypes.STRING);
    assertThat(table.depth()).isEqualTo(1);
  }

  @Test
  @DisplayName("Cannot exit root model scope")
  void cannotExitRoot() {
    SymbolTable table = new SymbolTable();
    assertThatThrownBy(table::exitScope).isInstanceOf(IllegalStateException.class);
  }
}
