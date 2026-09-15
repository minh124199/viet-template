package io.github.minh124199.viettemplate.spring.web.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.View;

class VietTemplateViewResolverTest {

  private TestTemplateEngine engine;
  private VietTemplateViewResolver resolver;

  @BeforeEach
  void setUp() {
    engine = new TestTemplateEngine();
    resolver = new VietTemplateViewResolver(engine);
    resolver.setPrefix("views/");
    resolver.setSuffix(".vtl");
  }

  @Test
  @DisplayName("Resolves view name with configured prefix and suffix to normalized TemplateId")
  void prefixAndSuffixResolution() throws Exception {
    TemplateId expectedId = TemplateId.of("views/home.vtl");
    engine.registerTemplate(
        expectedId,
        new Template() {
          @Override
          public TemplateDescriptor descriptor() {
            return null;
          }

          @Override
          public void render(RenderContext context, TemplateOutput output) throws IOException {
            output.write("OK");
          }
        });

    View view = resolver.resolveViewName("home", Locale.ENGLISH);

    assertThat(view).isInstanceOf(VietTemplateView.class);
    VietTemplateView vietView = (VietTemplateView) view;
    assertThat(vietView.getTemplateId()).isEqualTo(expectedId);
    assertThat(vietView.getEngine()).isSameAs(engine);
  }

  @Test
  @DisplayName("Resolves view name correctly with empty prefix and suffix")
  void emptyPrefixAndSuffix() throws Exception {
    resolver.setPrefix("");
    resolver.setSuffix("");

    TemplateId expectedId = TemplateId.of("dashboard");
    engine.registerTemplate(
        expectedId,
        new Template() {
          @Override
          public TemplateDescriptor descriptor() {
            return null;
          }

          @Override
          public void render(RenderContext context, TemplateOutput output) throws IOException {}
        });

    View view = resolver.resolveViewName("dashboard", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId()).isEqualTo(expectedId);
  }

  @ParameterizedTest(name = "Path traversal attack vector rejected: {0}")
  @ValueSource(
      strings = {
        "../secret",
        "../../etc/passwd",
        "sub/../../secret",
        "..",
        "views/..",
        "views/../secret",
        "sub\\view",
        "..\\secret",
        "views\\..\\secret",
        "%2e%2e/secret",
        "%2E%2E/secret",
        "sub/%2e%2e/secret",
        "sub/%2E%2E/secret",
        "foo%2ffoo",
        "foo%5cfoo",
        "foo%00bar",
        "foo\0bar"
      })
  void pathTraversalRejection(String maliciousViewName) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> resolver.resolveViewName(maliciousViewName, Locale.ROOT));
  }

  @ParameterizedTest(name = "Blank view names rejected: \"{0}\"")
  @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
  void blankViewNamesRejected(String blankViewName) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> resolver.resolveViewName(blankViewName, Locale.ROOT));
  }

  @Test
  @DisplayName("Null view name returns null according to ViewResolver contract")
  void nullViewNameReturnsNull() throws Exception {
    assertThat(resolver.resolveViewName(null, Locale.ROOT)).isNull();
  }

  @Test
  @DisplayName("Missing template returns null when checkTemplateLocation is true (default)")
  void missingTemplateReturnsNull() throws Exception {
    // Template "missing" not registered in engine repository
    View view = resolver.resolveViewName("missing", Locale.ROOT);
    assertThat(view).isNull();
  }

  @Test
  @DisplayName("Missing template returns view when checkTemplateLocation is false")
  void missingTemplateWithCheckLocationDisabledReturnsView() throws Exception {
    resolver.setCheckTemplateLocation(false);

    View view = resolver.resolveViewName("missing", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/missing.vtl"));
  }

  @Test
  @DisplayName("Order defaults to Ordered.LOWEST_PRECEDENCE and is configurable")
  void orderConfiguration() {
    assertThat(resolver.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);

    resolver.setOrder(100);
    assertThat(resolver.getOrder()).isEqualTo(100);
  }

  @Test
  @DisplayName("View caching: cache=true caches singleton views, cache=false resolves fresh views")
  void viewCaching() throws Exception {
    TemplateId id = TemplateId.of("views/cached.vtl");
    engine.registerTemplate(
        id,
        new Template() {
          @Override
          public TemplateDescriptor descriptor() {
            return null;
          }

          @Override
          public void render(RenderContext context, TemplateOutput output) throws IOException {}
        });

    // 1. cache = true (default)
    View view1 = resolver.resolveViewName("cached", Locale.ROOT);
    View view2 = resolver.resolveViewName("cached", Locale.ROOT);
    assertThat(view1).isNotNull();
    assertThat(view1).isSameAs(view2);

    // 2. clearCache
    resolver.clearCache();
    View view3 = resolver.resolveViewName("cached", Locale.ROOT);
    assertThat(view3).isNotSameAs(view1);
    assertThat(view3).isEqualTo(view1);

    // 3. removeFromCache
    boolean removed = resolver.removeFromCache("cached");
    assertThat(removed).isTrue();
    View view4 = resolver.resolveViewName("cached", Locale.ROOT);
    assertThat(view4).isNotSameAs(view3);

    // 4. cache = false
    resolver.setCache(false);
    View view5 = resolver.resolveViewName("cached", Locale.ROOT);
    View view6 = resolver.resolveViewName("cached", Locale.ROOT);
    assertThat(view5).isNotSameAs(view6);
    assertThat(view5).isEqualTo(view6);
  }

  @Test
  @DisplayName("Configurable contentType and charset propagated to resolved view")
  void customContentTypeAndCharset() throws Exception {
    resolver.setContentType("application/xml");
    resolver.setCharset(StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("views/feed.vtl");
    engine.registerTemplate(
        id,
        new Template() {
          @Override
          public TemplateDescriptor descriptor() {
            return null;
          }

          @Override
          public void render(RenderContext context, TemplateOutput output) throws IOException {}
        });

    View view = resolver.resolveViewName("feed", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    VietTemplateView vietView = (VietTemplateView) view;
    assertThat(vietView.getContentType()).isEqualTo("application/xml");
    assertThat(vietView.getCharset()).isEqualTo(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("Spring ApplicationContext lifecycle and afterPropertiesSet")
  void springLifecycle() throws Exception {
    VietTemplateViewResolver unconfigured = new VietTemplateViewResolver();

    // Fails when engine is missing
    assertThatIllegalArgumentException().isThrownBy(unconfigured::afterPropertiesSet);

    // Resolves engine from ApplicationContext
    GenericApplicationContext context = new GenericApplicationContext();
    context.registerBean("templateEngine", TemplateEngine.class, () -> engine);
    context.refresh();

    unconfigured.setApplicationContext(context);
    unconfigured.afterPropertiesSet();
    assertThat(unconfigured.getEngine()).isSameAs(engine);
  }
}
