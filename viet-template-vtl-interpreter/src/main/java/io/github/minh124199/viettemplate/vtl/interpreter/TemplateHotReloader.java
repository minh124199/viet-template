package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Thread-safe development hot reload manager for compiled {@link IrTemplate} representations.
 *
 * <p>Tracks source content hashes and generation numbers per template. When template source changes
 * during development, atomic generation swaps ensure zero-downtime reloads without disturbing
 * in-flight renders using prior generation instances.
 */
public final class TemplateHotReloader {

  public record CacheEntry(
      IrTemplate template, long generation, String contentHash, long loadedEpochMillis) {

    public CacheEntry {
      Objects.requireNonNull(template, "template must not be null");
      Objects.requireNonNull(contentHash, "contentHash must not be null");
    }
  }

  private final ConcurrentMap<TemplateId, CacheEntry> cache = new ConcurrentHashMap<>();
  private final AtomicLong globalGeneration = new AtomicLong(1);

  public TemplateHotReloader() {}

  /** Retrieves an existing cached {@link IrTemplate} or loads and compiles it if absent. */
  public IrTemplate getOrLoad(
      TemplateId id,
      String sourceContent,
      VtlSemanticOptions options,
      SpaceGobbler.Mode spaceGobbling) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(sourceContent, "sourceContent must not be null");
    Objects.requireNonNull(options, "options must not be null");
    Objects.requireNonNull(spaceGobbling, "spaceGobbling must not be null");

    String hash = hashContent(sourceContent);
    CacheEntry existing = cache.get(id);
    if (existing != null && existing.contentHash().equals(hash)) {
      return existing.template();
    }

    IrTemplate compiled = compile(id, sourceContent, options, spaceGobbling);
    long gen = globalGeneration.getAndIncrement();
    CacheEntry newEntry = new CacheEntry(compiled, gen, hash, System.currentTimeMillis());
    cache.put(id, newEntry);
    return compiled;
  }

  /** Retrieves an existing cached {@link IrTemplate} or loads it using a supplier. */
  public IrTemplate getOrLoad(
      TemplateId id,
      Supplier<String> sourceSupplier,
      VtlSemanticOptions options,
      SpaceGobbler.Mode spaceGobbling) {
    Objects.requireNonNull(sourceSupplier, "sourceSupplier must not be null");
    return getOrLoad(id, sourceSupplier.get(), options, spaceGobbling);
  }

  /**
   * Checks whether the template source content has changed and atomically compiles and updates the
   * cached template if so.
   *
   * @return true if the template was reloaded, false if content was unchanged
   */
  public boolean reloadIfChanged(
      TemplateId id,
      String newSourceContent,
      VtlSemanticOptions options,
      SpaceGobbler.Mode spaceGobbling) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(newSourceContent, "newSourceContent must not be null");

    String hash = hashContent(newSourceContent);
    CacheEntry existing = cache.get(id);
    if (existing != null && existing.contentHash().equals(hash)) {
      return false;
    }

    IrTemplate compiled = compile(id, newSourceContent, options, spaceGobbling);
    long gen = globalGeneration.getAndIncrement();
    CacheEntry newEntry = new CacheEntry(compiled, gen, hash, System.currentTimeMillis());
    cache.put(id, newEntry);
    return true;
  }

  /** Retrieves the currently cached {@link IrTemplate} for the given ID if present. */
  public Optional<IrTemplate> get(TemplateId id) {
    CacheEntry entry = cache.get(id);
    return entry != null ? Optional.of(entry.template()) : Optional.empty();
  }

  /** Returns the current generation counter of the cached template, or 0 if not loaded. */
  public long currentGeneration(TemplateId id) {
    CacheEntry entry = cache.get(id);
    return entry != null ? entry.generation() : 0L;
  }

  /** Retrieves an existing cached {@link CacheEntry} or loads and compiles it if absent. */
  public CacheEntry getOrLoadEntry(
      TemplateId id,
      String sourceContent,
      VtlSemanticOptions options,
      SpaceGobbler.Mode spaceGobbling) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(sourceContent, "sourceContent must not be null");
    Objects.requireNonNull(options, "options must not be null");
    Objects.requireNonNull(spaceGobbling, "spaceGobbling must not be null");

    String hash = hashContent(sourceContent);
    CacheEntry existing = cache.get(id);
    if (existing != null && existing.contentHash().equals(hash)) {
      return existing;
    }

    IrTemplate compiled = compile(id, sourceContent, options, spaceGobbling);
    long gen = globalGeneration.getAndIncrement();
    CacheEntry newEntry = new CacheEntry(compiled, gen, hash, System.currentTimeMillis());
    cache.put(id, newEntry);
    return newEntry;
  }

  public CacheEntry getOrLoadEntry(TemplateId id, String sourceContent) {
    return getOrLoadEntry(
        id, sourceContent, VtlSemanticOptions.builder().build(), SpaceGobbler.Mode.LINES);
  }

  public IrTemplate getOrLoad(TemplateId id, String sourceContent) {
    return getOrLoadEntry(id, sourceContent).template();
  }

  public boolean reloadIfChanged(TemplateId id, String newSourceContent) {
    return reloadIfChanged(
        id, newSourceContent, VtlSemanticOptions.builder().build(), SpaceGobbler.Mode.LINES);
  }

  public Optional<CacheEntry> getEntry(TemplateId id) {
    return Optional.ofNullable(cache.get(id));
  }

  public java.util.Set<TemplateId> cachedTemplateIds() {
    return java.util.Set.copyOf(cache.keySet());
  }

  /** Invalidates a specific template from the cache. */
  public void invalidate(TemplateId id) {
    cache.remove(id);
  }

  /** Clears all cached templates. */
  public void invalidateAll() {
    cache.clear();
  }

  /** Returns the number of templates currently cached. */
  public int size() {
    return cache.size();
  }

  private static IrTemplate compile(
      TemplateId id, String content, VtlSemanticOptions options, SpaceGobbler.Mode spaceGobbling) {
    SourceText source = SourceText.of(id.value(), content);
    VtlParseResult parseResult = VtlParser.parse(source);
    if (parseResult.hasErrors()) {
      throw new TemplateRenderException(
          "Syntax error while compiling template " + id.value(),
          id,
          parseResult.template().span(),
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    BitSet gobbled = SpaceGobbler.computeGobbledIndices(source, spaceGobbling);
    SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(parseResult.template(), options);
    return AstToIrLowerer.lower(parseResult.template(), source, analysis, options, gobbled);
  }

  private static String hashContent(String content) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      return Integer.toHexString(content.hashCode());
    }
  }
}
