package io.github.minh124199.viettemplate.language.vtl.lexer;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Result of scanning a {@link SourceText}, containing the emitted tokens and any accumulated
 * diagnostics.
 */
public record VtlLexResult(List<VtlToken> tokens, List<Diagnostic> diagnostics, SourceText source) {

  public VtlLexResult {
    Objects.requireNonNull(tokens, "tokens must not be null");
    Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    Objects.requireNonNull(source, "source must not be null");
    tokens = List.copyOf(tokens);
    diagnostics = List.copyOf(diagnostics);
  }

  public boolean hasErrors() {
    return diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
  }

  public List<VtlToken> nonTriviaTokens() {
    return tokens.stream().filter(t -> !t.isTrivia()).collect(Collectors.toUnmodifiableList());
  }

  /**
   * Formats the token stream into a deterministic human-readable table for debugging and golden
   * tests.
   */
  public String dumpTokenStream() {
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            "%-5s %-16s %-10s %-17s %s%n",
            "Index", "Token Kind", "Offsets", "Line:Col", "Raw Text Preview"));
    for (int i = 0; i < tokens.size(); i++) {
      VtlToken token = tokens.get(i);
      String raw = token.text(source);
      String escaped = escapeForDump(raw);
      sb.append(
          String.format(
              "%04d %-16s [%d..%d] (line %d:%d..%d:%d) \"%s\"%n",
              i,
              token.kind().name(),
              token.startOffset(),
              token.endOffset(),
              token.span().startLine(),
              token.span().startColumn(),
              token.span().endLine(),
              token.span().endColumn(),
              escaped));
    }
    return sb.toString();
  }

  private static String escapeForDump(String text) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
