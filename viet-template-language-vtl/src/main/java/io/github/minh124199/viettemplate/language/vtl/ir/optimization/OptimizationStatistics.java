package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe metrics collector tracking optimization actions performed across all compiler passes.
 */
public final class OptimizationStatistics {

  private final AtomicInteger deadCodeRemoved = new AtomicInteger();
  private final AtomicInteger constantsFolded = new AtomicInteger();
  private final AtomicInteger booleansSimplified = new AtomicInteger();
  private final AtomicInteger textConstantsMerged = new AtomicInteger();
  private final AtomicInteger utf8ConstantsEncoded = new AtomicInteger();
  private final AtomicInteger conversionsEliminated = new AtomicInteger();
  private final AtomicInteger accessorsBound = new AtomicInteger();
  private final AtomicInteger primitivesSpecialized = new AtomicInteger();
  private final AtomicInteger loopsSpecialized = new AtomicInteger();
  private final AtomicInteger macrosInlined = new AtomicInteger();
  private final AtomicInteger methodsSplit = new AtomicInteger();
  private final AtomicInteger escapesHoisted = new AtomicInteger();

  public OptimizationStatistics() {}

  public void recordDeadCodeRemoved(int count) {
    deadCodeRemoved.addAndGet(count);
  }

  public void recordConstantFolded() {
    constantsFolded.incrementAndGet();
  }

  public void recordBooleanSimplified() {
    booleansSimplified.incrementAndGet();
  }

  public void recordTextConstantsMerged(int count) {
    textConstantsMerged.addAndGet(count);
  }

  public void recordUtf8ConstantEncoded() {
    utf8ConstantsEncoded.incrementAndGet();
  }

  public void recordConversionEliminated() {
    conversionsEliminated.incrementAndGet();
  }

  public void recordAccessorBound() {
    accessorsBound.incrementAndGet();
  }

  public void recordPrimitiveSpecialized() {
    primitivesSpecialized.incrementAndGet();
  }

  public void recordLoopSpecialized() {
    loopsSpecialized.incrementAndGet();
  }

  public void recordMacroInlined() {
    macrosInlined.incrementAndGet();
  }

  public void recordMethodSplit() {
    methodsSplit.incrementAndGet();
  }

  public void recordEscapeHoisted() {
    escapesHoisted.incrementAndGet();
  }

  public int deadCodeRemoved() {
    return deadCodeRemoved.get();
  }

  public int constantsFolded() {
    return constantsFolded.get();
  }

  public int booleansSimplified() {
    return booleansSimplified.get();
  }

  public int textConstantsMerged() {
    return textConstantsMerged.get();
  }

  public int utf8ConstantsEncoded() {
    return utf8ConstantsEncoded.get();
  }

  public int conversionsEliminated() {
    return conversionsEliminated.get();
  }

  public int accessorsBound() {
    return accessorsBound.get();
  }

  public int primitivesSpecialized() {
    return primitivesSpecialized.get();
  }

  public int loopsSpecialized() {
    return loopsSpecialized.get();
  }

  public int macrosInlined() {
    return macrosInlined.get();
  }

  public int methodsSplit() {
    return methodsSplit.get();
  }

  public int escapesHoisted() {
    return escapesHoisted.get();
  }

  public int totalOptimizations() {
    return deadCodeRemoved.get()
        + constantsFolded.get()
        + booleansSimplified.get()
        + textConstantsMerged.get()
        + utf8ConstantsEncoded.get()
        + conversionsEliminated.get()
        + accessorsBound.get()
        + primitivesSpecialized.get()
        + loopsSpecialized.get()
        + macrosInlined.get()
        + methodsSplit.get()
        + escapesHoisted.get();
  }

  @Override
  public String toString() {
    return "OptimizationStatistics{"
        + "deadCodeRemoved="
        + deadCodeRemoved.get()
        + ", constantsFolded="
        + constantsFolded.get()
        + ", booleansSimplified="
        + booleansSimplified.get()
        + ", textConstantsMerged="
        + textConstantsMerged.get()
        + ", utf8ConstantsEncoded="
        + utf8ConstantsEncoded.get()
        + ", conversionsEliminated="
        + conversionsEliminated.get()
        + ", accessorsBound="
        + accessorsBound.get()
        + ", primitivesSpecialized="
        + primitivesSpecialized.get()
        + ", loopsSpecialized="
        + loopsSpecialized.get()
        + ", macrosInlined="
        + macrosInlined.get()
        + ", methodsSplit="
        + methodsSplit.get()
        + ", escapesHoisted="
        + escapesHoisted.get()
        + ", total="
        + totalOptimizations()
        + '}';
  }
}
