package io.github.minh124199.viettemplate.runtime.linker;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry managing cached {@link DynamicCallSite} instances bounded by capacity. */
public final class CallSiteRegistry {

  private static final int DEFAULT_CAPACITY = 4096;

  private final int maxCapacity;
  private final DynamicLinker linker;
  private final LinkerStatistics statistics;
  private final ConcurrentHashMap<CallSiteKey, DynamicCallSite> sites = new ConcurrentHashMap<>();

  /** Compound key uniquely identifying a call site by its site id, member key, and policy id. */
  public record CallSiteKey(int siteId, MemberKey memberKey, String policyId) {
    public CallSiteKey {
      Objects.requireNonNull(memberKey, "memberKey must not be null");
      Objects.requireNonNull(policyId, "policyId must not be null");
    }
  }

  public CallSiteRegistry() {
    this(DEFAULT_CAPACITY, new DynamicLinker(), new LinkerStatistics());
  }

  public CallSiteRegistry(int maxCapacity, DynamicLinker linker) {
    this(maxCapacity, linker, new LinkerStatistics());
  }

  public CallSiteRegistry(int maxCapacity, DynamicLinker linker, LinkerStatistics statistics) {
    if (maxCapacity <= 0) {
      throw new IllegalArgumentException("maxCapacity must be positive: " + maxCapacity);
    }
    this.maxCapacity = maxCapacity;
    this.linker = Objects.requireNonNull(linker, "linker must not be null");
    this.statistics = Objects.requireNonNull(statistics, "statistics must not be null");
  }

  /** Retrieves or creates an inline-cached dynamic call site. */
  public DynamicCallSite getOrCreate(int siteId, MemberKey memberKey, LinkerAccessPolicy policy) {
    Objects.requireNonNull(memberKey, "memberKey must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    CallSiteKey key = new CallSiteKey(siteId, memberKey, policy.policyId());
    DynamicCallSite existing = sites.get(key);
    if (existing != null) {
      return existing;
    }

    if (sites.size() >= maxCapacity) {
      var it = sites.keySet().iterator();
      if (it.hasNext()) {
        it.next();
        it.remove();
      }
    }

    DynamicCallSite created = new DynamicCallSite(siteId, memberKey, policy, linker, statistics);
    DynamicCallSite prior = sites.putIfAbsent(key, created);
    return prior != null ? prior : created;
  }

  public LinkerStatistics statistics() {
    return statistics;
  }

  public DynamicLinker linker() {
    return linker;
  }

  public int size() {
    return sites.size();
  }

  public int maxCapacity() {
    return maxCapacity;
  }

  public void clear() {
    sites.clear();
  }
}
