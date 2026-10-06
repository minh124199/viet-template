package io.github.minh124199.viettemplate.migration;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Comprehensive, immutable migration analysis report. */
public record MigrationReport(
    String sourceEngine,
    String sourceVersion,
    String targetEngine,
    String targetVersion,
    MigrationSummary summary,
    List<SingleTemplateMigration> templates,
    List<MigrationFinding> allFindings,
    List<TemplateAotDiagnostic> validationDiagnostics,
    MigrationReadinessStatus readinessStatus,
    boolean success)
    implements Serializable {

  public MigrationReport {
    Objects.requireNonNull(sourceEngine, "sourceEngine must not be null");
    Objects.requireNonNull(sourceVersion, "sourceVersion must not be null");
    Objects.requireNonNull(targetEngine, "targetEngine must not be null");
    Objects.requireNonNull(targetVersion, "targetVersion must not be null");
    Objects.requireNonNull(summary, "summary must not be null");
    templates = List.copyOf(Objects.requireNonNull(templates, "templates must not be null"));
    allFindings = List.copyOf(Objects.requireNonNull(allFindings, "allFindings must not be null"));
    validationDiagnostics =
        List.copyOf(
            Objects.requireNonNull(
                validationDiagnostics, "validationDiagnostics must not be null"));
    Objects.requireNonNull(readinessStatus, "readinessStatus must not be null");
  }

  public String asText() {
    return MigrationTextFormatter.format(this);
  }

  public String asJson() {
    return MigrationJsonFormatter.format(this);
  }
}
