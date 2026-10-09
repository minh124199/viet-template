package io.github.minh124199.viettemplate.assets;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.assets.vite.ViteAssetMode;
import io.github.minh124199.viettemplate.assets.vite.ViteAssetResolver;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.SafeUrl;
import org.junit.jupiter.api.Test;

class FrontendAssetsTest {

  private static final String MANIFEST =
      """
      {
        "src/pageA.ts": {
          "file": "assets/pageA-1.js",
          "isEntry": true,
          "imports": ["_vendor.js"],
          "css": ["assets/shared.css", "assets/pageA.css"]
        },
        "src/pageB.ts": {
          "file": "assets/pageB-2.js",
          "isEntry": true,
          "imports": ["_vendor.js"],
          "css": ["assets/shared.css", "assets/pageB.css"]
        },
        "_vendor.js": {
          "file": "assets/vendor-9.js"
        },
        "src/logo.svg": {
          "file": "assets/logo-xyz.svg"
        }
      }
      """;

  @Test
  void headEmitsStylesheetsAndPreloads() {
    ViteAssetResolver resolver = ViteAssetResolver.builder().manifest(MANIFEST).build();
    FrontendAssets assets = new FrontendAssets(resolver);

    SafeHtml head = assets.head("src/pageA.ts");
    assertThat(head.toString())
        .isEqualTo(
            """
            <link rel="stylesheet" href="/assets/shared.css">
            <link rel="stylesheet" href="/assets/pageA.css">
            <link rel="modulepreload" href="/assets/vendor-9.js">
            """);
  }

  @Test
  void headDeduplicatesSharedStylesheetsAndPreloadsAcrossMultipleEntries() {
    ViteAssetResolver resolver = ViteAssetResolver.builder().manifest(MANIFEST).build();
    FrontendAssets assets = new FrontendAssets(resolver);

    SafeHtml head = assets.head("src/pageA.ts", "src/pageB.ts");
    assertThat(head.toString())
        .isEqualTo(
            """
            <link rel="stylesheet" href="/assets/shared.css">
            <link rel="stylesheet" href="/assets/pageA.css">
            <link rel="stylesheet" href="/assets/pageB.css">
            <link rel="modulepreload" href="/assets/vendor-9.js">
            """);
  }

  @Test
  void bodyEmitsScriptModuleTags() {
    ViteAssetResolver resolver = ViteAssetResolver.builder().manifest(MANIFEST).build();
    FrontendAssets assets = new FrontendAssets(resolver);

    SafeHtml body = assets.body("src/pageA.ts");
    assertThat(body.toString())
        .isEqualTo("<script type=\"module\" src=\"/assets/pageA-1.js\"></script>\n");

    SafeHtml bodyMulti = assets.body("src/pageA.ts", "src/pageB.ts");
    assertThat(bodyMulti.toString())
        .isEqualTo(
            """
            <script type=\"module\" src=\"/assets/pageA-1.js\"></script>
            <script type=\"module\" src=\"/assets/pageB-2.js\"></script>
            """);
  }

  @Test
  void devModeEmitsViteClientOnceInHead() {
    ViteAssetResolver resolver =
        ViteAssetResolver.builder()
            .mode(ViteAssetMode.DEVELOPMENT)
            .devServer("http://localhost:5173")
            .build();
    FrontendAssets assets = new FrontendAssets(resolver);

    SafeHtml head = assets.head("src/pageA.ts", "src/pageB.ts");
    assertThat(head.toString())
        .isEqualTo(
            "<script type=\"module\" src=\"http://localhost:5173/@vite/client\"></script>\n");

    SafeHtml body = assets.body("src/pageA.ts");
    assertThat(body.toString())
        .isEqualTo(
            "<script type=\"module\" src=\"http://localhost:5173/src/pageA.ts\"></script>\n");
  }

  @Test
  void urlResolvesStaticAsset() {
    ViteAssetResolver resolver = ViteAssetResolver.builder().manifest(MANIFEST).build();
    FrontendAssets assets = new FrontendAssets(resolver);

    SafeUrl url = assets.url("src/logo.svg");
    assertThat(url.toString()).isEqualTo("/assets/logo-xyz.svg");
  }

  @Test
  void nullAndBlankInputsReturnEmptySafely() {
    ViteAssetResolver resolver = ViteAssetResolver.builder().manifest(MANIFEST).build();
    FrontendAssets assets = new FrontendAssets(resolver);

    assertThat(assets.head("").toString()).isEmpty();
    assertThat(assets.head((String) null).toString()).isEmpty();
    assertThat(assets.body("").toString()).isEmpty();
    assertThat(assets.body((String) null).toString()).isEmpty();
  }
}
