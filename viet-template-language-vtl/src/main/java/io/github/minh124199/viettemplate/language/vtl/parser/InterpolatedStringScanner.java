package io.github.minh124199.viettemplate.language.vtl.parser;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ast.ReferenceNotation;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlAccessStep;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlInterpolatedStringExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlInterpolatedStringExpression.VtlInterpolatedStringPart;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlReference;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlStringLiteralExpression;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Scans a double-quoted string literal and partitions it into static text fragments and
 * interpolated {@link VtlReference} expressions.
 */
final class InterpolatedStringScanner {

  private InterpolatedStringScanner() {}

  public static VtlInterpolatedStringExpression scan(
      SourceSpan stringSpan, SourceText source, boolean allowHyphenatedIdentifiers) {
    Objects.requireNonNull(stringSpan, "stringSpan must not be null");
    Objects.requireNonNull(source, "source must not be null");

    String raw = source.slice(stringSpan.startOffset(), stringSpan.endOffset()).toString();
    int len = raw.length();

    // Check opening and closing quotes
    int innerStart = 0;
    int innerEnd = len;
    if (len > 0 && raw.charAt(0) == '"') {
      innerStart = 1;
    }
    if (len > 1 && raw.charAt(len - 1) == '"') {
      innerEnd = len - 1;
    }

    List<VtlInterpolatedStringPart> parts = new ArrayList<>();
    int offset = innerStart;
    int textStart = offset;

    while (offset < innerEnd) {
      char c = raw.charAt(offset);

      if (c == '\\') {
        int slashStart = offset;
        while (offset < innerEnd && raw.charAt(offset) == '\\') {
          offset++;
        }
        int slashCount = offset - slashStart;
        if (offset < innerEnd
            && raw.charAt(offset) == '$'
            && isValidRefStart(raw, offset, innerEnd)) {
          if (slashCount % 2 == 1) {
            // Odd slashes: $ is escaped, skip past '$'
            offset++;
            continue;
          } else {
            // Even slashes: slashes are literal, $ is an active reference!
            int textEnd = offset;
            if (textEnd > textStart) {
              String text = unescapeString(raw.substring(textStart, textEnd));
              SourceSpan span =
                  source.spanAt(
                      stringSpan.startOffset() + textStart, stringSpan.startOffset() + textEnd);
              parts.add(new VtlInterpolatedStringPart.TextPart(text, span));
            }
            // Parse reference starting at '$'
            VtlReference ref =
                parseInlineReference(
                    raw,
                    offset,
                    innerEnd,
                    source,
                    stringSpan.startOffset(),
                    allowHyphenatedIdentifiers);
            parts.add(new VtlInterpolatedStringPart.ReferencePart(ref, ref.span()));
            offset = ref.span().endOffset() - stringSpan.startOffset();
            textStart = offset;
            continue;
          }
        }
        continue;
      }

      if (c == '$' && isValidRefStart(raw, offset, innerEnd)) {
        if (offset > textStart) {
          String text = unescapeString(raw.substring(textStart, offset));
          SourceSpan span =
              source.spanAt(
                  stringSpan.startOffset() + textStart, stringSpan.startOffset() + offset);
          parts.add(new VtlInterpolatedStringPart.TextPart(text, span));
        }
        VtlReference ref =
            parseInlineReference(
                raw,
                offset,
                innerEnd,
                source,
                stringSpan.startOffset(),
                allowHyphenatedIdentifiers);
        parts.add(new VtlInterpolatedStringPart.ReferencePart(ref, ref.span()));
        offset = ref.span().endOffset() - stringSpan.startOffset();
        textStart = offset;
        continue;
      }

      offset++;
    }

    if (offset > textStart) {
      String text = unescapeString(raw.substring(textStart, offset));
      SourceSpan span =
          source.spanAt(stringSpan.startOffset() + textStart, stringSpan.startOffset() + offset);
      parts.add(new VtlInterpolatedStringPart.TextPart(text, span));
    }

    if (parts.isEmpty()) {
      // Empty string: emit empty text part
      SourceSpan span =
          source.spanAt(
              stringSpan.startOffset() + innerStart, stringSpan.startOffset() + innerStart);
      parts.add(new VtlInterpolatedStringPart.TextPart("", span));
    }

    return new VtlInterpolatedStringExpression(parts, stringSpan);
  }

  private static boolean isValidRefStart(String s, int dollarPos, int limit) {
    int pos = dollarPos + 1;
    if (pos >= limit) {
      return false;
    }
    char c = s.charAt(pos);
    if (c == '!') {
      pos++;
      if (pos >= limit) {
        return false;
      }
      c = s.charAt(pos);
    }
    if (c == '{') {
      pos++;
      return pos < limit && isIdStart(s.charAt(pos));
    }
    return isIdStart(c);
  }

  private static VtlReference parseInlineReference(
      String raw,
      int dollarOffset,
      int limit,
      SourceText source,
      int baseOffset,
      boolean allowHyphenated) {
    int cur = dollarOffset;
    cur++; // skip '$'
    boolean quiet = false;
    if (cur < limit && raw.charAt(cur) == '!') {
      quiet = true;
      cur++;
    }
    boolean formal = false;
    if (cur < limit && raw.charAt(cur) == '{') {
      formal = true;
      cur++;
    }

    int idStart = cur;
    while (cur < limit && isIdPart(raw.charAt(cur), allowHyphenated)) {
      cur++;
    }
    String rootId = raw.substring(idStart, cur);

    List<VtlAccessStep> steps = new ArrayList<>();
    while (cur < limit) {
      char c = raw.charAt(cur);
      if (c == '.') {
        if (cur + 1 < limit && isIdStart(raw.charAt(cur + 1))) {
          int dotStart = cur;
          cur++;
          int propStart = cur;
          while (cur < limit && isIdPart(raw.charAt(cur), allowHyphenated)) {
            cur++;
          }
          String prop = raw.substring(propStart, cur);
          SourceSpan stepSpan = source.spanAt(baseOffset + dotStart, baseOffset + cur);
          steps.add(new VtlAccessStep.PropertyAccess(prop, stepSpan));
        } else {
          break;
        }
      } else {
        break;
      }
    }

    Optional<VtlExpression> altValue = Optional.empty();
    if (formal && cur < limit && raw.charAt(cur) == '|') {
      cur++;
      int altStart = cur;
      while (cur < limit && raw.charAt(cur) != '}') {
        cur++;
      }
      int altEnd = cur;
      String altStr = raw.substring(altStart, altEnd).trim();
      SourceSpan altSpan = source.spanAt(baseOffset + altStart, baseOffset + altEnd);
      if ((altStr.startsWith("'") && altStr.endsWith("'"))
          || (altStr.startsWith("\"") && altStr.endsWith("\""))) {
        String content = altStr.substring(1, altStr.length() - 1);
        altValue = Optional.of(new VtlStringLiteralExpression(content, altSpan));
      } else if (!altStr.isEmpty()) {
        altValue = Optional.of(new VtlStringLiteralExpression(altStr, altSpan));
      }
    }

    if (formal && cur < limit && raw.charAt(cur) == '}') {
      cur++;
    }

    SourceSpan refSpan = source.spanAt(baseOffset + dollarOffset, baseOffset + cur);
    return new VtlReference(ReferenceNotation.of(quiet, formal), rootId, steps, altValue, refSpan);
  }

  private static boolean isIdStart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  private static boolean isIdPart(char c, boolean allowHyphenated) {
    if (isIdStart(c) || (c >= '0' && c <= '9')) {
      return true;
    }
    return allowHyphenated && c == '-';
  }

  private static String unescapeString(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char next = s.charAt(i + 1);
        if (next == '"' || next == '\\' || next == '$') {
          sb.append(next);
          i += 2;
          continue;
        }
      }
      sb.append(c);
      i++;
    }
    return sb.toString();
  }
}
