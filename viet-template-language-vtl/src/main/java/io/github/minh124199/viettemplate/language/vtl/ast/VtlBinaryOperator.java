package io.github.minh124199.viettemplate.language.vtl.ast;

/** Binary operators supported in VTL expressions. */
public enum VtlBinaryOperator {
  MULTIPLY("*"),
  DIVIDE("/"),
  MODULO("%"),
  ADD("+"),
  SUBTRACT("-"),
  LESS_THAN("<"),
  LESS_THAN_OR_EQUAL("<="),
  GREATER_THAN(">"),
  GREATER_THAN_OR_EQUAL(">="),
  EQUAL("=="),
  NOT_EQUAL("!="),
  LOGICAL_AND("&&"),
  LOGICAL_OR("||");

  private final String symbol;

  VtlBinaryOperator(String symbol) {
    this.symbol = symbol;
  }

  public String symbol() {
    return symbol;
  }
}
