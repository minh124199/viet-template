package io.github.minh124199.viettemplate.spring.web.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.LayoutRenderPlan;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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

  @Test
  @DisplayName("Resolves multiple suffixes in configured order")
  void multipleSuffixesInOrder() throws Exception {
    resolver.setSuffixes(List.of(".vtl", ".vm", ".html"));
    registerDummyTemplate("views/page1.vtl");
    registerDummyTemplate("views/page2.vm");
    registerDummyTemplate("views/page3.html");

    View view1 = resolver.resolveViewName("page1", Locale.ROOT);
    assertThat(view1).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view1).getTemplateId())
        .isEqualTo(TemplateId.of("views/page1.vtl"));

    View view2 = resolver.resolveViewName("page2", Locale.ROOT);
    assertThat(view2).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view2).getTemplateId())
        .isEqualTo(TemplateId.of("views/page2.vm"));

    View view3 = resolver.resolveViewName("page3", Locale.ROOT);
    assertThat(view3).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view3).getTemplateId())
        .isEqualTo(TemplateId.of("views/page3.html"));
  }

  @Test
  @DisplayName(
      "Suffix precedence: earlier suffix in list wins when multiple template variants exist")
  void suffixPrecedenceWhenMultipleExist() throws Exception {
    resolver.setSuffixes(List.of(".vtl", ".vm"));
    registerDummyTemplate("views/conflict.vtl");
    registerDummyTemplate("views/conflict.vm");

    View view = resolver.resolveViewName("conflict", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/conflict.vtl"));
  }

  @Test
  @DisplayName("Returns null when no suffix candidate exists")
  void noCandidateExistsReturnsNull() throws Exception {
    resolver.setSuffixes(List.of(".vtl", ".vm"));
    assertThat(resolver.resolveViewName("nonexistent", Locale.ROOT)).isNull();
  }

  @Test
  @DisplayName("checkTemplateLocation=false deterministically uses first suffix without probing")
  void checkTemplateLocationFalseDeterministicFirstSuffix() throws Exception {
    resolver.setCheckTemplateLocation(false);
    resolver.setSuffixes(List.of(".vtl", ".vm"));

    View view = resolver.resolveViewName("unregistered", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/unregistered.vtl"));
  }

  @Test
  @DisplayName("checkTemplateLocation=false with matching suffix preserves exact logical name")
  void checkTemplateLocationFalseWithMatchingSuffix() throws Exception {
    resolver.setCheckTemplateLocation(false);
    resolver.setSuffixes(List.of(".vtl", ".vm"));

    View view = resolver.resolveViewName("dashboard.vm", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/dashboard.vm"));
  }

  @Test
  @DisplayName("Empty suffix alone and mixed with named suffixes resolves extensionless templates")
  void emptySuffixAloneAndMixed() throws Exception {
    resolver.setSuffixes(List.of(".vtl", ""));
    registerDummyTemplate("views/about");

    View view1 = resolver.resolveViewName("about", Locale.ROOT);
    assertThat(view1).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view1).getTemplateId()).isEqualTo(TemplateId.of("views/about"));

    resolver.setSuffixes(List.of(""));
    registerDummyTemplate("views/contact");

    View view2 = resolver.resolveViewName("contact", Locale.ROOT);
    assertThat(view2).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view2).getTemplateId())
        .isEqualTo(TemplateId.of("views/contact"));
  }

  @Test
  @DisplayName("Explicit extension in viewName resolves directly to matching candidate")
  void explicitExtensionInViewName() throws Exception {
    resolver.setSuffixes(List.of(".vtl", ".vm"));
    registerDummyTemplate("views/dashboard.vm");

    View view = resolver.resolveViewName("dashboard.vm", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/dashboard.vm"));
  }

  @Test
  @DisplayName("Dot in viewName that is not a configured extension appends suffix correctly")
  void dotInViewNameNotAnExtension() throws Exception {
    resolver.setSuffixes(List.of(".vtl", ".vm"));
    registerDummyTemplate("views/admin/user.profile.vtl");

    View view = resolver.resolveViewName("admin/user.profile", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/admin/user.profile.vtl"));
  }

  @Test
  @DisplayName(
      "View name with dotted directory name appends suffix correctly without treating directory dot"
          + " as suffix")
  void dottedDirectoryViewName() throws Exception {
    resolver.setSuffixes(List.of(".vtl", ".vm"));
    registerDummyTemplate("views/dir.with.dot/dashboard.vtl");

    View view = resolver.resolveViewName("dir.with.dot/dashboard", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/dir.with.dot/dashboard.vtl"));
  }

  @Test
  @DisplayName(
      "Empty suffix in suffixes list resolves extensionless templates and does not match all names"
          + " as explicit suffix")
  void emptySuffixInList() throws Exception {
    resolver.setSuffixes(List.of("", ".vtl"));
    registerDummyTemplate("views/dashboard");
    registerDummyTemplate("views/profile.vtl");

    View view1 = resolver.resolveViewName("dashboard", Locale.ROOT);
    assertThat(view1).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view1).getTemplateId())
        .isEqualTo(TemplateId.of("views/dashboard"));

    View view2 = resolver.resolveViewName("profile.vtl", Locale.ROOT);
    assertThat(view2).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view2).getTemplateId())
        .isEqualTo(TemplateId.of("views/profile.vtl"));
  }

  @Test
  @DisplayName(
      "Custom extensions like .tar.gz and -template resolve with explicit and implicit names")
  void customExtensionViewName() throws Exception {
    resolver.setSuffixes(List.of(".tar.gz"));
    registerDummyTemplate("views/archive.tar.gz");

    View view1 = resolver.resolveViewName("archive", Locale.ROOT);
    assertThat(view1).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view1).getTemplateId())
        .isEqualTo(TemplateId.of("views/archive.tar.gz"));

    View view2 = resolver.resolveViewName("archive.tar.gz", Locale.ROOT);
    assertThat(view2).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view2).getTemplateId())
        .isEqualTo(TemplateId.of("views/archive.tar.gz"));

    resolver.setSuffixes(List.of("-template"));
    registerDummyTemplate("views/report-template");

    View view3 = resolver.resolveViewName("report", Locale.ROOT);
    assertThat(view3).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view3).getTemplateId())
        .isEqualTo(TemplateId.of("views/report-template"));

    View view4 = resolver.resolveViewName("report-template", Locale.ROOT);
    assertThat(view4).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view4).getTemplateId())
        .isEqualTo(TemplateId.of("views/report-template"));
  }

  @Test
  @DisplayName("Mutating resolver setters clears the view cache")
  void resolverMutationClearsCache() throws Exception {
    registerDummyTemplate("views/home.vtl");
    registerDummyTemplate("other/home.vtl");
    registerDummyTemplate("views/home.vm");

    View v1 = resolver.resolveViewName("home", Locale.ROOT);
    View v2 = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(v1).isSameAs(v2);

    // setPrefix clears cache
    resolver.setPrefix("other/");
    View vPrefix = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(vPrefix).isNotSameAs(v1);
    assertThat(((VietTemplateView) vPrefix).getTemplateId())
        .isEqualTo(TemplateId.of("other/home.vtl"));

    // setSuffix clears cache
    resolver.setPrefix("views/");
    resolver.setSuffix(".vm");
    View vSuffix = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(vSuffix).isNotSameAs(vPrefix);
    assertThat(((VietTemplateView) vSuffix).getTemplateId())
        .isEqualTo(TemplateId.of("views/home.vm"));

    // setSuffixes clears cache
    resolver.setSuffixes(List.of(".vtl"));
    View vSuffixes = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(vSuffixes).isNotSameAs(vSuffix);
    assertThat(((VietTemplateView) vSuffixes).getTemplateId())
        .isEqualTo(TemplateId.of("views/home.vtl"));

    // setContentType clears cache
    resolver.setContentType("text/plain");
    View vCt = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(vCt).isNotSameAs(vSuffixes);
    assertThat(vCt.getContentType()).isEqualTo("text/plain");

    // setCharset clears cache
    resolver.setCharset(StandardCharsets.UTF_8);
    View vCs = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(vCs).isNotSameAs(vCt);
    assertThat(((VietTemplateView) vCs).getCharset()).isEqualTo(StandardCharsets.UTF_8);

    // setCharset(String) clears cache
    resolver.setCharset("UTF-8");
    View vCsStr = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(vCsStr).isNotSameAs(vCs);

    // setCheckTemplateLocation clears cache
    resolver.setCheckTemplateLocation(false);
    View vChk = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(vChk).isNotSameAs(vCsStr);

    // setEngine clears cache
    TestTemplateEngine engine2 = new TestTemplateEngine();
    engine2.registerTemplate(
        TemplateId.of("views/home.vtl"),
        v1 != null
            ? ((VietTemplateView) v1).getEngine().get(TemplateId.of("views/home.vtl"))
            : null);
    resolver.setEngine(engine2);
    View vEng = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(vEng).isNotSameAs(vChk);
    assertThat(((VietTemplateView) vEng).getEngine()).isSameAs(engine2);
  }

  @Test
  @DisplayName("setOrder modifies ViewResolver precedence but does not invalidate the view cache")
  void orderChangeDoesNotClearCache() throws Exception {
    registerDummyTemplate("views/home.vtl");

    View view1 = resolver.resolveViewName("home", Locale.ROOT);
    assertThat(view1).isNotNull();

    resolver.setOrder(Ordered.HIGHEST_PRECEDENCE);
    View view2 = resolver.resolveViewName("home", Locale.ROOT);

    assertThat(view2)
        .as("setOrder should not clear the viewCache since view resolution logic is unaffected")
        .isSameAs(view1);
  }

  @ParameterizedTest(name = "Invalid suffix rejected in setSuffix and setSuffixes: {0}")
  @ValueSource(
      strings = {
        "../traversal",
        "sub/dir",
        "sub\\dir",
        "proto:dir",
        "null\0byte",
        "%2efoo",
        "%2ffoo",
        "%5cfoo",
        "%00foo"
      })
  void invalidSuffixRejection(String maliciousSuffix) {
    assertThatIllegalArgumentException().isThrownBy(() -> resolver.setSuffix(maliciousSuffix));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> resolver.setSuffixes(List.of(maliciousSuffix)));
  }

  @ParameterizedTest(name = "Safe suffix accepted: {0}")
  @ValueSource(
      strings = {
        ".tar.gz",
        "-template",
        "_template",
        "",
        ".vtl",
        ".vm",
        ".html",
        ".min.html",
        ".en.vtl",
        ".template",
        "~template"
      })
  void safeSuffixesAccepted(String safeSuffix) {
    resolver.setSuffix(safeSuffix);
    assertThat(resolver.getSuffix()).isEqualTo(safeSuffix);

    resolver.setSuffixes(List.of(safeSuffix));
    assertThat(resolver.getSuffixes()).containsExactly(safeSuffix);
  }

  @Test
  @DisplayName("engine.get() throwing syntax exception does not fall through to next suffix")
  void engineGetThrowsSyntaxExceptionDoesNotFallThrough() throws Exception {
    TestTemplateEngine throwingEngine = new TestTemplateEngine();
    throwingEngine.setRepository(null); // Force engine.get() invocation
    throwingEngine.registerTemplate(
        TemplateId.of("views/broken.vm"),
        new Template() {
          @Override
          public TemplateDescriptor descriptor() {
            return null;
          }

          @Override
          public void render(RenderContext context, TemplateOutput output) throws IOException {}
        });

    // Custom engine where views/broken.vtl throws syntax RuntimeException
    TemplateEngine engineWithSyntaxError =
        new TemplateEngine() {
          @Override
          public Template get(TemplateId id) {
            if ("views/broken.vtl".equals(id.value())) {
              throw new IllegalStateException("Syntax error in broken.vtl");
            }
            return throwingEngine.get(id);
          }

          @Override
          public void render(RenderRequest request, TemplateOutput output) throws IOException {
            throwingEngine.render(request, output);
          }

          @Override
          public TemplateRepository repository() {
            return null;
          }

          @Override
          public LayoutRenderPlan prepareLayoutPlan(TemplateId screenId, RenderContext context) {
            return null;
          }

          @Override
          public TemplateDependencyGraph dependencyGraph() {
            return null;
          }

          @Override
          public Set<TemplateId> invalidateWithDependents(TemplateId id) {
            return Set.of();
          }

          @Override
          public void close() {}

          @Override
          public boolean rejectRuntimeCompilation() {
            return false;
          }
        };

    VietTemplateViewResolver errorResolver = new VietTemplateViewResolver(engineWithSyntaxError);
    errorResolver.setPrefix("views/");
    errorResolver.setSuffixes(List.of(".vtl", ".vm"));

    View view = errorResolver.resolveViewName("broken", Locale.ROOT);
    assertThat(view).isNotNull();
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/broken.vtl"));
  }

  @Test
  @DisplayName(
      "Deduplicates suffixes preserving order, rejects null elements, and protects defensive copy")
  void deduplicatedSuffixesAndNullHandling() {
    resolver.setSuffixes(List.of(".vtl", ".vm", ".vtl"));
    assertThat(resolver.getSuffixes()).containsExactly(".vtl", ".vm");
    assertThat(resolver.getEffectiveSuffixes()).containsExactly(".vtl", ".vm");

    assertThatThrownBy(() -> resolver.setSuffixes(Arrays.asList(".vtl", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");

    resolver.setSuffixes(null);
    assertThat(resolver.getSuffixes()).isEmpty();
    assertThat(resolver.getEffectiveSuffixes()).containsExactly(resolver.getSuffix());

    resolver.setSuffixes(List.of(".vtl"));
    assertThatThrownBy(() -> resolver.getSuffixes().add(".vm"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Repository find throwing TemplateResourceException falls through to engine.get()")
  void repositoryFindThrowsTemplateResourceExceptionFallsThroughToEngine() throws Exception {
    engine.setRepository(
        new TemplateRepository() {
          @Override
          public Optional<TemplateSource> find(TemplateId id) {
            throw new TemplateResourceException(
                "Simulated repository resource error",
                id,
                SourceSpan.UNKNOWN,
                DiagnosticCode.of("RESOURCE", "ERROR"));
          }
        });
    registerDummyTemplate("views/fallback.vtl");

    View view = resolver.resolveViewName("fallback", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/fallback.vtl"));
  }

  @Test
  @DisplayName("Repository find throwing unexpected RuntimeException falls through to engine.get()")
  void repositoryFindThrowsUnexpectedRuntimeExceptionFallsThroughToEngine() throws Exception {
    engine.setRepository(
        new TemplateRepository() {
          @Override
          public Optional<TemplateSource> find(TemplateId id) {
            throw new IllegalStateException("Simulated unexpected repository failure");
          }
        });
    registerDummyTemplate("views/fallback2.vtl");

    View view = resolver.resolveViewName("fallback2", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/fallback2.vtl"));
  }

  @Test
  @DisplayName("engine.get() throwing TemplateResourceException returns null")
  void engineGetThrowsTemplateResourceExceptionReturnsNull() throws Exception {
    engine.setRepository(null); // Force engine.get() probe
    View view = resolver.resolveViewName("nonexistent", Locale.ROOT);
    assertThat(view).isNull();
  }

  @Test
  @DisplayName(
      "engine.get() throwing unexpected RuntimeException selects candidate to preserve diagnostic")
  void engineGetThrowsUnexpectedRuntimeExceptionSelectsCandidate() throws Exception {
    TemplateEngine unexpectedErrorEngine =
        new TemplateEngine() {
          @Override
          public Template get(TemplateId id) {
            throw new IllegalStateException(
                "Unexpected engine failure during template compilation");
          }

          @Override
          public void render(RenderRequest request, TemplateOutput output) {}

          @Override
          public TemplateRepository repository() {
            return null;
          }

          @Override
          public LayoutRenderPlan prepareLayoutPlan(TemplateId screenId, RenderContext context) {
            return null;
          }

          @Override
          public TemplateDependencyGraph dependencyGraph() {
            return null;
          }

          @Override
          public Set<TemplateId> invalidateWithDependents(TemplateId id) {
            return Set.of();
          }

          @Override
          public void close() {}

          @Override
          public boolean rejectRuntimeCompilation() {
            return false;
          }
        };

    VietTemplateViewResolver customResolver = new VietTemplateViewResolver(unexpectedErrorEngine);
    customResolver.setPrefix("views/");
    customResolver.setSuffix(".vtl");

    View view = customResolver.resolveViewName("errorTemplate", Locale.ROOT);
    assertThat(view).isInstanceOf(VietTemplateView.class);
    assertThat(((VietTemplateView) view).getTemplateId())
        .isEqualTo(TemplateId.of("views/errorTemplate.vtl"));
  }

  private void registerDummyTemplate(String templateId) {
    engine.registerTemplate(
        TemplateId.of(templateId),
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
  }
}
