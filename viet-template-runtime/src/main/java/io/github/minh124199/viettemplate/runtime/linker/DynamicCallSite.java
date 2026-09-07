package io.github.minh124199.viettemplate.runtime.linker;

import java.util.Arrays;
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
  private volatile AccessLink monomorphicLink;
  private volatile AccessLink[] polymorphicLinks = new AccessLink[0];
  private volatile BoundedWeakClassCache<AccessLink> megamorphicCache;

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
      AccessLink mono = monomorphicLink;
      if (mono != null && mono.matches(targetClass)) {
        stats.recordPicHit();
        return mono;
      }
    }

    // 2. Fast Path: Polymorphic
    if (state == State.POLYMORPHIC) {
      AccessLink[] links = polymorphicLinks;
      for (int i = 0; i < links.length; i++) {
        AccessLink l = links[i];
        if (l.matches(targetClass)) {
          stats.recordPicHit();
          return l;
        }
      }
    }

    // 3. Fast Path: Megamorphic
    if (state == State.MEGAMORPHIC) {
      BoundedWeakClassCache<AccessLink> cache = megamorphicCache;
      AccessLink cached = cache.get(targetClass);
      if (cached != null) {
        stats.recordMegamorphicHit();
        return cached;
      }
      stats.recordMegamorphicMiss();
      AccessLink link = linkAndRecord(targetClass);
      cache.put(targetClass, link);
      return link;
    }

    // 4. Miss Path: Synchronized state transition
    return handleCacheMiss(targetClass);
  }

  private synchronized AccessLink handleCacheMiss(Class<?> targetClass) {
    // Re-check state under lock to prevent race conditions
    if (state == State.MONOMORPHIC
        && monomorphicLink != null
        && monomorphicLink.matches(targetClass)) {
      stats.recordPicHit();
      return monomorphicLink;
    }
    if (state == State.POLYMORPHIC) {
      for (AccessLink l : polymorphicLinks) {
        if (l.matches(targetClass)) {
          stats.recordPicHit();
          return l;
        }
      }
    }
    if (state == State.MEGAMORPHIC) {
      AccessLink cached = megamorphicCache.get(targetClass);
      if (cached != null) {
        stats.recordMegamorphicHit();
        return cached;
      }
      AccessLink link = linkAndRecord(targetClass);
      megamorphicCache.put(targetClass, link);
      return link;
    }

    stats.recordPicMiss();
    AccessLink newLink = linkAndRecord(targetClass);

    if (state == State.UNLINKED) {
      monomorphicLink = newLink;
      state = State.MONOMORPHIC;
      return newLink;
    }

    if (state == State.MONOMORPHIC) {
      AccessLink[] newArray = new AccessLink[] {monomorphicLink, newLink};
      polymorphicLinks = newArray;
      state = State.POLYMORPHIC;
      return newLink;
    }

    if (state == State.POLYMORPHIC) {
      if (polymorphicLinks.length < MAX_PIC_DEPTH) {
        AccessLink[] newArray = Arrays.copyOf(polymorphicLinks, polymorphicLinks.length + 1);
        newArray[polymorphicLinks.length] = newLink;
        polymorphicLinks = newArray;
        return newLink;
      }

      // Transition to Megamorphic
      BoundedWeakClassCache<AccessLink> cache =
          new BoundedWeakClassCache<>(DEFAULT_MEGAMORPHIC_BOUND);
      for (AccessLink l : polymorphicLinks) {
        Class<?> clazz = l.receiverClass();
        if (clazz != null) {
          cache.put(clazz, l);
        }
      }
      cache.put(targetClass, newLink);
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
