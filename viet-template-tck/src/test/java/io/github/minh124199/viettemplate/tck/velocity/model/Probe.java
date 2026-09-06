package io.github.minh124199.viettemplate.tck.velocity.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Side-effect probe used to verify method invocation counts, evaluation order, short-circuit
 * evaluation, and lazy alternate value evaluation.
 */
public final class Probe {

  private final AtomicInteger hitCount = new AtomicInteger(0);
  private final AtomicInteger returnNullCount = new AtomicInteger(0);
  private final String name;

  public Probe() {
    this("default");
  }

  public Probe(String name) {
    this.name = name;
  }

  public int hit() {
    return hitCount.incrementAndGet();
  }

  public Object returnNull() {
    returnNullCount.incrementAndGet();
    return null;
  }

  public boolean fail() {
    throw new DeterministicProbeException("Probe intentionally failed: " + name);
  }

  public int getHitCount() {
    return hitCount.get();
  }

  public int getReturnNullCount() {
    return returnNullCount.get();
  }

  public String getName() {
    return name;
  }

  public static final class DeterministicProbeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DeterministicProbeException(String message) {
      super(message);
    }
  }
}
