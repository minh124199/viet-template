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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Parameterized prototype exploring recency sampling ratios and drain thresholds for Milestone
 * M19.3a.2 low-concurrency fast-path tuning.
 *
 * <p>Preserves all M19.3a.1 memory model invariants (AtomicReferenceArray slots, setRelease
 * publication, getAndSet acquire-release draining, lastDrainedCursor stripe-skip fast-path, and
 * liveEntries check).
 */
public final class SampledDeferredRecencyCompileCache implements CompileCacheInterface {

  private static final int TEMPLATE_LOCK_COUNT = 64;
  private static final int RECENCY_STRIPES = 16;
  private static final int RECENCY_STRIPE_MASK = RECENCY_STRIPES - 1;
  private static final int RECENCY_BUFFER_CAPACITY = 64;
  private static final int RECENCY_BUFFER_MASK = RECENCY_BUFFER_CAPACITY - 1;

  private final int maxEntries;
  private final long negativeCacheTtlMillis;
  private final int maxNegativeEntries;
  private final int sampleShift;
  private final int drainThreshold;

  private final ConcurrentMap<CompileCacheKey, CompiledTemplateHandle> entries =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, CompileCacheKey> activeKeys = new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, Set<CompileCacheKey>> keysByTemplate =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, NegativeEntry> negativeEntries =
      new ConcurrentHashMap<>();

  private final Object[] templateLocks = new Object[TEMPLATE_LOCK_COUNT];
  final ReentrantLock maintenanceLock = new ReentrantLock();
  private final LinkedHashMap<CompileCacheKey, Boolean> lruOrder =
      new LinkedHashMap<>(128, 0.75f, true);
  private final RecencyStripe[] recencyStripes = new RecencyStripe[RECENCY_STRIPES];

  private static final class RecencyStripe {
    private final AtomicReferenceArray<CompileCacheKey> slots =
        new AtomicReferenceArray<>(RECENCY_BUFFER_CAPACITY);
    private final AtomicLong cursor = new AtomicLong();
    private long lastDrainedCursor = 0;
    private final int sampleShift;
    private final int drainThreshold;

    RecencyStripe(int sampleShift, int drainThreshold) {
      this.sampleShift = sampleShift;
      this.drainThreshold = drainThreshold;
    }

    boolean record(CompileCacheKey key) {
      long pos = cursor.getAndIncrement();
      if ((pos & ((1L << sampleShift) - 1)) == 0) {
        int index = (int) ((pos >> sampleShift) & RECENCY_BUFFER_MASK);
        slots.setRelease(index, key);
      }
      return (pos & (drainThreshold - 1)) == 0;
    }

    void drainInto(
        LinkedHashMap<CompileCacheKey, Boolean> lruOrder,
        ConcurrentMap<CompileCacheKey, ?> liveEntries) {
      long currentCursor = cursor.get();
      if (currentCursor == lastDrainedCursor) {
        return;
      }
      lastDrainedCursor = currentCursor;
      for (int i = 0; i < RECENCY_BUFFER_CAPACITY; i++) {
        CompileCacheKey key = slots.get(i);
        if (key != null) {
          key = slots.getAndSet(i, null);
          if (key != null && liveEntries.containsKey(key)) {
            lruOrder.put(key, Boolean.TRUE);
            lruOrder.get(key);
          }
        }
      }
    }

    void clear() {
      lastDrainedCursor = cursor.get();
      for (int i = 0; i < RECENCY_BUFFER_CAPACITY; i++) {
        slots.set(i, null);
      }
    }
  }

  private static final class NegativeEntry {
    private final TemplateId id;
    private final String reason;
    private final long expiresAtMillis;

    NegativeEntry(TemplateId id, String reason, long expiresAtMillis) {
      this.id = id;
      this.reason = reason;
      this.expiresAtMillis = expiresAtMillis;
    }

    boolean isExpired(long now) {
      return now >= expiresAtMillis;
    }
  }

  public SampledDeferredRecencyCompileCache(
      int maxEntries,
      long negativeCacheTtlMillis,
      int maxNegativeEntries,
      int sampleShift,
      int drainThreshold) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
    }
    this.maxEntries = maxEntries;
    this.negativeCacheTtlMillis = negativeCacheTtlMillis;
    this.maxNegativeEntries = Math.max(1, maxNegativeEntries);
    this.sampleShift = sampleShift;
    this.drainThreshold = drainThreshold;
    for (int i = 0; i < templateLocks.length; i++) {
      templateLocks[i] = new Object();
    }
    for (int i = 0; i < RECENCY_STRIPES; i++) {
      recencyStripes[i] = new RecencyStripe(sampleShift, drainThreshold);
    }
  }

  public SampledDeferredRecencyCompileCache(
      int maxEntries, long negativeCacheTtlMillis, int maxNegativeEntries, int sampleShift) {
    this(maxEntries, negativeCacheTtlMillis, maxNegativeEntries, sampleShift, 64 << sampleShift);
  }

  @Override
  public Optional<CompiledTemplateHandle> get(CompileCacheKey key) {
    Objects.requireNonNull(key, "key must not be null");
    CompiledTemplateHandle handle = entries.get(key);
    if (handle != null) {
      int stripe = (int) (Thread.currentThread().getId() & RECENCY_STRIPE_MASK);
      boolean shouldDrain = recencyStripes[stripe].record(key);
      if (shouldDrain) {
        tryDrainMaintenance();
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

    synchronized (templateLock(key.templateId())) {
      CompiledTemplateHandle previous = entries.put(key, handle);
      if (previous == null) {
        keysByTemplate
            .computeIfAbsent(key.templateId(), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
      }
      activeKeys.put(key.templateId(), key);
      negativeEntries.remove(key.templateId());
    }

    maintenanceLock.lock();
    try {
      drainAllUnderMaintenanceLock();
      lruOrder.put(key, Boolean.TRUE);
      enforceCapacityUnderMaintenanceLock();
    } finally {
      maintenanceLock.unlock();
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
          entries.remove(key);
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
    maintenanceLock.lock();
    try {
      invalidateAllUnderTemplateLocks(0);
      for (RecencyStripe stripe : recencyStripes) {
        stripe.clear();
      }
      lruOrder.clear();
    } finally {
      maintenanceLock.unlock();
    }
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
  }

  private void tryDrainMaintenance() {
    if (maintenanceLock.tryLock()) {
      try {
        drainAllUnderMaintenanceLock();
      } finally {
        maintenanceLock.unlock();
      }
    }
  }

  private void drainAllUnderMaintenanceLock() {
    for (RecencyStripe stripe : recencyStripes) {
      stripe.drainInto(lruOrder, entries);
    }
    enforceCapacityUnderMaintenanceLock();
  }

  private void enforceCapacityUnderMaintenanceLock() {
    while (entries.size() > maxEntries) {
      CompileCacheKey eldest = null;
      Iterator<Map.Entry<CompileCacheKey, Boolean>> it = lruOrder.entrySet().iterator();
      while (it.hasNext()) {
        CompileCacheKey candidate = it.next().getKey();
        it.remove();
        if (entries.containsKey(candidate)) {
          eldest = candidate;
          break;
        }
      }
      if (eldest == null) {
        Iterator<CompileCacheKey> fallbackIt = entries.keySet().iterator();
        if (fallbackIt.hasNext()) {
          eldest = fallbackIt.next();
        }
      }
      if (eldest != null) {
        evictEntry(eldest);
      } else {
        break;
      }
    }
  }

  private void evictEntry(CompileCacheKey key) {
    synchronized (templateLock(key.templateId())) {
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
    lruOrder.remove(key);
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
    maintenanceLock.lock();
    try {
      drainAllUnderMaintenanceLock();
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
      return lruOrder.keySet().containsAll(entries.keySet());
    } finally {
      maintenanceLock.unlock();
    }
  }

  private Object templateLock(TemplateId id) {
    return templateLocks[(id.hashCode() & 0x7fffffff) % templateLocks.length];
  }
}
