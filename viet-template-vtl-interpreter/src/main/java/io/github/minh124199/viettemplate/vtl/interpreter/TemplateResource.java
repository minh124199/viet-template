package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.Objects;

/** Resolved template resource content and identity. */
public record TemplateResource(TemplateId templateId, String content) {
  public TemplateResource {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(content, "content must not be null");
  }
}
