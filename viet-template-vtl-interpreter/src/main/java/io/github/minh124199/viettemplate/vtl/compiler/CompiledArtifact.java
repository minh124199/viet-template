package io.github.minh124199.viettemplate.vtl.compiler;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.List;
import java.util.Objects;

/** Immutable compiled template artifact containing binary class bytes, class name, and metadata. */
public record CompiledArtifact(
    TemplateId templateId,
    byte[] classBytes,
    String generatedClassName,
    String fingerprint,
    TemplateSidecarIndex sidecarIndex,
    List<Diagnostic> diagnostics) {

  public CompiledArtifact {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(classBytes, "classBytes must not be null");
    Objects.requireNonNull(generatedClassName, "generatedClassName must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    Objects.requireNonNull(sidecarIndex, "sidecarIndex must not be null");
    classBytes = classBytes.clone();
    diagnostics = (diagnostics != null) ? List.copyOf(diagnostics) : List.of();
  }

  @Override
  public byte[] classBytes() {
    return classBytes.clone();
  }
}
