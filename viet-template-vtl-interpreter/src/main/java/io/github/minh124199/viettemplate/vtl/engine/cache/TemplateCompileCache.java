package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, bounded multi-dimensional compile cache with LRU eviction, negative lookup caching,
 * and atomic template handle replacement.
 */
public final class TemplateCompileCache {

  private final int maxEntries;
  private final long negativeCacheTtlMillis;
  private final int maxNegativeEntries;

  private final ConcurrentMap<CompileCacheKey, CompiledTemplateHandle> entries =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, CompileCacheKey> activeKeys = new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, NegativeCacheEntry> negativeEntries =
      new ConcurrentHashMap<>();

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

    entries.put(key, handle);
    activeKeys.put(key.templateId(), key);
    negativeEntries.remove(key.templateId());

    CompileCacheKey toEvict = null;
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

    if (toEvict != null && !toEvict.equals(key)) {
      evict(toEvict);
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
    activeKeys.remove(id);
    negativeEntries.remove(id);

    entries
        .keySet()
        .removeIf(
            k -> {
              if (k.templateId().equals(id)) {
                synchronized (lruLock) {
                  lruOrder.remove(k);
                }
                return true;
              }
              return false;
            });
  }

  /** Completely clears all cached compiled templates and negative entries. */
  public void invalidateAll() {
    entries.clear();
    activeKeys.clear();
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

  private void evict(CompileCacheKey key) {
    CompiledTemplateHandle removed = entries.remove(key);
    if (removed != null) {
      // If this was the active key for this template id, remove it
      activeKeys.remove(key.templateId(), key);
    }
  }
}
