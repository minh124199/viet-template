package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.FreshnessToken;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateFreshnessProvider;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSource;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerationAwareCacheLookupTest {

  @Test
  @DisplayName("ClasspathTemplateRepository: multiple get() calls return canonical instance without repeated find()")
  void classpathRepositoryUsesFreshnessTokenForCanonicalFastPath(@TempDir Path tempDir) throws Exception {
    Path templateFile = tempDir.resolve("hello.vm");
    Files.writeString(templateFile, "Hello $name!");

    URLClassLoader cl =
        new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    ClasspathTemplateRepository classpathRepo = ClasspathTemplateRepository.of(cl, "");

    CountingFreshnessProviderWrapper wrapper = new CountingFreshnessProviderWrapper(classpathRepo);
    TemplateId id = TemplateId.of("hello.vm");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(wrapper).build()) {
      // 1. Initial retrieval: compiles and caches entry with FreshnessToken
      Template t1 = engine.get(id);
      assertThat(t1).isNotNull();
      assertThat(wrapper.findCallCount()).isEqualTo(1);
      assertThat(wrapper.freshnessCallCount()).isEqualTo(1);

      // 2. Subsequent retrieval: fast-path hit, find() is NOT called again
      Template t2 = engine.get(id);
      assertThat(t2).isSameAs(t1);
      assertThat(wrapper.findCallCount()).isEqualTo(1);
      assertThat(wrapper.freshnessCallCount()).isEqualTo(2);

      // 3. Third retrieval: still fast-path hit
      Template t3 = engine.get(id);
      assertThat(t3).isSameAs(t1);
      assertThat(wrapper.findCallCount()).isEqualTo(1);
      assertThat(wrapper.freshnessCallCount()).isEqualTo(3);

      // Render check
      StringTemplateOutput output = new StringTemplateOutput();
      t1.render(RenderContext.of(Map.of("name", "World")), output);
      assertThat(output.toString()).isEqualTo("Hello World!");
    } finally {
      cl.close();
    }
  }

  @Test
  @DisplayName("Unadorned TemplateRepository: find() IS called on every engine.get() call")
  void unadornedRepositoryCallsFindOnEveryGet() {
    CountingUnadornedRepository repo = new CountingUnadornedRepository();
    TemplateId id = TemplateId.of("greeting.vm");
    repo.put(id, "Greeting: $msg");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      // 1. Initial retrieval: find() called, compiled, stored
      Template t1 = engine.get(id);
      assertThat(t1).isNotNull();
      assertThat(repo.findCallCount()).isEqualTo(1);

      // 2. Second retrieval: fast path skipped (no freshnessToken), find() called again, cache hit
      Template t2 = engine.get(id);
      assertThat(t2).isSameAs(t1);
      assertThat(repo.findCallCount()).isEqualTo(2);

      // 3. Third retrieval: find() called again
      Template t3 = engine.get(id);
      assertThat(t3).isSameAs(t1);
      assertThat(repo.findCallCount()).isEqualTo(3);
    }
  }

  @Test
  @DisplayName("InMemoryTemplateRepository: updated template detects freshness version change and renders updated content")
  void inMemoryRepositoryDetectsVersionChange() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("dynamic.vm");
    repo.put(id, "Version 1: $value");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      Template t1 = engine.get(id);
      StringTemplateOutput out1 = new StringTemplateOutput();
      t1.render(RenderContext.of(Map.of("value", "Alpha")), out1);
      assertThat(out1.toString()).isEqualTo("Version 1: Alpha");

      // Verify same instance returned while unchanged
      Template t1Cached = engine.get(id);
      assertThat(t1Cached).isSameAs(t1);

      // Update content in repository (increments version in freshness token)
      repo.put(id, "Version 2: $value");

      Template t2 = engine.get(id);
      assertThat(t2).isNotSameAs(t1);

      StringTemplateOutput out2 = new StringTemplateOutput();
      t2.render(RenderContext.of(Map.of("value", "Beta")), out2);
      assertThat(out2.toString()).isEqualTo("Version 2: Beta");

      // Verify new canonical instance is now cached
      Template t2Cached = engine.get(id);
      assertThat(t2Cached).isSameAs(t2);
    }
  }

  @Test
  @DisplayName("InMemoryTemplateRepository: removed template falls through to repository lookup and throws TemplateResourceException")
  void inMemoryRepositoryRemovalFallsThroughAndThrows() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("ephemeral.vm");
    repo.put(id, "I will be deleted soon");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      Template t1 = engine.get(id);
      assertThat(t1).isNotNull();

      // Remove from repository
      repo.remove(id);

      // Next engine.get() must fail because freshnessToken is empty and repository.find() returns empty
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("Template not found in repository");

      // Subsequent call hits negative cache
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("Template not found (cached negative lookup)");
    }
  }

  @Test
  @DisplayName("engine.invalidate(id) and invalidateAll() clear active entries and force fresh load")
  void invalidationForcesFreshLoad(@TempDir Path tempDir) throws Exception {
    Path templateFile = tempDir.resolve("inval.vm");
    Files.writeString(templateFile, "Content: $val");

    URLClassLoader cl =
        new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    ClasspathTemplateRepository classpathRepo = ClasspathTemplateRepository.of(cl, "");
    CountingFreshnessProviderWrapper wrapper = new CountingFreshnessProviderWrapper(classpathRepo);
    TemplateId id = TemplateId.of("inval.vm");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(wrapper).build()) {
      Template t1 = engine.get(id);
      assertThat(wrapper.findCallCount()).isEqualTo(1);

      // 1. Invalidate single template
      engine.invalidate(id);

      Template t2 = engine.get(id);
      assertThat(wrapper.findCallCount()).isEqualTo(2);
      assertThat(t2).isNotNull();

      // 2. Invalidate all templates
      engine.invalidateAll();

      Template t3 = engine.get(id);
      assertThat(wrapper.findCallCount()).isEqualTo(3);
      assertThat(t3).isNotNull();
    } finally {
      cl.close();
    }
  }

  @Test
  @DisplayName("Global macro invalidation bumps macro generation, bypassing fast path and recompiling template")
  void globalMacroInvalidationBypassesFastPathAndRecompiles() throws IOException {
    InMemoryTemplateRepository memoryRepo = InMemoryTemplateRepository.create();
    TemplateId macroId = TemplateId.of("macros.vm");
    TemplateId templateId = TemplateId.of("main.vm");

    memoryRepo.put(macroId, "#macro(greeting $name)Hello $name!#end");
    memoryRepo.put(templateId, "#greeting('Alice')");

    CountingFreshnessProviderWrapper wrapper = new CountingFreshnessProviderWrapper(memoryRepo);

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(wrapper)
            .globalMacroLibraries(List.of(macroId))
            .build()) {
      long initialGen = engine.globalMacroManager().generation();
      assertThat(initialGen).isEqualTo(1L);

      // 1. Initial retrieval: compiles and caches entry with FreshnessToken and macroGeneration = 1
      Template t1 = engine.get(templateId);
      assertThat(t1).isNotNull();
      StringTemplateOutput out1 = new StringTemplateOutput();
      t1.render(RenderContext.empty(), out1);
      assertThat(out1.toString()).isEqualTo("Hello Alice!");
      int initialFindCount = wrapper.findCallCount();

      // 2. Fast-path check: returns same template instance, find() is NOT called
      Template t2 = engine.get(templateId);
      assertThat(t2).isSameAs(t1);
      assertThat(wrapper.findCallCount()).isEqualTo(initialFindCount);

      // 3. Invalidate macro library: changes globalMacroManager.generation()
      memoryRepo.put(macroId, "#macro(greeting $name)Hi $name!#end");
      engine.invalidate(macroId);
      long nextGen = engine.globalMacroManager().generation();
      assertThat(nextGen).isGreaterThan(initialGen);

      // 4. Subsequent retrieval: fast-path is bypassed because macroGeneration changed, recompiling template
      Template t3 = engine.get(templateId);
      assertThat(t3).isNotSameAs(t1);
      assertThat(wrapper.findCallCount()).isGreaterThan(initialFindCount);

      StringTemplateOutput out3 = new StringTemplateOutput();
      t3.render(RenderContext.empty(), out3);
      assertThat(out3.toString()).isEqualTo("Hi Alice!");

      // 5. Subsequent retrieval returns the newly recompiled template instance via fast-path
      Template t4 = engine.get(templateId);
      assertThat(t4).isSameAs(t3);
    }
  }

  @Test
  @DisplayName("InMemoryTemplateRepository: negative cache entry is invalidated when template is added to repository")
  void negativeCacheInvalidatesWhenTemplateAddedToRepository() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("dynamic_added.vm");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class);
      assertThat(engine.cache().isNegativelyCached(id)).isTrue();

      repo.put("dynamic_added.vm", "Hello Newly Added!");

      Template template = engine.get(id);
      assertThat(template).isNotNull();

      StringTemplateOutput output = new StringTemplateOutput();
      template.render(RenderContext.empty(), output);
      assertThat(output.toString()).isEqualTo("Hello Newly Added!");

      assertThat(engine.cache().isNegativelyCached(id)).isFalse();
    }
  }

  /** Wrapper implementing TemplateFreshnessProvider that counts find() and freshnessToken() invocations. */
  private static final class CountingFreshnessProviderWrapper
      implements TemplateRepository, TemplateFreshnessProvider {

    private final TemplateRepository delegate;
    private final AtomicInteger findCalls = new AtomicInteger();
    private final AtomicInteger freshnessCalls = new AtomicInteger();

    CountingFreshnessProviderWrapper(TemplateRepository delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<TemplateSource> find(TemplateId id) {
      findCalls.incrementAndGet();
      return delegate.find(id);
    }

    @Override
    public Optional<FreshnessToken> freshnessToken(TemplateId id) {
      freshnessCalls.incrementAndGet();
      return delegate.freshnessToken(id);
    }

    int findCallCount() {
      return findCalls.get();
    }

    int freshnessCallCount() {
      return freshnessCalls.get();
    }
  }

  /** Unadorned repository that does NOT implement TemplateFreshnessProvider. */
  private static final class CountingUnadornedRepository implements TemplateRepository {

    private final Map<TemplateId, String> storage = new ConcurrentHashMap<>();
    private final AtomicInteger findCalls = new AtomicInteger();

    void put(TemplateId id, String content) {
      storage.put(id, content);
    }

    @Override
    public Optional<TemplateSource> find(TemplateId id) {
      findCalls.incrementAndGet();
      String content = storage.get(id);
      if (content == null) {
        return Optional.empty();
      }
      return Optional.of(TemplateSource.fromString(id, content));
    }

    int findCallCount() {
      return findCalls.get();
    }
  }
}
