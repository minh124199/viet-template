package io.github.minh124199.viettemplate.api;

import java.util.Optional;

/**
 * Capability interface for template repositories that support freshness checking and change detection.
 */
@FunctionalInterface
public interface TemplateFreshnessProvider {

  /**
   * Returns a freshness token for the template identified by {@code id} if present in this repository.
   *
   * @param id the normalized template identifier
   * @return an {@link Optional} containing the freshness token if the template is present, or empty if absent or indeterminate
   */
  Optional<FreshnessToken> freshnessToken(TemplateId id);
}
