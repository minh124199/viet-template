package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.assets.AssetDiagnosticCode;
import io.github.minh124199.viettemplate.assets.AssetException;
import io.github.minh124199.viettemplate.assets.AssetResolver;
import io.github.minh124199.viettemplate.assets.ClientData;
import io.github.minh124199.viettemplate.assets.ClientDataSerializer;
import io.github.minh124199.viettemplate.assets.FrontendAssets;
import io.github.minh124199.viettemplate.assets.FrontendAssetsRenderContextContributor;
import io.github.minh124199.viettemplate.assets.SimpleJsonSerializer;
import io.github.minh124199.viettemplate.assets.vite.ViteAssetMode;
import io.github.minh124199.viettemplate.assets.vite.ViteAssetResolver;
import io.github.minh124199.viettemplate.spring.client.SpringJacksonClientDataSerializer;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateEngineCustomizer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Spring Boot {@link AutoConfiguration auto-configuration} for Viet Template Frontend Asset
 * Integration.
 *
 * <p>Activates only when enabled via {@code viet-template.assets.enabled=true}.
 */
@AutoConfiguration(before = VietTemplateAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({TemplateEngine.class, FrontendAssets.class})
@ConditionalOnProperty(name = "viet-template.assets.enabled", havingValue = "true")
@EnableConfigurationProperties(VietTemplateAssetProperties.class)
public class VietTemplateAssetAutoConfiguration {

  private static final Log logger = LogFactory.getLog(VietTemplateAssetAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(AssetResolver.class)
  public AssetResolver vietTemplateAssetResolver(
      VietTemplateAssetProperties properties, ResourceLoader resourceLoader) {
    String modeStr = properties.getMode().trim().toUpperCase(Locale.ROOT);
    ViteAssetMode mode;
    try {
      mode = ViteAssetMode.valueOf(modeStr);
    } catch (IllegalArgumentException e) {
      mode = ViteAssetMode.PRODUCTION;
    }

    if (mode == ViteAssetMode.DEVELOPMENT) {
      return ViteAssetResolver.development(properties.getEffectiveDevServer());
    }

    String manifestLocation = properties.getEffectiveManifestLocation();
    Resource resource = resourceLoader.getResource(manifestLocation);

    if (!resource.exists()) {
      if (properties.isFailFast()) {
        throw new AssetException(
            AssetDiagnosticCode.VT_ASSET_001,
            manifestLocation,
            "Vite manifest not found at location: " + manifestLocation);
      }
      logger.warn(
          "Vite manifest not found at location: "
              + manifestLocation
              + ". Asset resolver initialized in empty fallback mode.");
      return ViteAssetResolver.productionBuilder()
          .publicBase(properties.getPublicBase())
          .modulePreload(properties.isModulePreload())
          .build();
    }

    try (InputStream in = resource.getInputStream()) {
      String manifestJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return ViteAssetResolver.productionBuilder()
          .publicBase(properties.getPublicBase())
          .modulePreload(properties.isModulePreload())
          .manifest(manifestJson)
          .build();
    } catch (IOException e) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_002,
          manifestLocation,
          "Failed reading Vite manifest from: " + manifestLocation + " (" + e.getMessage() + ")",
          e);
    }
  }

  @Bean
  @ConditionalOnMissingBean(FrontendAssets.class)
  public FrontendAssets vietTemplateFrontendAssets(AssetResolver assetResolver) {
    return new FrontendAssets(assetResolver);
  }

  @Bean
  @ConditionalOnMissingBean(ClientDataSerializer.class)
  public ClientDataSerializer vietTemplateClientDataSerializer(ApplicationContext context) {
    try {
      Class<?> j3Class = Class.forName("tools.jackson.databind.ObjectMapper");
      ObjectProvider<?> j3Provider = context.getBeanProvider(j3Class);
      Object j3Mapper = j3Provider.getIfAvailable();
      if (j3Mapper != null) {
        return new SpringJacksonClientDataSerializer(j3Mapper);
      }
    } catch (ClassNotFoundException ignored) {
    }

    try {
      Class<?> j2Class = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
      ObjectProvider<?> j2Provider = context.getBeanProvider(j2Class);
      Object j2Mapper = j2Provider.getIfAvailable();
      if (j2Mapper != null) {
        return new SpringJacksonClientDataSerializer(j2Mapper);
      }
    } catch (ClassNotFoundException ignored) {
    }

    try {
      Class<?> jmClass = Class.forName("tools.jackson.databind.json.JsonMapper");
      ObjectProvider<?> jmProvider = context.getBeanProvider(jmClass);
      Object jm = jmProvider.getIfAvailable();
      if (jm != null) {
        return new SpringJacksonClientDataSerializer(jm);
      }
    } catch (ClassNotFoundException ignored) {
    }

    return SimpleJsonSerializer.INSTANCE;
  }

  @Bean
  @ConditionalOnMissingBean(ClientData.class)
  public ClientData vietTemplateClientData(ClientDataSerializer serializer) {
    return new ClientData(serializer);
  }

  @Bean
  @ConditionalOnMissingBean(FrontendAssetsRenderContextContributor.class)
  public FrontendAssetsRenderContextContributor vietTemplateAssetsRenderContextContributor(
      FrontendAssets assets, ClientData clientData) {
    return new FrontendAssetsRenderContextContributor(assets, clientData);
  }

  @Bean(name = "vietTemplateAssetsEngineCustomizer")
  @ConditionalOnMissingBean(name = "vietTemplateAssetsEngineCustomizer")
  public VietTemplateEngineCustomizer vietTemplateAssetsEngineCustomizer(
      FrontendAssetsRenderContextContributor contributor) {
    return builder -> builder.addContextContributor(contributor);
  }
}
