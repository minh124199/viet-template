package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import java.util.Objects;

/**
 * Configuration options controlling the compiler optimization pipeline and individual passes.
 *
 * <p>Every pass can be enabled or disabled independently for benchmarking, debugging, and A/B
 * correctness testing.
 */
public record IrOptimizationOptions(
    OptimizationLevel level,
    boolean deadCodeElimination,
    boolean constantFolding,
    boolean booleanSimplification,
    boolean mergeTextConstants,
    boolean preEncodeUtf8,
    boolean redundantConversion,
    boolean directAccessorBinding,
    boolean primitiveSpecialization,
    boolean loopSpecialization,
    boolean macroInlining,
    boolean escapeSpecialization,
    boolean methodSizePlanning,
    int maxInliningDepth,
    int maxInliningStatements,
    int methodSplitThreshold) {

  public static final int DEFAULT_MAX_INLINING_DEPTH = 2;
  public static final int DEFAULT_MAX_INLINING_STATEMENTS = 10;
  public static final int DEFAULT_METHOD_SPLIT_THRESHOLD = 50;

  public IrOptimizationOptions {
    Objects.requireNonNull(level, "level must not be null");
    if (maxInliningDepth < 0) {
      throw new IllegalArgumentException("maxInliningDepth must be >= 0");
    }
    if (maxInliningStatements < 0) {
      throw new IllegalArgumentException("maxInliningStatements must be >= 0");
    }
    if (methodSplitThreshold <= 0) {
      throw new IllegalArgumentException("methodSplitThreshold must be > 0");
    }
  }

  public static IrOptimizationOptions o0() {
    return new IrOptimizationOptions(
        OptimizationLevel.O0,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        DEFAULT_MAX_INLINING_DEPTH,
        DEFAULT_MAX_INLINING_STATEMENTS,
        DEFAULT_METHOD_SPLIT_THRESHOLD);
  }

  public static IrOptimizationOptions o1() {
    return new IrOptimizationOptions(
        OptimizationLevel.O1,
        true, // deadCodeElimination
        true, // constantFolding
        false, // booleanSimplification
        true, // mergeTextConstants
        true, // preEncodeUtf8
        true, // redundantConversion
        false, // directAccessorBinding
        false, // primitiveSpecialization
        false, // loopSpecialization
        false, // macroInlining
        false, // escapeSpecialization
        false, // methodSizePlanning
        DEFAULT_MAX_INLINING_DEPTH,
        DEFAULT_MAX_INLINING_STATEMENTS,
        DEFAULT_METHOD_SPLIT_THRESHOLD);
  }

  public static IrOptimizationOptions o2() {
    return new IrOptimizationOptions(
        OptimizationLevel.O2,
        true, // deadCodeElimination
        true, // constantFolding
        true, // booleanSimplification
        true, // mergeTextConstants
        true, // preEncodeUtf8
        true, // redundantConversion
        true, // directAccessorBinding
        true, // primitiveSpecialization
        true, // loopSpecialization
        true, // macroInlining
        true, // escapeSpecialization
        true, // methodSizePlanning
        DEFAULT_MAX_INLINING_DEPTH,
        DEFAULT_MAX_INLINING_STATEMENTS,
        DEFAULT_METHOD_SPLIT_THRESHOLD);
  }

  public static IrOptimizationOptions o3() {
    return new IrOptimizationOptions(
        OptimizationLevel.O3,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        true,
        4, // deeper inlining
        20, // larger inlining statement budget
        30);
  }

  public static IrOptimizationOptions defaultOptions() {
    return o2();
  }

  public static Builder builder() {
    return new Builder(o2());
  }

  public static Builder builder(OptimizationLevel level) {
    return switch (level) {
      case O0 -> new Builder(o0());
      case O1 -> new Builder(o1());
      case O2 -> new Builder(o2());
      case O3 -> new Builder(o3());
    };
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static final class Builder {
    private OptimizationLevel level;
    private boolean deadCodeElimination;
    private boolean constantFolding;
    private boolean booleanSimplification;
    private boolean mergeTextConstants;
    private boolean preEncodeUtf8;
    private boolean redundantConversion;
    private boolean directAccessorBinding;
    private boolean primitiveSpecialization;
    private boolean loopSpecialization;
    private boolean macroInlining;
    private boolean escapeSpecialization;
    private boolean methodSizePlanning;
    private int maxInliningDepth;
    private int maxInliningStatements;
    private int methodSplitThreshold;

    private Builder(IrOptimizationOptions defaults) {
      this.level = defaults.level();
      this.deadCodeElimination = defaults.deadCodeElimination();
      this.constantFolding = defaults.constantFolding();
      this.booleanSimplification = defaults.booleanSimplification();
      this.mergeTextConstants = defaults.mergeTextConstants();
      this.preEncodeUtf8 = defaults.preEncodeUtf8();
      this.redundantConversion = defaults.redundantConversion();
      this.directAccessorBinding = defaults.directAccessorBinding();
      this.primitiveSpecialization = defaults.primitiveSpecialization();
      this.loopSpecialization = defaults.loopSpecialization();
      this.macroInlining = defaults.macroInlining();
      this.escapeSpecialization = defaults.escapeSpecialization();
      this.methodSizePlanning = defaults.methodSizePlanning();
      this.maxInliningDepth = defaults.maxInliningDepth();
      this.maxInliningStatements = defaults.maxInliningStatements();
      this.methodSplitThreshold = defaults.methodSplitThreshold();
    }

    public Builder level(OptimizationLevel level) {
      this.level = Objects.requireNonNull(level, "level must not be null");
      return this;
    }

    public Builder deadCodeElimination(boolean deadCodeElimination) {
      this.deadCodeElimination = deadCodeElimination;
      return this;
    }

    public Builder constantFolding(boolean constantFolding) {
      this.constantFolding = constantFolding;
      return this;
    }

    public Builder booleanSimplification(boolean booleanSimplification) {
      this.booleanSimplification = booleanSimplification;
      return this;
    }

    public Builder mergeTextConstants(boolean mergeTextConstants) {
      this.mergeTextConstants = mergeTextConstants;
      return this;
    }

    public Builder preEncodeUtf8(boolean preEncodeUtf8) {
      this.preEncodeUtf8 = preEncodeUtf8;
      return this;
    }

    public Builder redundantConversion(boolean redundantConversion) {
      this.redundantConversion = redundantConversion;
      return this;
    }

    public Builder directAccessorBinding(boolean directAccessorBinding) {
      this.directAccessorBinding = directAccessorBinding;
      return this;
    }

    public Builder primitiveSpecialization(boolean primitiveSpecialization) {
      this.primitiveSpecialization = primitiveSpecialization;
      return this;
    }

    public Builder loopSpecialization(boolean loopSpecialization) {
      this.loopSpecialization = loopSpecialization;
      return this;
    }

    public Builder macroInlining(boolean macroInlining) {
      this.macroInlining = macroInlining;
      return this;
    }

    public Builder escapeSpecialization(boolean escapeSpecialization) {
      this.escapeSpecialization = escapeSpecialization;
      return this;
    }

    public Builder methodSizePlanning(boolean methodSizePlanning) {
      this.methodSizePlanning = methodSizePlanning;
      return this;
    }

    public Builder maxInliningDepth(int maxInliningDepth) {
      this.maxInliningDepth = maxInliningDepth;
      return this;
    }

    public Builder maxInliningStatements(int maxInliningStatements) {
      this.maxInliningStatements = maxInliningStatements;
      return this;
    }

    public Builder methodSplitThreshold(int methodSplitThreshold) {
      this.methodSplitThreshold = methodSplitThreshold;
      return this;
    }

    public IrOptimizationOptions build() {
      return new IrOptimizationOptions(
          level,
          deadCodeElimination,
          constantFolding,
          booleanSimplification,
          mergeTextConstants,
          preEncodeUtf8,
          redundantConversion,
          directAccessorBinding,
          primitiveSpecialization,
          loopSpecialization,
          macroInlining,
          escapeSpecialization,
          methodSizePlanning,
          maxInliningDepth,
          maxInliningStatements,
          methodSplitThreshold);
    }
  }
}
