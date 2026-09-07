package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared compilation context passed through all optimization passes in the pipeline.
 *
 * <p>Tracks mutable compiler state (fresh local slots, call site IDs, metrics, constant pool).
 */
public final class OptimizationContext {

  private final IrOptimizationOptions options;
  private final OptimizationStatistics statistics;
  private final IrConstantPool constantPool;
  private final AtomicInteger nextSlot;
  private final AtomicInteger nextCallSiteId;
  private final AtomicInteger nextChunkFunctionId;

  public OptimizationContext(IrTemplate template, IrOptimizationOptions options) {
    Objects.requireNonNull(template, "template must not be null");
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.statistics = new OptimizationStatistics();
    this.constantPool = template.constants();

    int maxSlot = 0;
    for (IrParameter param : template.parameters()) {
      maxSlot = Math.max(maxSlot, param.slot() + 1);
    }
    for (IrFunction fn : template.functions()) {
      for (IrParameter p : fn.parameters()) {
        maxSlot = Math.max(maxSlot, p.slot() + 1);
      }
      for (IrLocal l : fn.locals()) {
        maxSlot = Math.max(maxSlot, l.slot() + 1);
      }
    }

    this.nextSlot = new AtomicInteger(maxSlot + 10);
    this.nextCallSiteId = new AtomicInteger(1000);
    this.nextChunkFunctionId = new AtomicInteger(1);
  }

  public OptimizationContext(IrOptimizationOptions options, IrConstantPool constantPool) {
    this(options, new OptimizationStatistics(), constantPool, 10, 1000);
  }

  public OptimizationContext(
      IrOptimizationOptions options,
      OptimizationStatistics statistics,
      IrConstantPool constantPool,
      int initialSlot,
      int initialCallSiteId) {
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.statistics = Objects.requireNonNull(statistics, "statistics must not be null");
    this.constantPool = Objects.requireNonNull(constantPool, "constantPool must not be null");
    this.nextSlot = new AtomicInteger(initialSlot);
    this.nextCallSiteId = new AtomicInteger(initialCallSiteId);
    this.nextChunkFunctionId = new AtomicInteger(1);
  }

  public IrOptimizationOptions options() {
    return options;
  }

  public OptimizationStatistics statistics() {
    return statistics;
  }

  public IrConstantPool constantPool() {
    return constantPool;
  }

  public int allocateLocalSlot() {
    return nextSlot.getAndIncrement();
  }

  public int allocateCallSiteId() {
    return nextCallSiteId.getAndIncrement();
  }

  public String nextChunkFunctionName() {
    return "__render_chunk_" + nextChunkFunctionId.getAndIncrement();
  }
}
