package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import io.github.minh124199.viettemplate.spring.security.CsrfViewFactory;
import io.github.minh124199.viettemplate.spring.security.SecurityViewFactory;
import io.github.minh124199.viettemplate.spring.security.SpringSecurityRenderContextContributor;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateEngineCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

/**
 * Spring Boot {@link AutoConfiguration auto-configuration} for Viet Template Spring Security
 * integration.
 *
 * <p>Activates only when both Spring Security ({@link Authentication}) and Viet Template Spring
 * Security ({@link SpringSecurityRenderContextContributor}) are present on the classpath, in a
 * Servlet web environment, and when not disabled via {@code viet-template.security.enabled=false}.
 *
 * <p>Provides back-off beans for {@link SecurityViewFactory}, {@link CsrfViewFactory}, and {@link
 * SpringSecurityRenderContextContributor}, and registers the contributor with the template engine
 * via a {@link VietTemplateEngineCustomizer}.
 */
@AutoConfiguration(before = VietTemplateAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Authentication.class, SpringSecurityRenderContextContributor.class})
@ConditionalOnProperty(name = "viet-template.security.enabled", matchIfMissing = true)
@EnableConfigurationProperties(VietTemplateProperties.class)
public class VietTemplateSecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(SecurityViewFactory.class)
  public SecurityViewFactory securityViewFactory() {
    return SecurityViewFactory.defaultFactory();
  }

  @Bean
  @ConditionalOnMissingBean(CsrfViewFactory.class)
  public CsrfViewFactory csrfViewFactory() {
    return CsrfViewFactory.defaultFactory();
  }

  @Bean
  @ConditionalOnMissingBean(SpringSecurityRenderContextContributor.class)
  public SpringSecurityRenderContextContributor springSecurityRenderContextContributor(
      SecurityViewFactory securityViewFactory, CsrfViewFactory csrfViewFactory) {
    return new SpringSecurityRenderContextContributor(securityViewFactory, csrfViewFactory);
  }

  @Bean
  @ConditionalOnMissingBean(name = "vietTemplateSpringSecurityEngineCustomizer")
  public VietTemplateEngineCustomizer vietTemplateSpringSecurityEngineCustomizer(
      SpringSecurityRenderContextContributor contributor) {
    return builder -> builder.addContextContributor(contributor);
  }
}
