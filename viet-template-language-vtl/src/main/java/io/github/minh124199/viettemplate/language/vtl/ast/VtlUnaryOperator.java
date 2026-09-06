package io.github.minh124199.viettemplate.language.vtl.ast;

/** Unary operators supported in VTL expressions. */
public enum VtlUnaryOperator {
  NOT("!"),
  MINUS("-"),
  PLUS("+");

  private final String symbol;

  VtlUnaryOperator(String symbol) {
    this.symbol = symbol;
  }

  public String symbol() {
    return symbol;
  }
}
