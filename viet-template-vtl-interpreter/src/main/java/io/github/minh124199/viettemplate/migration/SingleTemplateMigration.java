package io.github.minh124199.viettemplate.migration;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Migration analysis results for a single discovered template. */
public record SingleTemplateMigration(
    TemplateId templateId,
    String relativePath,
    List<MigrationFinding> findings,
    List<TemplateAotDiagnostic> diagnostics,
    boolean valid,
    boolean compatible)
    implements Serializable {

  public SingleTemplateMigration {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(relativePath, "relativePath must not be null");
    findings = List.copyOf(Objects.requireNonNull(findings, "findings must not be null"));
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
  }

  public String relPath() {
    return relativePath();
  }
}
