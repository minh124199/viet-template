package io.github.minh124199.viettemplate.spring.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateViewResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StarterClasspathIsolationTest {

  @Test
  @DisplayName("Spring Security core classes must NOT be present on standard starter classpath")
  void springSecurityCoreNotOnClasspath() {
    assertThatThrownBy(() -> Class.forName("org.springframework.security.core.Authentication"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  @DisplayName("Spring Security web classes must NOT be present on standard starter classpath")
  void springSecurityWebNotOnClasspath() {
    assertThatThrownBy(() -> Class.forName("org.springframework.security.web.SecurityFilterChain"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  @DisplayName("viet-template-spring-security must NOT be present on standard starter classpath")
  void vietTemplateSpringSecurityNotOnClasspath() {
    assertThatThrownBy(
            () -> Class.forName("io.github.minh124199.viettemplate.spring.security.SecurityView"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  @DisplayName("Standard starter core classes are present and loadable")
  void standardStarterCoreClassesPresent() {
    assertThat(TemplateEngine.class).isNotNull();
    assertThat(VietTemplateViewResolver.class).isNotNull();
  }
}
