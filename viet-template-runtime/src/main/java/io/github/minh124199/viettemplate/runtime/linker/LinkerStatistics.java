package io.github.minh124199.viettemplate.runtime.linker;

import java.util.concurrent.atomic.AtomicLong;

/** Low-overhead atomic statistics tracking dynamic call site and linker performance. */
public final class LinkerStatistics {

  private final AtomicLong links = new AtomicLong();
  private final AtomicLong picHits = new AtomicLong();
  private final AtomicLong picMisses = new AtomicLong();
  private final AtomicLong megamorphicHits = new AtomicLong();
  private final AtomicLong megamorphicMisses = new AtomicLong();
  private final AtomicLong denied = new AtomicLong();

  /** Immutable snapshot of current statistics. */
  public record Snapshot(
      long links,
      long picHits,
      long picMisses,
      long megamorphicHits,
      long megamorphicMisses,
      long denied) {

    public double hitRatio() {
      long total = picHits + picMisses;
      return total == 0 ? 1.0 : (double) picHits / total;
    }
  }

  public void recordLink() {
    links.incrementAndGet();
  }

  public void recordPicHit() {
    picHits.incrementAndGet();
  }

  public void recordPicMiss() {
    picMisses.incrementAndGet();
  }

  public void recordMegamorphicHit() {
    megamorphicHits.incrementAndGet();
  }

  public void recordMegamorphicMiss() {
    megamorphicMisses.incrementAndGet();
  }

  public void recordDenied() {
    denied.incrementAndGet();
  }

  public long links() {
    return links.get();
  }

  public long totalLinks() {
    return links.get();
  }

  public long picHits() {
    return picHits.get();
  }

  public long picMisses() {
    return picMisses.get();
  }

  public long megamorphicHits() {
    return megamorphicHits.get();
  }

  public long megamorphicMisses() {
    return megamorphicMisses.get();
  }

  public long denied() {
    return denied.get();
  }

  public Snapshot snapshot() {
    return new Snapshot(
        links.get(),
        picHits.get(),
        picMisses.get(),
        megamorphicHits.get(),
        megamorphicMisses.get(),
        denied.get());
  }

  public void reset() {
    links.set(0);
    picHits.set(0);
    picMisses.set(0);
    megamorphicHits.set(0);
    megamorphicMisses.set(0);
    denied.set(0);
  }
}
