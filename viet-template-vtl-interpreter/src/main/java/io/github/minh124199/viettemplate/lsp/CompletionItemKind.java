package io.github.minh124199.viettemplate.lsp;

/** Standard LSP completion item kinds. */
enum CompletionItemKind {
  TEXT(1),
  METHOD(2),
  FUNCTION(3),
  CONSTRUCTOR(4),
  FIELD(5),
  VARIABLE(6),
  CLASS(7),
  INTERFACE(8),
  MODULE(9),
  PROPERTY(10),
  UNIT(11),
  VALUE(12),
  ENUM(13),
  KEYWORD(14),
  SNIPPET(15),
  COLOR(16),
  FILE(17),
  REFERENCE(18);

  private final int value;

  CompletionItemKind(int value) {
    this.value = value;
  }

  public int value() {
    return value;
  }
}
