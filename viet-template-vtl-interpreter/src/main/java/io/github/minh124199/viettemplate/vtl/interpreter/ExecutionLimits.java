package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.runtime.RenderBudget;

/** Immutable execution limits guarding against resource exhaustion. */
public record ExecutionLimits(
    int maxLoopIterations,
    int maxRangeSize,
    int maxMacroDepth,
    int maxParseDepth,
    int maxEvaluateDepth,
    int maxDynamicSourceLength,
    long maxOutputCharacters,
    long maxExecutionTimeMillis) {

  public static final int DEFAULT_MAX_LOOP_ITERATIONS = 100_000;
  public static final int DEFAULT_MAX_RANGE_SIZE = 10_000;
  public static final int DEFAULT_MAX_MACRO_DEPTH = 100;
  public static final int DEFAULT_MAX_PARSE_DEPTH = 50;
  public static final int DEFAULT_MAX_EVALUATE_DEPTH = 20;
  public static final int DEFAULT_MAX_DYNAMIC_SOURCE_LENGTH = 100_000;
  public static final long DEFAULT_MAX_OUTPUT_CHARACTERS = 10_000_000L;
  public static final long DEFAULT_MAX_EXECUTION_TIME_MILLIS = 0L;

  public static final ExecutionLimits DEFAULT =
      new ExecutionLimits(
          DEFAULT_MAX_LOOP_ITERATIONS,
          DEFAULT_MAX_RANGE_SIZE,
          DEFAULT_MAX_MACRO_DEPTH,
          DEFAULT_MAX_PARSE_DEPTH,
          DEFAULT_MAX_EVALUATE_DEPTH,
          DEFAULT_MAX_DYNAMIC_SOURCE_LENGTH,
          DEFAULT_MAX_OUTPUT_CHARACTERS,
          DEFAULT_MAX_EXECUTION_TIME_MILLIS);

  public ExecutionLimits {
    if (maxLoopIterations < 0
        || maxRangeSize < 0
        || maxMacroDepth < 0
        || maxParseDepth < 0
        || maxEvaluateDepth < 0
        || maxDynamicSourceLength < 0
        || maxOutputCharacters < 0
        || maxExecutionTimeMillis < 0) {
      throw new IllegalArgumentException("Execution limits must not be negative");
    }
  }

  public ExecutionLimits(
      int maxLoopIterations,
      int maxRangeSize,
      int maxMacroDepth,
      int maxParseDepth,
      int maxEvaluateDepth,
      int maxDynamicSourceLength,
      long maxOutputCharacters) {
    this(
        maxLoopIterations,
        maxRangeSize,
        maxMacroDepth,
        maxParseDepth,
        maxEvaluateDepth,
        maxDynamicSourceLength,
        maxOutputCharacters,
        DEFAULT_MAX_EXECUTION_TIME_MILLIS);
  }

  public static ExecutionLimits unlimited() {
    return new ExecutionLimits(
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        Long.MAX_VALUE,
        0L);
  }

  public RenderBudget createRenderBudget() {
    return new RenderBudget(maxOutputCharacters, maxExecutionTimeMillis, maxLoopIterations);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private int maxLoopIterations = DEFAULT_MAX_LOOP_ITERATIONS;
    private int maxRangeSize = DEFAULT_MAX_RANGE_SIZE;
    private int maxMacroDepth = DEFAULT_MAX_MACRO_DEPTH;
    private int maxParseDepth = DEFAULT_MAX_PARSE_DEPTH;
    private int maxEvaluateDepth = DEFAULT_MAX_EVALUATE_DEPTH;
    private int maxDynamicSourceLength = DEFAULT_MAX_DYNAMIC_SOURCE_LENGTH;
    private long maxOutputCharacters = DEFAULT_MAX_OUTPUT_CHARACTERS;
    private long maxExecutionTimeMillis = DEFAULT_MAX_EXECUTION_TIME_MILLIS;

    public Builder maxLoopIterations(int maxLoopIterations) {
      this.maxLoopIterations = maxLoopIterations;
      return this;
    }

    public Builder maxRangeSize(int maxRangeSize) {
      this.maxRangeSize = maxRangeSize;
      return this;
    }

    public Builder maxMacroDepth(int maxMacroDepth) {
      this.maxMacroDepth = maxMacroDepth;
      return this;
    }

    public Builder maxParseDepth(int maxParseDepth) {
      this.maxParseDepth = maxParseDepth;
      return this;
    }

    public Builder maxEvaluateDepth(int maxEvaluateDepth) {
      this.maxEvaluateDepth = maxEvaluateDepth;
      return this;
    }

    public Builder maxDynamicSourceLength(int maxDynamicSourceLength) {
      this.maxDynamicSourceLength = maxDynamicSourceLength;
      return this;
    }

    public Builder maxOutputCharacters(long maxOutputCharacters) {
      this.maxOutputCharacters = maxOutputCharacters;
      return this;
    }

    public Builder maxExecutionTimeMillis(long maxExecutionTimeMillis) {
      this.maxExecutionTimeMillis = maxExecutionTimeMillis;
      return this;
    }

    public ExecutionLimits build() {
      return new ExecutionLimits(
          maxLoopIterations,
          maxRangeSize,
          maxMacroDepth,
          maxParseDepth,
          maxEvaluateDepth,
          maxDynamicSourceLength,
          maxOutputCharacters,
          maxExecutionTimeMillis);
    }
  }
}
