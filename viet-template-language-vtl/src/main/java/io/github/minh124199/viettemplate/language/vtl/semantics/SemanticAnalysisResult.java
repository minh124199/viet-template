package io.github.minh124199.viettemplate.language.vtl.semantics;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlExpression;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.scope.SymbolTable;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The completed result of semantic analysis and type binding for a parsed VTL template. */
public record SemanticAnalysisResult(
    VtlTemplate template,
    List<Diagnostic> diagnostics,
    TemplateCapabilities capabilities,
    SymbolTable symbolTable,
    Map<VtlExpression, VType> expressionTypes,
    Map<VtlNode, VType> nodeTypes) {

  public SemanticAnalysisResult {
    Objects.requireNonNull(template, "template must not be null");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    Objects.requireNonNull(capabilities, "capabilities must not be null");
    Objects.requireNonNull(symbolTable, "symbolTable must not be null");
    expressionTypes =
        Map.copyOf(Objects.requireNonNull(expressionTypes, "expressionTypes must not be null"));
    nodeTypes = Map.copyOf(Objects.requireNonNull(nodeTypes, "nodeTypes must not be null"));
  }

  public boolean hasErrors() {
    return diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
  }

  public Optional<VType> typeOf(VtlExpression expr) {
    return Optional.ofNullable(expressionTypes.get(expr));
  }

  public Optional<VType> typeOf(VtlNode node) {
    return Optional.ofNullable(nodeTypes.get(node));
  }
}
