package io.github.minh124199.viettemplate.benchmarks.cache.prototype;

import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Diagnostic baseline cache: isolates the raw cost of {@code ConcurrentHashMap.get(key)} without
 * any access-order recording or monitor synchronization on read hits.
 *
 * <p>Establishes the empirical theoretical maximum throughput ceiling for compile-cache lookups.
 */
public final class NoLruBookkeepingCache implements CompileCacheInterface {

  private static final int TEMPLATE_LOCK_COUNT = 64;

  private final int maxEntries;
  private final long negativeCacheTtlMillis;
  private final int maxNegativeEntries;

  private final ConcurrentMap<CompileCacheKey, CompiledTemplateHandle> entries =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, CompileCacheKey> activeKeys = new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, Set<CompileCacheKey>> keysByTemplate =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, NegativeEntry> negativeEntries =
      new ConcurrentHashMap<>();

  private final Object[] templateLocks = new Object[TEMPLATE_LOCK_COUNT];

  public NoLruBookkeepingCache(
      int maxEntries, long negativeCacheTtlMillis, int maxNegativeEntries) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
    }
    this.maxEntries = maxEntries;
    this.negativeCacheTtlMillis = negativeCacheTtlMillis;
    this.maxNegativeEntries = Math.max(1, maxNegativeEntries);
    for (int i = 0; i < templateLocks.length; i++) {
      templateLocks[i] = new Object();
    }
  }

  public NoLruBookkeepingCache() {
    this(500, 5000L, 200);
  }

  @Override
  public Optional<CompiledTemplateHandle> get(CompileCacheKey key) {
    Objects.requireNonNull(key, "key must not be null");
    // Direct concurrent map get with ZERO synchronization or access recording
    return Optional.ofNullable(entries.get(key));
  }

  @Override
  public Optional<CompiledTemplateHandle> getActive(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    CompileCacheKey activeKey = activeKeys.get(id);
    return activeKey != null ? get(activeKey) : Optional.empty();
  }

  @Override
  public void put(CompileCacheKey key, CompiledTemplateHandle handle) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(handle, "handle must not be null");

    synchronized (templateLock(key.templateId())) {
      CompiledTemplateHandle prev = entries.put(key, handle);
      if (prev == null) {
        keysByTemplate
            .computeIfAbsent(key.templateId(), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
      }
      activeKeys.put(key.templateId(), key);
      negativeEntries.remove(key.templateId());
    }

    if (entries.size() > maxEntries) {
      Iterator<CompileCacheKey> it = entries.keySet().iterator();
      if (it.hasNext()) {
        CompileCacheKey toEvict = it.next();
        if (toEvict != null && !toEvict.equals(key)) {
          synchronized (templateLock(toEvict.templateId())) {
            removeEntry(toEvict);
          }
        }
      }
    }
  }

  @Override
  public boolean isNegativelyCached(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    if (negativeCacheTtlMillis <= 0) {
      return false;
    }
    NegativeEntry entry = negativeEntries.get(id);
    if (entry == null) {
      return false;
    }
    if (entry.isExpired(System.currentTimeMillis())) {
      negativeEntries.remove(id, entry);
      return false;
    }
    return true;
  }

  @Override
  public void recordNegative(TemplateId id, String reason) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    if (negativeCacheTtlMillis <= 0) {
      return;
    }
    if (negativeEntries.size() >= maxNegativeEntries) {
      Iterator<TemplateId> it = negativeEntries.keySet().iterator();
      if (it.hasNext()) {
        negativeEntries.remove(it.next());
      }
    }
    long expiresAt = System.currentTimeMillis() + negativeCacheTtlMillis;
    negativeEntries.put(id, new NegativeEntry(id, reason, expiresAt));
  }

  @Override
  public void invalidate(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    synchronized (templateLock(id)) {
      Set<CompileCacheKey> keys = keysByTemplate.remove(id);
      activeKeys.remove(id);
      negativeEntries.remove(id);
      if (keys != null) {
        for (CompileCacheKey key : keys) {
          removeEntry(key);
        }
      }
    }
  }

  @Override
  public Set<TemplateId> invalidateWithDependents(TemplateId id, TemplateDependencyGraph graph) {
    Objects.requireNonNull(id, "id must not be null");
    Set<TemplateId> toInvalidate = new LinkedHashSet<>();
    toInvalidate.add(id);
    if (graph != null) {
      toInvalidate.addAll(graph.transitiveDependentsOf(id));
    }
    for (TemplateId target : toInvalidate) {
      invalidate(target);
    }
    return Collections.unmodifiableSet(toInvalidate);
  }

  @Override
  public void invalidateAll() {
    invalidateAllUnderLocks(0);
  }

  private void invalidateAllUnderLocks(int lockIndex) {
    if (lockIndex < templateLocks.length) {
      synchronized (templateLocks[lockIndex]) {
        invalidateAllUnderLocks(lockIndex + 1);
      }
      return;
    }
    entries.clear();
    activeKeys.clear();
    keysByTemplate.clear();
    negativeEntries.clear();
  }

  @Override
  public int size() {
    return entries.size();
  }

  @Override
  public int negativeCacheSize() {
    return negativeEntries.size();
  }

  @Override
  public boolean isInternallyConsistent() {
    Set<CompileCacheKey> indexed = ConcurrentHashMap.newKeySet();
    keysByTemplate.forEach(
        (id, keys) -> {
          for (CompileCacheKey k : keys) {
            if (k.templateId().equals(id)) {
              indexed.add(k);
            }
          }
        });
    return indexed.equals(entries.keySet());
  }

  private Object templateLock(TemplateId id) {
    return templateLocks[(id.hashCode() & 0x7fffffff) % templateLocks.length];
  }

  private void removeEntry(CompileCacheKey key) {
    CompiledTemplateHandle removed = entries.remove(key);
    if (removed != null) {
      activeKeys.remove(key.templateId(), key);
      keysByTemplate.computeIfPresent(
          key.templateId(),
          (ignored, keys) -> {
            keys.remove(key);
            return keys.isEmpty() ? null : keys;
          });
    }
  }

  private record NegativeEntry(TemplateId id, String reason, long expiresAtMillis) {
    boolean isExpired(long now) {
      return now >= expiresAtMillis;
    }
  }
}
