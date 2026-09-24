package io.github.minh124199.viettemplate.quarkus;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/** Configuration mapping for Viet Template Quarkus extension. */
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.viet-template")
public interface VietTemplateConfig {

  /** Base path within the classpath where templates are located. Defaults to "templates". */
  @WithDefault("templates")
  String path();

  /** Primary file suffix for templates. Defaults to ".vtl". */
  @WithDefault(".vtl")
  String suffix();

  /** Additional file suffixes for templates. Defaults to empty. */
  Optional<List<String>> additionalSuffixes();

  /**
   * Whether runtime template compilation is allowed. Defaults to false in production, true in
   * development mode.
   */
  @WithDefault("false")
  boolean runtimeCompilationEnabled();

  /** Maximum number of entries in the template compilation cache. Defaults to 500. */
  @WithDefault("500")
  int cacheMaxEntries();

  /** Negative cache time-to-live in milliseconds. Defaults to 5000ms. */
  @WithDefault("5000")
  long negativeCacheTtlMillis();

  /**
   * Policy governing evaluation behavior on undefined references. Supported values: SILENT, WARN,
   * ERROR. Defaults to "SILENT".
   */
  @WithDefault("SILENT")
  String undefinedReferencePolicy();

  /** Character encoding for reading templates. Defaults to "UTF-8". */
  @WithDefault("UTF-8")
  String encoding();

  /**
   * Returns effective additional suffixes as a list, or empty list if unconfigured.
   *
   * @return list of additional suffixes
   */
  default List<String> effectiveAdditionalSuffixes() {
    return additionalSuffixes().orElseGet(List::of);
  }
}
