package io.github.minh124199.viettemplate.vtl.engine.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import org.junit.jupiter.api.Test;

class TemplateCompileCacheTest {

  @Test
  void cacheHitAndMissAcrossDimensions() {
    TemplateCompileCache cache = new TemplateCompileCache(10, 5000L, 10);
    TemplateId id = TemplateId.of("test.vm");

    CompileCacheKey key1 =
        CompileCacheKey.of(
            id,
            "hash1",
            "0.1.1-SNAPSHOT",
            OptimizationLevel.O2,
            ExecutionTier.AOT_BYTECODE,
            "policy1",
            "model1",
            "v1");

    CompiledTemplateHandle handle1 = CompiledTemplateHandle.ofIr(id, 1L, key1, null);
    cache.put(key1, handle1);

    assertThat(cache.get(key1)).isPresent();
    assertThat(cache.getActive(id)).isPresent();
    assertThat(cache.getActive(id).get().generation()).isEqualTo(1L);

    // Different fingerprint -> miss
    CompileCacheKey key2 =
        CompileCacheKey.of(
            id,
            "hash2",
            "0.1.1-SNAPSHOT",
            OptimizationLevel.O2,
            ExecutionTier.AOT_BYTECODE,
            "policy1",
            "model1",
            "v1");
    assertThat(cache.get(key2)).isEmpty();

    // Different policy -> miss
    CompileCacheKey key3 =
        CompileCacheKey.of(
            id,
            "hash1",
            "0.1.1-SNAPSHOT",
            OptimizationLevel.O2,
            ExecutionTier.AOT_BYTECODE,
            "policy2",
            "model1",
            "v1");
    assertThat(cache.get(key3)).isEmpty();

    // Different model signature -> miss
    CompileCacheKey key4 =
        CompileCacheKey.of(
            id,
            "hash1",
            "0.1.1-SNAPSHOT",
            OptimizationLevel.O2,
            ExecutionTier.AOT_BYTECODE,
            "policy1",
            "model2",
            "v1");
    assertThat(cache.get(key4)).isEmpty();

    // Different optimization level -> miss
    CompileCacheKey key5 =
        CompileCacheKey.of(
            id,
            "hash1",
            "0.1.1-SNAPSHOT",
            OptimizationLevel.O0,
            ExecutionTier.AOT_BYTECODE,
            "policy1",
            "model1",
            "v1");
    assertThat(cache.get(key5)).isEmpty();
  }

  @Test
  void enforcesBoundedLruEviction() {
    TemplateCompileCache cache = new TemplateCompileCache(2, 5000L, 5);

    TemplateId id1 = TemplateId.of("t1.vm");
    TemplateId id2 = TemplateId.of("t2.vm");
    TemplateId id3 = TemplateId.of("t3.vm");

    CompileCacheKey k1 =
        CompileCacheKey.of(
            id1, "h1", "0.1.1-SNAPSHOT", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");
    CompileCacheKey k2 =
        CompileCacheKey.of(
            id2, "h2", "0.1.1-SNAPSHOT", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");
    CompileCacheKey k3 =
        CompileCacheKey.of(
            id3, "h3", "0.1.1-SNAPSHOT", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");

    cache.put(k1, CompiledTemplateHandle.ofIr(id1, 1L, k1, null));
    cache.put(k2, CompiledTemplateHandle.ofIr(id2, 2L, k2, null));
    assertThat(cache.size()).isEqualTo(2);

    // Access k1 to make k2 the eldest
    cache.get(k1);

    // Insert k3 -> should evict k2
    cache.put(k3, CompiledTemplateHandle.ofIr(id3, 3L, k3, null));
    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.get(k1)).isPresent();
    assertThat(cache.get(k3)).isPresent();
    assertThat(cache.get(k2)).isEmpty();
  }

  @Test
  void negativeCachingWithTtlAndInvalidation() throws InterruptedException {
    TemplateCompileCache cache = new TemplateCompileCache(10, 50L, 5); // 50ms TTL
    TemplateId missing = TemplateId.of("missing.vm");

    assertThat(cache.isNegativelyCached(missing)).isFalse();

    cache.recordNegative(missing, "File not found");
    assertThat(cache.isNegativelyCached(missing)).isTrue();

    // Wait for TTL expiration
    Thread.sleep(80L);
    assertThat(cache.isNegativelyCached(missing)).isFalse();

    // Re-record and test explicit invalidation
    cache.recordNegative(missing, "File not found");
    assertThat(cache.isNegativelyCached(missing)).isTrue();
    cache.invalidate(missing);
    assertThat(cache.isNegativelyCached(missing)).isFalse();
  }

  @Test
  void atomicReplacementUpdatesGeneration() {
    TemplateCompileCache cache = new TemplateCompileCache();
    TemplateId id = TemplateId.of("dynamic.vm");

    CompileCacheKey k1 =
        CompileCacheKey.of(
            id, "h1", "0.1.1-SNAPSHOT", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");
    CompileCacheKey k2 =
        CompileCacheKey.of(
            id, "h2", "0.1.1-SNAPSHOT", OptimizationLevel.O2, ExecutionTier.IR, "p", "m", "v");

    long gen1 = cache.nextGeneration();
    cache.put(k1, CompiledTemplateHandle.ofIr(id, gen1, k1, null));
    assertThat(cache.currentGeneration(id)).isEqualTo(gen1);

    long gen2 = cache.nextGeneration();
    cache.put(k2, CompiledTemplateHandle.ofIr(id, gen2, k2, null));
    assertThat(cache.currentGeneration(id)).isEqualTo(gen2);
    assertThat(gen2).isGreaterThan(gen1);
  }
}
