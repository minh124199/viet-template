package io.github.minh124199.viettemplate.migration;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Metadata definition of an individual migration rule referencing differential compatibility
 * evidence, specification sections, and architectural decision records (ADRs).
 */
public record MigrationRule(
    String id,
    MigrationCategory category,
    MigrationSeverity defaultSeverity,
    MigrationClassification classification,
    String title,
    String velocityBehavior,
    String vietTemplateBehavior,
    String migrationAction,
    MigrationConfidence confidence,
    List<String> evidenceScenarioIds,
    String specSection,
    String adr)
    implements Serializable {

  public MigrationRule {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(defaultSeverity, "defaultSeverity must not be null");
    Objects.requireNonNull(classification, "classification must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(velocityBehavior, "velocityBehavior must not be null");
    Objects.requireNonNull(vietTemplateBehavior, "vietTemplateBehavior must not be null");
    Objects.requireNonNull(migrationAction, "migrationAction must not be null");
    Objects.requireNonNull(confidence, "confidence must not be null");
    evidenceScenarioIds =
        List.copyOf(
            Objects.requireNonNull(evidenceScenarioIds, "evidenceScenarioIds must not be null"));
    Objects.requireNonNull(specSection, "specSection must not be null");
    Objects.requireNonNull(adr, "adr must not be null");
  }
}
