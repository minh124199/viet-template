package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.spring.boot.autoconfigure.aot.VietTemplateRuntimeHints;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateEngineCustomizer;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateViewResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.template.TemplateLocation;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.Resource;
import org.springframework.core.log.LogMessage;

/** Spring Boot 3 {@link AutoConfiguration auto-configuration} for Viet Template. */
@AutoConfiguration
@ConditionalOnClass({TemplateEngine.class, VietTemplateViewResolver.class})
@EnableConfigurationProperties(VietTemplateProperties.class)
@ConditionalOnProperty(name = "viet-template.enabled", matchIfMissing = true)
@ImportRuntimeHints(VietTemplateRuntimeHints.class)
public class VietTemplateAutoConfiguration implements InitializingBean {

  private static final Log logger = LogFactory.getLog(VietTemplateAutoConfiguration.class);
  private static final String AOT_INDEX_LOCATION =
      "classpath:/META-INF/viet-template/templates.idx";
  private static final String DEFAULT_SOURCE_TEMPLATE_LOCATION = "classpath:/templates/";

  private final VietTemplateProperties properties;
  private final ApplicationContext applicationContext;

  public VietTemplateAutoConfiguration(
      VietTemplateProperties properties, ApplicationContext applicationContext) {
    this.properties = properties;
    this.applicationContext = applicationContext;
  }

  @Override
  public void afterPropertiesSet() {
    checkTemplateLocationExists();
  }

  public void checkTemplateLocationExists() {
    if (!this.properties.isCheckTemplateLocation()) {
      return;
    }
    if (hasAotTemplates(this.applicationContext)) {
      return;
    }
    TemplateLocation location = resolveTemplateLocation(this.properties.getPrefix());
    if (!location.exists(this.applicationContext)) {
      logger.warn(
          LogMessage.format(
              "Cannot find template location: %s (please add some templates, check your Viet"
                  + " Template configuration, or set viet-template.check-template-location=false)",
              location));
    }
  }

  private boolean hasAotTemplates(ApplicationContext context) {
    try {
      Resource resource = context.getResource(AOT_INDEX_LOCATION);
      if (resource != null && resource.exists()) {
        return true;
      }
      ClassLoader cl = context.getClassLoader();
      if (cl != null && cl.getResource("META-INF/viet-template/templates.idx") != null) {
        return true;
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  private TemplateLocation resolveTemplateLocation(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      return new TemplateLocation(DEFAULT_SOURCE_TEMPLATE_LOCATION);
    }
    if (prefix.contains(":")) {
      return new TemplateLocation(prefix);
    }
    String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
    return new TemplateLocation("classpath:" + normalized);
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(TemplateEngine.class)
  public TemplateEngine vietTemplateEngine(
      VietTemplateProperties properties, ObjectProvider<VietTemplateEngineCustomizer> customizers) {
    TemplateEngine.Builder builder = TemplateEngine.builder();
    builder.rejectRuntimeCompilation(!properties.isRuntimeCompilationEnabled());
    builder.maxCacheEntries(properties.getMaxCacheEntries());
    builder.negativeCacheTtlMillis(properties.getNegativeCacheTtlMillis());
    builder.hotReload(properties.isHotReload());
    builder.watchDebounceMillis(properties.getWatchDebounceMillis());
    customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
    return builder.build();
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  static class VietTemplateWebMvcConfiguration {

    @Bean(name = "vietTemplateViewResolver")
    @ConditionalOnMissingBean(name = "vietTemplateViewResolver")
    public VietTemplateViewResolver vietTemplateViewResolver(
        TemplateEngine engine, VietTemplateProperties properties) {
      VietTemplateViewResolver resolver = new VietTemplateViewResolver(engine);
      resolver.setPrefix(properties.getPrefix());
      resolver.setSuffix(properties.getSuffix());
      resolver.setSuffixes(properties.getSuffixes());
      resolver.setContentType(properties.getContentType());
      resolver.setCharset(properties.getCharset());
      resolver.setCache(properties.isCache());
      resolver.setCheckTemplateLocation(properties.isCheckTemplateLocation());
      resolver.setOrder(properties.getOrder());
      return resolver;
    }
  }
}
