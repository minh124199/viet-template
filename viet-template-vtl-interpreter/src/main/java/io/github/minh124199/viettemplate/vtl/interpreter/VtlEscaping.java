package io.github.minh124199.viettemplate.vtl.interpreter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Implements Velocity 2.4.x compatible backslash escaping rules for references and directives. */
public final class VtlEscaping {

  // Pattern matching backslashes preceding $ or # followed by an identifier or formal ref
  private static final Pattern ESCAPED_VTL_PATTERN =
      Pattern.compile("(\\\\+)([$#])(!?)(?:\\{([a-zA-Z0-9_\\-]+)\\}|([a-zA-Z0-9_\\-]+))");

  private VtlEscaping() {}

  /** Renders text containing escaped references or directives according to context definition. */
  public static String renderText(String text, ExecutionContext context) {
    if (text == null || text.indexOf('\\') == -1) {
      return text;
    }

    Matcher matcher = ESCAPED_VTL_PATTERN.matcher(text);
    StringBuilder sb = new StringBuilder(text.length());

    while (matcher.find()) {
      String slashes = matcher.group(1);
      String sigil = matcher.group(2);
      String quiet = matcher.group(3);
      String formalName = matcher.group(4);
      String simpleName = matcher.group(5);
      String varName = formalName != null ? formalName : simpleName;

      int slashCount = slashes.length();

      if ("#".equals(sigil)) {
        // Directives: odd backslashes escape the directive (#if -> #if)
        int halved = slashCount / 2;
        matcher.appendReplacement(
            sb,
            Matcher.quoteReplacement("\\".repeat(halved) + "#" + (varName != null ? varName : "")));
      } else {
        // References: $foo or ${foo}
        boolean isDefined = false;
        if (varName != null) {
          EvaluationValue val = context.lookup(varName);
          isDefined = val.isNonNull();
        }

        if (isDefined) {
          // In Velocity, when the reference is defined:
          // 2k backslashes -> k backslashes
          // 2k + 1 backslashes -> k backslashes + $ref
          int pairs = slashCount / 2;
          if (slashCount % 2 == 1) {
            // Odd: escapes the reference, outputs $var or ${var}
            String refStr =
                "$" + quiet + (formalName != null ? "{" + formalName + "}" : simpleName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("\\".repeat(pairs) + refStr));
          } else {
            // Even: active reference handled separately or preserved
            String refStr =
                "$" + quiet + (formalName != null ? "{" + formalName + "}" : simpleName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("\\".repeat(pairs) + refStr));
          }
        } else {
          // Undefined / null reference:
          // In Velocity, odd slashes (2k + 1) halve the 2k slashes to k slashes, leaving (k + 1)
          // slashes + $ref.
          // Even slashes (2k) are preserved verbatim.
          if (slashCount % 2 == 1) {
            int pairs = slashCount / 2;
            String refStr =
                "$" + quiet + (formalName != null ? "{" + formalName + "}" : simpleName);
            matcher.appendReplacement(
                sb, Matcher.quoteReplacement("\\".repeat(pairs + 1) + refStr));
          } else {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
          }
        }
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /** Adjusts trailing backslashes in a text node that immediately precedes an active reference. */
  public static String adjustTrailingBackslashesBeforeActiveReference(
      String text, boolean referenceIsDefined) {
    if (text == null || !referenceIsDefined || !text.endsWith("\\")) {
      return text;
    }

    int i = text.length() - 1;
    while (i >= 0 && text.charAt(i) == '\\') {
      i--;
    }
    int slashCount = text.length() - 1 - i;
    if (slashCount > 0 && slashCount % 2 == 0) {
      // Halve the even backslashes
      return text.substring(0, i + 1) + "\\".repeat(slashCount / 2);
    }
    return text;
  }

  /** Adjusts trailing backslashes in a text node that immediately precedes an active directive. */
  public static String adjustTrailingBackslashesBeforeActiveDirective(String text) {
    if (text == null || !text.endsWith("\\")) {
      return text;
    }

    int i = text.length() - 1;
    while (i >= 0 && text.charAt(i) == '\\') {
      i--;
    }
    int slashCount = text.length() - 1 - i;
    if (slashCount > 0 && slashCount % 2 == 0) {
      // Halve the even backslashes
      return text.substring(0, i + 1) + "\\".repeat(slashCount / 2);
    }
    return text;
  }
}
