package io.github.minh124199.viettemplate.benchmarks.cache.prototype;

import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import java.util.Arrays;
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
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pre-hardening M19.3a compile cache fixture for benchmark comparison.
 *
 * <p>Preserves the pre-M19.3a.1 implementation: ring buffers with sequence reservation ({@code
 * tail.getAndIncrement()}) and ordinary array element writes, prior to the atomic-slot memory-model
 * hardening.
 */
public final class PreHardeningDeferredRecencyCache implements CompileCacheInterface {

  private static final int TEMPLATE_LOCK_COUNT = 64;
  private static final int RECENCY_STRIPES = 16;
  private static final int RECENCY_STRIPE_MASK = RECENCY_STRIPES - 1;
  private static final int RECENCY_BUFFER_CAPACITY = 64;
  private static final int RECENCY_BUFFER_MASK = RECENCY_BUFFER_CAPACITY - 1;
  private static final int READ_DRAIN_THRESHOLD = 64;

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
  private final ReentrantLock maintenanceLock = new ReentrantLock();
  private final LinkedHashMap<CompileCacheKey, Boolean> lruOrder =
      new LinkedHashMap<>(128, 0.75f, true);
  private final RecencyBuffer[] recencyBuffers = new RecencyBuffer[RECENCY_STRIPES];

  private static final class RecencyBuffer {
    private final CompileCacheKey[] ring = new CompileCacheKey[RECENCY_BUFFER_CAPACITY];
    private final AtomicLong tail = new AtomicLong();
    private long head = 0;

    boolean record(CompileCacheKey key) {
      long pos = tail.getAndIncrement();
      ring[(int) (pos & RECENCY_BUFFER_MASK)] = key;
      return (pos & (READ_DRAIN_THRESHOLD - 1)) == 0;
    }

    void drainInto(
        LinkedHashMap<CompileCacheKey, Boolean> lruOrder,
        ConcurrentMap<CompileCacheKey, ?> liveEntries) {
      long currentTail = tail.get();
      if (currentTail - head > RECENCY_BUFFER_CAPACITY) {
        head = currentTail - RECENCY_BUFFER_CAPACITY;
      }
      while (head < currentTail) {
        int index = (int) (head & RECENCY_BUFFER_MASK);
        CompileCacheKey key = ring[index];
        ring[index] = null;
        head++;
        if (key != null && liveEntries.containsKey(key)) {
          lruOrder.put(key, Boolean.TRUE);
          lruOrder.get(key);
        }
      }
    }

    void clear() {
      head = tail.get();
      Arrays.fill(ring, null);
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

  public PreHardeningDeferredRecencyCache(
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
    for (int i = 0; i < RECENCY_STRIPES; i++) {
      recencyBuffers[i] = new RecencyBuffer();
    }
  }

  public PreHardeningDeferredRecencyCache() {
    this(500, 5000L, 200);
  }

  @Override
  public Optional<CompiledTemplateHandle> get(CompileCacheKey key) {
    Objects.requireNonNull(key, "key must not be null");
    CompiledTemplateHandle handle = entries.get(key);
    if (handle != null) {
      int stripe = (int) (Thread.currentThread().getId() & RECENCY_STRIPE_MASK);
      boolean shouldDrain = recencyBuffers[stripe].record(key);
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
      for (RecencyBuffer buffer : recencyBuffers) {
        buffer.clear();
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
    for (RecencyBuffer buffer : recencyBuffers) {
      buffer.drainInto(lruOrder, entries);
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
