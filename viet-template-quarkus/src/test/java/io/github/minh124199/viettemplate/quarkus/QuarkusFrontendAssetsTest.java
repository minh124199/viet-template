package io.github.minh124199.viettemplate.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.assets.AssetException;
import io.github.minh124199.viettemplate.assets.AssetResolver;
import io.github.minh124199.viettemplate.assets.ClientData;
import io.github.minh124199.viettemplate.assets.ClientDataSerializer;
import io.github.minh124199.viettemplate.assets.FrontendAssets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class QuarkusFrontendAssetsTest {

  private TemplateEngine engine;

  @AfterEach
  public void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  @Test
  @DisplayName(
      "Quarkus producer with assets enabled in production resolves TS and direct .svelte entries")
  public void productionAssetsResolution() throws Exception {
    VietTemplateConfig config =
        new TestVietTemplateConfig() {
          @Override
          public boolean assetsEnabled() {
            return true;
          }

          @Override
          public String assetsMode() {
            return "production";
          }

          @Override
          public String assetsManifestLocation() {
            return "static/.vite/manifest.json";
          }
        };

    VietTemplateProducer producer = new VietTemplateProducer(config);
    this.engine = producer.produceTemplateEngine();

    // Verify rendering of template using $assets and $clientData
    String rendered =
        engine.render(
            "asset-test.vtl",
            io.github.minh124199.viettemplate.api.RenderContext.of(
                "employee", Map.of("name", "Alice", "role", "<Engineer>&")));

    assertThat(rendered).contains("href=\"/assets/employees-e5f6g7h8.css\"");
    assertThat(rendered)
        .contains("<script type=\"module\" src=\"/assets/employees-a1b2c3d4.js\"></script>");
    assertThat(rendered)
        .contains("<script type=\"module\" src=\"/assets/payroll-99887766.js\"></script>");
    assertThat(rendered)
        .contains("<script type=\"application/json\" data-vt-client-data=\"emp-data\">");
    assertThat(rendered).contains("\\u003CEngineer\\u003E\\u0026");
  }

  @Test
  @DisplayName("Quarkus producer with assets enabled in dev mode emits Vite HMR client")
  public void devModeAssetsResolution() throws Exception {
    VietTemplateConfig config =
        new TestVietTemplateConfig() {
          @Override
          public boolean assetsEnabled() {
            return true;
          }

          @Override
          public String assetsMode() {
            return "development";
          }

          @Override
          public String assetsDevServer() {
            return "http://localhost:5173";
          }
        };

    VietTemplateProducer producer = new VietTemplateProducer(config);
    this.engine = producer.produceTemplateEngine();

    String rendered =
        engine.render("dev-test.vtl", io.github.minh124199.viettemplate.api.RenderContext.empty());

    assertThat(rendered)
        .contains("<script type=\"module\" src=\"http://localhost:5173/@vite/client\"></script>");
    assertThat(rendered)
        .contains("<script type=\"module\" src=\"http://localhost:5173/src/main.ts\"></script>");
  }

  @Test
  @DisplayName("Missing manifest in Quarkus production with failFast=true throws AssetException")
  public void missingManifestFailFastThrows() {
    VietTemplateConfig config =
        new TestVietTemplateConfig() {
          @Override
          public boolean assetsEnabled() {
            return true;
          }

          @Override
          public String assetsMode() {
            return "production";
          }

          @Override
          public boolean assetsFailFast() {
            return true;
          }

          @Override
          public String assetsManifestLocation() {
            return "nonexistent/manifest.json";
          }
        };

    VietTemplateProducer producer = new VietTemplateProducer(config);
    assertThatThrownBy(producer::produceTemplateEngine)
        .isInstanceOf(AssetException.class)
        .hasFieldOrPropertyWithValue("code", "VT-ASSET-001");
  }

  @Test
  @DisplayName("Quarkus producer produces AssetResolver, FrontendAssets, and ClientData CDI beans")
  public void cdiProducerBeans() {
    VietTemplateConfig config =
        new TestVietTemplateConfig() {
          @Override
          public boolean assetsEnabled() {
            return true;
          }

          @Override
          public String assetsMode() {
            return "production";
          }

          @Override
          public String assetsManifestLocation() {
            return "static/.vite/manifest.json";
          }
        };

    VietTemplateProducer producer = new VietTemplateProducer(config);
    AssetResolver resolver = producer.produceAssetResolver();
    assertThat(resolver).isNotNull();

    FrontendAssets assets = producer.produceFrontendAssets(resolver);
    assertThat(assets).isNotNull();

    ClientDataSerializer serializer = producer.produceClientDataSerializer();
    assertThat(serializer).isNotNull();

    ClientData clientData = producer.produceClientData(serializer);
    assertThat(clientData).isNotNull();
    assertThat(clientData.script("test", Map.of("key", "value")))
        .contains("data-vt-client-data=\"test\"");
  }

  private static class TestVietTemplateConfig implements VietTemplateConfig {
    @Override
    public String path() {
      return "templates";
    }

    @Override
    public String suffix() {
      return ".vtl";
    }

    @Override
    public Optional<List<String>> additionalSuffixes() {
      return Optional.empty();
    }

    @Override
    public boolean runtimeCompilationEnabled() {
      return true;
    }

    @Override
    public int cacheMaxEntries() {
      return 500;
    }

    @Override
    public long negativeCacheTtlMillis() {
      return 5000L;
    }

    @Override
    public String undefinedReferencePolicy() {
      return "SILENT";
    }

    @Override
    public String encoding() {
      return "UTF-8";
    }
  }
}
