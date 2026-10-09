package io.github.minh124199.viettemplate.assets.vite;

import io.github.minh124199.viettemplate.assets.AssetDiagnosticCode;
import io.github.minh124199.viettemplate.assets.AssetException;
import io.github.minh124199.viettemplate.assets.AssetModulePreload;
import io.github.minh124199.viettemplate.assets.AssetResolver;
import io.github.minh124199.viettemplate.assets.AssetScript;
import io.github.minh124199.viettemplate.assets.AssetStylesheet;
import io.github.minh124199.viettemplate.assets.ResolvedAsset;
import io.github.minh124199.viettemplate.assets.ResolvedFrontendEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * High-performance, production-ready {@link AssetResolver} for Vite frontend projects.
 *
 * <p>Supports both {@link ViteAssetMode#PRODUCTION} (with precomputed, immutable lookup tables
 * generated from {@code .vite/manifest.json}) and {@link ViteAssetMode#DEVELOPMENT} (generating
 * direct Vite dev-server HMR URLs).
 */
public final class ViteAssetResolver implements AssetResolver {

  private final ViteAssetMode mode;
  private final String publicBase;
  private final String devServerOrigin;
  private final Map<String, ResolvedFrontendEntry> entryMap;
  private final Map<String, ResolvedAsset> assetMap;

  private ViteAssetResolver(
      ViteAssetMode mode,
      String publicBase,
      String devServerOrigin,
      Map<String, ResolvedFrontendEntry> entryMap,
      Map<String, ResolvedAsset> assetMap) {
    this.mode = mode;
    this.publicBase = publicBase;
    this.devServerOrigin = devServerOrigin;
    this.entryMap = entryMap != null ? Map.copyOf(entryMap) : Collections.emptyMap();
    this.assetMap = assetMap != null ? Map.copyOf(assetMap) : Collections.emptyMap();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ViteAssetResolver development(String devServerOrigin) {
    return builder().mode(ViteAssetMode.DEVELOPMENT).devServer(devServerOrigin).build();
  }

  public static Builder productionBuilder() {
    return builder().mode(ViteAssetMode.PRODUCTION);
  }

  @Override
  public ResolvedFrontendEntry resolveEntry(String logicalEntry) {
    if (logicalEntry == null || logicalEntry.isBlank()) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_003,
          logicalEntry,
          "Logical entry must not be null or blank.");
    }

    if (this.mode == ViteAssetMode.DEVELOPMENT) {
      AssetPathValidator.validateRelativePath(logicalEntry);
      String cleanEntry = logicalEntry.trim();
      if (cleanEntry.startsWith("/")) {
        cleanEntry = cleanEntry.substring(1);
      }
      AssetScript clientScript = new AssetScript(this.devServerOrigin + "/@vite/client", true);
      AssetScript entryScript = new AssetScript(this.devServerOrigin + "/" + cleanEntry, true);
      return new ResolvedFrontendEntry(
          logicalEntry, List.of(), List.of(), entryScript, clientScript);
    }

    ResolvedFrontendEntry resolved = this.entryMap.get(logicalEntry);
    if (resolved != null) {
      return resolved;
    }

    // Try normalized match (strip leading /)
    if (logicalEntry.startsWith("/")) {
      resolved = this.entryMap.get(logicalEntry.substring(1));
      if (resolved != null) {
        return resolved;
      }
    }

    List<String> suggestions = findClosestEntries(logicalEntry, this.entryMap.keySet());
    throw new AssetException(
        AssetDiagnosticCode.VT_ASSET_003,
        logicalEntry,
        "Frontend entrypoint not found in Vite manifest.",
        suggestions,
        null);
  }

  @Override
  public ResolvedAsset resolveAsset(String logicalAsset) {
    if (logicalAsset == null || logicalAsset.isBlank()) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_003,
          logicalAsset,
          "Logical asset must not be null or blank.");
    }

    if (this.mode == ViteAssetMode.DEVELOPMENT) {
      AssetPathValidator.validateRelativePath(logicalAsset);
      String cleanAsset = logicalAsset.trim();
      if (cleanAsset.startsWith("/")) {
        cleanAsset = cleanAsset.substring(1);
      }
      return new ResolvedAsset(logicalAsset, this.devServerOrigin + "/" + cleanAsset);
    }

    ResolvedAsset resolved = this.assetMap.get(logicalAsset);
    if (resolved != null) {
      return resolved;
    }

    if (logicalAsset.startsWith("/")) {
      resolved = this.assetMap.get(logicalAsset.substring(1));
      if (resolved != null) {
        return resolved;
      }
    }

    // Also check if this asset is registered as an entry
    ResolvedFrontendEntry entry = this.entryMap.get(logicalAsset);
    if (entry != null && entry.script() != null) {
      return new ResolvedAsset(logicalAsset, entry.script().url());
    }

    List<String> suggestions = findClosestEntries(logicalAsset, this.assetMap.keySet());
    throw new AssetException(
        AssetDiagnosticCode.VT_ASSET_003,
        logicalAsset,
        "Static asset not found in Vite manifest.",
        suggestions,
        null);
  }

  public ViteAssetMode mode() {
    return mode;
  }

  public String publicBase() {
    return publicBase;
  }

  public String devServerOrigin() {
    return devServerOrigin;
  }

  public Set<String> registeredEntries() {
    return entryMap.keySet();
  }

  public Set<String> registeredAssets() {
    return assetMap.keySet();
  }

  private static List<String> findClosestEntries(String target, Set<String> available) {
    if (available.isEmpty()) {
      return Collections.emptyList();
    }
    List<Map.Entry<String, Integer>> distances = new ArrayList<>();
    String lowerTarget = target.toLowerCase();
    for (String cand : available) {
      int dist = levenshteinDistance(lowerTarget, cand.toLowerCase());
      if (dist <= 4 || cand.contains(target) || target.contains(cand)) {
        distances.add(Map.entry(cand, dist));
      }
    }
    distances.sort(Comparator.comparingInt(Map.Entry::getValue));
    List<String> suggestions = new ArrayList<>();
    for (int i = 0; i < Math.min(3, distances.size()); i++) {
      suggestions.add(distances.get(i).getKey());
    }
    return suggestions;
  }

  private static int levenshteinDistance(String a, String b) {
    int[] costs = new int[b.length() + 1];
    for (int j = 0; j < costs.length; j++) {
      costs[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      costs[0] = i;
      int nw = i - 1;
      for (int j = 1; j <= b.length(); j++) {
        int cj =
            Math.min(
                1 + Math.min(costs[j], costs[j - 1]),
                a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
        nw = costs[j];
        costs[j] = cj;
      }
    }
    return costs[b.length()];
  }

  /** Builder for constructing {@link ViteAssetResolver} instances. */
  public static final class Builder {
    private ViteAssetMode mode = ViteAssetMode.PRODUCTION;
    private String publicBase = "/";
    private String devServerOrigin = "http://localhost:5173";
    private boolean modulePreload = true;
    private boolean failFast = true;
    private Supplier<InputStream> manifestSupplier;
    private String manifestJson;

    private Builder() {}

    public Builder mode(ViteAssetMode mode) {
      this.mode = Objects.requireNonNull(mode, "mode must not be null");
      return this;
    }

    public Builder publicBase(String publicBase) {
      this.publicBase = publicBase;
      return this;
    }

    public Builder devServer(String devServerOrigin) {
      this.devServerOrigin = devServerOrigin;
      return this;
    }

    public Builder modulePreload(boolean modulePreload) {
      this.modulePreload = modulePreload;
      return this;
    }

    public Builder failFast(boolean failFast) {
      this.failFast = failFast;
      return this;
    }

    public Builder manifest(String manifestJson) {
      this.manifestJson = manifestJson;
      return this;
    }

    public Builder manifest(InputStream inputStream) {
      Objects.requireNonNull(inputStream, "inputStream must not be null");
      try {
        this.manifestJson =
            new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new AssetException(
            AssetDiagnosticCode.VT_ASSET_001,
            "Failed reading manifest input stream",
            e.getMessage(),
            null,
            e);
      }
      return this;
    }

    public Builder manifest(Path manifestPath) {
      Objects.requireNonNull(manifestPath, "manifestPath must not be null");
      if (!Files.exists(manifestPath)) {
        throw new AssetException(
            AssetDiagnosticCode.VT_ASSET_001,
            manifestPath.toString(),
            "Manifest file not found at path: " + manifestPath);
      }
      try {
        this.manifestJson = Files.readString(manifestPath);
      } catch (IOException e) {
        throw new AssetException(
            AssetDiagnosticCode.VT_ASSET_001,
            manifestPath.toString(),
            "Failed reading manifest file at path: " + manifestPath,
            null,
            e);
      }
      return this;
    }

    public Builder manifestSupplier(Supplier<InputStream> supplier) {
      this.manifestSupplier = supplier;
      return this;
    }

    public ViteAssetResolver build() {
      String validatedPublicBase =
          AssetPathValidator.validateAndNormalizePublicBase(this.publicBase);
      String validatedDevServer =
          (this.devServerOrigin != null)
              ? AssetPathValidator.validateDevServerOrigin(this.devServerOrigin)
              : "http://localhost:5173";

      if (this.mode == ViteAssetMode.DEVELOPMENT) {
        return new ViteAssetResolver(
            ViteAssetMode.DEVELOPMENT, validatedPublicBase, validatedDevServer, Map.of(), Map.of());
      }

      // In PRODUCTION mode, resolve manifest
      String jsonContent = this.manifestJson;
      if (jsonContent == null && this.manifestSupplier != null) {
        try (InputStream is = this.manifestSupplier.get()) {
          if (is != null) {
            jsonContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
          }
        } catch (IOException e) {
          throw new AssetException(
              AssetDiagnosticCode.VT_ASSET_001,
              "Failed loading manifest from supplier: " + e.getMessage(),
              e.getMessage(),
              null,
              e);
        }
      }

      if (jsonContent == null || jsonContent.isBlank()) {
        if (this.failFast) {
          throw new AssetException(
              AssetDiagnosticCode.VT_ASSET_001,
              "No manifest provided for ViteAssetResolver in PRODUCTION mode.");
        }
        return new ViteAssetResolver(
            ViteAssetMode.PRODUCTION, validatedPublicBase, validatedDevServer, Map.of(), Map.of());
      }

      Map<String, ViteRawEntry> rawEntries = ViteManifestParser.parse(jsonContent);
      Map<String, ResolvedFrontendEntry> entryMap = new HashMap<>();
      Map<String, ResolvedAsset> assetMap = new HashMap<>();

      // Precompute all entries
      for (Map.Entry<String, ViteRawEntry> e : rawEntries.entrySet()) {
        String key = e.getKey();
        ViteRawEntry raw = e.getValue();

        // Register static assets defined in raw entry
        for (String assetPath : raw.assets()) {
          String url = AssetPathValidator.resolveAssetUrl(validatedPublicBase, assetPath);
          assetMap.put(assetPath, new ResolvedAsset(assetPath, url));
        }

        // If the entry represents a static asset by filename (e.g. logo.svg)
        if (isStaticAssetKey(raw.file())) {
          String url = AssetPathValidator.resolveAssetUrl(validatedPublicBase, raw.file());
          assetMap.put(key, new ResolvedAsset(key, url));
        }

        // Resolve graph for this entry
        LinkedHashSet<String> cssUrls = new LinkedHashSet<>();
        LinkedHashSet<String> preloadUrls = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        Set<String> activePath = new HashSet<>();

        traverseStaticGraph(
            key,
            rawEntries,
            validatedPublicBase,
            this.modulePreload,
            this.failFast,
            visited,
            activePath,
            cssUrls,
            preloadUrls,
            true);

        List<AssetStylesheet> stylesheets = new ArrayList<>();
        for (String cssUrl : cssUrls) {
          stylesheets.add(new AssetStylesheet(cssUrl));
        }

        List<AssetModulePreload> preloads = new ArrayList<>();
        for (String preloadUrl : preloadUrls) {
          preloads.add(new AssetModulePreload(preloadUrl));
        }

        String entryFileUrl = AssetPathValidator.resolveAssetUrl(validatedPublicBase, raw.file());
        AssetScript script = new AssetScript(entryFileUrl, true);

        ResolvedFrontendEntry resolvedEntry =
            new ResolvedFrontendEntry(key, stylesheets, preloads, script);
        entryMap.put(key, resolvedEntry);
      }

      return new ViteAssetResolver(
          ViteAssetMode.PRODUCTION, validatedPublicBase, validatedDevServer, entryMap, assetMap);
    }

    private static void traverseStaticGraph(
        String currentKey,
        Map<String, ViteRawEntry> allEntries,
        String publicBase,
        boolean includePreload,
        boolean failFast,
        Set<String> visited,
        Set<String> activePath,
        LinkedHashSet<String> cssUrls,
        LinkedHashSet<String> preloadUrls,
        boolean isRoot) {

      if (activePath.contains(currentKey)) {
        // Cycle detected: safe dedupe without recursion explosion
        return;
      }

      if (visited.contains(currentKey)) {
        return;
      }

      ViteRawEntry current = allEntries.get(currentKey);
      if (current == null) {
        if (failFast) {
          throw new AssetException(
              AssetDiagnosticCode.VT_ASSET_004,
              currentKey,
              "Referenced manifest chunk or import missing: '" + currentKey + "'.");
        }
        return;
      }

      visited.add(currentKey);
      activePath.add(currentKey);

      // Collect CSS from current chunk
      for (String css : current.css()) {
        String cssUrl = AssetPathValidator.resolveAssetUrl(publicBase, css);
        cssUrls.add(cssUrl);
      }

      // If imported chunk (not root), collect as modulepreload
      if (!isRoot && includePreload) {
        String preloadUrl = AssetPathValidator.resolveAssetUrl(publicBase, current.file());
        preloadUrls.add(preloadUrl);
      }

      // Traverse static imports recursively
      for (String importedKey : current.imports()) {
        traverseStaticGraph(
            importedKey,
            allEntries,
            publicBase,
            includePreload,
            failFast,
            visited,
            activePath,
            cssUrls,
            preloadUrls,
            false);
      }

      activePath.remove(currentKey);
    }

    private static boolean isStaticAssetKey(String filename) {
      if (filename == null) {
        return false;
      }
      String lower = filename.toLowerCase();
      return lower.endsWith(".svg")
          || lower.endsWith(".png")
          || lower.endsWith(".jpg")
          || lower.endsWith(".jpeg")
          || lower.endsWith(".gif")
          || lower.endsWith(".webp")
          || lower.endsWith(".woff")
          || lower.endsWith(".woff2")
          || lower.endsWith(".ttf")
          || lower.endsWith(".wasm");
    }
  }
}
