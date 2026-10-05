package io.github.minh124199.viettemplate.validation;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable result of build-time template validation. */
public record TemplateValidationResult(
    boolean success,
    int validatedCount,
    int errorCount,
    int warningCount,
    List<TemplateAotDiagnostic> diagnostics,
    TemplateDependencyGraph dependencyGraph)
    implements Serializable {

  private static final class EmptyDependencyGraph implements TemplateDependencyGraph, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public Set<TemplateDependency> dependenciesOf(TemplateId templateId) {
      return Set.of();
    }

    @Override
    public Set<TemplateId> dependentsOf(TemplateId templateId) {
      return Set.of();
    }

    @Override
    public Set<TemplateId> transitiveDependentsOf(TemplateId templateId) {
      return Set.of();
    }

    @Override
    public void replaceDependencies(TemplateId templateId, Set<TemplateDependency> dependencies) {}

    @Override
    public void removeTemplate(TemplateId templateId) {}

    @Override
    public void clear() {}

    @Override
    public int size() {
      return 0;
    }
  }

  private static final TemplateDependencyGraph EMPTY_GRAPH = new EmptyDependencyGraph();

  public TemplateValidationResult {
    diagnostics = (diagnostics != null) ? List.copyOf(diagnostics) : List.of();
    Objects.requireNonNull(dependencyGraph, "dependencyGraph must not be null");
  }

  public boolean isSuccess() {
    return success;
  }

  public boolean hasErrors() {
    return errorCount > 0;
  }

  public boolean hasWarnings() {
    return warningCount > 0;
  }

  public static TemplateValidationResult success(
      int validatedCount,
      int warningCount,
      List<TemplateAotDiagnostic> diagnostics,
      TemplateDependencyGraph dependencyGraph) {
    return new TemplateValidationResult(
        true, validatedCount, 0, warningCount, diagnostics, dependencyGraph);
  }

  public static TemplateValidationResult failure(
      int validatedCount,
      int errorCount,
      int warningCount,
      List<TemplateAotDiagnostic> diagnostics,
      TemplateDependencyGraph dependencyGraph) {
    return new TemplateValidationResult(
        false, validatedCount, errorCount, warningCount, diagnostics, dependencyGraph);
  }

  public static TemplateValidationResult failure(List<TemplateAotDiagnostic> diagnostics) {
    int errors = 0;
    int warnings = 0;
    if (diagnostics != null) {
      for (TemplateAotDiagnostic d : diagnostics) {
        if (d.severity() == DiagnosticSeverity.ERROR) {
          errors++;
        } else if (d.severity() == DiagnosticSeverity.WARNING) {
          warnings++;
        }
      }
    }
    return new TemplateValidationResult(false, 0, errors, warnings, diagnostics, EMPTY_GRAPH);
  }
}
