package io.github.minh124199.viettemplate.benchmarks.cache.prototype;

import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Pre-M19.3a baseline compile cache implementation for side-by-side benchmark comparison.
 *
 * <p>Preserves the previous architecture: concurrent map for storage, per-template striped locks
 * for invalidations, and a single global monitor ({@code lruLock}) synchronizing read-hit recency
 * bookkeeping on {@link #get(CompileCacheKey)}.
 */
public final class LegacyLockedCompileCache implements CompileCacheInterface {

  private static final int TEMPLATE_LOCK_COUNT = 64;

  private final int maxEntries;
  private final long negativeCacheTtlMillis;
  private final int maxNegativeEntries;

  private final ConcurrentMap<CompileCacheKey, CompiledTemplateHandle> entries =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, CompileCacheKey> activeKeys = new ConcurrentHashMap<>();

  /**
   * Reverse index for targeted invalidation. Membership mirrors {@link #entries}: every positive
   * cache key is present in exactly one set, and empty sets are removed.
   */
  private final ConcurrentMap<TemplateId, Set<CompileCacheKey>> keysByTemplate =
      new ConcurrentHashMap<>();

  private final ConcurrentMap<TemplateId, NegativeEntry> negativeEntries =
      new ConcurrentHashMap<>();

  // Serializes mutations for one template without globally serializing unrelated templates.
  private final Object[] templateLocks = new Object[TEMPLATE_LOCK_COUNT];

  // Guard for LRU order tracking
  private final Object lruLock = new Object();
  private final LinkedHashMap<CompileCacheKey, Boolean> lruOrder =
      new LinkedHashMap<>(128, 0.75f, true);

  public LegacyLockedCompileCache(
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

  public LegacyLockedCompileCache() {
    this(500, 5000L, 200);
  }

  @Override
  public Optional<CompiledTemplateHandle> get(CompileCacheKey key) {
    Objects.requireNonNull(key, "key must not be null");
    CompiledTemplateHandle handle = entries.get(key);
    if (handle != null) {
      synchronized (lruLock) {
        lruOrder.get(key); // records access order under global monitor
      }
      return Optional.of(handle);
    }
    return Optional.empty();
  }

  @Override
  public Optional<CompiledTemplateHandle> getActive(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    CompileCacheKey activeKey = activeKeys.get(id);
    if (activeKey != null) {
      return get(activeKey);
    }
    return Optional.empty();
  }

  @Override
  public void put(CompileCacheKey key, CompiledTemplateHandle handle) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(handle, "handle must not be null");

    CompileCacheKey toEvict = null;
    synchronized (templateLock(key.templateId())) {
      CompiledTemplateHandle previous = entries.put(key, handle);
      if (previous == null) {
        keysByTemplate
            .computeIfAbsent(key.templateId(), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
      }
      activeKeys.put(key.templateId(), key);
      negativeEntries.remove(key.templateId());

      synchronized (lruLock) {
        lruOrder.put(key, Boolean.TRUE);
        if (lruOrder.size() > maxEntries) {
          Iterator<Map.Entry<CompileCacheKey, Boolean>> it = lruOrder.entrySet().iterator();
          if (it.hasNext()) {
            toEvict = it.next().getKey();
            it.remove();
          }
        }
      }
    }

    if (toEvict != null && !toEvict.equals(key)) {
      evictIfStillEldest(toEvict);
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
      // Evict oldest negative entry
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
    // The template stripe is the linearization boundary: a concurrent put for this template is
    // either wholly before invalidation (and removed) or wholly after it (and retained).
    synchronized (templateLock(id)) {
      Set<CompileCacheKey> keys = keysByTemplate.remove(id);
      activeKeys.remove(id);
      negativeEntries.remove(id);
      if (keys != null) {
        for (CompileCacheKey key : keys) {
          removeEntryUnderTemplateLock(key, true);
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
    invalidateAllUnderTemplateLocks(0);
  }

  private void invalidateAllUnderTemplateLocks(int lockIndex) {
    if (lockIndex < templateLocks.length) {
      synchronized (templateLocks[lockIndex]) {
        invalidateAllUnderTemplateLocks(lockIndex + 1);
      }
      return;
    }
    entries.clear();
    activeKeys.clear();
    keysByTemplate.clear();
    negativeEntries.clear();
    synchronized (lruLock) {
      lruOrder.clear();
    }
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
    Set<CompileCacheKey> indexedKeys = ConcurrentHashMap.newKeySet();
    boolean[] invalidMembership = {false};
    keysByTemplate.forEach(
        (id, keys) -> {
          for (CompileCacheKey key : keys) {
            if (!key.templateId().equals(id)) {
              invalidMembership[0] = true;
            }
            indexedKeys.add(key);
          }
        });
    if (invalidMembership[0]
        || !indexedKeys.equals(entries.keySet())
        || keysByTemplate.values().stream().anyMatch(Set::isEmpty)
        || activeKeys.entrySet().stream()
            .anyMatch(
                entry ->
                    !entry.getKey().equals(entry.getValue().templateId())
                        || !entries.containsKey(entry.getValue()))) {
      return false;
    }
    synchronized (lruLock) {
      return lruOrder.keySet().equals(entries.keySet());
    }
  }

  private Object templateLock(TemplateId id) {
    return templateLocks[(id.hashCode() & 0x7fffffff) % templateLocks.length];
  }

  private void evictIfStillEldest(CompileCacheKey key) {
    synchronized (templateLock(key.templateId())) {
      // The key was removed from LRU order before this template lock was acquired. A concurrent
      // re-put adds it back; in that case it is a new live insertion and must not be evicted.
      synchronized (lruLock) {
        if (lruOrder.containsKey(key)) {
          return;
        }
      }
      removeEntryUnderTemplateLock(key, false);
    }
  }

  private void removeEntryUnderTemplateLock(CompileCacheKey key, boolean removeFromLru) {
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
    if (removeFromLru) {
      synchronized (lruLock) {
        lruOrder.remove(key);
      }
    }
  }

  private record NegativeEntry(TemplateId id, String reason, long expiresAtMillis) {
    boolean isExpired(long now) {
      return now >= expiresAtMillis;
    }
  }
}
