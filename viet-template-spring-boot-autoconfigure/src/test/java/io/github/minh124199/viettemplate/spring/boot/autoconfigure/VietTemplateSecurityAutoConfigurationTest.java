package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.spring.security.CsrfViewFactory;
import io.github.minh124199.viettemplate.spring.security.SecurityView;
import io.github.minh124199.viettemplate.spring.security.SecurityViewFactory;
import io.github.minh124199.viettemplate.spring.security.SpringSecurityRenderContextContributor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

class VietTemplateSecurityAutoConfigurationTest {

  private final WebApplicationContextRunner webContextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  VietTemplateAutoConfiguration.class,
                  VietTemplateSecurityAutoConfiguration.class));

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  VietTemplateAutoConfiguration.class,
                  VietTemplateSecurityAutoConfiguration.class));

  @Test
  @DisplayName("Default web configuration activates Spring Security contributor and factories")
  void defaultWebSecurityConfiguration() {
    this.webContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(SecurityViewFactory.class);
          assertThat(context).hasSingleBean(CsrfViewFactory.class);
          assertThat(context).hasSingleBean(SpringSecurityRenderContextContributor.class);
          assertThat(context).hasBean("vietTemplateSpringSecurityEngineCustomizer");

          TemplateEngine engine = context.getBean(TemplateEngine.class);
          assertThat(engine).isInstanceOf(TestTemplateEngine.class);
          TestTemplateEngine testEngine = (TestTemplateEngine) engine;
          assertThat(testEngine.getBuilder().getContextContributors())
              .anyMatch(c -> c instanceof SpringSecurityRenderContextContributor);
        });
  }

  @Test
  @DisplayName("viet-template.security.enabled=false backs off security auto-configuration")
  void securityDisabledViaProperty() {
    this.webContextRunner
        .withPropertyValues("viet-template.security.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TemplateEngine.class);
              assertThat(context).doesNotHaveBean(SecurityViewFactory.class);
              assertThat(context).doesNotHaveBean(CsrfViewFactory.class);
              assertThat(context).doesNotHaveBean(SpringSecurityRenderContextContributor.class);
              assertThat(context).doesNotHaveBean("vietTemplateSpringSecurityEngineCustomizer");
            });
  }

  @Test
  @DisplayName("User-defined SecurityViewFactory causes default factory to back off")
  void userSecurityViewFactoryBacksOff() {
    this.webContextRunner
        .withUserConfiguration(CustomSecurityViewFactoryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(SecurityViewFactory.class);
              assertThat(context.getBean(SecurityViewFactory.class))
                  .isSameAs(CustomSecurityViewFactoryConfiguration.CUSTOM_FACTORY);
            });
  }

  @Test
  @DisplayName("User-defined CsrfViewFactory causes default factory to back off")
  void userCsrfViewFactoryBacksOff() {
    this.webContextRunner
        .withUserConfiguration(CustomCsrfViewFactoryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CsrfViewFactory.class);
              assertThat(context.getBean(CsrfViewFactory.class))
                  .isSameAs(CustomCsrfViewFactoryConfiguration.CUSTOM_FACTORY);
            });
  }

  @Test
  @DisplayName(
      "User-defined SpringSecurityRenderContextContributor causes default contributor to back off")
  void userContributorBacksOff() {
    this.webContextRunner
        .withUserConfiguration(CustomContributorConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(SpringSecurityRenderContextContributor.class);
              assertThat(context.getBean(SpringSecurityRenderContextContributor.class))
                  .isSameAs(CustomContributorConfiguration.CUSTOM_CONTRIBUTOR);
            });
  }

  @Test
  @DisplayName("Non-web application backs off security auto-configuration")
  void nonWebApplicationBacksOff() {
    this.nonWebContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TemplateEngine.class);
          assertThat(context).doesNotHaveBean(SpringSecurityRenderContextContributor.class);
          assertThat(context).doesNotHaveBean(SecurityViewFactory.class);
        });
  }

  @Test
  @DisplayName("Missing Authentication class backs off security auto-configuration")
  void missingAuthenticationClassBacksOff() {
    this.webContextRunner
        .withClassLoader(new FilteredClassLoader(Authentication.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(TemplateEngine.class);
              assertThat(context).doesNotHaveBean(SpringSecurityRenderContextContributor.class);
              assertThat(context).doesNotHaveBean(SecurityViewFactory.class);
            });
  }

  @Test
  @DisplayName(
      "Missing SpringSecurityRenderContextContributor backs off security auto-configuration")
  void missingContributorClassBacksOff() {
    this.webContextRunner
        .withClassLoader(new FilteredClassLoader(SpringSecurityRenderContextContributor.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(TemplateEngine.class);
              assertThat(context).doesNotHaveBean(SpringSecurityRenderContextContributor.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomSecurityViewFactoryConfiguration {
    static final SecurityViewFactory CUSTOM_FACTORY =
        (authentication, request) -> SecurityView.anonymousView();

    @Bean
    SecurityViewFactory securityViewFactory() {
      return CUSTOM_FACTORY;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomCsrfViewFactoryConfiguration {
    static final CsrfViewFactory CUSTOM_FACTORY = (HttpServletRequest request) -> null;

    @Bean
    CsrfViewFactory csrfViewFactory() {
      return CUSTOM_FACTORY;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomContributorConfiguration {
    static final SpringSecurityRenderContextContributor CUSTOM_CONTRIBUTOR =
        new SpringSecurityRenderContextContributor();

    @Bean
    SpringSecurityRenderContextContributor springSecurityRenderContextContributor() {
      return CUSTOM_CONTRIBUTOR;
    }
  }
}
