package io.github.minh124199.viettemplate.runtime.linker;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Thread-safe bounded cache keyed by {@link Class} using weak references.
 *
 * <p>Prevents ClassLoader leaks during dynamic template execution or application redeployment by
 * ensuring receiver classes are collectable. Enforces a strict upper bound on entries.
 *
 * @param <V> value type
 */
public final class BoundedWeakClassCache<V> {

  private final int maxCapacity;
  private final ConcurrentHashMap<Object, V> map = new ConcurrentHashMap<>();
  private final ReferenceQueue<Class<?>> queue = new ReferenceQueue<>();

  public BoundedWeakClassCache(int maxCapacity) {
    if (maxCapacity <= 0) {
      throw new IllegalArgumentException("maxCapacity must be positive: " + maxCapacity);
    }
    this.maxCapacity = maxCapacity;
  }

  public V get(Class<?> clazz) {
    if (clazz == null) {
      return null;
    }
    purgeStaleEntries();
    return map.get(new LookupKey(clazz));
  }

  public V computeIfAbsent(Class<?> clazz, Function<Class<?>, V> mappingFunction) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    Objects.requireNonNull(mappingFunction, "mappingFunction must not be null");

    purgeStaleEntries();
    V existing = map.get(new LookupKey(clazz));
    if (existing != null) {
      return existing;
    }

    V created = mappingFunction.apply(clazz);
    if (created == null) {
      return null;
    }

    if (map.size() >= maxCapacity) {
      evictOne();
    }

    WeakKey weakKey = new WeakKey(clazz, queue);
    map.put(weakKey, created);
    return created;
  }

  public void put(Class<?> clazz, V value) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    Objects.requireNonNull(value, "value must not be null");

    purgeStaleEntries();
    if (map.size() >= maxCapacity) {
      evictOne();
    }
    map.put(new WeakKey(clazz, queue), value);
  }

  public int size() {
    purgeStaleEntries();
    return map.size();
  }

  public int maxCapacity() {
    return maxCapacity;
  }

  public void clear() {
    map.clear();
    while (queue.poll() != null) {
      // drain queue
    }
  }

  private void purgeStaleEntries() {
    Reference<? extends Class<?>> ref;
    while ((ref = queue.poll()) != null) {
      map.remove(ref);
    }
  }

  private synchronized void evictOne() {
    purgeStaleEntries();
    if (map.size() >= maxCapacity) {
      Iterator<Object> it = map.keySet().iterator();
      if (it.hasNext()) {
        it.next();
        it.remove();
      }
    }
  }

  private static final class WeakKey extends WeakReference<Class<?>> {
    private final int hash;

    WeakKey(Class<?> referent, ReferenceQueue<Class<?>> q) {
      super(referent, q);
      this.hash = System.identityHashCode(referent);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj instanceof WeakKey other) {
        Class<?> r1 = get();
        Class<?> r2 = other.get();
        return r1 != null && r1 == r2;
      }
      if (obj instanceof LookupKey lk) {
        return get() != null && get() == lk.clazz;
      }
      return false;
    }
  }

  private static final class LookupKey {
    private final Class<?> clazz;
    private final int hash;

    LookupKey(Class<?> clazz) {
      this.clazz = clazz;
      this.hash = System.identityHashCode(clazz);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof WeakKey wk) {
        return clazz == wk.get();
      }
      if (obj instanceof LookupKey lk) {
        return clazz == lk.clazz;
      }
      return false;
    }
  }
}
