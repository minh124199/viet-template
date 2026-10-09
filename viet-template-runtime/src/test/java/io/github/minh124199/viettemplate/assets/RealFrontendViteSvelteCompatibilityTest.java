package io.github.minh124199.viettemplate.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.assets.vite.ViteAssetMode;
import io.github.minh124199.viettemplate.assets.vite.ViteAssetResolver;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Real-toolchain qualification test asserting that {@link ViteAssetResolver} correctly consumes and
 * resolves actual production assets emitted by the pinned Vite + TypeScript + Svelte build.
 *
 * <p>When executed in standard offline/Java builds without prior frontend build artifacts, this
 * test gracefully skips using {@link Assumptions#assumeTrue(boolean, String)}.
 */
public class RealFrontendViteSvelteCompatibilityTest {

  private static Path manifestPath;
  private static Path distDir;

  @BeforeAll
  static void locateRealManifest() {
    manifestPath = resolveManifestPath();
    if (manifestPath != null && Files.isRegularFile(manifestPath)) {
      distDir = manifestPath.getParent().getParent(); // dist/.vite/manifest.json -> dist
    }
  }

  public static Path resolveManifestPath() {
    String prop = System.getProperty("viet-template.frontend.manifest");
    if (prop != null && !prop.isBlank()) {
      Path p = Path.of(prop.trim());
      if (Files.isRegularFile(p)) {
        return p.toAbsolutePath().normalize();
      }
    }

    String env = System.getenv("VIET_TEMPLATE_FRONTEND_MANIFEST");
    if (env != null && !env.isBlank()) {
      Path p = Path.of(env.trim());
      if (Files.isRegularFile(p)) {
        return p.toAbsolutePath().normalize();
      }
    }

    List<Path> candidates =
        List.of(
            Path.of("examples/frontend-svelte-islands/dist/.vite/manifest.json"),
            Path.of("../examples/frontend-svelte-islands/dist/.vite/manifest.json"),
            Path.of("../../examples/frontend-svelte-islands/dist/.vite/manifest.json"));

    for (Path candidate : candidates) {
      if (Files.isRegularFile(candidate)) {
        return candidate.toAbsolutePath().normalize();
      }
    }
    return null;
  }

  private void assumeRealBuildAvailable() {
    Assumptions.assumeTrue(
        manifestPath != null && Files.isRegularFile(manifestPath),
        "Real Vite/Svelte manifest not found at "
            + manifestPath
            + " (run scripts/verify-frontend-vite-svelte.sh to produce build artifacts). Skipping"
            + " live qualification.");
  }

  @Test
  @DisplayName(
      "Qualifies real Vite production manifest entry resolution and physical artifact existence")
  void testRealEmployeesEntryResolutionAndArtifacts() {
    assumeRealBuildAvailable();

    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("/")
            .manifest(manifestPath)
            .build();

    // 13.1 Entry exists
    String logicalEntry = "src/pages/employees/index.ts";
    ResolvedFrontendEntry entry = resolver.resolveEntry(logicalEntry);
    assertThat(entry).isNotNull();
    assertThat(entry.logicalEntry()).isEqualTo(logicalEntry);

    // 13.2 JS/module output
    assertThat(entry.script()).isNotNull();
    assertThat(entry.script().module()).isTrue();
    String scriptUrl = entry.script().url();
    assertThat(scriptUrl).startsWith("/assets/employees-").endsWith(".js");
    Path scriptFile = distDir.resolve(scriptUrl.substring(1));
    assertThat(Files.isRegularFile(scriptFile))
        .withFailMessage("Generated JS file must exist at: %s", scriptFile)
        .isTrue();

    // 13.3 CSS output
    assertThat(entry.stylesheets())
        .withFailMessage("Emitted CSS expected for employees island")
        .isNotEmpty();
    for (AssetStylesheet sheet : entry.stylesheets()) {
      assertThat(sheet.url()).startsWith("/assets/").endsWith(".css");
      Path cssFile = distDir.resolve(sheet.url().substring(1));
      assertThat(Files.isRegularFile(cssFile))
          .withFailMessage("Generated CSS file must exist at: %s", cssFile)
          .isTrue();
    }

    // 13.4 Static imports / shared chunks
    assertThat(entry.modulePreloads())
        .withFailMessage("Expected preloads for shared module chunks")
        .isNotEmpty();
    for (AssetModulePreload preload : entry.modulePreloads()) {
      assertThat(preload.url()).startsWith("/assets/").endsWith(".js");
      Path chunkFile = distDir.resolve(preload.url().substring(1));
      assertThat(Files.isRegularFile(chunkFile))
          .withFailMessage("Emitted chunk file must exist at: %s", chunkFile)
          .isTrue();
    }
  }

  @Test
  @DisplayName("Qualifies real Vite counter island entry and shared chunk consumption")
  void testRealCounterEntryResolution() {
    assumeRealBuildAvailable();

    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("/")
            .manifest(manifestPath)
            .build();

    String logicalEntry = "src/pages/counter/index.ts";
    ResolvedFrontendEntry entry = resolver.resolveEntry(logicalEntry);
    assertThat(entry).isNotNull();
    assertThat(entry.logicalEntry()).isEqualTo(logicalEntry);
    assertThat(entry.script()).isNotNull();
    assertThat(entry.script().url()).startsWith("/assets/counter-").endsWith(".js");

    Path scriptFile = distDir.resolve(entry.script().url().substring(1));
    assertThat(Files.isRegularFile(scriptFile)).isTrue();

    assertThat(entry.stylesheets()).isNotEmpty();
    Path cssFile = distDir.resolve(entry.stylesheets().get(0).url().substring(1));
    assertThat(Files.isRegularFile(cssFile)).isTrue();
  }

  @Test
  @DisplayName(
      "13.7 & 14 Framework neutrality: qualifies direct .svelte file entry without extension"
          + " whitelist")
  void testDirectSvelteEntryResolution() {
    assumeRealBuildAvailable();

    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("/")
            .manifest(manifestPath)
            .build();

    String directSvelteEntry = "src/pages/payroll/Payroll.svelte";
    ResolvedFrontendEntry entry = resolver.resolveEntry(directSvelteEntry);
    assertThat(entry).isNotNull();
    assertThat(entry.logicalEntry()).isEqualTo(directSvelteEntry);
    assertThat(entry.script()).isNotNull();
    assertThat(entry.script().url()).startsWith("/assets/payroll-").endsWith(".js");

    Path scriptFile = distDir.resolve(entry.script().url().substring(1));
    assertThat(Files.isRegularFile(scriptFile)).isTrue();

    assertThat(entry.stylesheets()).isNotEmpty();
    Path cssFile = distDir.resolve(entry.stylesheets().get(0).url().substring(1));
    assertThat(Files.isRegularFile(cssFile)).isTrue();
  }

  @Test
  @DisplayName("13.5 & 34 Deduplication across multiple entries and HTML tag generation")
  void testDeduplicationAndHtmlMarkupGeneration() {
    assumeRealBuildAvailable();

    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("/")
            .manifest(manifestPath)
            .build();

    FrontendAssets assets = new FrontendAssets(resolver);

    // Multi-entry head rendering
    SafeHtml head = assets.head("src/pages/employees/index.ts", "src/pages/counter/index.ts");
    String headHtml = head.toString();

    // Verify all emitted links exist and each preload appears exactly once
    Set<String> seenHrefs = new LinkedHashSet<>();
    for (String line : headHtml.lines().toList()) {
      line = line.trim();
      if (line.startsWith("<link rel=\"modulepreload\"")) {
        int hrefStart = line.indexOf("href=\"") + 6;
        int hrefEnd = line.indexOf("\"", hrefStart);
        String href = line.substring(hrefStart, hrefEnd);
        assertThat(seenHrefs.add(href))
            .withFailMessage("Duplicate modulepreload link generated: %s", href)
            .isTrue();
      }
    }

    // Verify body script tag
    SafeHtml body = assets.body("src/pages/employees/index.ts");
    assertThat(body.toString())
        .startsWith("<script type=\"module\" src=\"/assets/employees-")
        .endsWith("></script>\n");
  }

  @Test
  @DisplayName("13.6 Stable ordering across repeated resolution")
  void testStableOrderingAcrossRepeatedResolution() {
    assumeRealBuildAvailable();

    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("/")
            .manifest(manifestPath)
            .build();

    ResolvedFrontendEntry first = resolver.resolveEntry("src/pages/employees/index.ts");
    ResolvedFrontendEntry second = resolver.resolveEntry("src/pages/employees/index.ts");

    assertThat(first.stylesheets()).isEqualTo(second.stylesheets());
    assertThat(first.modulePreloads()).isEqualTo(second.modulePreloads());
    assertThat(first.script().url()).isEqualTo(second.script().url());
  }

  @Test
  @DisplayName("13.8 Public URL safety and custom CDN base")
  void testPublicUrlSafetyWithCdnBase() {
    assumeRealBuildAvailable();

    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("https://cdn.example.com/assets-app/")
            .manifest(manifestPath)
            .build();

    ResolvedFrontendEntry entry = resolver.resolveEntry("src/pages/employees/index.ts");
    assertThat(entry.script().url())
        .startsWith("https://cdn.example.com/assets-app/assets/employees-");
    for (AssetStylesheet sheet : entry.stylesheets()) {
      assertThat(sheet.url()).startsWith("https://cdn.example.com/assets-app/assets/");
    }
  }

  @Test
  @DisplayName("Unknown entry diagnostics propose closest valid entries")
  void testUnknownEntryProvidesHelpfulSuggestions() {
    assumeRealBuildAvailable();

    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("/")
            .manifest(manifestPath)
            .build();

    assertThatThrownBy(() -> resolver.resolveEntry("src/pages/employees/index.js"))
        .isInstanceOf(AssetException.class)
        .satisfies(
            ex -> {
              AssetException ae = (AssetException) ex;
              assertThat(ae.code()).isEqualTo(AssetDiagnosticCode.VT_ASSET_003);
              assertThat(ae.suggestions()).contains("src/pages/employees/index.ts");
            });
  }

  /** CLI runner allowing direct invocation by Python or shell verification scripts. */
  public static void main(String[] args) throws Exception {
    Path chosenManifest = args.length > 0 ? Path.of(args[0]) : resolveManifestPath();
    if (chosenManifest == null || !Files.isRegularFile(chosenManifest)) {
      System.err.println("[FAIL] Manifest file does not exist: " + chosenManifest);
      System.exit(1);
    }

    manifestPath = chosenManifest.toAbsolutePath().normalize();
    distDir =
        args.length > 1
            ? Path.of(args[1]).toAbsolutePath().normalize()
            : manifestPath.getParent().getParent();

    System.out.println("[INFO] Qualifying Vite manifest at: " + manifestPath);
    System.out.println("[INFO] Assets directory at: " + distDir);

    RealFrontendViteSvelteCompatibilityTest runner = new RealFrontendViteSvelteCompatibilityTest();
    runner.testRealEmployeesEntryResolutionAndArtifacts();
    runner.testRealCounterEntryResolution();
    runner.testDirectSvelteEntryResolution();
    runner.testDeduplicationAndHtmlMarkupGeneration();
    runner.testStableOrderingAcrossRepeatedResolution();
    runner.testPublicUrlSafetyWithCdnBase();
    runner.testUnknownEntryProvidesHelpfulSuggestions();

    System.out.println("[PASS] Real Vite + Svelte frontend manifest qualification successful.");
  }
}
