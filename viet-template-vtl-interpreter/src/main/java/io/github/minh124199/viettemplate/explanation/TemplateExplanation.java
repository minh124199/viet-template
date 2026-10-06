package io.github.minh124199.viettemplate.explanation;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import java.io.Serializable;
import java.util.List;

/**
 * Top-level structured compiler explanation result for an explanation request.
 *
 * @param success whether compilation and explanation completed without errors
 * @param totalTemplates total number of templates explained
 * @param totalExpressions total number of expressions explained across all templates
 * @param templates explanations for each explained template
 * @param diagnostics compiler diagnostics emitted during analysis
 */
public record TemplateExplanation(
    boolean success,
    int totalTemplates,
    int totalExpressions,
    List<SingleTemplateExplanation> templates,
    List<TemplateAotDiagnostic> diagnostics)
    implements Serializable {

  public TemplateExplanation {
    templates = templates != null ? List.copyOf(templates) : List.of();
    diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
  }

  /**
   * Formats this explanation into human-readable text report.
   *
   * @return formatted plain text explanation report
   */
  public String asText() {
    return ExplanationTextFormatter.format(this);
  }

  /**
   * Formats this explanation into deterministic JSON (formatVersion = 1).
   *
   * @return formatted deterministic JSON explanation document
   */
  public String asJson() {
    return ExplanationJsonFormatter.format(this);
  }
}
