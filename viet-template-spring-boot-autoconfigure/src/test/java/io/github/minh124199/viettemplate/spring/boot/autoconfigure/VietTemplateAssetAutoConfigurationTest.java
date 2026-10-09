package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.assets.AssetDiagnosticCode;
import io.github.minh124199.viettemplate.assets.AssetException;
import io.github.minh124199.viettemplate.assets.AssetResolver;
import io.github.minh124199.viettemplate.assets.ClientData;
import io.github.minh124199.viettemplate.assets.ClientDataSerializer;
import io.github.minh124199.viettemplate.assets.FrontendAssets;
import io.github.minh124199.viettemplate.assets.FrontendAssetsRenderContextContributor;
import io.github.minh124199.viettemplate.assets.ResolvedFrontendEntry;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class VietTemplateAssetAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  VietTemplateAutoConfiguration.class, VietTemplateAssetAutoConfiguration.class));

  @Test
  @DisplayName("Assets auto-configuration is disabled by default")
  void assetsDisabledByDefault() {
    this.contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(AssetResolver.class);
          assertThat(context).doesNotHaveBean(FrontendAssets.class);
          assertThat(context).doesNotHaveBean(ClientData.class);
          assertThat(context).doesNotHaveBean(FrontendAssetsRenderContextContributor.class);
          assertThat(context).doesNotHaveBean("vietTemplateAssetsEngineCustomizer");
        });
  }

  @Test
  @DisplayName("Development mode configures dev ViteAssetResolver and emits HMR client")
  void devModeConfiguration() {
    this.contextRunner
        .withPropertyValues(
            "viet-template.assets.enabled=true",
            "viet-template.assets.mode=development",
            "viet-template.assets.dev-server=http://localhost:5173")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AssetResolver.class);
              assertThat(context).hasSingleBean(FrontendAssets.class);
              assertThat(context).hasSingleBean(ClientData.class);
              assertThat(context).hasSingleBean(FrontendAssetsRenderContextContributor.class);
              assertThat(context).hasBean("vietTemplateAssetsEngineCustomizer");

              FrontendAssets assets = context.getBean(FrontendAssets.class);
              SafeHtml head = assets.head("src/main.ts");
              assertThat(head.content())
                  .contains(
                      "<script type=\"module\""
                          + " src=\"http://localhost:5173/@vite/client\"></script>");

              SafeHtml body = assets.body("src/main.ts");
              assertThat(body.content())
                  .contains(
                      "<script type=\"module\""
                          + " src=\"http://localhost:5173/src/main.ts\"></script>");
            });
  }

  @Test
  @DisplayName("Production mode with existing manifest resolves hashed entries and CSS")
  void productionModeConfigurationWithExistingManifest() {
    this.contextRunner
        .withPropertyValues(
            "viet-template.assets.enabled=true",
            "viet-template.assets.mode=production",
            "viet-template.assets.manifest-location=classpath:/static/.vite/manifest.json")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AssetResolver.class);
              AssetResolver resolver = context.getBean(AssetResolver.class);
              ResolvedFrontendEntry entry = resolver.resolveEntry("src/main.ts");
              assertThat(entry.script().url()).isEqualTo("/assets/main-a1b2c3d4.js");
              assertThat(entry.stylesheets()).hasSize(2);

              FrontendAssets assets = context.getBean(FrontendAssets.class);
              SafeHtml head = assets.head("src/main.ts");
              assertThat(head.content()).contains("href=\"/assets/main-e5f6g7h8.css\"");
              assertThat(head.content()).contains("href=\"/assets/shared-5678.css\"");

              SafeHtml body = assets.body("src/main.ts");
              assertThat(body.content())
                  .contains("<script type=\"module\" src=\"/assets/main-a1b2c3d4.js\"></script>");
            });
  }

  @Test
  @DisplayName("Missing manifest in production mode with fail-fast=true throws startup exception")
  void productionModeMissingManifestFailFastThrows() {
    this.contextRunner
        .withPropertyValues(
            "viet-template.assets.enabled=true",
            "viet-template.assets.mode=production",
            "viet-template.assets.fail-fast=true",
            "viet-template.assets.manifest-location=classpath:/nonexistent/manifest.json")
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable root =
                  org.springframework.core.NestedExceptionUtils.getMostSpecificCause(
                      context.getStartupFailure());
              assertThat(root).isInstanceOf(AssetException.class);
              assertThat(((AssetException) root).code())
                  .isEqualTo(AssetDiagnosticCode.VT_ASSET_001);
            });
  }

  @Test
  @DisplayName("ClientData bean safely serializes objects with script breakout protection")
  void clientDataScriptBreakoutProtection() {
    this.contextRunner
        .withPropertyValues(
            "viet-template.assets.enabled=true", "viet-template.assets.mode=development")
        .run(
            context -> {
              ClientData clientData = context.getBean(ClientData.class);
              SafeHtml script =
                  clientData.script(
                      "test-payload", Map.of("message", "</script><script>alert(1)</script>"));
              assertThat(script.content()).doesNotContain("</script><script>");
              assertThat(script.content()).contains("\\u003C/script\\u003E");
            });
  }

  @Test
  @DisplayName("Custom ClientDataSerializer bean takes precedence over default")
  void customSerializerTakesPrecedence() {
    this.contextRunner
        .withUserConfiguration(CustomSerializerConfig.class)
        .withPropertyValues(
            "viet-template.assets.enabled=true", "viet-template.assets.mode=development")
        .run(
            context -> {
              ClientDataSerializer serializer = context.getBean(ClientDataSerializer.class);
              assertThat(serializer).isInstanceOf(CustomTestSerializer.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomSerializerConfig {
    @Bean
    public ClientDataSerializer customClientDataSerializer() {
      return new CustomTestSerializer();
    }
  }

  static class CustomTestSerializer implements ClientDataSerializer {
    @Override
    public void serialize(Object value, Appendable target) {
      try {
        target.append("{\"custom\":true}");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
