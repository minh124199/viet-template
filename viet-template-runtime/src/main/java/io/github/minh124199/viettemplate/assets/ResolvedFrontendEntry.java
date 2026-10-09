package io.github.minh124199.viettemplate.assets;

import java.util.List;
import java.util.Objects;

/**
 * Immutable record representing a fully resolved frontend entry point.
 *
 * @param logicalEntry the original logical entry name
 * @param stylesheets ordered, deduplicated list of required stylesheet links
 * @param modulePreloads ordered, deduplicated list of module preload links
 * @param script the primary entry script tag
 * @param devClientScript optional Vite development HMR client script (e.g. {@code /@vite/client})
 */
public record ResolvedFrontendEntry(
    String logicalEntry,
    List<AssetStylesheet> stylesheets,
    List<AssetModulePreload> modulePreloads,
    AssetScript script,
    AssetScript devClientScript) {

  public ResolvedFrontendEntry {
    Objects.requireNonNull(logicalEntry, "logicalEntry must not be null");
    stylesheets = stylesheets != null ? List.copyOf(stylesheets) : List.of();
    modulePreloads = modulePreloads != null ? List.copyOf(modulePreloads) : List.of();
  }

  public ResolvedFrontendEntry(
      String logicalEntry,
      List<AssetStylesheet> stylesheets,
      List<AssetModulePreload> modulePreloads,
      AssetScript script) {
    this(logicalEntry, stylesheets, modulePreloads, script, null);
  }
}
