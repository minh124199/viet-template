package io.github.minh124199.viettemplate.quarkus;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.UndefinedReferencePolicy;
import io.github.minh124199.viettemplate.assets.AssetDiagnosticCode;
import io.github.minh124199.viettemplate.assets.AssetException;
import io.github.minh124199.viettemplate.assets.AssetResolver;
import io.github.minh124199.viettemplate.assets.ClientData;
import io.github.minh124199.viettemplate.assets.ClientDataSerializer;
import io.github.minh124199.viettemplate.assets.FrontendAssets;
import io.github.minh124199.viettemplate.assets.FrontendAssetsRenderContextContributor;
import io.github.minh124199.viettemplate.assets.JacksonClientDataSerializer;
import io.github.minh124199.viettemplate.assets.SimpleJsonSerializer;
import io.github.minh124199.viettemplate.assets.vite.ViteAssetResolver;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.quarkus.security.QuarkusSecurityRenderContextContributor;
import io.github.minh124199.viettemplate.quarkus.security.QuarkusSecurityViewFactory;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineBuilder;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.LaunchMode;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** CDI producer for configuring and instantiating the {@link TemplateEngine} in Quarkus. */
@ApplicationScoped
public class VietTemplateProducer {

  @Inject VietTemplateConfig config;

  @Inject Instance<RenderContextContributor> customContributors;

  private TemplateEngine engine;

  public VietTemplateProducer() {}

  public VietTemplateProducer(VietTemplateConfig config) {
    this.config = config;
  }

  @Produces
  @ApplicationScoped
  @SuppressWarnings("removal")
  public TemplateEngine produceTemplateEngine() {
    Charset charset;
    try {
      charset = Charset.forName(config.encoding());
    } catch (IllegalArgumentException e) {
      charset = StandardCharsets.UTF_8;
    }

    VtlTemplateEngineBuilder builder = VtlTemplateEngine.builder();
    builder.repository(
        ClasspathTemplateRepository.of(
            Thread.currentThread().getContextClassLoader(), config.path(), charset));

    boolean runtimeAllowed =
        config.runtimeCompilationEnabled() || LaunchMode.current() == LaunchMode.DEVELOPMENT;
    builder.rejectRuntimeCompilation(!runtimeAllowed);
    builder.maxCacheEntries(config.cacheMaxEntries());
    builder.negativeCacheTtlMillis(config.negativeCacheTtlMillis());

    UndefinedReferencePolicy undefinedPolicy;
    try {
      undefinedPolicy =
          UndefinedReferencePolicy.valueOf(
              config.undefinedReferencePolicy().trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      undefinedPolicy = UndefinedReferencePolicy.SILENT;
    }
    VtlProfile profile;
    try {
      profile = VtlProfile.valueOf(config.profile().trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      profile = VtlProfile.VTL_MIGRATION;
    }
    builder.interpreterOptions(
        VtlInterpreterOptions.builder()
            .profile(profile)
            .undefinedReferencePolicy(undefinedPolicy)
            .build());

    boolean hasSecurityContributor = false;
    if (customContributors != null) {
      for (RenderContextContributor contributor : customContributors) {
        builder.addContextContributor(contributor);
        if (contributor instanceof QuarkusSecurityRenderContextContributor) {
          hasSecurityContributor = true;
        }
      }
    }
    if (!hasSecurityContributor) {
      try {
        Class<?> secIdClass =
            Class.forName(
                "io.quarkus.security.identity.SecurityIdentity",
                false,
                Thread.currentThread().getContextClassLoader());
        ArcContainer container = Arc.container();
        if (container != null && container.isRunning()) {
          @SuppressWarnings({"rawtypes", "unchecked"})
          InstanceHandle<?> handle = container.instance((Class) secIdClass);
          if (handle != null && handle.isAvailable()) {
            QuarkusSecurityViewFactory viewFactory = null;
            InstanceHandle<QuarkusSecurityViewFactory> factoryHandle =
                container.instance(QuarkusSecurityViewFactory.class);
            if (factoryHandle != null && factoryHandle.isAvailable()) {
              viewFactory = factoryHandle.get();
            }
            builder.addContextContributor(
                new QuarkusSecurityRenderContextContributor(
                    null,
                    viewFactory,
                    null,
                    QuarkusSecurityRenderContextContributor.DEFAULT_SECURITY_VARIABLE_NAME,
                    QuarkusSecurityRenderContextContributor.DEFAULT_CSRF_VARIABLE_NAME));
          }
        }
      } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
        // Quarkus Security extension is not present on classpath - optional!
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (Throwable ignored) {
      }
    }

    if (config.assetsEnabled()) {
      AssetResolver assetResolver;
      String mode = config.assetsMode().trim().toUpperCase(Locale.ROOT);
      if ("DEVELOPMENT".equals(mode)
          || (LaunchMode.current() == LaunchMode.DEVELOPMENT && "DEVELOPMENT".equals(mode))) {
        assetResolver = ViteAssetResolver.development(config.assetsDevServer());
      } else {
        String manifestLocation = config.assetsManifestLocation();
        String resPath =
            manifestLocation.startsWith("/") ? manifestLocation.substring(1) : manifestLocation;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(resPath)) {
          if (in == null) {
            if (config.assetsFailFast()) {
              throw new AssetException(
                  AssetDiagnosticCode.VT_ASSET_001,
                  manifestLocation,
                  "Vite manifest not found on classpath: " + manifestLocation);
            }
            assetResolver =
                ViteAssetResolver.productionBuilder()
                    .publicBase(config.assetsPublicBase())
                    .modulePreload(config.assetsModulePreload())
                    .build();
          } else {
            String manifestJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assetResolver =
                ViteAssetResolver.productionBuilder()
                    .publicBase(config.assetsPublicBase())
                    .modulePreload(config.assetsModulePreload())
                    .manifest(manifestJson)
                    .build();
          }
        } catch (IOException e) {
          throw new AssetException(
              AssetDiagnosticCode.VT_ASSET_002,
              manifestLocation,
              "Failed reading Vite manifest: " + e.getMessage(),
              e);
        }
      }

      FrontendAssets frontendAssets = new FrontendAssets(assetResolver);

      ClientDataSerializer serializer = SimpleJsonSerializer.INSTANCE;
      ArcContainer container = Arc.container();
      if (container != null && container.isRunning()) {
        InstanceHandle<ClientDataSerializer> serHandle =
            container.instance(ClientDataSerializer.class);
        if (serHandle != null && serHandle.isAvailable()) {
          serializer = serHandle.get();
        } else {
          ClassLoader cl = Thread.currentThread().getContextClassLoader();
          try {
            Class<?> omClass =
                Class.forName("com.fasterxml.jackson.databind.ObjectMapper", false, cl);
            @SuppressWarnings({"rawtypes", "unchecked"})
            InstanceHandle<?> omHandle = container.instance((Class) omClass);
            if (omHandle != null && omHandle.isAvailable()) {
              serializer = new JacksonClientDataSerializer(omHandle.get());
            }
          } catch (ClassNotFoundException | LinkageError ignored) {
          }
        }
      }

      ClientData clientData = new ClientData(serializer);
      builder.addContextContributor(
          new FrontendAssetsRenderContextContributor(frontendAssets, clientData));
    }

    this.engine = builder.build();
    return this.engine;
  }

  @PreDestroy
  public void close() {
    if (this.engine != null) {
      this.engine.close();
    }
  }
}
