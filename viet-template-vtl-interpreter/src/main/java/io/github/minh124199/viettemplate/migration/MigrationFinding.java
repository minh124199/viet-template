package io.github.minh124199.viettemplate.migration;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/** An individual migration finding detected during static analysis of a template. */
public record MigrationFinding(
    String ruleId,
    TemplateId templateId,
    SourceSpan sourceSpan,
    MigrationCategory category,
    MigrationSeverity severity,
    MigrationClassification classification,
    MigrationConfidence confidence,
    String construct,
    String message,
    String velocityBehavior,
    String vietTemplateBehavior,
    String migrationAction,
    Optional<DiagnosticCode> relatedDiagnosticCode)
    implements Serializable {

  public MigrationFinding {
    Objects.requireNonNull(ruleId, "ruleId must not be null");
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(sourceSpan, "sourceSpan must not be null");
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(severity, "severity must not be null");
    Objects.requireNonNull(classification, "classification must not be null");
    Objects.requireNonNull(confidence, "confidence must not be null");
    Objects.requireNonNull(construct, "construct must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(velocityBehavior, "velocityBehavior must not be null");
    Objects.requireNonNull(vietTemplateBehavior, "vietTemplateBehavior must not be null");
    Objects.requireNonNull(migrationAction, "migrationAction must not be null");
    Objects.requireNonNull(relatedDiagnosticCode, "relatedDiagnosticCode must not be null");
  }

  public static MigrationFinding of(
      String ruleId,
      TemplateId templateId,
      SourceSpan sourceSpan,
      MigrationCategory category,
      MigrationSeverity severity,
      MigrationClassification classification,
      MigrationConfidence confidence,
      String construct,
      String message,
      String velocityBehavior,
      String vietTemplateBehavior,
      String migrationAction,
      DiagnosticCode relatedDiagnosticCode) {
    return new MigrationFinding(
        ruleId,
        templateId,
        sourceSpan,
        category,
        severity,
        classification,
        confidence,
        construct,
        message,
        velocityBehavior,
        vietTemplateBehavior,
        migrationAction,
        Optional.ofNullable(relatedDiagnosticCode));
  }
}
