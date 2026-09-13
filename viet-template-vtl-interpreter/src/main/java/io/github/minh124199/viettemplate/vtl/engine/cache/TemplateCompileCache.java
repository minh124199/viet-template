package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
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
 * Thread-safe, bounded compile cache with approximate deferred-recency eviction, negative lookup
 * caching, and atomic template handle replacement.
 *
 * <h3>Exact State vs. Approximate Recency</h3>
 *
 * <p>Mappings between {@link CompileCacheKey} and {@link CompiledTemplateHandle}, active template
 * versions ({@link #activeKeys}), reverse invalidation sets ({@link #keysByTemplate}), and negative
 * cache entries ({@link #negativeEntries}) are exact and immediately consistent across all threads.
 * Mutations and invalidations are linearized using striped {@code templateLocks}.
 *
 * <p>Recency tracking is intentionally decoupled and approximate. Read hits and writes record cache
 * keys into per-thread striped circular recency buffers ({@link RecencyBuffer}) without global
 * synchronization, heap allocation, or lock acquisition. These recency records are periodically
 * drained into an internal LRU structure under {@link #maintenanceLock}. This design trades strict
 * global LRU access ordering for lock-free read throughput with zero allocations.
 *
 * <h3>Lock Hierarchy</h3>
 *
 * <p>To prevent deadlocks, lock acquisition must strictly follow this hierarchy:
 *
 * <ol>
 *   <li>{@link #maintenanceLock}: guards LRU maintenance, ring buffer draining, and capacity
 *       eviction.
 *   <li>{@code templateLock}: guards mutations and invalidations for a specific template stripe.
 * </ol>
 *
 * <p>A thread holding a {@code templateLock} must never attempt to acquire {@link
 * #maintenanceLock}. Conversely, {@link #maintenanceLock} may acquire one or more {@code
 * templateLocks} (for example, during capacity eviction or full cache invalidation).
 */
public final class TemplateCompileCache {

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

  // Guards LRU order tracking, recency buffer draining, and capacity eviction.
  private final ReentrantLock maintenanceLock = new ReentrantLock();
  private final LinkedHashMap<CompileCacheKey, Boolean> lruOrder =
      new LinkedHashMap<>(128, 0.75f, true);

  private final RecencyBuffer[] recencyBuffers = new RecencyBuffer[RECENCY_STRIPES];

  private final AtomicLong globalGeneration = new AtomicLong(1);

  /**
   * Per-stripe bounded ring buffer for deferred recency recording.
   *
   * <p>Readers and writers record accessed keys lock-free. Read drain threshold checks determine
   * when maintenance draining should be triggered.
   */
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
    for (int i = 0; i < RECENCY_STRIPES; i++) {
      recencyBuffers[i] = new RecencyBuffer();
    }
  }

  public TemplateCompileCache() {
    this(500, 5000L, 200);
  }

  /**
   * Retrieves a cached {@link CompiledTemplateHandle} by its exact multi-dimensional {@link
   * CompileCacheKey}.
   *
   * <p>On hit, records recency into the current thread's stripe buffer without acquiring locks. If
   * the stripe's read threshold is reached, maintenance draining is opportunistically attempted.
   */
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

  /** Retrieves the currently active handle for a given {@link TemplateId}. */
  public Optional<CompiledTemplateHandle> getActive(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    CompileCacheKey activeKey = activeKeys.get(id);
    if (activeKey != null) {
      return get(activeKey);
    }
    return Optional.empty();
  }

  /**
   * Atomically stores a compiled template handle and sets it as the active version.
   *
   * <p>Enforces maximum capacity eviction under {@link #maintenanceLock} if entries exceed {@link
   * #maxEntries}.
   */
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

  /**
   * Invalidates all cache entries (positive and negative) for the specified template.
   *
   * <p>Linearized under the template stripe lock. Deferred recency buffers are not modified; stale
   * recency events are discarded lazily during buffer draining or eviction.
   */
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
          entries.remove(key);
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

  /**
   * Completely clears all cached compiled templates, negative entries, recency ring buffers, and
   * LRU order tracking.
   *
   * <p>Acquires {@link #maintenanceLock} first, followed by all template locks in canonical order.
   */
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

  /**
   * Synchronously drains all recency buffers into the LRU order and enforces capacity limits under
   * {@link #maintenanceLock}.
   */
  void drainMaintenance() {
    maintenanceLock.lock();
    try {
      drainAllUnderMaintenanceLock();
    } finally {
      maintenanceLock.unlock();
    }
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

  int indexedKeyCount(TemplateId id) {
    Set<CompileCacheKey> keys = keysByTemplate.get(id);
    return keys == null ? 0 : keys.size();
  }

  private Object templateLock(TemplateId id) {
    return templateLocks[(id.hashCode() & 0x7fffffff) % templateLocks.length];
  }
}
