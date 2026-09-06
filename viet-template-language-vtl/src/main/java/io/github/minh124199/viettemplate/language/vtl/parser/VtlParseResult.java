package io.github.minh124199.viettemplate.language.vtl.parser;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import java.util.Objects;

/** Result of parsing a VTL template into an immutable AST along with any diagnostics. */
public record VtlParseResult(
    VtlTemplate template, List<Diagnostic> diagnostics, SourceText source) {

  public VtlParseResult {
    Objects.requireNonNull(template, "template must not be null");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    Objects.requireNonNull(source, "source must not be null");
  }

  public boolean hasErrors() {
    return diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
  }

  /** Returns a deterministic, human-readable indented AST dump for golden tests and debugging. */
  public String dumpAst() {
    return VtlAstDumper.dump(template, source);
  }
}
