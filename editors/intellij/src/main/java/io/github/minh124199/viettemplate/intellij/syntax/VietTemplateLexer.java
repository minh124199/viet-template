package io.github.minh124199.viettemplate.intellij.syntax;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Handwritten, high-performance lexical scanner for Viet Template files (*.vtl, *.vm, *.vt).
 */
public class VietTemplateLexer extends LexerBase {

  public static final int STATE_DEFAULT = 0;
  public static final int STATE_EXPRESSION = 1;

  private static final Set<String> STANDARD_DIRECTIVES = Set.of(
      "if", "else", "elseif", "end", "foreach", "set", "macro", "define", "evaluate", "parse", "stop", "break"
  );

  private static final Set<String> DIRECTIVES_WITH_ARGS = Set.of(
      "if", "elseif", "foreach", "set", "macro", "define", "evaluate", "parse"
  );

  private CharSequence buffer;
  private int startOffset;
  private int endOffset;
  private int currentOffset;
  private int tokenStart;
  private int tokenEnd;
  private IElementType tokenType;
  private int state;
  private int parenDepth;
  private boolean expectingArgs;

  public VietTemplateLexer() {
    this.state = STATE_DEFAULT;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.buffer = buffer;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.currentOffset = startOffset;
    this.tokenStart = startOffset;
    this.tokenEnd = startOffset;
    this.tokenType = null;
    this.state = initialState;
    this.parenDepth = 0;
    this.expectingArgs = false;
    advance();
  }

  @Override
  public int getState() {
    return state;
  }

  @Nullable
  @Override
  public IElementType getTokenType() {
    return tokenType;
  }

  @Override
  public int getTokenStart() {
    return tokenStart;
  }

  @Override
  public int getTokenEnd() {
    return tokenEnd;
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return buffer;
  }

  @Override
  public int getBufferEnd() {
    return endOffset;
  }

  @Override
  public void advance() {
    if (currentOffset >= endOffset) {
      tokenType = null;
      tokenStart = endOffset;
      tokenEnd = endOffset;
      return;
    }

    tokenStart = currentOffset;

    if (state == STATE_EXPRESSION) {
      scanInExpressionMode();
    } else {
      scanInDefaultMode();
    }

    tokenEnd = currentOffset;
  }

  private void scanInDefaultMode() {
    char c = buffer.charAt(currentOffset);

    // If expecting args (e.g. after #if) and there are spaces, emit whitespace
    if (expectingArgs) {
      if (Character.isWhitespace(c)) {
        while (currentOffset < endOffset && Character.isWhitespace(buffer.charAt(currentOffset))) {
          currentOffset++;
        }
        tokenType = VietTemplateTokenTypes.WHITE_SPACE;
        return;
      }
      if (c == '(') {
        expectingArgs = false;
        state = STATE_EXPRESSION;
        parenDepth = 1;
        currentOffset++;
        tokenType = VietTemplateTokenTypes.OPERATOR;
        return;
      }
      // Not followed by '('
      expectingArgs = false;
    }

    // Line comment: ##
    if (c == '#' && currentOffset + 1 < endOffset && buffer.charAt(currentOffset + 1) == '#') {
      currentOffset += 2;
      while (currentOffset < endOffset && buffer.charAt(currentOffset) != '\n') {
        currentOffset++;
      }
      tokenType = VietTemplateTokenTypes.COMMENT;
      return;
    }

    // Block comment: #* ... *#
    if (c == '#' && currentOffset + 1 < endOffset && buffer.charAt(currentOffset + 1) == '*') {
      currentOffset += 2;
      while (currentOffset < endOffset) {
        if (buffer.charAt(currentOffset) == '*' && currentOffset + 1 < endOffset && buffer.charAt(currentOffset + 1) == '#') {
          currentOffset += 2;
          break;
        }
        currentOffset++;
      }
      tokenType = VietTemplateTokenTypes.BLOCK_COMMENT;
      return;
    }

    // Directive: #directive or #{directive}
    if (c == '#' && currentOffset + 1 < endOffset) {
      char next = buffer.charAt(currentOffset + 1);
      if (next == '{') {
        // Formal directive #{if}
        int pos = currentOffset + 2;
        if (pos < endOffset && isIdentifierStart(buffer.charAt(pos))) {
          while (pos < endOffset && isIdentifierPart(buffer.charAt(pos))) {
            pos++;
          }
          if (pos < endOffset && buffer.charAt(pos) == '}') {
            pos++;
            currentOffset = pos;
            tokenType = VietTemplateTokenTypes.DIRECTIVE;
            return;
          }
        }
      } else if (isValidDirective(currentOffset)) {
        int pos = currentOffset + 1;
        while (pos < endOffset && isIdentifierPart(buffer.charAt(pos))) {
          pos++;
        }
        String directiveName = buffer.subSequence(currentOffset + 1, pos).toString().toLowerCase();
        currentOffset = pos;
        tokenType = VietTemplateTokenTypes.DIRECTIVE;
        if (DIRECTIVES_WITH_ARGS.contains(directiveName)) {
          expectingArgs = true;
        }
        return;
      }
    }

    // Reference: $foo, $!foo, ${foo}, $!{foo}
    if (c == '$' && isValidReferenceStart(currentOffset)) {
      scanReference();
      tokenType = VietTemplateTokenTypes.REFERENCE;
      return;
    }

    // Otherwise, accumulate template text until next directive, comment, or reference
    currentOffset++;
    while (currentOffset < endOffset) {
      char curr = buffer.charAt(currentOffset);
      if (curr == '#' && isValidDirectiveOrCommentStart(currentOffset)) {
        break;
      }
      if (curr == '$' && isValidReferenceStart(currentOffset)) {
        break;
      }
      currentOffset++;
    }
    tokenType = VietTemplateTokenTypes.TEXT;
  }

  private void scanInExpressionMode() {
    char c = buffer.charAt(currentOffset);

    // Whitespace
    if (Character.isWhitespace(c)) {
      while (currentOffset < endOffset && Character.isWhitespace(buffer.charAt(currentOffset))) {
        currentOffset++;
      }
      tokenType = VietTemplateTokenTypes.WHITE_SPACE;
      return;
    }

    // String literals: "..." or '...'
    if (c == '"' || c == '\'') {
      char quote = c;
      currentOffset++;
      while (currentOffset < endOffset) {
        char ch = buffer.charAt(currentOffset);
        if (ch == '\\' && currentOffset + 1 < endOffset) {
          currentOffset += 2;
          continue;
        }
        if (ch == quote) {
          currentOffset++;
          break;
        }
        currentOffset++;
      }
      tokenType = VietTemplateTokenTypes.STRING_LITERAL;
      return;
    }

    // Reference in expression: $var, $!var, ${var}
    if (c == '$' && isValidReferenceStart(currentOffset)) {
      scanReference();
      tokenType = VietTemplateTokenTypes.REFERENCE;
      return;
    }

    // Numbers: 123, 3.14
    if (Character.isDigit(c)) {
      currentOffset++;
      while (currentOffset < endOffset && Character.isDigit(buffer.charAt(currentOffset))) {
        currentOffset++;
      }
      if (currentOffset + 1 < endOffset && buffer.charAt(currentOffset) == '.' && Character.isDigit(buffer.charAt(currentOffset + 1))) {
        currentOffset += 2;
        while (currentOffset < endOffset && Character.isDigit(buffer.charAt(currentOffset))) {
          currentOffset++;
        }
      }
      tokenType = VietTemplateTokenTypes.NUMBER;
      return;
    }

    // Two-character operators
    if (currentOffset + 1 < endOffset) {
      char next = buffer.charAt(currentOffset + 1);
      if ((c == '=' && next == '=') ||
          (c == '!' && next == '=') ||
          (c == '<' && next == '=') ||
          (c == '>' && next == '=') ||
          (c == '&' && next == '&') ||
          (c == '|' && next == '|')) {
        currentOffset += 2;
        tokenType = VietTemplateTokenTypes.OPERATOR;
        return;
      }
    }

    // Single-character operators / punctuation
    if (c == '(') {
      parenDepth++;
      currentOffset++;
      tokenType = VietTemplateTokenTypes.OPERATOR;
      return;
    }
    if (c == ')') {
      parenDepth--;
      currentOffset++;
      if (parenDepth <= 0) {
        state = STATE_DEFAULT;
        parenDepth = 0;
      }
      tokenType = VietTemplateTokenTypes.OPERATOR;
      return;
    }
    if (c == '[' || c == ']' || c == '{' || c == '}' ||
        c == '+' || c == '-' || c == '*' || c == '/' || c == '%' ||
        c == '=' || c == '<' || c == '>' || c == '!' ||
        c == ',' || c == '.' || c == ';') {
      currentOffset++;
      tokenType = VietTemplateTokenTypes.OPERATOR;
      return;
    }

    // Identifier in expression: e.g. "in", "true", "false", "null", or property names
    if (isIdentifierStart(c)) {
      currentOffset++;
      while (currentOffset < endOffset && isIdentifierPart(buffer.charAt(currentOffset))) {
        currentOffset++;
      }
      tokenType = VietTemplateTokenTypes.IDENTIFIER;
      return;
    }

    // Fallback single character
    currentOffset++;
    tokenType = VietTemplateTokenTypes.BAD_CHARACTER;
  }

  private void scanReference() {
    currentOffset++; // skip '$'
    if (currentOffset < endOffset && buffer.charAt(currentOffset) == '!') {
      currentOffset++;
    }
    if (currentOffset < endOffset && buffer.charAt(currentOffset) == '{') {
      currentOffset++;
      while (currentOffset < endOffset && buffer.charAt(currentOffset) != '}') {
        currentOffset++;
      }
      if (currentOffset < endOffset && buffer.charAt(currentOffset) == '}') {
        currentOffset++;
      }
      return;
    }

    // Regular reference: $foo or $foo.bar or $foo.bar()
    while (currentOffset < endOffset && isIdentifierPart(buffer.charAt(currentOffset))) {
      currentOffset++;
    }
    while (currentOffset < endOffset && buffer.charAt(currentOffset) == '.') {
      if (currentOffset + 1 < endOffset && isIdentifierStart(buffer.charAt(currentOffset + 1))) {
        currentOffset += 2;
        while (currentOffset < endOffset && isIdentifierPart(buffer.charAt(currentOffset))) {
          currentOffset++;
        }
        if (currentOffset + 1 < endOffset && buffer.charAt(currentOffset) == '(' && buffer.charAt(currentOffset + 1) == ')') {
          currentOffset += 2;
        }
      } else {
        break;
      }
    }
  }

  private boolean isValidReferenceStart(int dollarPos) {
    int pos = dollarPos + 1;
    if (pos >= endOffset) return false;
    char c = buffer.charAt(pos);
    if (c == '!') {
      pos++;
      if (pos >= endOffset) return false;
      c = buffer.charAt(pos);
    }
    if (c == '{') {
      pos++;
      return pos < endOffset && isIdentifierStart(buffer.charAt(pos));
    }
    return isIdentifierStart(c);
  }

  private boolean isValidDirectiveOrCommentStart(int hashPos) {
    int pos = hashPos + 1;
    if (pos >= endOffset) return false;
    char c = buffer.charAt(pos);
    if (c == '#' || c == '*') return true;
    if (c == '{') {
      pos++;
      return pos < endOffset && isIdentifierStart(buffer.charAt(pos));
    }
    return isValidDirective(hashPos);
  }

  private boolean isValidDirective(int hashPos) {
    int pos = hashPos + 1;
    if (pos >= endOffset || !isIdentifierStart(buffer.charAt(pos))) {
      return false;
    }
    int idStart = pos;
    while (pos < endOffset && isIdentifierPart(buffer.charAt(pos))) {
      pos++;
    }
    String name = buffer.subSequence(idStart, pos).toString().toLowerCase();
    if (STANDARD_DIRECTIVES.contains(name)) {
      return true;
    }
    int lookahead = pos;
    while (lookahead < endOffset && Character.isWhitespace(buffer.charAt(lookahead))) {
      lookahead++;
    }
    return lookahead < endOffset && buffer.charAt(lookahead) == '(';
  }

  private static boolean isIdentifierStart(char c) {
    return Character.isLetter(c) || c == '_';
  }

  private static boolean isIdentifierPart(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-';
  }
}
