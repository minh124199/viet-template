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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Prototype B: Batched deferred recency maintenance compile cache.
 *
 * <p>Completely eliminates monitor synchronization on the read path: read hits record access into
 * bounded, lossy lock-free ring buffers. Maintenance (LRU reordering and bounded eviction) is
 * performed in amortized batches via non-blocking {@code tryLock()}, ensuring reader threads never
 * stall or queue behind a global monitor.
 */
public final class BatchedDeferredLruCompileCache implements CompileCacheInterface {

  private static final int TEMPLATE_LOCK_COUNT = 64;
  private static final int READ_BUFFER_STRIPES = 8;
  private static final int BUFFER_CAPACITY = 64;
  private static final int DRAIN_INTERVAL_MASK = 63; // drain attempt every 64 reads per stripe

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

  // Maintenance lock guarding central LRU map
  private final ReentrantLock maintenanceLock = new ReentrantLock();
  private final LinkedHashMap<CompileCacheKey, Boolean> lruOrder;

  // Striped bounded lossy ring buffers for read-hit access events
  private final ReadBuffer[] readBuffers = new ReadBuffer[READ_BUFFER_STRIPES];
  private final AtomicLong readCounter = new AtomicLong(0);

  public BatchedDeferredLruCompileCache(
      int maxEntries, long negativeCacheTtlMillis, int maxNegativeEntries) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
    }
    this.maxEntries = maxEntries;
    this.negativeCacheTtlMillis = negativeCacheTtlMillis;
    this.maxNegativeEntries = Math.max(1, maxNegativeEntries);
    this.lruOrder = new LinkedHashMap<>(Math.min(128, maxEntries), 0.75f, true);

    for (int i = 0; i < templateLocks.length; i++) {
      templateLocks[i] = new Object();
    }
    for (int i = 0; i < READ_BUFFER_STRIPES; i++) {
      readBuffers[i] = new ReadBuffer(BUFFER_CAPACITY);
    }
  }

  public BatchedDeferredLruCompileCache() {
    this(500, 5000L, 200);
  }

  @Override
  public Optional<CompiledTemplateHandle> get(CompileCacheKey key) {
    Objects.requireNonNull(key, "key must not be null");
    CompiledTemplateHandle handle = entries.get(key);
    if (handle != null) {
      // 100% lock-free read hit: record access to striped ring buffer
      int stripe = ((int) Thread.currentThread().getId()) & (READ_BUFFER_STRIPES - 1);
      readBuffers[stripe].offer(key);

      // Amortized non-blocking maintenance drain check
      long count = readCounter.incrementAndGet();
      if ((count & DRAIN_INTERVAL_MASK) == 0) {
        tryDrainMaintenance(false);
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

    // Acquire maintenance lock outside templateLock to prevent lock inversion deadlock
    maintenanceLock.lock();
    try {
      drainAllBuffers();
      lruOrder.put(key, Boolean.TRUE);
      enforceCapacity();
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
    Set<CompileCacheKey> keys;
    synchronized (templateLock(id)) {
      keys = keysByTemplate.remove(id);
      activeKeys.remove(id);
      negativeEntries.remove(id);
      if (keys != null) {
        for (CompileCacheKey key : keys) {
          entries.remove(key);
        }
      }
    }
    if (keys != null && !keys.isEmpty()) {
      maintenanceLock.lock();
      try {
        for (CompileCacheKey key : keys) {
          lruOrder.remove(key);
        }
      } finally {
        maintenanceLock.unlock();
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
      for (ReadBuffer rb : readBuffers) {
        rb.clear();
      }
      lruOrder.clear();
      invalidateAllUnderTemplateLocks(0);
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
      drainAllBuffers();
      Set<CompileCacheKey> indexed = ConcurrentHashMap.newKeySet();
      keysByTemplate.forEach(
          (id, keys) -> {
            for (CompileCacheKey k : keys) {
              if (k.templateId().equals(id)) {
                indexed.add(k);
              }
            }
          });
      return indexed.equals(entries.keySet()) && lruOrder.keySet().equals(entries.keySet());
    } finally {
      maintenanceLock.unlock();
    }
  }

  private void tryDrainMaintenance(boolean force) {
    if (force || maintenanceLock.tryLock()) {
      try {
        drainAllBuffers();
        enforceCapacity();
      } finally {
        maintenanceLock.unlock();
      }
    }
  }

  private void drainAllBuffers() {
    for (ReadBuffer rb : readBuffers) {
      CompileCacheKey key;
      while ((key = rb.poll()) != null) {
        if (entries.containsKey(key)) {
          lruOrder.get(key); // records access order in LinkedHashMap
        }
      }
    }
  }

  private void enforceCapacity() {
    while (lruOrder.size() > maxEntries) {
      Iterator<Map.Entry<CompileCacheKey, Boolean>> it = lruOrder.entrySet().iterator();
      if (!it.hasNext()) {
        break;
      }
      CompileCacheKey eldest = it.next().getKey();
      it.remove();
      synchronized (templateLock(eldest.templateId())) {
        CompiledTemplateHandle removed = entries.remove(eldest);
        if (removed != null) {
          activeKeys.remove(eldest.templateId(), eldest);
          keysByTemplate.computeIfPresent(
              eldest.templateId(),
              (ignored, keys) -> {
                keys.remove(eldest);
                return keys.isEmpty() ? null : keys;
              });
        }
      }
    }
  }

  private Object templateLock(TemplateId id) {
    return templateLocks[(id.hashCode() & 0x7FFFFFFF) % templateLocks.length];
  }

  /** Bounded lossy circular array for recording access keys without locks. */
  private static final class ReadBuffer {
    private final CompileCacheKey[] buffer;
    private final int mask;
    private final AtomicInteger head = new AtomicInteger(0);
    private final AtomicInteger tail = new AtomicInteger(0);

    ReadBuffer(int capacity) {
      int cap = 1;
      while (cap < capacity) {
        cap <<= 1;
      }
      this.buffer = new CompileCacheKey[cap];
      this.mask = cap - 1;
    }

    void offer(CompileCacheKey key) {
      int t = tail.get();
      int h = head.get();
      if (t - h > mask) {
        // Buffer is saturated: lossy drop to prevent reader stalling
        return;
      }
      buffer[t & mask] = key;
      tail.lazySet(t + 1);
    }

    CompileCacheKey poll() {
      int h = head.get();
      int t = tail.get();
      if (h >= t) {
        return null;
      }
      CompileCacheKey key = buffer[h & mask];
      buffer[h & mask] = null;
      head.lazySet(h + 1);
      return key;
    }

    void clear() {
      head.set(0);
      tail.set(0);
      for (int i = 0; i < buffer.length; i++) {
        buffer[i] = null;
      }
    }
  }

  private record NegativeEntry(TemplateId id, String reason, long expiresAtMillis) {
    boolean isExpired(long now) {
      return now >= expiresAtMillis;
    }
  }
}
