package io.github.minh124199.viettemplate.benchmarks.cache.prototype;

import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Prototype A: Striped access-order compile cache.
 *
 * <p>Partitions LRU recency tracking across N independent stripes (default 16), decoupling
 * concurrent read-hit bookkeeping so threads accessing different keys do not collide on a single
 * global monitor.
 */
public final class StripedLruCompileCache implements CompileCacheInterface {

  public static final int DEFAULT_STRIPE_COUNT = 16;
  private static final int TEMPLATE_LOCK_COUNT = 64;

  private final int maxEntries;
  private final int stripeCount;
  private final int stripeCapacity;
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

  // Per-stripe LRU order and lock
  private final Object[] stripeLocks;
  private final List<LinkedHashMap<CompileCacheKey, Boolean>> stripeLru;

  public StripedLruCompileCache(
      int maxEntries, long negativeCacheTtlMillis, int maxNegativeEntries, int stripeCount) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
    }
    if (stripeCount <= 0 || (stripeCount & (stripeCount - 1)) != 0) {
      throw new IllegalArgumentException("stripeCount must be a power of 2: " + stripeCount);
    }
    this.maxEntries = maxEntries;
    this.stripeCount = stripeCount;
    this.stripeCapacity = Math.max(1, (maxEntries + stripeCount - 1) / stripeCount);
    this.negativeCacheTtlMillis = negativeCacheTtlMillis;
    this.maxNegativeEntries = Math.max(1, maxNegativeEntries);

    for (int i = 0; i < templateLocks.length; i++) {
      templateLocks[i] = new Object();
    }

    this.stripeLocks = new Object[stripeCount];
    List<LinkedHashMap<CompileCacheKey, Boolean>> lrus = new ArrayList<>(stripeCount);
    for (int i = 0; i < stripeCount; i++) {
      stripeLocks[i] = new Object();
      lrus.add(new LinkedHashMap<>(Math.min(64, stripeCapacity), 0.75f, true));
    }
    this.stripeLru = Collections.unmodifiableList(lrus);
  }

  public StripedLruCompileCache(
      int maxEntries, long negativeCacheTtlMillis, int maxNegativeEntries) {
    this(maxEntries, negativeCacheTtlMillis, maxNegativeEntries, DEFAULT_STRIPE_COUNT);
  }

  public StripedLruCompileCache() {
    this(500, 5000L, 200, DEFAULT_STRIPE_COUNT);
  }

  @Override
  public Optional<CompiledTemplateHandle> get(CompileCacheKey key) {
    Objects.requireNonNull(key, "key must not be null");
    CompiledTemplateHandle handle = entries.get(key);
    if (handle != null) {
      // Record access on stripe lock rather than global lock
      int stripe = stripeIndex(key);
      synchronized (stripeLocks[stripe]) {
        stripeLru.get(stripe).get(key);
      }
      return Optional.of(handle);
    }
    return Optional.empty();
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

    CompileCacheKey toEvict = null;
    int stripe = stripeIndex(key);

    synchronized (templateLock(key.templateId())) {
      CompiledTemplateHandle prev = entries.put(key, handle);
      if (prev == null) {
        keysByTemplate
            .computeIfAbsent(key.templateId(), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
      }
      activeKeys.put(key.templateId(), key);
      negativeEntries.remove(key.templateId());

      synchronized (stripeLocks[stripe]) {
        LinkedHashMap<CompileCacheKey, Boolean> lru = stripeLru.get(stripe);
        lru.put(key, Boolean.TRUE);
        if (lru.size() > stripeCapacity) {
          Iterator<Map.Entry<CompileCacheKey, Boolean>> it = lru.entrySet().iterator();
          if (it.hasNext()) {
            toEvict = it.next().getKey();
            it.remove();
          }
        }
      }
    }

    if (toEvict != null && !toEvict.equals(key)) {
      evictIfStillEldest(toEvict, stripe);
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
    for (int i = 0; i < stripeCount; i++) {
      synchronized (stripeLocks[i]) {
        stripeLru.get(i).clear();
      }
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
    Set<CompileCacheKey> indexed = ConcurrentHashMap.newKeySet();
    keysByTemplate.forEach(
        (id, keys) -> {
          for (CompileCacheKey k : keys) {
            if (k.templateId().equals(id)) {
              indexed.add(k);
            }
          }
        });
    if (!indexed.equals(entries.keySet())) {
      return false;
    }
    Set<CompileCacheKey> lruAll = ConcurrentHashMap.newKeySet();
    for (int i = 0; i < stripeCount; i++) {
      synchronized (stripeLocks[i]) {
        lruAll.addAll(stripeLru.get(i).keySet());
      }
    }
    return lruAll.equals(entries.keySet());
  }

  private int stripeIndex(CompileCacheKey key) {
    return (key.hashCode() & 0x7FFFFFFF) & (stripeCount - 1);
  }

  private Object templateLock(TemplateId id) {
    return templateLocks[(id.hashCode() & 0x7FFFFFFF) % templateLocks.length];
  }

  private void evictIfStillEldest(CompileCacheKey key, int stripe) {
    synchronized (templateLock(key.templateId())) {
      synchronized (stripeLocks[stripe]) {
        if (stripeLru.get(stripe).containsKey(key)) {
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
      int stripe = stripeIndex(key);
      synchronized (stripeLocks[stripe]) {
        stripeLru.get(stripe).remove(key);
      }
    }
  }

  private record NegativeEntry(TemplateId id, String reason, long expiresAtMillis) {
    boolean isExpired(long now) {
      return now >= expiresAtMillis;
    }
  }
}
