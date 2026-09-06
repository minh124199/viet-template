package io.github.minh124199.viettemplate.language.vtl.lexer;

/** Lexical token classifications for VTL-compatible template syntax. */
public enum VtlTokenKind {
  // Template text
  TEXT,
  RAW_TEXT,

  // Introducers
  DOLLAR, // '$'
  HASH, // '#'
  AT, // '@' (block macro call '#@name')
  BANG, // '!'
  PIPE, // '|' (formal reference alternate value separator)

  // Delimiters
  LEFT_PAREN, // '('
  RIGHT_PAREN, // ')'
  LEFT_BRACE, // '{'
  RIGHT_BRACE, // '}'
  LEFT_BRACKET, // '['
  RIGHT_BRACKET, // ']'
  DOT, // '.'
  COMMA, // ','
  COLON, // ':'

  // Operators
  EQUAL, // '='
  EQUAL_EQUAL, // '=='
  NOT_EQUAL, // '!='
  LESS, // '<'
  LESS_EQUAL, // '<='
  GREATER, // '>'
  GREATER_EQUAL, // '>='
  LOGICAL_AND, // '&&'
  LOGICAL_OR, // '||'
  LOGICAL_NOT, // '!'
  PLUS, // '+'
  MINUS, // '-'
  STAR, // '*'
  SLASH, // '/'
  PERCENT, // '%'
  RANGE, // '..'

  // Textual operators (in expression context)
  AND, // 'and'
  OR, // 'or'
  NOT, // 'not'
  EQ, // 'eq'
  NE, // 'ne'
  LT, // 'lt'
  LE, // 'le'
  GT, // 'gt'
  GE, // 'ge'

  // Keywords
  TRUE, // 'true'
  FALSE, // 'false'
  NULL, // 'null'
  IN, // 'in'

  // Identifiers & Literals
  IDENTIFIER,
  INTEGER,
  FLOAT,
  STRING_SINGLE, // '...'
  STRING_DOUBLE, // "..."

  // Trivia & Terminal
  COMMENT,
  WHITESPACE,
  EOF;

  public boolean isTrivia() {
    return this == COMMENT || this == WHITESPACE;
  }
}
