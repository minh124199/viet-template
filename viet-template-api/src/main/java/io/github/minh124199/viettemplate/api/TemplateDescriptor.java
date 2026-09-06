package io.github.minh124199.viettemplate.api;

import java.util.Objects;

/** Metadata descriptor for a compiled or loaded template. */
public record TemplateDescriptor(TemplateId id, String executionTier) {

  public TemplateDescriptor {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(executionTier, "executionTier must not be null");
    if (executionTier.isBlank()) {
      throw new IllegalArgumentException("executionTier must not be blank");
    }
  }

  public static TemplateDescriptor of(TemplateId id, String executionTier) {
    return new TemplateDescriptor(id, executionTier);
  }
}
