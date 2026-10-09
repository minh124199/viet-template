package io.github.minh124199.viettemplate.assets.vite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.assets.AssetDiagnosticCode;
import io.github.minh124199.viettemplate.assets.AssetException;
import org.junit.jupiter.api.Test;

class ViteManifestParserTest {

  @Test
  void parsesStandardViteManifest() {
    String manifestJson =
        """
        {
          "src/main.ts": {
            "file": "assets/main-C0de.js",
            "name": "main",
            "src": "src/main.ts",
            "isEntry": true,
            "imports": ["_vendor-X1.js"],
            "css": ["assets/main-D9.css"],
            "assets": ["assets/logo-E2.png"]
          },
          "_vendor-X1.js": {
            "file": "assets/vendor-X1.js",
            "name": "vendor",
            "css": ["assets/vendor-F3.css"]
          }
        }
        """;

    var entries = ViteManifestParser.parse(manifestJson);
    assertThat(entries).hasSize(2);

    var main = entries.get("src/main.ts");
    assertThat(main).isNotNull();
    assertThat(main.file()).isEqualTo("assets/main-C0de.js");
    assertThat(main.name()).isEqualTo("main");
    assertThat(main.src()).isEqualTo("src/main.ts");
    assertThat(main.isEntry()).isTrue();
    assertThat(main.imports()).containsExactly("_vendor-X1.js");
    assertThat(main.css()).containsExactly("assets/main-D9.css");
    assertThat(main.assets()).containsExactly("assets/logo-E2.png");

    var vendor = entries.get("_vendor-X1.js");
    assertThat(vendor).isNotNull();
    assertThat(vendor.file()).isEqualTo("assets/vendor-X1.js");
    assertThat(vendor.isEntry()).isFalse();
    assertThat(vendor.css()).containsExactly("assets/vendor-F3.css");
  }

  @Test
  void ignoresUnknownFieldsForForwardCompatibility() {
    String manifestJson =
        """
        {
          "src/future.ts": {
            "file": "assets/future-123.js",
            "unknownString": "hello",
            "unknownNumber": 42.5,
            "unknownBool": true,
            "unknownObject": { "nested": "data", "deep": [1, 2, 3] },
            "unknownArray": ["a", "b", null],
            "css": ["assets/future.css"]
          }
        }
        """;

    var entries = ViteManifestParser.parse(manifestJson);
    assertThat(entries).hasSize(1);
    var entry = entries.get("src/future.ts");
    assertThat(entry.file()).isEqualTo("assets/future-123.js");
    assertThat(entry.css()).containsExactly("assets/future.css");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> ViteManifestParser.parse("not a json"))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_002));

    assertThatThrownBy(() -> ViteManifestParser.parse("{ invalid: 123 }"))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_002));

    assertThatThrownBy(() -> ViteManifestParser.parse("{\"entry\": {}}"))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_002));
  }

  @Test
  void missingFilePropertyThrowsVtAsset009() {
    assertThatThrownBy(() -> ViteManifestParser.parse("{\"entry\": {\"src\": \"src/main.ts\"}}"))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_009));
  }

  @Test
  void handlesNullArrayGracefully() {
    String manifest =
        """
        {
          "entry": {
            "file": "assets/app.js",
            "imports": null,
            "css": null
          }
        }
        """;
    var entries = ViteManifestParser.parse(manifest);
    assertThat(entries.get("entry").imports()).isEmpty();
    assertThat(entries.get("entry").css()).isEmpty();
  }

  @Test
  void rejectsUnescapedControlCharactersInString() {
    assertThatThrownBy(
            () -> ViteManifestParser.parse("{\"entry\": {\"file\": \"assets\u0001test.js\"}}"))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_002));
  }
}
