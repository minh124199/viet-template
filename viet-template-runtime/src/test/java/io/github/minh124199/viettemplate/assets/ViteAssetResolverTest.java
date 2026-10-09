package io.github.minh124199.viettemplate.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.assets.vite.ViteAssetMode;
import io.github.minh124199.viettemplate.assets.vite.ViteAssetResolver;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ViteAssetResolverTest {

  private static final String SAMPLE_MANIFEST =
      """
      {
        "src/pages/employees/index.ts": {
          "file": "assets/employees-C0de.js",
          "name": "employees",
          "src": "src/pages/employees/index.ts",
          "isEntry": true,
          "imports": ["_vendor-A1.js", "_ui-B2.js"],
          "css": ["assets/employees-E1.css"]
        },
        "_vendor-A1.js": {
          "file": "assets/vendor-A1.js",
          "name": "vendor",
          "imports": ["_common-C3.js"],
          "css": ["assets/vendor-V1.css"]
        },
        "_ui-B2.js": {
          "file": "assets/ui-B2.js",
          "name": "ui",
          "imports": ["_common-C3.js"],
          "css": ["assets/ui-U2.css"]
        },
        "_common-C3.js": {
          "file": "assets/common-C3.js",
          "name": "common",
          "css": ["assets/common-C3.css"]
        },
        "src/pages/payroll/Payroll.svelte": {
          "file": "assets/payroll-S9.js",
          "src": "src/pages/payroll/Payroll.svelte",
          "isEntry": true,
          "css": ["assets/payroll-P1.css"]
        },
        "src/images/logo.svg": {
          "file": "assets/logo-L1.svg",
          "src": "src/images/logo.svg"
        }
      }
      """;

  @Test
  void resolvesProductionEntryWithRecursiveImportsAndCssDeduplication() {
    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("/app/")
            .manifest(SAMPLE_MANIFEST)
            .build();

    ResolvedFrontendEntry entry = resolver.resolveEntry("src/pages/employees/index.ts");
    assertThat(entry).isNotNull();
    assertThat(entry.logicalEntry()).isEqualTo("src/pages/employees/index.ts");
    assertThat(entry.script().url()).isEqualTo("/app/assets/employees-C0de.js");
    assertThat(entry.script().module()).isTrue();
    assertThat(entry.devClientScript()).isNull();

    // Check collected CSS (entry + vendor + common + ui, with common deduplicated)
    assertThat(entry.stylesheets())
        .extracting(AssetStylesheet::url)
        .containsExactly(
            "/app/assets/employees-E1.css",
            "/app/assets/vendor-V1.css",
            "/app/assets/common-C3.css",
            "/app/assets/ui-U2.css");

    // Check preloads for imported chunks
    assertThat(entry.modulePreloads())
        .extracting(AssetModulePreload::url)
        .containsExactly(
            "/app/assets/vendor-A1.js", "/app/assets/common-C3.js", "/app/assets/ui-B2.js");
  }

  @Test
  void resolvesDirectSvelteEntryWithoutExtensionRestrictions() {
    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .manifest(SAMPLE_MANIFEST)
            .build();

    ResolvedFrontendEntry entry = resolver.resolveEntry("src/pages/payroll/Payroll.svelte");
    assertThat(entry).isNotNull();
    assertThat(entry.script().url()).isEqualTo("/assets/payroll-S9.js");
    assertThat(entry.stylesheets())
        .extracting(AssetStylesheet::url)
        .containsExactly("/assets/payroll-P1.css");
  }

  @Test
  void resolvesStaticAsset() {
    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .publicBase("https://cdn.example.com/")
            .manifest(SAMPLE_MANIFEST)
            .build();

    ResolvedAsset asset = resolver.resolveAsset("src/images/logo.svg");
    assertThat(asset.url()).isEqualTo("https://cdn.example.com/assets/logo-L1.svg");
  }

  @Test
  void unknownEntryThrowsWithHelpfulSuggestions() {
    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .manifest(SAMPLE_MANIFEST)
            .build();

    assertThatThrownBy(() -> resolver.resolveEntry("src/pages/employee/index.ts"))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e -> {
              AssetException ae = (AssetException) e;
              assertThat(ae.code()).isEqualTo(AssetDiagnosticCode.VT_ASSET_003);
              assertThat(ae.suggestions()).contains("src/pages/employees/index.ts");
              assertThat(ae.getMessage()).contains("Did you mean: src/pages/employees/index.ts");
            });
  }

  @Test
  void cycleInStaticImportsHandledGracefullyWithoutInfiniteLoop() {
    String cyclicManifest =
        """
        {
          "entry.js": {
            "file": "assets/entry.js",
            "isEntry": true,
            "imports": ["cycle1.js"],
            "css": ["assets/entry.css"]
          },
          "cycle1.js": {
            "file": "assets/cycle1.js",
            "imports": ["cycle2.js"],
            "css": ["assets/cycle1.css"]
          },
          "cycle2.js": {
            "file": "assets/cycle2.js",
            "imports": ["cycle1.js"],
            "css": ["assets/cycle2.css"]
          }
        }
        """;

    ViteAssetResolver resolver =
        ViteAssetResolver.builder().mode(ViteAssetMode.PRODUCTION).manifest(cyclicManifest).build();

    ResolvedFrontendEntry entry = resolver.resolveEntry("entry.js");
    assertThat(entry).isNotNull();
    assertThat(entry.stylesheets())
        .extracting(AssetStylesheet::url)
        .containsExactly("/assets/entry.css", "/assets/cycle1.css", "/assets/cycle2.css");
  }

  @Test
  void missingImportFailsFastWhenEnabled() {
    String brokenManifest =
        """
        {
          "entry.js": {
            "file": "assets/entry.js",
            "isEntry": true,
            "imports": ["missing.js"]
          }
        }
        """;

    assertThatThrownBy(
            () ->
                ViteAssetResolver.builder()
                    .mode(ViteAssetMode.PRODUCTION)
                    .failFast(true)
                    .manifest(brokenManifest)
                    .build())
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_004));
  }

  @Test
  void developmentModeGeneratesDevServerUrlsWithoutManifest() {
    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.DEVELOPMENT)
            .devServer("http://localhost:5173")
            .build();

    ResolvedFrontendEntry entry = resolver.resolveEntry("src/pages/employees/index.ts");
    assertThat(entry).isNotNull();
    assertThat(entry.script().url())
        .isEqualTo("http://localhost:5173/src/pages/employees/index.ts");
    assertThat(entry.devClientScript()).isNotNull();
    assertThat(entry.devClientScript().url()).isEqualTo("http://localhost:5173/@vite/client");
    assertThat(entry.stylesheets()).isEmpty();
    assertThat(entry.modulePreloads()).isEmpty();

    ResolvedAsset asset = resolver.resolveAsset("src/images/logo.svg");
    assertThat(asset.url()).isEqualTo("http://localhost:5173/src/images/logo.svg");
  }

  @Test
  void concurrentReadsAreThreadSafeUnderVirtualThreads() throws Exception {
    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .manifest(SAMPLE_MANIFEST)
            .build();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks = new java.util.ArrayList<Future<ResolvedFrontendEntry>>();
      for (int i = 0; i < 200; i++) {
        tasks.add(executor.submit(() -> resolver.resolveEntry("src/pages/employees/index.ts")));
      }
      for (var f : tasks) {
        ResolvedFrontendEntry entry = f.get();
        assertThat(entry.script().url()).isEqualTo("/assets/employees-C0de.js");
      }
    }
  }
}
