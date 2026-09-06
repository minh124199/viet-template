package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.language.vtl.lexer.VtlLexResult;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlLexer;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlLexerOptions;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlToken;
import io.github.minh124199.viettemplate.language.vtl.lexer.VtlTokenKind;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

/** Implements Apache Velocity 2.x compatible space gobbling (LINES mode). */
public final class SpaceGobbler {

  public enum Mode {
    NONE,
    LINES
  }

  private static final Set<String> GOBBLE_DIRECTIVES =
      Set.of("if", "elseif", "else", "end", "foreach", "macro", "define", "set", "break", "stop");

  private SpaceGobbler() {}

  /**
   * Pre-calculates character indices that should be suppressed in LINES space gobbling mode.
   *
   * @param source source text
   * @param mode space gobbling mode
   * @return BitSet where set bits correspond to character offsets suppressed in output
   */
  public static BitSet computeGobbledIndices(SourceText source, Mode mode) {
    if (mode != Mode.LINES || source == null) {
      return new BitSet();
    }

    String content = source.content();
    int length = content.length();
    if (length == 0) {
      return new BitSet();
    }

    BitSet gobbled = new BitSet(length);

    // 1. Check standalone single-line comments ##
    int cIdx = 0;
    while ((cIdx = content.indexOf("##", cIdx)) != -1) {
      int lineStart = cIdx;
      boolean cleanLeading = true;
      while (lineStart > 0) {
        char c = content.charAt(lineStart - 1);
        if (c == '\n' || c == '\r') {
          break;
        }
        if (c != ' ' && c != '\t') {
          cleanLeading = false;
          break;
        }
        lineStart--;
      }

      int lineEnd = cIdx + 2;
      while (lineEnd < length
          && content.charAt(lineEnd) != '\n'
          && content.charAt(lineEnd) != '\r') {
        lineEnd++;
      }
      if (lineEnd < length && content.charAt(lineEnd) == '\r') {
        lineEnd++;
        if (lineEnd < length && content.charAt(lineEnd) == '\n') {
          lineEnd++;
        }
      } else if (lineEnd < length && content.charAt(lineEnd) == '\n') {
        lineEnd++;
      }

      if (cleanLeading) {
        for (int i = lineStart; i < cIdx; i++) {
          gobbled.set(i);
        }
        int commentEnd = cIdx + 2;
        while (commentEnd < length
            && content.charAt(commentEnd) != '\n'
            && content.charAt(commentEnd) != '\r') {
          commentEnd++;
        }
        for (int i = commentEnd; i < lineEnd; i++) {
          gobbled.set(i);
        }
      }
      cIdx = lineEnd;
    }

    // 2. Lex tokens to find control directive tags
    VtlLexResult lexResult =
        VtlLexer.lex(source, VtlLexerOptions.builder().includeTrivia(false).build());
    List<VtlToken> tokens = lexResult.tokens();

    for (int i = 0; i < tokens.size(); i++) {
      VtlToken tok = tokens.get(i);
      if (tok.kind() != VtlTokenKind.HASH) {
        continue;
      }

      int tagStart = tok.span().startOffset();
      int tagEnd = -1;

      int nextIdx = i + 1;
      if (nextIdx >= tokens.size()) {
        continue;
      }

      boolean braced = false;
      VtlToken nextTok = tokens.get(nextIdx);
      if (nextTok.kind() == VtlTokenKind.LEFT_BRACE) {
        braced = true;
        nextIdx++;
        if (nextIdx >= tokens.size()) {
          continue;
        }
        nextTok = tokens.get(nextIdx);
      }

      if (nextTok.kind() != VtlTokenKind.IDENTIFIER) {
        continue;
      }

      String dirName =
          source
              .content()
              .substring(nextTok.span().startOffset(), nextTok.span().endOffset())
              .toLowerCase();

      if (!GOBBLE_DIRECTIVES.contains(dirName)) {
        continue;
      }

      tagEnd = nextTok.span().endOffset();
      int scan = nextIdx + 1;

      // If directive is followed by '(', find matching ')'
      if (scan < tokens.size() && tokens.get(scan).kind() == VtlTokenKind.LEFT_PAREN) {
        int parenDepth = 0;
        while (scan < tokens.size()) {
          VtlTokenKind k = tokens.get(scan).kind();
          if (k == VtlTokenKind.LEFT_PAREN) {
            parenDepth++;
          } else if (k == VtlTokenKind.RIGHT_PAREN) {
            parenDepth--;
            if (parenDepth == 0) {
              tagEnd = tokens.get(scan).span().endOffset();
              scan++;
              break;
            }
          }
          scan++;
        }
      }

      if (braced) {
        if (scan < tokens.size() && tokens.get(scan).kind() == VtlTokenKind.RIGHT_BRACE) {
          tagEnd = tokens.get(scan).span().endOffset();
          scan++;
        }
      }

      if (tagEnd == -1) {
        continue;
      }

      // Check line conditions
      int lineStart = tagStart;
      boolean cleanLeading = true;
      while (lineStart > 0) {
        char c = content.charAt(lineStart - 1);
        if (c == '\n' || c == '\r') {
          break;
        }
        if (c != ' ' && c != '\t') {
          cleanLeading = false;
          break;
        }
        lineStart--;
      }

      int lineEnd = tagEnd;
      boolean cleanTrailing = true;
      while (lineEnd < length) {
        char c = content.charAt(lineEnd);
        if (c == '\n') {
          lineEnd++;
          break;
        }
        if (c == '\r') {
          lineEnd++;
          if (lineEnd < length && content.charAt(lineEnd) == '\n') {
            lineEnd++;
          }
          break;
        }
        if (c == '#' && lineEnd + 1 < length && content.charAt(lineEnd + 1) == '#') {
          // Comment on same line
          lineEnd += 2;
          while (lineEnd < length
              && content.charAt(lineEnd) != '\n'
              && content.charAt(lineEnd) != '\r') {
            lineEnd++;
          }
          if (lineEnd < length && content.charAt(lineEnd) == '\r') {
            lineEnd++;
            if (lineEnd < length && content.charAt(lineEnd) == '\n') {
              lineEnd++;
            }
          } else if (lineEnd < length && content.charAt(lineEnd) == '\n') {
            lineEnd++;
          }
          break;
        }
        if (c != ' ' && c != '\t') {
          cleanTrailing = false;
          break;
        }
        lineEnd++;
      }

      if (cleanLeading && cleanTrailing) {
        for (int b = lineStart; b < tagStart; b++) {
          gobbled.set(b);
        }
        for (int a = tagEnd; a < lineEnd; a++) {
          gobbled.set(a);
        }
      }
    }

    return gobbled;
  }
}
