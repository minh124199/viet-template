package io.github.minh124199.viettemplate.benchmarks.cache.prototype;

import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import java.util.Optional;
import java.util.Set;

/**
 * Common abstraction for benchmark qualification comparing current production compile-cache with
 * experimental low-contention prototype designs.
 */
public interface CompileCacheInterface {

  Optional<CompiledTemplateHandle> get(CompileCacheKey key);

  Optional<CompiledTemplateHandle> getActive(TemplateId id);

  void put(CompileCacheKey key, CompiledTemplateHandle handle);

  boolean isNegativelyCached(TemplateId id);

  void recordNegative(TemplateId id, String reason);

  void invalidate(TemplateId id);

  Set<TemplateId> invalidateWithDependents(TemplateId id, TemplateDependencyGraph graph);

  void invalidateAll();

  int size();

  int negativeCacheSize();

  boolean isInternallyConsistent();
}
