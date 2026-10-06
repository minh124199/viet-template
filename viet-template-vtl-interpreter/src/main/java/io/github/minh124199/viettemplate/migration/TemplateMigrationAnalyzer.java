package io.github.minh124199.viettemplate.migration;

/** Public interface for analyzing templates for migration readiness from Apache Velocity. */
public interface TemplateMigrationAnalyzer {

  /**
   * Analyzes templates defined in the request and produces a comprehensive migration report.
   *
   * @param request the migration analysis request
   * @return immutable migration analysis report
   */
  MigrationReport analyze(TemplateMigrationRequest request);

  /**
   * Creates a default implementation of {@link TemplateMigrationAnalyzer}.
   *
   * @return default template migration analyzer
   */
  static TemplateMigrationAnalyzer create() {
    return new DefaultTemplateMigrationAnalyzer();
  }
}
