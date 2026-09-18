package io.github.minh124199.viettemplate.runtime.linker;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dynamic call site with adaptive inline caching: monomorphic fast-path, small polymorphic inline
 * cache (PIC, depth 4), and bounded megamorphic fallback.
 */
public final class DynamicCallSite {

  public static final int MAX_PIC_DEPTH = 4;
  private static final int DEFAULT_MEGAMORPHIC_BOUND = 1024;

  public enum State {
    UNLINKED,
    MONOMORPHIC,
    POLYMORPHIC,
    MEGAMORPHIC
  }

  private final int siteId;
  private final MemberKey memberKey;
  private final LinkerAccessPolicy policy;
  private final DynamicLinker linker;
  private final LinkerStatistics stats;

  private volatile State state = State.UNLINKED;
  private volatile WeakReference<AccessLink> monomorphicLinkRef;
  private volatile WeakReference<AccessLink>[] polymorphicLinks = emptyLinkArray();
  private volatile BoundedWeakClassCache<WeakReference<AccessLink>> megamorphicCache;

  @SuppressWarnings("unchecked")
  private static WeakReference<AccessLink>[] emptyLinkArray() {
    return (WeakReference<AccessLink>[]) new WeakReference<?>[0];
  }

  public DynamicCallSite(
      int siteId,
      MemberKey memberKey,
      LinkerAccessPolicy policy,
      DynamicLinker linker,
      LinkerStatistics stats) {
    this.siteId = siteId;
    this.memberKey = Objects.requireNonNull(memberKey, "memberKey must not be null");
    this.policy = Objects.requireNonNull(policy, "policy must not be null");
    this.linker = Objects.requireNonNull(linker, "linker must not be null");
    this.stats = stats != null ? stats : new LinkerStatistics();
  }

  public int siteId() {
    return siteId;
  }

  public MemberKey memberKey() {
    return memberKey;
  }

  public LinkerAccessPolicy policy() {
    return policy;
  }

  public State state() {
    return state;
  }

  public LinkerStatistics statistics() {
    return stats;
  }

  /** Invokes a zero-argument operation (e.g. property read) through the inline cache. */
  public Object invoke(Object target) throws Throwable {
    if (target == null) {
      return null;
    }
    AccessLink link = resolveLink(target.getClass());
    if (link.isMissing()) {
      return null;
    }
    return link.invoke(target);
  }

  /**
   * Invokes a single-argument operation (e.g. property write or index read) through the inline
   * cache.
   */
  public Object invoke(Object target, Object arg) throws Throwable {
    if (target == null) {
      return null;
    }
    AccessLink link = resolveLink(target.getClass());
    if (link.isMissing()) {
      return null;
    }
    return link.invoke(target, arg);
  }

  /** Invokes a two-argument operation (e.g. index write) through the inline cache. */
  public Object invoke(Object target, Object arg1, Object arg2) throws Throwable {
    if (target == null) {
      return null;
    }
    AccessLink link = resolveLink(target.getClass());
    if (link.isMissing()) {
      return null;
    }
    return link.invokeIndexSet(target, arg1, arg2);
  }

  /** Invokes a multi-argument operation (e.g. method call) through the inline cache. */
  public Object invokeWithArgs(Object target, Object[] args) throws Throwable {
    if (target == null) {
      return null;
    }
    AccessLink link = resolveLink(target.getClass());
    if (link.isMissing()) {
      return null;
    }
    return link.invokeWithArgs(target, args);
  }

  /** Resolves the access link for a target class using the inline cache state machine. */
  public AccessLink resolveLink(Class<?> targetClass) {
    // 1. Fast Path: Monomorphic
    if (state == State.MONOMORPHIC) {
      WeakReference<AccessLink> ref = monomorphicLinkRef;
      AccessLink mono = ref != null ? ref.get() : null;
      if (mono != null && mono.matches(targetClass)) {
        stats.recordPicHit();
        return mono;
      }
    }

    // 2. Fast Path: Polymorphic
    if (state == State.POLYMORPHIC) {
      WeakReference<AccessLink>[] links = polymorphicLinks;
      for (int i = 0; i < links.length; i++) {
        WeakReference<AccessLink> ref = links[i];
        AccessLink l = ref != null ? ref.get() : null;
        if (l != null && l.matches(targetClass)) {
          stats.recordPicHit();
          return l;
        }
      }
    }

    // 3. Fast Path: Megamorphic
    if (state == State.MEGAMORPHIC) {
      BoundedWeakClassCache<WeakReference<AccessLink>> cache = megamorphicCache;
      if (cache != null) {
        WeakReference<AccessLink> ref = cache.get(targetClass);
        AccessLink cached = ref != null ? ref.get() : null;
        if (cached != null) {
          stats.recordMegamorphicHit();
          return cached;
        }
      }
      stats.recordMegamorphicMiss();
      AccessLink link = linkAndRecord(targetClass);
      if (cache != null) {
        cache.put(targetClass, new WeakReference<>(link));
      }
      return link;
    }

    // 4. Miss Path: Synchronized state transition
    return handleCacheMiss(targetClass);
  }

  private synchronized AccessLink handleCacheMiss(Class<?> targetClass) {
    // Re-check state under lock to prevent race conditions
    if (state == State.MONOMORPHIC) {
      WeakReference<AccessLink> ref = monomorphicLinkRef;
      AccessLink mono = ref != null ? ref.get() : null;
      if (mono != null && mono.matches(targetClass)) {
        stats.recordPicHit();
        return mono;
      }
    }
    if (state == State.POLYMORPHIC) {
      for (WeakReference<AccessLink> ref : polymorphicLinks) {
        AccessLink l = ref != null ? ref.get() : null;
        if (l != null && l.matches(targetClass)) {
          stats.recordPicHit();
          return l;
        }
      }
    }
    if (state == State.MEGAMORPHIC) {
      BoundedWeakClassCache<WeakReference<AccessLink>> cache = megamorphicCache;
      if (cache != null) {
        WeakReference<AccessLink> ref = cache.get(targetClass);
        AccessLink cached = ref != null ? ref.get() : null;
        if (cached != null) {
          stats.recordMegamorphicHit();
          return cached;
        }
      }
      AccessLink link = linkAndRecord(targetClass);
      if (cache != null) {
        cache.put(targetClass, new WeakReference<>(link));
      }
      return link;
    }

    stats.recordPicMiss();
    AccessLink newLink = linkAndRecord(targetClass);

    if (state == State.UNLINKED) {
      monomorphicLinkRef = new WeakReference<>(newLink);
      state = State.MONOMORPHIC;
      return newLink;
    }

    if (state == State.MONOMORPHIC) {
      WeakReference<AccessLink> existingRef = monomorphicLinkRef;
      AccessLink existing = existingRef != null ? existingRef.get() : null;
      if (existing == null || existing.receiverClass() == null) {
        monomorphicLinkRef = new WeakReference<>(newLink);
        return newLink;
      }
      @SuppressWarnings("unchecked")
      WeakReference<AccessLink>[] newArray =
          (WeakReference<AccessLink>[])
              new WeakReference<?>[] {existingRef, new WeakReference<>(newLink)};
      polymorphicLinks = newArray;
      state = State.POLYMORPHIC;
      return newLink;
    }

    if (state == State.POLYMORPHIC) {
      List<WeakReference<AccessLink>> live = new ArrayList<>();
      for (WeakReference<AccessLink> ref : polymorphicLinks) {
        AccessLink l = ref != null ? ref.get() : null;
        if (l != null && l.receiverClass() != null) {
          live.add(ref);
        }
      }
      if (live.size() < MAX_PIC_DEPTH) {
        live.add(new WeakReference<>(newLink));
        @SuppressWarnings("unchecked")
        WeakReference<AccessLink>[] newArray =
            (WeakReference<AccessLink>[]) live.toArray(new WeakReference<?>[0]);
        polymorphicLinks = newArray;
        return newLink;
      }

      // Transition to Megamorphic
      BoundedWeakClassCache<WeakReference<AccessLink>> cache =
          new BoundedWeakClassCache<>(DEFAULT_MEGAMORPHIC_BOUND);
      for (WeakReference<AccessLink> ref : live) {
        AccessLink l = ref.get();
        if (l != null) {
          Class<?> clazz = l.receiverClass();
          if (clazz != null) {
            cache.put(clazz, ref);
          }
        }
      }
      cache.put(targetClass, new WeakReference<>(newLink));
      megamorphicCache = cache;
      state = State.MEGAMORPHIC;
      return newLink;
    }

    return newLink;
  }

  private AccessLink linkAndRecord(Class<?> targetClass) {
    stats.recordLink();
    AccessLink link = linker.link(targetClass, memberKey, policy);
    if (link.isDenied()) {
      stats.recordDenied();
    }
    return link;
  }
}
