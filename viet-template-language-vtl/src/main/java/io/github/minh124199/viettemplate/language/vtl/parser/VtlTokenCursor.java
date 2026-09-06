package io.github.minh124199.viettemplate.language.vtl.parser;

import io.github.minh124199.viettemplate.language.vtl.lexer.VtlToken;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlTokenKind;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import java.util.Objects;

/** Internal token stream cursor providing lookahead and navigational utilities for the parser. */
final class VtlTokenCursor {

  private final List<VtlToken> tokens;
  private final SourceText source;
  private int position;

  VtlTokenCursor(List<VtlToken> tokens, SourceText source) {
    this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.position = 0;
  }

  SourceText source() {
    return source;
  }

  int position() {
    return position;
  }

  void seek(int pos) {
    this.position = Math.max(0, Math.min(pos, tokens.size() - 1));
  }

  VtlToken current() {
    if (position >= tokens.size()) {
      return tokens.get(tokens.size() - 1);
    }
    return tokens.get(position);
  }

  VtlToken peek(int offset) {
    int idx = position + offset;
    if (idx >= tokens.size()) {
      return tokens.get(tokens.size() - 1);
    }
    if (idx < 0) {
      return tokens.get(0);
    }
    return tokens.get(idx);
  }

  VtlToken previous() {
    if (position <= 0) {
      return tokens.get(0);
    }
    return tokens.get(position - 1);
  }

  boolean isAtEnd() {
    return position >= tokens.size() || current().kind() == VtlTokenKind.EOF;
  }

  VtlToken advance() {
    if (!isAtEnd()) {
      VtlToken token = tokens.get(position);
      position++;
      return token;
    }
    return current();
  }

  boolean check(VtlTokenKind kind) {
    if (isAtEnd() && kind != VtlTokenKind.EOF) {
      return false;
    }
    return current().kind() == kind;
  }

  boolean match(VtlTokenKind kind) {
    if (check(kind)) {
      advance();
      return true;
    }
    return false;
  }

  String text(VtlToken token) {
    return token.text(source);
  }

  String currentText() {
    return text(current());
  }
}
