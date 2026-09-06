package io.github.minh124199.viettemplate.language.vtl.lexer;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * High-performance, deterministic lexical scanner for VTL-compatible template syntax.
 *
 * <p>Produces an immutable {@link VtlLexResult} with zero eager string copies for contiguous
 * template text chunks.
 */
public final class VtlLexer {

  private static final String DIAG_CATEGORY = "LEXER";

  private final SourceText source;
  private final VtlLexerOptions options;
  private final String content;
  private final int len;

  private int offset = 0;
  private final List<VtlToken> tokens = new ArrayList<>();
  private final List<Diagnostic> diagnostics = new ArrayList<>();
  private final Deque<LexerMode> modeStack = new ArrayDeque<>();

  private enum LexerMode {
    TEMPLATE_TEXT,
    EXPRESSION,
    FORMAL_REFERENCE,
    REFERENCE
  }

  private VtlLexer(SourceText source, VtlLexerOptions options) {
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.content = source.content();
    this.len = source.length();
    this.modeStack.push(LexerMode.TEMPLATE_TEXT);
  }

  public static VtlLexResult lex(SourceText source) {
    return lex(source, VtlLexerOptions.DEFAULT);
  }

  public static VtlLexResult lex(SourceText source, VtlLexerOptions options) {
    VtlLexer lexer = new VtlLexer(source, options);
    return lexer.scanAll();
  }

  private VtlLexResult scanAll() {
    while (offset < len) {
      int previousOffset = offset;
      int previousStackSize = modeStack.size();
      LexerMode previousMode = modeStack.peek();

      LexerMode currentMode = modeStack.peek();

      if (currentMode == LexerMode.TEMPLATE_TEXT) {
        scanTemplateText();
      } else if (currentMode == LexerMode.EXPRESSION) {
        scanExpression();
      } else if (currentMode == LexerMode.FORMAL_REFERENCE) {
        scanFormalReference();
      } else if (currentMode == LexerMode.REFERENCE) {
        scanReferenceTail();
      }

      // Invariant guard: lexer must strictly advance on every iteration or terminate
      if (offset <= previousOffset
          && modeStack.size() == previousStackSize
          && modeStack.peek() == previousMode
          && offset < len) {
        // Safeguard against any unexpected non-advancing condition
        emitToken(VtlTokenKind.TEXT, offset, offset + 1);
        offset++;
      }
    }

    // Emit final EOF token at end of input
    SourceSpan eofSpan = source.spanAt(len, len);
    tokens.add(VtlToken.of(VtlTokenKind.EOF, eofSpan));

    return new VtlLexResult(tokens, diagnostics, source);
  }

  // =========================================================================
  // TEMPLATE TEXT MODE
  // =========================================================================

  private void scanTemplateText() {
    int textStart = offset;

    while (offset < len) {
      char c = content.charAt(offset);

      if (c == '\\') {
        // Count consecutive backslashes
        int slashStart = offset;
        while (offset < len && content.charAt(offset) == '\\') {
          offset++;
        }
        int slashCount = offset - slashStart;

        if (offset < len) {
          char next = content.charAt(offset);
          if (next == '$' && isValidReferenceStart(offset)) {
            if (slashCount % 2 == 1) {
              // Odd backslashes: the '$' is escaped, remains literal template text!
              // Advance past '$' and its identifier to prevent active reference recognition.
              offset++;
              consumeReferenceTailInText();
              continue;
            } else {
              // Even backslashes: pairs of literal backslashes, '$' IS an active reference!
              // Emit text up to the active '$'
              int textEnd = offset;
              if (textEnd > textStart) {
                emitToken(VtlTokenKind.TEXT, textStart, textEnd);
              }
              scanReference();
              return;
            }
          } else if (next == '#' && isValidDirectiveOrCommentStart(offset)) {
            if (slashCount % 2 == 1) {
              // Odd backslashes: '#' is escaped, remains literal text!
              offset++;
              continue;
            } else {
              // Even backslashes: '#' IS an active directive / comment!
              int textEnd = offset;
              if (textEnd > textStart) {
                emitToken(VtlTokenKind.TEXT, textStart, textEnd);
              }
              scanDirectiveOrComment();
              return;
            }
          }
        }
        // Backslashes followed by non-VTL characters: continue text accumulation
        continue;
      }

      if (c == '$') {
        if (isValidReferenceStart(offset)) {
          if (offset > textStart) {
            emitToken(VtlTokenKind.TEXT, textStart, offset);
          }
          scanReference();
          return;
        }
        // Not an active reference start (e.g. $2.50, $$, $-)
        offset++;
        continue;
      }

      if (c == '#') {
        if (isValidDirectiveOrCommentStart(offset)) {
          if (offset > textStart) {
            emitToken(VtlTokenKind.TEXT, textStart, offset);
          }
          scanDirectiveOrComment();
          return;
        }
        // Not an active directive or comment (e.g. #ffffff, #123, C#)
        offset++;
        continue;
      }

      offset++;
    }

    if (offset > textStart) {
      emitToken(VtlTokenKind.TEXT, textStart, offset);
    }
  }

  private void consumeReferenceTailInText() {
    if (offset < len && content.charAt(offset) == '!') {
      offset++;
    }
    if (offset < len && content.charAt(offset) == '{') {
      offset++;
      while (offset < len && content.charAt(offset) != '}') {
        offset++;
      }
      if (offset < len && content.charAt(offset) == '}') {
        offset++;
      }
    } else {
      while (offset < len && isIdentifierPart(content.charAt(offset))) {
        offset++;
      }
    }
  }

  private boolean isValidReferenceStart(int dollarPos) {
    int pos = dollarPos + 1;
    if (pos >= len) {
      return false;
    }
    char c = content.charAt(pos);
    if (c == '!') {
      pos++;
      if (pos >= len) {
        return false;
      }
      c = content.charAt(pos);
    }
    if (c == '{') {
      pos++;
      return pos < len && isIdentifierStart(content.charAt(pos));
    }
    return isIdentifierStart(c);
  }

  private boolean isValidDirectiveOrCommentStart(int hashPos) {
    int pos = hashPos + 1;
    if (pos >= len) {
      return false;
    }
    char c = content.charAt(pos);
    if (c == '#' || c == '*') {
      // ## or #* comment
      return true;
    }
    if (c == '[' && pos + 1 < len && content.charAt(pos + 1) == '[') {
      // #[[ raw block
      return true;
    }
    if (c == '@' && pos + 1 < len && isIdentifierStart(content.charAt(pos + 1))) {
      return true;
    }
    if (c == '{') {
      // #{directive} or unterminated #{directive
      pos++;
      return pos < len && isIdentifierStart(content.charAt(pos));
    }
    if (isIdentifierStart(c)) {
      int idStart = pos;
      while (pos < len && isIdentifierPart(content.charAt(pos))) {
        pos++;
      }
      String id = content.substring(idStart, pos);
      // Check standalone directives that don't require '('
      if (isDirectiveWithoutArgsAllowed(id)) {
        return true;
      }
      // Directives with arguments require '('
      int lookahead = pos;
      while (lookahead < len && isWhitespace(content.charAt(lookahead))) {
        lookahead++;
      }
      return lookahead < len && content.charAt(lookahead) == '(';
    }
    return false;
  }

  private static boolean isDirectiveWithoutArgsAllowed(String id) {
    return "else".equalsIgnoreCase(id)
        || "end".equalsIgnoreCase(id)
        || "break".equalsIgnoreCase(id)
        || "stop".equalsIgnoreCase(id);
  }

  private static boolean isAlwaysArgumentlessDirective(String id) {
    return "else".equalsIgnoreCase(id) || "end".equalsIgnoreCase(id);
  }

  // =========================================================================
  // REFERENCES
  // =========================================================================

  private void scanReference() {
    int dollarStart = offset;
    emitToken(VtlTokenKind.DOLLAR, dollarStart, dollarStart + 1);
    offset++;

    boolean quiet = false;
    if (offset < len && content.charAt(offset) == '!') {
      quiet = true;
      emitToken(VtlTokenKind.BANG, offset, offset + 1);
      offset++;
    }

    if (offset < len && content.charAt(offset) == '{') {
      emitToken(VtlTokenKind.LEFT_BRACE, offset, offset + 1);
      offset++;
      modeStack.push(LexerMode.FORMAL_REFERENCE);
      return;
    }

    if (offset < len && isIdentifierStart(content.charAt(offset))) {
      int idStart = offset;
      while (offset < len && isIdentifierPart(content.charAt(offset))) {
        offset++;
      }
      emitToken(VtlTokenKind.IDENTIFIER, idStart, offset);
      modeStack.push(LexerMode.REFERENCE);
    } else {
      // Incomplete reference (e.g. $! followed by non-identifier)
      SourceSpan errSpan = source.spanAt(dollarStart, offset);
      diagnostics.add(
          Diagnostic.error(
              DiagnosticCode.of(DIAG_CATEGORY, "INCOMPLETE_REFERENCE"),
              "Incomplete reference: expected identifier after " + (quiet ? "'$!'" : "'$'"),
              errSpan));
    }
  }

  private void scanReferenceTail() {
    if (offset >= len) {
      modeStack.pop();
      return;
    }

    char c = content.charAt(offset);
    if (c == '.') {
      if (offset + 1 < len && isIdentifierStart(content.charAt(offset + 1))) {
        emitToken(VtlTokenKind.DOT, offset, offset + 1);
        offset++;
        int propStart = offset;
        while (offset < len && isIdentifierPart(content.charAt(offset))) {
          offset++;
        }
        emitToken(VtlTokenKind.IDENTIFIER, propStart, offset);
        return;
      }
    } else if (c == '(') {
      boolean isAfterMethod =
          tokens.size() >= 2
              && tokens.get(tokens.size() - 1).kind() == VtlTokenKind.IDENTIFIER
              && tokens.get(tokens.size() - 2).kind() == VtlTokenKind.DOT;
      if (isAfterMethod) {
        emitToken(VtlTokenKind.LEFT_PAREN, offset, offset + 1);
        offset++;
        modeStack.push(LexerMode.EXPRESSION);
        return;
      }
    } else if (c == '[') {
      emitToken(VtlTokenKind.LEFT_BRACKET, offset, offset + 1);
      offset++;
      modeStack.push(LexerMode.EXPRESSION);
      return;
    }

    modeStack.pop();
  }

  // =========================================================================
  // FORMAL REFERENCES: ${...} or $!{...}
  // =========================================================================

  private void scanFormalReference() {
    while (offset < len) {
      skipExpressionWhitespace();
      if (offset >= len) {
        break;
      }

      char c = content.charAt(offset);
      if (c == '}') {
        emitToken(VtlTokenKind.RIGHT_BRACE, offset, offset + 1);
        offset++;
        modeStack.pop();
        return;
      }

      if (c == '|') {
        emitToken(VtlTokenKind.PIPE, offset, offset + 1);
        offset++;
        continue;
      }

      if (c == '.') {
        emitToken(VtlTokenKind.DOT, offset, offset + 1);
        offset++;
        continue;
      }

      if (c == '(') {
        emitToken(VtlTokenKind.LEFT_PAREN, offset, offset + 1);
        offset++;
        modeStack.push(LexerMode.EXPRESSION);
        return;
      }

      if (c == '[') {
        emitToken(VtlTokenKind.LEFT_BRACKET, offset, offset + 1);
        offset++;
        modeStack.push(LexerMode.EXPRESSION);
        return;
      }

      if (c == '\'' || c == '\"') {
        scanString();
        continue;
      }

      if (isDigit(c)) {
        scanNumber();
        continue;
      }

      if (c == '$') {
        scanReference();
        continue;
      }

      if (isIdentifierStart(c)) {
        scanIdentifierOrKeyword();
        continue;
      }

      // Unrecognized character inside formal reference
      SourceSpan charSpan = source.spanAt(offset, offset + 1);
      diagnostics.add(
          Diagnostic.error(
              DiagnosticCode.of(DIAG_CATEGORY, "UNEXPECTED_CHARACTER"),
              "Unexpected character inside formal reference: '" + c + "'",
              charSpan));
      offset++;
    }

    // EOF before closing '}'
    SourceSpan errSpan = source.spanAt(offset, offset);
    diagnostics.add(
        Diagnostic.error(
            DiagnosticCode.of(DIAG_CATEGORY, "UNTERMINATED_FORMAL_REFERENCE"),
            "Unterminated formal reference: missing closing '}'",
            errSpan));
    modeStack.pop();
  }

  // =========================================================================
  // DIRECTIVES & COMMENTS & RAW BLOCKS
  // =========================================================================

  private void scanDirectiveOrComment() {
    int hashStart = offset;

    if (offset + 1 < len) {
      char next = content.charAt(offset + 1);
      if (next == '#') {
        scanSingleLineComment();
        return;
      }
      if (next == '*') {
        scanBlockComment();
        return;
      }
      if (next == '[' && offset + 2 < len && content.charAt(offset + 2) == '[') {
        scanRawBlock();
        return;
      }
    }

    // Directives
    emitToken(VtlTokenKind.HASH, hashStart, hashStart + 1);
    offset++;

    String directiveName = "";
    if (offset < len && content.charAt(offset) == '@') {
      // Block macro #@panel(...)
      emitToken(VtlTokenKind.AT, offset, offset + 1);
      offset++;
      int idStart = offset;
      while (offset < len && isIdentifierPart(content.charAt(offset))) {
        offset++;
      }
      directiveName = content.substring(idStart, offset);
      emitToken(VtlTokenKind.IDENTIFIER, idStart, offset);
    } else if (offset < len && content.charAt(offset) == '{') {
      // Braced directive #{else}, #{end}, #{if}(...)
      emitToken(VtlTokenKind.LEFT_BRACE, offset, offset + 1);
      offset++;
      int idStart = offset;
      while (offset < len && isIdentifierPart(content.charAt(offset))) {
        offset++;
      }
      directiveName = content.substring(idStart, offset);
      emitToken(VtlTokenKind.IDENTIFIER, idStart, offset);
      if (offset < len && content.charAt(offset) == '}') {
        emitToken(VtlTokenKind.RIGHT_BRACE, offset, offset + 1);
        offset++;
      } else {
        SourceSpan span = source.spanAt(hashStart, offset);
        diagnostics.add(
            Diagnostic.error(
                DiagnosticCode.of(DIAG_CATEGORY, "UNTERMINATED_DIRECTIVE"),
                "Unterminated braced directive: missing '}'",
                span));
      }
    } else {
      // Standard directive #if, #set, #else, etc.
      int idStart = offset;
      while (offset < len && isIdentifierPart(content.charAt(offset))) {
        offset++;
      }
      directiveName = content.substring(idStart, offset);
      emitToken(VtlTokenKind.IDENTIFIER, idStart, offset);
    }

    // Check if followed by argument list '('
    if (!isAlwaysArgumentlessDirective(directiveName)) {
      int savedOffset = offset;
      while (offset < len && (content.charAt(offset) == ' ' || content.charAt(offset) == '\t')) {
        offset++;
      }
      if (offset < len && content.charAt(offset) == '(') {
        if (options.includeTrivia() && offset > savedOffset) {
          emitToken(VtlTokenKind.WHITESPACE, savedOffset, offset);
        }
        emitToken(VtlTokenKind.LEFT_PAREN, offset, offset + 1);
        offset++;
        modeStack.push(LexerMode.EXPRESSION);
      } else {
        offset = savedOffset;
      }
    }
  }

  private void scanSingleLineComment() {
    int start = offset;
    offset += 2; // skip "##"
    while (offset < len) {
      char c = content.charAt(offset);
      if (c == '\r' || c == '\n') {
        if (c == '\r' && offset + 1 < len && content.charAt(offset + 1) == '\n') {
          offset += 2;
        } else {
          offset++;
        }
        break;
      }
      offset++;
    }
    if (options.includeTrivia()) {
      emitToken(VtlTokenKind.COMMENT, start, offset);
    }
  }

  private void scanBlockComment() {
    int start = offset;
    offset += 2; // skip "#*"
    boolean closed = false;

    while (offset + 1 < len) {
      if (content.charAt(offset) == '*' && content.charAt(offset + 1) == '#') {
        offset += 2;
        closed = true;
        break;
      }
      offset++;
    }

    if (!closed) {
      offset = len;
      SourceSpan span = source.spanAt(start, offset);
      diagnostics.add(
          Diagnostic.error(
              DiagnosticCode.of(DIAG_CATEGORY, "UNTERMINATED_COMMENT"),
              "Unterminated block comment: missing '*#'",
              span));
    }

    if (options.includeTrivia()) {
      emitToken(VtlTokenKind.COMMENT, start, offset);
    }
  }

  private void scanRawBlock() {
    int start = offset;
    offset += 3; // skip "#[["
    boolean closed = false;

    while (offset + 2 < len) {
      if (content.charAt(offset) == ']'
          && content.charAt(offset + 1) == ']'
          && content.charAt(offset + 2) == '#') {
        offset += 3;
        closed = true;
        break;
      }
      offset++;
    }

    if (!closed) {
      offset = len;
      SourceSpan span = source.spanAt(start, offset);
      diagnostics.add(
          Diagnostic.error(
              DiagnosticCode.of(DIAG_CATEGORY, "UNTERMINATED_RAW_BLOCK"),
              "Unterminated raw content block: missing ']]#'",
              span));
    }

    emitToken(VtlTokenKind.RAW_TEXT, start, offset);
  }

  // =========================================================================
  // EXPRESSION MODE (Directives arguments, method calls, indices)
  // =========================================================================

  private void scanExpression() {
    skipExpressionWhitespace();
    if (offset >= len) {
      modeStack.pop();
      return;
    }

    char c = content.charAt(offset);

    // Delimiters
    if (c == ')') {
      emitToken(VtlTokenKind.RIGHT_PAREN, offset, offset + 1);
      offset++;
      modeStack.pop();
      return;
    }
    if (c == ']') {
      emitToken(VtlTokenKind.RIGHT_BRACKET, offset, offset + 1);
      offset++;
      modeStack.pop();
      return;
    }
    if (c == '(') {
      emitToken(VtlTokenKind.LEFT_PAREN, offset, offset + 1);
      offset++;
      modeStack.push(LexerMode.EXPRESSION);
      return;
    }
    if (c == '[') {
      emitToken(VtlTokenKind.LEFT_BRACKET, offset, offset + 1);
      offset++;
      modeStack.push(LexerMode.EXPRESSION);
      return;
    }
    if (c == '{') {
      emitToken(VtlTokenKind.LEFT_BRACE, offset, offset + 1);
      offset++;
      return;
    }
    if (c == '}') {
      emitToken(VtlTokenKind.RIGHT_BRACE, offset, offset + 1);
      offset++;
      return;
    }
    if (c == ',') {
      emitToken(VtlTokenKind.COMMA, offset, offset + 1);
      offset++;
      return;
    }
    if (c == ':') {
      emitToken(VtlTokenKind.COLON, offset, offset + 1);
      offset++;
      return;
    }
    if (c == '.') {
      if (offset + 1 < len && content.charAt(offset + 1) == '.') {
        emitToken(VtlTokenKind.RANGE, offset, offset + 2);
        offset += 2;
      } else {
        emitToken(VtlTokenKind.DOT, offset, offset + 1);
        offset++;
      }
      return;
    }

    // Operators
    if (c == '=') {
      if (offset + 1 < len && content.charAt(offset + 1) == '=') {
        emitToken(VtlTokenKind.EQUAL_EQUAL, offset, offset + 2);
        offset += 2;
      } else {
        emitToken(VtlTokenKind.EQUAL, offset, offset + 1);
        offset++;
      }
      return;
    }
    if (c == '!') {
      if (offset + 1 < len && content.charAt(offset + 1) == '=') {
        emitToken(VtlTokenKind.NOT_EQUAL, offset, offset + 2);
        offset += 2;
      } else {
        emitToken(VtlTokenKind.LOGICAL_NOT, offset, offset + 1);
        offset++;
      }
      return;
    }
    if (c == '<') {
      if (offset + 1 < len && content.charAt(offset + 1) == '=') {
        emitToken(VtlTokenKind.LESS_EQUAL, offset, offset + 2);
        offset += 2;
      } else {
        emitToken(VtlTokenKind.LESS, offset, offset + 1);
        offset++;
      }
      return;
    }
    if (c == '>') {
      if (offset + 1 < len && content.charAt(offset + 1) == '=') {
        emitToken(VtlTokenKind.GREATER_EQUAL, offset, offset + 2);
        offset += 2;
      } else {
        emitToken(VtlTokenKind.GREATER, offset, offset + 1);
        offset++;
      }
      return;
    }
    if (c == '&' && offset + 1 < len && content.charAt(offset + 1) == '&') {
      emitToken(VtlTokenKind.LOGICAL_AND, offset, offset + 2);
      offset += 2;
      return;
    }
    if (c == '|' && offset + 1 < len && content.charAt(offset + 1) == '|') {
      emitToken(VtlTokenKind.LOGICAL_OR, offset, offset + 2);
      offset += 2;
      return;
    }
    if (c == '+') {
      emitToken(VtlTokenKind.PLUS, offset, offset + 1);
      offset++;
      return;
    }
    if (c == '-') {
      emitToken(VtlTokenKind.MINUS, offset, offset + 1);
      offset++;
      return;
    }
    if (c == '*') {
      emitToken(VtlTokenKind.STAR, offset, offset + 1);
      offset++;
      return;
    }
    if (c == '/') {
      emitToken(VtlTokenKind.SLASH, offset, offset + 1);
      offset++;
      return;
    }
    if (c == '%') {
      emitToken(VtlTokenKind.PERCENT, offset, offset + 1);
      offset++;
      return;
    }

    // Literals
    if (c == '\'' || c == '\"') {
      scanString();
      return;
    }
    if (isDigit(c)) {
      scanNumber();
      return;
    }

    // Embedded reference inside expression
    if (c == '$') {
      scanReference();
      return;
    }

    // Identifiers and keywords
    if (isIdentifierStart(c)) {
      scanIdentifierOrKeyword();
      return;
    }

    // Unexpected character in expression mode
    SourceSpan errSpan = source.spanAt(offset, offset + 1);
    diagnostics.add(
        Diagnostic.error(
            DiagnosticCode.of(DIAG_CATEGORY, "UNEXPECTED_CHARACTER"),
            "Unexpected character in expression: '" + c + "'",
            errSpan));
    offset++;
  }

  // =========================================================================
  // LITERALS & IDENTIFIERS
  // =========================================================================

  private void scanString() {
    char quote = content.charAt(offset);
    int start = offset;
    offset++; // skip opening quote
    boolean closed = false;

    while (offset < len) {
      char c = content.charAt(offset);
      if (c == '\\') {
        // Escape sequence in string
        offset++;
        if (offset < len) {
          offset++;
        }
        continue;
      }
      if (c == quote) {
        offset++; // skip closing quote
        closed = true;
        break;
      }
      if (c == '\r' || c == '\n') {
        // Unterminated across line break
        break;
      }
      offset++;
    }

    VtlTokenKind kind = (quote == '\'') ? VtlTokenKind.STRING_SINGLE : VtlTokenKind.STRING_DOUBLE;
    if (!closed) {
      SourceSpan span = source.spanAt(start, offset);
      diagnostics.add(
          Diagnostic.error(
              DiagnosticCode.of(DIAG_CATEGORY, "UNTERMINATED_STRING"),
              "Unterminated string literal: missing closing " + quote,
              span));
    }
    emitToken(kind, start, offset);
  }

  private void scanNumber() {
    int start = offset;
    while (offset < len && isDigit(content.charAt(offset))) {
      offset++;
    }

    // Check if floating point (must be '.' followed by a digit, NOT '..')
    if (offset + 1 < len && content.charAt(offset) == '.' && isDigit(content.charAt(offset + 1))) {
      offset++; // consume '.'
      while (offset < len && isDigit(content.charAt(offset))) {
        offset++;
      }
      emitToken(VtlTokenKind.FLOAT, start, offset);
    } else {
      emitToken(VtlTokenKind.INTEGER, start, offset);
    }
  }

  private void scanIdentifierOrKeyword() {
    int start = offset;
    while (offset < len && isIdentifierPart(content.charAt(offset))) {
      offset++;
    }

    String word = content.substring(start, offset);
    VtlTokenKind kind =
        switch (word) {
          case "true" -> VtlTokenKind.TRUE;
          case "false" -> VtlTokenKind.FALSE;
          case "null" -> VtlTokenKind.NULL;
          case "in" -> VtlTokenKind.IN;
          case "and" -> VtlTokenKind.AND;
          case "or" -> VtlTokenKind.OR;
          case "not" -> VtlTokenKind.NOT;
          case "eq" -> VtlTokenKind.EQ;
          case "ne" -> VtlTokenKind.NE;
          case "lt" -> VtlTokenKind.LT;
          case "le" -> VtlTokenKind.LE;
          case "gt" -> VtlTokenKind.GT;
          case "ge" -> VtlTokenKind.GE;
          default -> VtlTokenKind.IDENTIFIER;
        };

    emitToken(kind, start, offset);
  }

  // =========================================================================
  // WHITESPACE & HELPER UTILITIES
  // =========================================================================

  private void skipExpressionWhitespace() {
    int start = offset;
    while (offset < len && isWhitespace(content.charAt(offset))) {
      offset++;
    }
    if (offset > start && options.includeTrivia()) {
      emitToken(VtlTokenKind.WHITESPACE, start, offset);
    }
  }

  private void skipDirectiveWhitespace() {
    int start = offset;
    while (offset < len && (content.charAt(offset) == ' ' || content.charAt(offset) == '\t')) {
      offset++;
    }
    if (offset > start && options.includeTrivia()) {
      emitToken(VtlTokenKind.WHITESPACE, start, offset);
    }
  }

  private void emitToken(VtlTokenKind kind, int startOffset, int endOffset) {
    SourceSpan span = source.spanAt(startOffset, endOffset);
    tokens.add(VtlToken.of(kind, span));
  }

  private boolean isIdentifierStart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  private boolean isIdentifierPart(char c) {
    if (isIdentifierStart(c) || isDigit(c) || c == '_') {
      return true;
    }
    return options.allowHyphenatedIdentifiers() && c == '-';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  }
}
