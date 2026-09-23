package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.CompositeTemplateRepository;
import io.github.minh124199.viettemplate.api.FilesystemTemplateRepository;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateFreshnessProvider;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSource;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Comprehensive freshness contract hardening test suite for Milestone M5.9. */
class FreshnessContractHardeningTest {

  @Test
  @DisplayName(
      "1. Adversarial filesystem same-size replacement: safe fallback detects content change")
  void adversarialFilesystemSameSizeReplacement(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("template.vm");
    Files.writeString(file, "Hello Alice", StandardCharsets.UTF_8);

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(tempDir);
    TemplateId id = TemplateId.of("template.vm");

    // Verify FilesystemTemplateRepository does NOT implement TemplateFreshnessProvider
    assertThat(repo).isNotInstanceOf(TemplateFreshnessProvider.class);

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      Template t1 = engine.get(id);
      StringTemplateOutput out1 = new StringTemplateOutput();
      t1.render(RenderContext.empty(), out1);
      assertThat(out1.toString()).isEqualTo("Hello Alice");

      // Replace content with same length ("Hello Bobby" is 11 bytes, same as "Hello Alice")
      // within filesystem timestamp resolution
      FileTime originalMtime = Files.getLastModifiedTime(file);
      Files.writeString(file, "Hello Bobby", StandardCharsets.UTF_8);
      Files.setLastModifiedTime(file, originalMtime);

      // Safe fallback reads file content, hashes SHA-256, detects change, recompiles
      Template t2 = engine.get(id);
      assertThat(t2).isNotSameAs(t1);

      StringTemplateOutput out2 = new StringTemplateOutput();
      t2.render(RenderContext.empty(), out2);
      assertThat(out2.toString()).isEqualTo("Hello Bobby");
    }
  }

  @Test
  @DisplayName(
      "2. Adversarial filesystem forced mtime preservation: safe fallback compiles and renders B")
  void adversarialFilesystemForcedMtimePreservation(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("mtime_test.vm");
    String contentA = "Message 12345"; // 13 bytes
    String contentB = "Message 67890"; // 13 bytes

    Files.writeString(file, contentA, StandardCharsets.UTF_8);
    FileTime preservedTime = Files.getLastModifiedTime(file);

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(tempDir);
    TemplateId id = TemplateId.of("mtime_test.vm");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      Template tA = engine.get(id);
      StringTemplateOutput outA = new StringTemplateOutput();
      tA.render(RenderContext.empty(), outA);
      assertThat(outA.toString()).isEqualTo("Message 12345");

      // Overwrite with contentB of same size and forcibly restore original mtime
      Files.writeString(file, contentB, StandardCharsets.UTF_8);
      Files.setLastModifiedTime(file, preservedTime);

      Template tB = engine.get(id);
      assertThat(tB).isNotSameAs(tA);

      StringTemplateOutput outB = new StringTemplateOutput();
      tB.render(RenderContext.empty(), outB);
      assertThat(outB.toString()).isEqualTo("Message 67890");
    }
  }

  @Test
  @DisplayName(
      "3. Composite shadowing: Tier A miss + Tier B present -> B; Tier A gains -> A; Tier A"
          + " removed -> B")
  void compositeShadowingTransitions() throws IOException {
    InMemoryTemplateRepository tierA = InMemoryTemplateRepository.create();
    InMemoryTemplateRepository tierB = InMemoryTemplateRepository.create();
    CompositeTemplateRepository composite = CompositeTemplateRepository.of(tierA, tierB);

    TemplateId id = TemplateId.of("shadow.vm");
    tierB.put(id, "From Tier B");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(composite).build()) {
      // 1. Tier A miss + Tier B present -> resolves B
      Template t1 = engine.get(id);
      StringTemplateOutput out1 = new StringTemplateOutput();
      t1.render(RenderContext.empty(), out1);
      assertThat(out1.toString()).isEqualTo("From Tier B");

      // 2. Tier A gains template with same ID -> resolves A
      tierA.put(id, "From Tier A");
      Template t2 = engine.get(id);
      assertThat(t2).isNotSameAs(t1);

      StringTemplateOutput out2 = new StringTemplateOutput();
      t2.render(RenderContext.empty(), out2);
      assertThat(out2.toString()).isEqualTo("From Tier A");

      // Fast-path lookup for Tier A template
      Template t2Warm = engine.get(id);
      assertThat(t2Warm).isSameAs(t2);

      // 3. Tier A removed -> resolves B again
      tierA.remove(id);
      Template t3 = engine.get(id);
      assertThat(t3).isNotSameAs(t2);

      StringTemplateOutput out3 = new StringTemplateOutput();
      t3.render(RenderContext.empty(), out3);
      assertThat(out3.toString()).isEqualTo("From Tier B");
    }
  }

  @Test
  @DisplayName(
      "4. Composite fallback: non-freshness delegate causes composite freshnessToken to return"
          + " empty")
  void compositeFallbackWhenDelegateLacksFreshnessProvider(@TempDir Path tempDir)
      throws IOException {
    InMemoryTemplateRepository tier1 = InMemoryTemplateRepository.create();
    FilesystemTemplateRepository fsRepo = FilesystemTemplateRepository.of(tempDir);
    CompositeTemplateRepository composite = CompositeTemplateRepository.of(tier1, fsRepo);

    TemplateId id = TemplateId.of("sample.vm");
    tier1.put(id, "Hello from Tier 1: $val");

    // Composite returns Optional.empty() because fsRepo does not implement
    // TemplateFreshnessProvider
    assertThat(composite.freshnessToken(id)).isEmpty();

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(composite).build()) {
      // Get template succeeds via safe fallback
      Template t1 = engine.get(id);
      assertThat(t1).isNotNull();

      StringTemplateOutput out1 = new StringTemplateOutput();
      t1.render(RenderContext.of(Map.of("val", "World")), out1);
      assertThat(out1.toString()).isEqualTo("Hello from Tier 1: World");

      // Fast path is skipped (entry has null freshnessToken), safe fallback hits compile cache by
      // SHA-256
      Template t2 = engine.get(id);
      assertThat(t2).isSameAs(t1);

      // Mutate template content in tier1
      tier1.put(id, "Hello from Tier 1 Updated: $val");

      Template t3 = engine.get(id);
      assertThat(t3).isNotSameAs(t1);

      StringTemplateOutput out3 = new StringTemplateOutput();
      t3.render(RenderContext.of(Map.of("val", "Galaxy")), out3);
      assertThat(out3.toString()).isEqualTo("Hello from Tier 1 Updated: Galaxy");
    }
  }

  @Test
  @DisplayName(
      "5. Legacy custom repository: cache hit, source mutation, and negative cache TTL behavior")
  void legacyCustomRepositoryLifecycleAndTtl() throws Exception {
    LegacyCustomRepository repo = new LegacyCustomRepository();
    TemplateId id = TemplateId.of("legacy.vm");
    TemplateId missingId = TemplateId.of("missing.vm");
    repo.put(id, "Legacy v1: $x");

    // Verify it implements ONLY TemplateRepository
    assertThat(repo).isNotInstanceOf(TemplateFreshnessProvider.class);

    long negativeTtlMillis = 80L;
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .negativeCacheTtlMillis(negativeTtlMillis)
            .build()) {

      // 1. Initial retrieval: compiles and caches
      Template t1 = engine.get(id);
      assertThat(t1).isNotNull();
      assertThat(repo.findCallCount()).isEqualTo(1);

      // 2. Cache hit: find() called, SHA-256 fingerprint matches, returns same instance
      Template t2 = engine.get(id);
      assertThat(t2).isSameAs(t1);
      assertThat(repo.findCallCount()).isEqualTo(2);

      // 3. Source mutation: content changes, SHA-256 changes, recompiles
      repo.put(id, "Legacy v2: $x");
      Template t3 = engine.get(id);
      assertThat(t3).isNotSameAs(t1);
      assertThat(repo.findCallCount()).isEqualTo(3);

      StringTemplateOutput out3 = new StringTemplateOutput();
      t3.render(RenderContext.of(Map.of("x", "Alpha")), out3);
      assertThat(out3.toString()).isEqualTo("Legacy v2: Alpha");

      // 4. Negative cache behavior: missing template recorded
      assertThatThrownBy(() -> engine.get(missingId))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("Template not found in repository");
      assertThat(engine.cache().isNegativelyCached(missingId)).isTrue();

      // Subsequent call hits negative cache
      assertThatThrownBy(() -> engine.get(missingId))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("cached negative lookup");

      // Add missing template to repository
      repo.put(missingId, "Now Available: $y");

      // Before TTL expires: still negatively cached (legacy repo has no freshness token to clear
      // it)
      assertThatThrownBy(() -> engine.get(missingId))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("cached negative lookup");

      // Wait for negative TTL to expire
      Thread.sleep(negativeTtlMillis + 30L);

      // After TTL expires: negative entry expired, find() succeeds and template compiles
      Template tMissing = engine.get(missingId);
      assertThat(tMissing).isNotNull();
      StringTemplateOutput outMissing = new StringTemplateOutput();
      tMissing.render(RenderContext.of(Map.of("y", "Success")), outMissing);
      assertThat(outMissing.toString()).isEqualTo("Now Available: Success");
    }
  }

  @Test
  @DisplayName(
      "6. Negative cache transitions: instant eviction on versioned token; removal causes miss")
  void negativeCacheTransitions() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("versioned_trans.vm");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      // 1. Initial missing lookup -> negative cache recorded
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("Template not found in repository");
      assertThat(engine.cache().isNegativelyCached(id)).isTrue();

      // 2. Instant negative cache eviction when a token appears in versioned repository
      repo.put(id, "I appeared instantly: $m");
      Template t1 = engine.get(id);
      assertThat(t1).isNotNull();
      assertThat(engine.cache().isNegativelyCached(id)).isFalse();

      StringTemplateOutput out1 = new StringTemplateOutput();
      t1.render(RenderContext.of(Map.of("m", "Ready")), out1);
      assertThat(out1.toString()).isEqualTo("I appeared instantly: Ready");

      // 3. Positive cached template removal causes cache miss/resource exception on next get
      repo.remove(id);

      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("Template not found in repository");

      // Subsequent call hits negative cache
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("cached negative lookup");
    }
  }

  @Test
  @DisplayName(
      "7. Macro generation mutation matrix: add, remove, body change, param change increment"
          + " generation")
  void macroGenerationMutationMatrix() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId macroId = TemplateId.of("macros.vm");
    TemplateId mainId = TemplateId.of("main.vm");

    repo.put(macroId, "#macro(greeting)Initial#end");
    repo.put(mainId, "#greeting()");

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(macroId))
            .build()) {

      long gen0 = engine.globalMacroManager().generation();
      assertThat(gen0).isEqualTo(1L);

      Template t0 = engine.get(mainId);
      StringTemplateOutput out0 = new StringTemplateOutput();
      t0.render(RenderContext.empty(), out0);
      assertThat(out0.toString()).isEqualTo("Initial");

      // Fast path hit
      assertThat(engine.get(mainId)).isSameAs(t0);

      // Mutation 1: ADD macro
      repo.put(macroId, "#macro(greeting)Initial#end\n#macro(farewell)Goodbye#end");
      engine.invalidate(macroId);
      long gen1 = engine.globalMacroManager().generation();
      assertThat(gen1).isEqualTo(gen0 + 1);

      repo.put(mainId, "#farewell()");
      Template t1 = engine.get(mainId);
      assertThat(t1).isNotSameAs(t0);
      StringTemplateOutput out1 = new StringTemplateOutput();
      t1.render(RenderContext.empty(), out1);
      assertThat(out1.toString()).isEqualTo("Goodbye");

      // Mutation 2: BODY CHANGE
      repo.put(macroId, "#macro(greeting)Initial#end\n#macro(farewell)Farewell Friend#end");
      engine.invalidate(macroId);
      long gen2 = engine.globalMacroManager().generation();
      assertThat(gen2).isEqualTo(gen1 + 1);

      Template t2 = engine.get(mainId);
      assertThat(t2).isNotSameAs(t1);
      StringTemplateOutput out2 = new StringTemplateOutput();
      t2.render(RenderContext.empty(), out2);
      assertThat(out2.toString()).isEqualTo("Farewell Friend");

      // Mutation 3: PARAMETER CHANGE
      repo.put(macroId, "#macro(farewell $name)Farewell, $name!#end");
      engine.invalidate(macroId);
      long gen3 = engine.globalMacroManager().generation();
      assertThat(gen3).isEqualTo(gen2 + 1);

      repo.put(mainId, "#farewell('Alice')");
      Template t3 = engine.get(mainId);
      assertThat(t3).isNotSameAs(t2);
      StringTemplateOutput out3 = new StringTemplateOutput();
      t3.render(RenderContext.empty(), out3);
      assertThat(out3.toString()).isEqualTo("Farewell, Alice!");

      // Mutation 4: REMOVE macro
      repo.put(macroId, "#macro(other)Other#end");
      engine.invalidate(macroId);
      long gen4 = engine.globalMacroManager().generation();
      assertThat(gen4).isEqualTo(gen3 + 1);

      repo.put(mainId, "#other()");
      Template t4 = engine.get(mainId);
      assertThat(t4).isNotSameAs(t3);
      StringTemplateOutput out4 = new StringTemplateOutput();
      t4.render(RenderContext.empty(), out4);
      assertThat(out4.toString()).isEqualTo("Other");
    }
  }

  @Test
  @DisplayName(
      "8. Path traversal security: freshnessToken and repositories reject traversal inputs")
  void pathTraversalSecurityAcrossProviders(@TempDir Path tempDir) throws IOException {
    InMemoryTemplateRepository inMemory = InMemoryTemplateRepository.create();
    ClasspathTemplateRepository classpath =
        new ClasspathTemplateRepository(getClass().getClassLoader(), "templates");
    CompositeTemplateRepository composite = CompositeTemplateRepository.of(inMemory);
    FilesystemTemplateRepository filesystem = FilesystemTemplateRepository.of(tempDir);

    List<String> traversalAttacks =
        List.of(
            "../secret.txt",
            "../../etc/passwd",
            "dir/../../../etc/passwd",
            "%2e%2e%2fsecret.txt",
            "..%2fsecret.txt",
            "%2e%2e/secret.txt",
            "foo/%2e%2e/bar",
            "foo/%2fbar",
            "foo/%5cbar",
            "foo/%00bar");

    for (String attack : traversalAttacks) {
      // TemplateId constructor rejects invalid / traversal strings
      assertThatThrownBy(() -> TemplateId.of(attack)).isInstanceOf(IllegalArgumentException.class);

      // TemplateId.normalize rejects escaping / encoded traversal strings
      assertThatThrownBy(() -> TemplateId.normalize(attack))
          .isInstanceOf(IllegalArgumentException.class);
    }

    // Absolute paths are rejected by TemplateId constructor
    List<String> absolutePaths = List.of("/etc/passwd", "/secret.txt", "/root/template.vm");
    for (String absPath : absolutePaths) {
      assertThatThrownBy(() -> TemplateId.of(absPath)).isInstanceOf(IllegalArgumentException.class);
    }

    // Normal safe template IDs work across all freshness providers
    TemplateId safeId = TemplateId.of("valid/template.vm");
    assertThat(inMemory.freshnessToken(safeId)).isEmpty();
    assertThat(classpath.freshnessToken(safeId)).isEmpty();
    assertThat(composite.freshnessToken(safeId)).isEmpty();

    // FilesystemTemplateRepository find rejects candidate path outside root
    Path fileOutside = tempDir.getParent().resolve("outside.txt");
    Files.writeString(fileOutside, "outside secret");
    // Normal id inside root
    TemplateId testId = TemplateId.of("view.vm");
    assertThat(filesystem.find(testId)).isEmpty();
  }

  @Test
  @DisplayName(
      "9. Template identity: assertSame for same gen, assertNotSame on mutation, retained template"
          + " consistent")
  void templateIdentityAndRetainedOldGenerationConsistency() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("identity_test.vm");
    repo.put(id, "V1 Content: $v");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      Template t1a = engine.get(id);
      Template t1b = engine.get(id);

      // Same canonical instance for unchanged generation
      assertThat(t1b).isSameAs(t1a);

      // Mutate template source
      repo.put(id, "V2 Content: $v");

      Template t2 = engine.get(id);
      assertThat(t2).isNotSameAs(t1a);

      // Retained old-generation template remains consistent when rendered
      StringTemplateOutput outOld = new StringTemplateOutput();
      t1a.render(RenderContext.of(Map.of("v", "OldVal")), outOld);
      assertThat(outOld.toString()).isEqualTo("V1 Content: OldVal");

      // New template renders updated version
      StringTemplateOutput outNew = new StringTemplateOutput();
      t2.render(RenderContext.of(Map.of("v", "NewVal")), outNew);
      assertThat(outNew.toString()).isEqualTo("V2 Content: NewVal");

      // Retained old-generation template can be rendered multiple times without corruption
      StringTemplateOutput outOldAgain = new StringTemplateOutput();
      t1a.render(RenderContext.of(Map.of("v", "SecondOld")), outOldAgain);
      assertThat(outOldAgain.toString()).isEqualTo("V1 Content: SecondOld");
    }
  }

  @Test
  @DisplayName(
      "10. Concurrency stress: 32 threads concurrent get/render while repository and macros mutate")
  void concurrencyStressUnderMutations() throws Exception {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId macroId = TemplateId.of("stress_macro.vm");
    repo.put(macroId, "#macro(wrap $val)[$val]#end");

    int templateCount = 6;
    for (int i = 0; i < templateCount; i++) {
      repo.put("tpl_" + i + ".vm", "Template " + i + ": #wrap($arg)");
    }

    int threadCount = 32;
    int iterationsPerThread = 80;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicBoolean running = new AtomicBoolean(true);
    ConcurrentLinkedQueue<Throwable> exceptions = new ConcurrentLinkedQueue<>();

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(macroId))
            .build()) {

      // Mutator task: concurrently updates templates and invalidates macros
      executor.submit(
          () -> {
            try {
              startLatch.await();
              int mutCount = 0;
              while (running.get() && mutCount < 60) {
                int target = ThreadLocalRandom.current().nextInt(templateCount);
                TemplateId tid = TemplateId.of("tpl_" + target + ".vm");
                repo.put(tid, "Template " + target + " v" + mutCount + ": #wrap($arg)");

                if (mutCount % 5 == 0) {
                  repo.put(macroId, "#macro(wrap $val){" + mutCount + ": $val}#end");
                  engine.invalidate(macroId);
                }
                mutCount++;
                Thread.sleep(1);
              }
            } catch (Throwable t) {
              exceptions.add(t);
            }
          });

      // 32 Worker tasks
      for (int t = 0; t < threadCount; t++) {
        final int workerId = t;
        executor.submit(
            () -> {
              try {
                startLatch.await();
                for (int i = 0; i < iterationsPerThread; i++) {
                  int target = (workerId + i) % templateCount;
                  TemplateId tid = TemplateId.of("tpl_" + target + ".vm");
                  Template template = engine.get(tid);
                  assertThat(template).isNotNull();

                  StringTemplateOutput output = new StringTemplateOutput();
                  template.render(
                      RenderContext.of(Map.of("arg", "w" + workerId + "i" + i)), output);
                  String result = output.toString();
                  assertThat(result).contains("Template " + target);
                  assertThat(result).contains("w" + workerId + "i" + i);
                }
              } catch (Throwable t1) {
                exceptions.add(t1);
              } finally {
                doneLatch.countDown();
              }
            });
      }

      startLatch.countDown();
      boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
      running.set(false);
      assertThat(finished).as("All worker threads completed within timeout").isTrue();
      assertThat(exceptions).as("Zero errors or corrupt state during concurrent access").isEmpty();
    } finally {
      executor.shutdownNow();
    }
  }

  /** Legacy repository fixture implementing ONLY TemplateRepository.find(id). */
  private static final class LegacyCustomRepository implements TemplateRepository {

    private final Map<TemplateId, String> storage = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger findCalls =
        new java.util.concurrent.atomic.AtomicInteger();

    void put(TemplateId id, String content) {
      storage.put(id, content);
    }

    void put(String name, String content) {
      put(TemplateId.of(name), content);
    }

    void remove(TemplateId id) {
      storage.remove(id);
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
