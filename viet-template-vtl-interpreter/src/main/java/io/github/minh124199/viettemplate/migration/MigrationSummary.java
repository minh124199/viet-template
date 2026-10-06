package io.github.minh124199.viettemplate.migration;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/** Summary metrics and distribution for a migration analysis run. */
public record MigrationSummary(
    int totalTemplates,
    int validTemplates,
    int compatibleTemplates,
    int totalFindings,
    MigrationReadinessStatus readinessStatus,
    Map<MigrationSeverity, Integer> findingsBySeverity,
    Map<MigrationCategory, Integer> findingsByCategory)
    implements Serializable {

  public MigrationSummary {
    Objects.requireNonNull(readinessStatus, "readinessStatus must not be null");
    findingsBySeverity =
        Map.copyOf(
            Objects.requireNonNull(findingsBySeverity, "findingsBySeverity must not be null"));
    findingsByCategory =
        Map.copyOf(
            Objects.requireNonNull(findingsByCategory, "findingsByCategory must not be null"));
  }
}
