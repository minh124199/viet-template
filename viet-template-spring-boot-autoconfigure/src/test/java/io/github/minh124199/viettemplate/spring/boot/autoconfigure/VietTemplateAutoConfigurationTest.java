package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateEngineCustomizer;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateView;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateViewResolver;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@ExtendWith(OutputCaptureExtension.class)
class VietTemplateAutoConfigurationTest {

  private final WebApplicationContextRunner webContextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(VietTemplateAutoConfiguration.class));

  private final ApplicationContextRunner nonWebContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(VietTemplateAutoConfiguration.class));

  @Test
  @DisplayName(
      "Default auto-configuration in Servlet web application registers engine and resolver")
  void defaultWebConfiguration() {
    this.webContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TemplateEngine.class);
          assertThat(context).hasSingleBean(VietTemplateViewResolver.class);
          assertThat(context).hasSingleBean(VietTemplateProperties.class);

          VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
          assertThat(resolver.getPrefix()).isEmpty();
          assertThat(resolver.getSuffix()).isEmpty();
          assertThat(resolver.getSuffixes()).isEmpty();
          assertThat(resolver.getContentType()).isEqualTo(VietTemplateView.DEFAULT_CONTENT_TYPE);
          assertThat(resolver.getCharset()).isEqualTo(StandardCharsets.UTF_8);
          assertThat(resolver.isCache()).isTrue();
          assertThat(resolver.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);

          TemplateEngine engine = context.getBean(TemplateEngine.class);
          assertThat(resolver.getEngine()).isSameAs(engine);
          assertThat(engine).isInstanceOf(TestTemplateEngine.class);

          TestTemplateEngine testEngine = (TestTemplateEngine) engine;
          assertThat(testEngine.getBuilder().getMaxCacheEntries()).isEqualTo(500);
          assertThat(testEngine.getBuilder().getNegativeCacheTtlMillis()).isEqualTo(5000L);
          assertThat(testEngine.getBuilder().isHotReload()).isFalse();
          assertThat(testEngine.getBuilder().getWatchDebounceMillis()).isEqualTo(50L);
          assertThat(testEngine.getBuilder().isRejectRuntimeCompilation()).isFalse();
        });
  }

  @Test
  @DisplayName("Non-web application backs off VietTemplateViewResolver but creates TemplateEngine")
  void nonWebApplicationBacksOffResolver() {
    this.nonWebContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TemplateEngine.class);
          assertThat(context).doesNotHaveBean(VietTemplateViewResolver.class);
        });
  }

  @Test
  @DisplayName("Disabled property (viet-template.enabled=false) backs off completely")
  void disabledPropertyBacksOffCompletely() {
    this.webContextRunner
        .withPropertyValues("viet-template.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(TemplateEngine.class);
              assertThat(context).doesNotHaveBean(VietTemplateViewResolver.class);
              assertThat(context).doesNotHaveBean(VietTemplateProperties.class);
            });
  }

  @Test
  @DisplayName("User-defined TemplateEngine bean causes auto-configured engine to back off")
  void userTemplateEngineBacksOff() {
    this.webContextRunner
        .withUserConfiguration(CustomTemplateEngineConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(TemplateEngine.class);
              TemplateEngine engine = context.getBean(TemplateEngine.class);
              assertThat(engine).isSameAs(CustomTemplateEngineConfiguration.USER_ENGINE);

              VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
              assertThat(resolver.getEngine())
                  .isSameAs(CustomTemplateEngineConfiguration.USER_ENGINE);
            });
  }

  @Test
  @DisplayName(
      "User-defined VietTemplateViewResolver bean causes auto-configured resolver to back off")
  void userViewResolverBacksOff() {
    this.webContextRunner
        .withUserConfiguration(CustomViewResolverConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(VietTemplateViewResolver.class);
              VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
              assertThat(resolver.getPrefix()).isEqualTo("custom-prefix/");
            });
  }

  @Test
  @DisplayName("VietTemplateEngineCustomizer beans are executed against the engine builder")
  void customizerExecution() {
    this.webContextRunner
        .withUserConfiguration(SingleCustomizerConfiguration.class)
        .run(
            context -> {
              TemplateEngine engine = context.getBean(TemplateEngine.class);
              assertThat(engine).isInstanceOf(TestTemplateEngine.class);

              TestTemplateEngine testEngine = (TestTemplateEngine) engine;
              assertThat(testEngine.getBuilder().isCustomized()).isTrue();
              assertThat(testEngine.getBuilder().getMaxCacheEntries()).isEqualTo(12345);
            });
  }

  @Test
  @DisplayName("Multiple VietTemplateEngineCustomizer beans execute in specified @Order")
  void orderedCustomizerExecution() {
    this.webContextRunner
        .withUserConfiguration(OrderedCustomizersConfiguration.class)
        .run(
            context -> {
              assertThat(OrderedCustomizersConfiguration.EXECUTION_ORDER)
                  .containsExactly("firstCustomizer", "secondCustomizer");
            });
  }

  @Test
  @DisplayName(
      "Configuration properties bind correctly to VietTemplateProperties, resolver, and engine")
  void configurationPropertyBinding() {
    this.webContextRunner
        .withPropertyValues(
            "viet-template.prefix=views/",
            "viet-template.suffix=.vtl",
            "viet-template.content-type=application/xhtml+xml",
            "viet-template.charset=UTF-8",
            "viet-template.cache=false",
            "viet-template.check-template-location=false",
            "viet-template.runtime-compilation-enabled=false",
            "viet-template.max-cache-entries=1000",
            "viet-template.negative-cache-ttl-millis=2000",
            "viet-template.hot-reload=true",
            "viet-template.watch-debounce-millis=100",
            "viet-template.order=10")
        .run(
            context -> {
              VietTemplateProperties properties = context.getBean(VietTemplateProperties.class);
              assertThat(properties.getPrefix()).isEqualTo("views/");
              assertThat(properties.getSuffix()).isEqualTo(".vtl");
              assertThat(properties.getContentType()).isEqualTo("application/xhtml+xml");
              assertThat(properties.getCharset()).isEqualTo(StandardCharsets.UTF_8);
              assertThat(properties.isCache()).isFalse();
              assertThat(properties.isCheckTemplateLocation()).isFalse();
              assertThat(properties.isRuntimeCompilationEnabled()).isFalse();
              assertThat(properties.getMaxCacheEntries()).isEqualTo(1000);
              assertThat(properties.getNegativeCacheTtlMillis()).isEqualTo(2000L);
              assertThat(properties.isHotReload()).isTrue();
              assertThat(properties.getWatchDebounceMillis()).isEqualTo(100L);
              assertThat(properties.getOrder()).isEqualTo(10);

              VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
              assertThat(resolver.getPrefix()).isEqualTo("views/");
              assertThat(resolver.getSuffix()).isEqualTo(".vtl");
              assertThat(resolver.getContentType()).isEqualTo("application/xhtml+xml");
              assertThat(resolver.getCharset()).isEqualTo(StandardCharsets.UTF_8);
              assertThat(resolver.isCache()).isFalse();
              assertThat(resolver.isCheckTemplateLocation()).isFalse();
              assertThat(resolver.getOrder()).isEqualTo(10);

              TestTemplateEngine engine =
                  (TestTemplateEngine) context.getBean(TemplateEngine.class);
              assertThat(engine.getBuilder().isRejectRuntimeCompilation()).isTrue();
              assertThat(engine.getBuilder().getMaxCacheEntries()).isEqualTo(1000);
              assertThat(engine.getBuilder().getNegativeCacheTtlMillis()).isEqualTo(2000L);
              assertThat(engine.getBuilder().isHotReload()).isTrue();
              assertThat(engine.getBuilder().getWatchDebounceMillis()).isEqualTo(100L);
            });
  }

  @Test
  @DisplayName(
      "Configuration properties bind indexed suffixes to VietTemplateProperties and resolver")
  void indexedSuffixesBinding() {
    this.webContextRunner
        .withPropertyValues("viet-template.suffixes[0]=.vtl", "viet-template.suffixes[1]=.vm")
        .run(
            context -> {
              VietTemplateProperties properties = context.getBean(VietTemplateProperties.class);
              assertThat(properties.getSuffixes()).containsExactly(".vtl", ".vm");
              assertThat(properties.determineEffectiveSuffixes()).containsExactly(".vtl", ".vm");

              VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
              assertThat(resolver.getSuffix()).isEmpty();
              assertThat(resolver.getSuffixes()).containsExactly(".vtl", ".vm");
            });
  }

  @Test
  @DisplayName(
      "Configuration properties bind comma-separated suffixes to VietTemplateProperties and"
          + " resolver")
  void commaSeparatedSuffixesBinding() {
    this.webContextRunner
        .withPropertyValues("viet-template.suffixes=.vtl,.vm")
        .run(
            context -> {
              VietTemplateProperties properties = context.getBean(VietTemplateProperties.class);
              assertThat(properties.getSuffixes()).containsExactly(".vtl", ".vm");
              assertThat(properties.determineEffectiveSuffixes()).containsExactly(".vtl", ".vm");

              VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
              assertThat(resolver.getSuffix()).isEmpty();
              assertThat(resolver.getSuffixes()).containsExactly(".vtl", ".vm");
            });
  }

  @Test
  @DisplayName(
      "Configuration properties bind comma-separated suffixes with whitespace to"
          + " VietTemplateProperties and resolver")
  void commaSeparatedSuffixesWithWhitespaceBinding() {
    this.webContextRunner
        .withPropertyValues("viet-template.suffixes=.vtl, .vm")
        .run(
            context -> {
              VietTemplateProperties properties = context.getBean(VietTemplateProperties.class);
              assertThat(properties.getSuffixes()).containsExactly(".vtl", ".vm");
              assertThat(properties.determineEffectiveSuffixes()).containsExactly(".vtl", ".vm");

              VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
              assertThat(resolver.getSuffix()).isEmpty();
              assertThat(resolver.getSuffixes()).containsExactly(".vtl", ".vm");
            });
  }

  @Test
  @DisplayName(
      "Both suffix and suffixes set: suffixes takes precedence on resolver and"
          + " determineEffectiveSuffixes")
  void suffixAndSuffixesPrecedenceBinding() {
    this.webContextRunner
        .withPropertyValues("viet-template.suffix=.html", "viet-template.suffixes=.vtl,.vm")
        .run(
            context -> {
              VietTemplateProperties properties = context.getBean(VietTemplateProperties.class);
              assertThat(properties.getSuffix()).isEqualTo(".html");
              assertThat(properties.getSuffixes()).containsExactly(".vtl", ".vm");
              assertThat(properties.determineEffectiveSuffixes()).containsExactly(".vtl", ".vm");

              VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
              assertThat(resolver.getSuffix()).isEqualTo(".html");
              assertThat(resolver.getSuffixes()).containsExactly(".vtl", ".vm");
            });
  }

  @Test
  @DisplayName("Empty suffixes property falls back to single suffix")
  void emptySuffixesBinding() {
    this.webContextRunner
        .withPropertyValues("viet-template.suffix=.vtl", "viet-template.suffixes=")
        .run(
            context -> {
              VietTemplateProperties properties = context.getBean(VietTemplateProperties.class);
              assertThat(properties.getSuffix()).isEqualTo(".vtl");
              assertThat(properties.getSuffixes()).isEmpty();
              assertThat(properties.determineEffectiveSuffixes()).containsExactly(".vtl");

              VietTemplateViewResolver resolver = context.getBean(VietTemplateViewResolver.class);
              assertThat(resolver.getSuffix()).isEqualTo(".vtl");
              assertThat(resolver.getSuffixes()).isEmpty();
            });
  }

  @Test
  @DisplayName("Clean TemplateEngine.close() invocation on application context shutdown")
  void cleanCloseOnShutdown() {
    this.webContextRunner.run(
        context -> {
          TemplateEngine engine = context.getBean(TemplateEngine.class);
          assertThat(engine).isInstanceOf(TestTemplateEngine.class);
          TestTemplateEngine testEngine = (TestTemplateEngine) engine;

          assertThat(testEngine.isClosed()).isFalse();
          context.close();
          assertThat(testEngine.isClosed()).isTrue();
        });
  }

  @Test
  @DisplayName("Template location check logs warning when template location is missing")
  void templateLocationCheckWarnsWhenNotFound(CapturedOutput output) {
    this.webContextRunner.run(
        context -> {
          assertThat(output.getAll())
              .contains("Cannot find template location: classpath:/templates/");
        });
  }

  @Test
  @DisplayName("Template location check disabled does not log warning")
  void templateLocationCheckDisabledDoesNotWarn(CapturedOutput output) {
    this.webContextRunner
        .withPropertyValues("viet-template.check-template-location=false")
        .run(
            context -> {
              assertThat(output.getAll()).doesNotContain("Cannot find template location");
            });
  }

  @Test
  @DisplayName("Template location check distinguishes AOT templates.idx and skips warning")
  void templateLocationCheckDistinguishesAotIndex(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir, CapturedOutput output)
      throws Exception {
    java.nio.file.Path idxFile = tempDir.resolve("templates.idx");
    java.nio.file.Files.writeString(idxFile, "home=com.example.Home");
    URL aotIndexUrl = idxFile.toUri().toURL();
    ClassLoader aotClassLoader =
        new ClassLoader(getClass().getClassLoader()) {
          @Override
          public URL getResource(String name) {
            if ("META-INF/viet-template/templates.idx".equals(name)) {
              return aotIndexUrl;
            }
            return super.getResource(name);
          }
        };

    this.webContextRunner
        .withClassLoader(aotClassLoader)
        .run(
            context -> {
              assertThat(output.getAll()).doesNotContain("Cannot find template location");
            });
  }

  @Test
  @DisplayName("Configuration metadata contains suffix and suffixes properties")
  void configurationMetadataVerification() throws Exception {
    try (InputStream in =
        getClass()
            .getClassLoader()
            .getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
      assertThat(in).isNotNull();
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(json).contains("\"name\": \"viet-template.suffix\"");
      assertThat(json).contains("\"name\": \"viet-template.suffixes\"");
      assertThat(json).contains("\"type\": \"java.lang.String\"");
      assertThat(json).contains("\"type\": \"java.util.List<java.lang.String>\"");
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomTemplateEngineConfiguration {

    static final TemplateEngine USER_ENGINE =
        new TestTemplateEngine(new TestTemplateEngineBuilder());

    @Bean
    TemplateEngine customTemplateEngine() {
      return USER_ENGINE;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomViewResolverConfiguration {

    @Bean(name = "vietTemplateViewResolver")
    VietTemplateViewResolver vietTemplateViewResolver(TemplateEngine engine) {
      VietTemplateViewResolver resolver = new VietTemplateViewResolver(engine);
      resolver.setPrefix("custom-prefix/");
      return resolver;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class SingleCustomizerConfiguration {

    @Bean
    VietTemplateEngineCustomizer singleCustomizer() {
      return builder -> {
        ((TestTemplateEngineBuilder) builder).markCustomized();
        builder.maxCacheEntries(12345);
      };
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OrderedCustomizersConfiguration {

    static final List<String> EXECUTION_ORDER = new ArrayList<>();

    @Bean
    @Order(1)
    VietTemplateEngineCustomizer firstCustomizer() {
      return builder -> EXECUTION_ORDER.add("firstCustomizer");
    }

    @Bean
    @Order(2)
    VietTemplateEngineCustomizer secondCustomizer() {
      return builder -> EXECUTION_ORDER.add("secondCustomizer");
    }
  }
}
