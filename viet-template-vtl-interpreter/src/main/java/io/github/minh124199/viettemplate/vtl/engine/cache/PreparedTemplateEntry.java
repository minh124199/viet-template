package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.Objects;

/**
 * Cached prepared template entry bundling the canonical {@link Template} instance, its execution
 * handle, generation metadata, and descriptor.
 */
public record PreparedTemplateEntry(
    TemplateId templateId,
    long generation,
    CompileCacheKey key,
    CompiledTemplateHandle handle,
    Template templateInstance,
    TemplateDescriptor descriptor) {

  public PreparedTemplateEntry {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(handle, "handle must not be null");
    Objects.requireNonNull(templateInstance, "templateInstance must not be null");
    Objects.requireNonNull(descriptor, "descriptor must not be null");
  }
}
