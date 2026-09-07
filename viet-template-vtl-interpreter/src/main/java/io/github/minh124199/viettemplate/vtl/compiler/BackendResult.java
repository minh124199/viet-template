package io.github.minh124199.viettemplate.vtl.compiler;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.Diagnostic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of compiling a template via a {@link TemplateBackend}. */
public record BackendResult(
    CompilationStatus status,
    CompiledArtifact artifact,
    Class<? extends CompiledTemplate> compiledClass,
    CompiledTemplate templateInstance,
    List<Diagnostic> diagnostics) {

  public BackendResult {
    Objects.requireNonNull(status, "status must not be null");
    diagnostics = (diagnostics != null) ? List.copyOf(diagnostics) : List.of();
  }

  public boolean isSuccess() {
    return status.isSuccess()
        && artifact != null
        && diagnostics.stream().noneMatch(d -> d.severity() == io.github.minh124199.viettemplate.api.DiagnosticSeverity.ERROR);
  }

  public Optional<CompiledArtifact> optArtifact() {
    return Optional.ofNullable(artifact);
  }

  public Optional<CompiledTemplate> compiledTemplate() {
    return Optional.ofNullable(templateInstance);
  }

  public static BackendResult success(
      CompilationStatus status,
      CompiledArtifact artifact,
      Class<? extends CompiledTemplate> compiledClass,
      CompiledTemplate templateInstance) {
    return new BackendResult(status, artifact, compiledClass, templateInstance, List.of());
  }

  public static BackendResult failure(CompilationStatus status, List<Diagnostic> diagnostics) {
    return new BackendResult(status, null, null, null, diagnostics);
  }
}
