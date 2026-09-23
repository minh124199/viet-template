package io.github.minh124199.viettemplate.quarkus;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.UndefinedReferencePolicy;
import io.github.minh124199.viettemplate.quarkus.security.QuarkusSecurityRenderContextContributor;
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
    builder.interpreterOptions(
        VtlInterpreterOptions.builder().undefinedReferencePolicy(undefinedPolicy).build());

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
            builder.addContextContributor(new QuarkusSecurityRenderContextContributor());
          }
        }
      } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
        // Quarkus Security extension is not present on classpath - optional!
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (Throwable ignored) {
      }
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
