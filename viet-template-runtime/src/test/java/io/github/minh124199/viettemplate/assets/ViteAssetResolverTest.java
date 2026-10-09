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

  @Test
  void resolvesCssEntrypointInProductionMode() {
    String manifestWithCssEntry =
        """
        {
          "src/styles/theme.css": {
            "file": "assets/theme-ABC1.css",
            "src": "src/styles/theme.css",
            "isEntry": true
          }
        }
        """;

    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.PRODUCTION)
            .manifest(manifestWithCssEntry)
            .build();

    ResolvedFrontendEntry entry = resolver.resolveEntry("src/styles/theme.css");
    assertThat(entry).isNotNull();
    assertThat(entry.script()).isNull();
    assertThat(entry.stylesheets())
        .extracting(AssetStylesheet::url)
        .containsExactly("/assets/theme-ABC1.css");
  }

  @Test
  void resolvesCssEntrypointInDevelopmentMode() {
    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.DEVELOPMENT)
            .devServer("http://localhost:5173")
            .build();

    ResolvedFrontendEntry entry = resolver.resolveEntry("src/styles/theme.css");
    assertThat(entry).isNotNull();
    assertThat(entry.script()).isNull();
    assertThat(entry.devClientScript()).isNotNull();
    assertThat(entry.stylesheets())
        .extracting(AssetStylesheet::url)
        .containsExactly("http://localhost:5173/src/styles/theme.css");
  }

  @Test
  void resolvesStaticAssetsWithoutExtensionWhitelist() {
    String manifest =
        """
        {
          "src/icons/fav.ico": {
            "file": "assets/fav-123.ico",
            "src": "src/icons/fav.ico"
          },
          "src/images/hero.avif": {
            "file": "assets/hero-456.avif",
            "src": "src/images/hero.avif"
          },
          "src/docs/manual.pdf": {
            "file": "assets/manual-789.pdf",
            "src": "src/docs/manual.pdf"
          }
        }
        """;

    ViteAssetResolver resolver =
        ViteAssetResolver.builder().mode(ViteAssetMode.PRODUCTION).manifest(manifest).build();

    assertThat(resolver.resolveAsset("src/icons/fav.ico").url()).isEqualTo("/assets/fav-123.ico");
    assertThat(resolver.resolveAsset("src/images/hero.avif").url())
        .isEqualTo("/assets/hero-456.avif");
    assertThat(resolver.resolveAsset("src/docs/manual.pdf").url())
        .isEqualTo("/assets/manual-789.pdf");
  }

  @Test
  void deepTraversalExceedingMaxDepthThrowsVtAsset005() {
    StringBuilder sb = new StringBuilder("{\n");
    sb.append(
        "  \"entry.js\": { \"file\": \"assets/entry.js\", \"isEntry\": true, \"imports\":"
            + " [\"node1.js\"] },\n");
    for (int i = 1; i <= 135; i++) {
      sb.append("  \"node")
          .append(i)
          .append(".js\": { \"file\": \"assets/node")
          .append(i)
          .append(".js\"");
      if (i < 135) {
        sb.append(", \"imports\": [\"node").append(i + 1).append(".js\"]");
      }
      sb.append(" }");
      if (i < 135) sb.append(",\n");
    }
    sb.append("\n}");

    assertThatThrownBy(
            () ->
                ViteAssetResolver.builder()
                    .mode(ViteAssetMode.PRODUCTION)
                    .manifest(sb.toString())
                    .build())
        .isInstanceOf(AssetException.class)
        .satisfies(
            e -> {
              AssetException ae = (AssetException) e;
              assertThat(ae.code()).isEqualTo(AssetDiagnosticCode.VT_ASSET_005);
              assertThat(ae.getMessage()).contains("exceeds maximum traversal depth limit");
            });
  }

  @Test
  void resolvesCheckedInVite5StaticManifest() throws Exception {
    try (var is = getClass().getResourceAsStream("/assets/vite/vite5/manifest.json")) {
      assertThat(is).isNotNull();
      String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      ViteAssetResolver resolver =
          ViteAssetResolver.builder()
              .mode(ViteAssetMode.PRODUCTION)
              .publicBase("/")
              .manifest(json)
              .build();

      ResolvedFrontendEntry employees = resolver.resolveEntry("src/pages/employees/index.ts");
      assertThat(employees.script().url()).isEqualTo("/assets/employees-Bzett4zY.js");
      assertThat(employees.stylesheets())
          .extracting(AssetStylesheet::url)
          .containsExactly("/assets/employees-CILAsAkH.css");
      assertThat(employees.modulePreloads())
          .extracting(AssetModulePreload::url)
          .containsExactly("/assets/client-data-CY6Bac0A.js", "/assets/index-IHki7fMi.js");

      ResolvedFrontendEntry payroll = resolver.resolveEntry("src/pages/payroll/Payroll.svelte");
      assertThat(payroll.script().url()).isEqualTo("/assets/payroll-6AW3eUXM.js");
      assertThat(payroll.stylesheets())
          .extracting(AssetStylesheet::url)
          .containsExactly("/assets/payroll-BISp2dIo.css");
    }
  }

  @Test
  void resolvesCheckedInVite8RolldownStaticManifest() throws Exception {
    try (var is = getClass().getResourceAsStream("/assets/vite/vite8/manifest.json")) {
      assertThat(is).isNotNull();
      String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      ViteAssetResolver resolver =
          ViteAssetResolver.builder()
              .mode(ViteAssetMode.PRODUCTION)
              .publicBase("/")
              .manifest(json)
              .build();

      ResolvedFrontendEntry employees = resolver.resolveEntry("src/pages/employees/index.ts");
      assertThat(employees.script().url()).isEqualTo("/assets/employees-emSWLR2G.js");
      assertThat(employees.stylesheets())
          .extracting(AssetStylesheet::url)
          .containsExactly("/assets/employees-DhptBPMj.css");
      assertThat(employees.modulePreloads())
          .extracting(AssetModulePreload::url)
          .containsExactly(
              "/assets/disclose-version-Co18oFQL.js", "/assets/client-data-DFP0J2HC.js");

      ResolvedFrontendEntry payroll = resolver.resolveEntry("src/pages/payroll/Payroll.svelte");
      assertThat(payroll.script().url()).isEqualTo("/assets/payroll-CE8ed3Mp.js");
      assertThat(payroll.stylesheets())
          .extracting(AssetStylesheet::url)
          .containsExactly("/assets/payroll-DY3Px75B.css");
    }
  }
}
