package io.github.minh124199.viettemplate.benchmarks.cache.prototype;

import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/** Adapter wrapping the existing production {@link TemplateCompileCache}. */
public final class CurrentTemplateCompileCacheAdapter implements CompileCacheInterface {

  private final TemplateCompileCache delegate;

  public CurrentTemplateCompileCacheAdapter(
      int maxEntries, long negativeCacheTtlMillis, int maxNegativeEntries) {
    this.delegate =
        new TemplateCompileCache(maxEntries, negativeCacheTtlMillis, maxNegativeEntries);
  }

  public CurrentTemplateCompileCacheAdapter() {
    this.delegate = new TemplateCompileCache();
  }

  public TemplateCompileCache delegate() {
    return delegate;
  }

  @Override
  public Optional<CompiledTemplateHandle> get(CompileCacheKey key) {
    return delegate.get(key);
  }

  @Override
  public Optional<CompiledTemplateHandle> getActive(TemplateId id) {
    return delegate.getActive(id);
  }

  @Override
  public void put(CompileCacheKey key, CompiledTemplateHandle handle) {
    delegate.put(key, handle);
  }

  @Override
  public boolean isNegativelyCached(TemplateId id) {
    return delegate.isNegativelyCached(id);
  }

  @Override
  public void recordNegative(TemplateId id, String reason) {
    delegate.recordNegative(id, reason);
  }

  @Override
  public void invalidate(TemplateId id) {
    delegate.invalidate(id);
  }

  @Override
  public Set<TemplateId> invalidateWithDependents(TemplateId id, TemplateDependencyGraph graph) {
    return delegate.invalidateWithDependents(id, graph);
  }

  @Override
  public void invalidateAll() {
    delegate.invalidateAll();
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public int negativeCacheSize() {
    return delegate.negativeCacheSize();
  }

  @Override
  public boolean isInternallyConsistent() {
    try {
      Method m = TemplateCompileCache.class.getDeclaredMethod("isInternallyConsistent");
      m.setAccessible(true);
      return (boolean) m.invoke(delegate);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
