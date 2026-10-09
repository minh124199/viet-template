package io.github.minh124199.viettemplate.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.assets.vite.AssetPathValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AssetPathValidatorTest {

  @Test
  void normalizesPublicBase() {
    assertThat(AssetPathValidator.validateAndNormalizePublicBase("/")).isEqualTo("/");
    assertThat(AssetPathValidator.validateAndNormalizePublicBase("/assets")).isEqualTo("/assets/");
    assertThat(AssetPathValidator.validateAndNormalizePublicBase("/assets/")).isEqualTo("/assets/");
    assertThat(AssetPathValidator.validateAndNormalizePublicBase("https://cdn.example.com/assets"))
        .isEqualTo("https://cdn.example.com/assets/");
    assertThat(AssetPathValidator.validateAndNormalizePublicBase(null)).isEqualTo("/");
    assertThat(AssetPathValidator.validateAndNormalizePublicBase("")).isEqualTo("/");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../assets",
        "/assets/../secret",
        "assets/..\\secret",
        "javascript:alert(1)",
        "vbscript:msgbox",
        "data:text/html,bad",
        "file:///etc/passwd",
        "blob:http://example.com/uuid",
        "//host.com/assets"
      })
  void rejectsHostilePublicBase(String hostileBase) {
    assertThatThrownBy(() -> AssetPathValidator.validateAndNormalizePublicBase(hostileBase))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_008));
  }

  @Test
  void normalizesDevServerOrigin() {
    assertThat(AssetPathValidator.validateDevServerOrigin("http://localhost:5173"))
        .isEqualTo("http://localhost:5173");
    assertThat(AssetPathValidator.validateDevServerOrigin("https://vite.local:3000/"))
        .isEqualTo("https://vite.local:3000");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ftp://localhost:5173",
        "javascript:alert(1)",
        "http://localhost:5173/../etc",
        "",
        "   "
      })
  void rejectsInvalidDevServerOrigin(String invalidOrigin) {
    assertThatThrownBy(() -> AssetPathValidator.validateDevServerOrigin(invalidOrigin))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_007));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../secret.js",
        "..\\secret.js",
        "sub/../../secret.js",
        "sub/%2e%2e/secret.js",
        "sub/%252e%252e/secret.js",
        "javascript:alert(1)",
        "data:text/javascript,evil()",
        "vbscript:alert",
        "file:///etc/hosts",
        "blob:http://evil.com/123",
        "//evil.example/asset.js",
        "assets/bad\nname.js",
        "assets/bad\rname.js",
        "assets/bad\0name.js",
        "assets/quote\"injection.js",
        "assets/tag<injection>.js"
      })
  void rejectsHostileRelativePaths(String hostilePath) {
    assertThatThrownBy(() -> AssetPathValidator.validateRelativePath(hostilePath))
        .isInstanceOf(AssetException.class)
        .satisfies(
            e ->
                assertThat(((AssetException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_ASSET_006));
  }

  @Test
  void resolvesAssetUrlCorrectly() {
    assertThat(AssetPathValidator.resolveAssetUrl("/assets/", "main-C0de.js"))
        .isEqualTo("/assets/main-C0de.js");
    assertThat(AssetPathValidator.resolveAssetUrl("/assets/", "/main-C0de.js"))
        .isEqualTo("/assets/main-C0de.js");
    assertThat(AssetPathValidator.resolveAssetUrl("https://cdn.example.com/", "css/style-D9.css"))
        .isEqualTo("https://cdn.example.com/css/style-D9.css");
  }
}
