package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, bounded multi-dimensional compile cache with LRU eviction, negative lookup caching,
 * and atomic template handle replacement.
 */
public final class TemplateCompileCache {

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

  private final ConcurrentMap<TemplateId, NegativeCacheEntry> negativeEntries =
      new ConcurrentHashMap<>();

  // Serializes mutations for one template without globally serializing unrelated templates.
  private final Object[] templateLocks = new Object[TEMPLATE_LOCK_COUNT];

  // Guard for LRU order tracking
  private final Object lruLock = new Object();
  private final LinkedHashMap<CompileCacheKey, Boolean> lruOrder =
      new LinkedHashMap<>(128, 0.75f, true);

  private final AtomicLong globalGeneration = new AtomicLong(1);

  public TemplateCompileCache(int maxEntries, long negativeCacheTtlMillis, int maxNegativeEntries) {
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

  public TemplateCompileCache() {
    this(500, 5000L, 200);
  }

  /**
   * Retrieves a cached {@link CompiledTemplateHandle} by its exact multi-dimensional {@link
   * CompileCacheKey}.
   */
  public Optional<CompiledTemplateHandle> get(CompileCacheKey key) {
    Objects.requireNonNull(key, "key must not be null");
    CompiledTemplateHandle handle = entries.get(key);
    if (handle != null) {
      synchronized (lruLock) {
        lruOrder.get(key); // records access order
      }
      return Optional.of(handle);
    }
    return Optional.empty();
  }

  /** Retrieves the currently active handle for a given {@link TemplateId}. */
  public Optional<CompiledTemplateHandle> getActive(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    CompileCacheKey activeKey = activeKeys.get(id);
    if (activeKey != null) {
      return get(activeKey);
    }
    return Optional.empty();
  }

  /** Atomically stores a compiled template handle and sets it as the active version. */
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

  /**
   * Checks whether the template is currently negatively cached (i.e. known to not exist or failed
   * to load).
   */
  public boolean isNegativelyCached(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    if (negativeCacheTtlMillis <= 0) {
      return false;
    }
    NegativeCacheEntry entry = negativeEntries.get(id);
    if (entry == null) {
      return false;
    }
    if (entry.isExpired(System.currentTimeMillis())) {
      negativeEntries.remove(id, entry);
      return false;
    }
    return true;
  }

  /** Records a negative cache entry for a missing or unparseable template. */
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
    negativeEntries.put(id, new NegativeCacheEntry(id, reason, expiresAt));
  }

  /** Invalidates all cache entries (positive and negative) for the specified template. */
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

  /**
   * Invalidates the specified template and all its transitive dependents found in the dependency
   * graph.
   *
   * @param id root template identifier being invalidated
   * @param graph dependency graph tracking relationships
   * @return set of all invalidated template identifiers (including the root template)
   */
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

  /** Completely clears all cached compiled templates and negative entries. */
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

  /** Generates and returns the next atomic generation counter for template updates. */
  public long nextGeneration() {
    return globalGeneration.getAndIncrement();
  }

  /** Returns the current generation counter of the active template, or 0 if not loaded. */
  public long currentGeneration(TemplateId id) {
    return getActive(id).map(CompiledTemplateHandle::generation).orElse(0L);
  }

  public int size() {
    return entries.size();
  }

  public int negativeCacheSize() {
    return negativeEntries.size();
  }

  /** Package-private quiescent-state invariant check for focused cache tests. */
  boolean isInternallyConsistent() {
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

  int indexedKeyCount(TemplateId id) {
    Set<CompileCacheKey> keys = keysByTemplate.get(id);
    return keys == null ? 0 : keys.size();
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
}
