package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import java.io.Serializable;
import java.util.List;

/** Immutable result of an Ahead-Of-Time (AOT) template compilation invocation. */
public record TemplateAotResult(
    boolean isSuccess,
    int compiledCount,
    int skippedCount,
    int deletedCount,
    List<TemplateAotArtifact> artifacts,
    List<TemplateAotDiagnostic> diagnostics)
    implements Serializable {

  public TemplateAotResult {
    artifacts = (artifacts != null) ? List.copyOf(artifacts) : List.of();
    diagnostics = (diagnostics != null) ? List.copyOf(diagnostics) : List.of();
  }

  public boolean success() {
    return isSuccess;
  }

  public boolean hasErrors() {
    return diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
  }

  public boolean hasWarnings() {
    return diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.WARNING);
  }

  public static TemplateAotResult success(
      int compiledCount,
      int skippedCount,
      int deletedCount,
      List<TemplateAotArtifact> artifacts,
      List<TemplateAotDiagnostic> diagnostics) {
    return new TemplateAotResult(
        true, compiledCount, skippedCount, deletedCount, artifacts, diagnostics);
  }

  public static TemplateAotResult failure(
      List<TemplateAotDiagnostic> diagnostics,
      List<TemplateAotArtifact> artifacts,
      int compiledCount,
      int skippedCount,
      int deletedCount) {
    return new TemplateAotResult(
        false, compiledCount, skippedCount, deletedCount, artifacts, diagnostics);
  }

  public static TemplateAotResult failure(List<TemplateAotDiagnostic> diagnostics) {
    return new TemplateAotResult(false, 0, 0, 0, List.of(), diagnostics);
  }
}
