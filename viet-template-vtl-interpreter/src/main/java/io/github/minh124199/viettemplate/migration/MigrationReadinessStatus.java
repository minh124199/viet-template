package io.github.minh124199.viettemplate.migration;

/** Overall migration readiness status for templates and migration runs. */
public enum MigrationReadinessStatus {
  READY,
  READY_WITH_WARNINGS,
  ATTENTION_REQUIRED,
  BLOCKED
}
