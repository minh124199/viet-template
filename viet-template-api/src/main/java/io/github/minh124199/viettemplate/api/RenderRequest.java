package io.github.minh124199.viettemplate.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Request context provided to {@link RenderContextContributor} instances during context enrichment.
 *
 * @param templateId identifier of the template being rendered
 * @param userModel user-supplied model context
 * @param attributes optional request-scoped attributes
 */
public record RenderRequest(
    TemplateId templateId, RenderContext userModel, Map<String, Object> attributes) {

  public RenderRequest {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(userModel, "userModel must not be null");
    attributes =
        attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap();
  }

  public static RenderRequest of(TemplateId templateId, RenderContext userModel) {
    return new RenderRequest(templateId, userModel, Collections.emptyMap());
  }

  public static RenderRequest of(
      TemplateId templateId, RenderContext userModel, Map<String, Object> attributes) {
    return new RenderRequest(templateId, userModel, attributes);
  }
}
