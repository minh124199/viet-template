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
   * Compatibility profile for template language feature availability and security boundaries.
   * Supported values: VTL_CORE, VTL_MIGRATION, VTL_DYNAMIC, VTL_SAFE. Defaults to "VTL_MIGRATION".
   */
  @WithDefault("VTL_MIGRATION")
  String profile();

  /**
   * Returns effective additional suffixes as a list, or empty list if unconfigured.
   *
   * @return list of additional suffixes
   */
  default List<String> effectiveAdditionalSuffixes() {
    return additionalSuffixes().orElseGet(List::of);
  }

  /** Whether frontend asset integration is enabled. Defaults to false. */
  @io.smallrye.config.WithName("assets.enabled")
  @WithDefault("false")
  boolean assetsEnabled();

  /** Frontend asset provider type. Defaults to "vite". */
  @io.smallrye.config.WithName("assets.provider")
  @WithDefault("vite")
  String assetsProvider();

  /** Asset resolution mode: "production" or "development". Defaults to "production". */
  @io.smallrye.config.WithName("assets.mode")
  @WithDefault("production")
  String assetsMode();

  /** Public base URL or CDN path prefix for asset tags. Defaults to "/". */
  @io.smallrye.config.WithName("assets.public-base")
  @WithDefault("/")
  String assetsPublicBase();

  /** Whether module preload tags are emitted in the HTML head. Defaults to true. */
  @io.smallrye.config.WithName("assets.module-preload")
  @WithDefault("true")
  boolean assetsModulePreload();

  /**
   * Whether to fail startup if the production manifest is missing or malformed. Defaults to true.
   */
  @io.smallrye.config.WithName("assets.fail-fast")
  @WithDefault("true")
  boolean assetsFailFast();

  /**
   * Resource location of the Vite production manifest file on classpath. Defaults to
   * "static/.vite/manifest.json".
   */
  @io.smallrye.config.WithName("assets.manifest-location")
  @WithDefault("static/.vite/manifest.json")
  String assetsManifestLocation();

  /** Vite dev server URL when in development mode. Defaults to "http://localhost:5173". */
  @io.smallrye.config.WithName("assets.dev-server")
  @WithDefault("http://localhost:5173")
  String assetsDevServer();
}
