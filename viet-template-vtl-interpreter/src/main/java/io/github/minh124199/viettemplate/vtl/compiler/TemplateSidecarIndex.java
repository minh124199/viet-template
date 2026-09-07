package io.github.minh124199.viettemplate.vtl.compiler;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.List;
import java.util.Objects;

/**
 * Sidecar metadata index recording debug, source span, and bytecode mappings for a compiled
 * template.
 */
public record TemplateSidecarIndex(
    TemplateId templateId,
    String generatedClassName,
    String fingerprint,
    List<SourceMapping> mappings) {

  public TemplateSidecarIndex {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(generatedClassName, "generatedClassName must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    mappings = (mappings != null) ? List.copyOf(mappings) : List.of();
  }

  public record SourceMapping(int bytecodeOffset, int lineNumber, SourceSpan span) {
    public SourceMapping {
      Objects.requireNonNull(span, "span must not be null");
    }
  }
}
