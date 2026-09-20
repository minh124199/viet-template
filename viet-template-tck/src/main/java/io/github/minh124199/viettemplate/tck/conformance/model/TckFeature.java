package io.github.minh124199.viettemplate.tck.conformance.model;

import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata definition for a claimed language feature in the VTL Feature Claim Matrix.
 *
 * @param id durable feature identifier (e.g. {@code LEX-001})
 * @param category functional category (e.g. {@code LEXICAL})
 * @param name short descriptive title
 * @param description detailed description of the feature claim
 * @param status support status (SUPPORTED, INTENTIONAL_DIFFERENCE, EXTENSION)
 * @param classification behavioral classification (EXACT_MATCH, EXPECTED_DIFFERENCE,
 *     VIET_EXTENSION)
 * @param requiredBackends execution tiers required to support this feature
 * @param specificationReference specification document or section pointer
 * @param rationale architectural rationale for intentional differences or extensions
 */
public record TckFeature(
    String id,
    String category,
    String name,
    String description,
    String status,
    String classification,
    Set<ExecutionTier> requiredBackends,
    String specificationReference,
    String rationale) {

  public TckFeature {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(description, "description must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(classification, "classification must not be null");
    Objects.requireNonNull(specificationReference, "specificationReference must not be null");
    requiredBackends = (requiredBackends != null) ? Set.copyOf(requiredBackends) : Set.of();
  }
}
