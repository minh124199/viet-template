package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic execution budget guarding against resource exhaustion across top-level templates,
 * included templates, evaluations, and layouts.
 */
public final class RenderBudget {

  public static final DiagnosticCode CODE_LIMIT_EXCEEDED =
      DiagnosticCode.of("LIMIT", "LIMIT_EXCEEDED");
  public static final DiagnosticCode CODE_TIME_LIMIT =
      DiagnosticCode.of("LIMIT", "TIME_LIMIT_EXCEEDED");

  private final long maxOutputCharacters;
  private final long maxExecutionTimeMillis;
  private final long deadlineNano; // 0 if unlimited
  private final int maxLoopIterations;
  private final AtomicLong characterCount = new AtomicLong(0);
  private final AtomicLong loopIterationCount = new AtomicLong(0);

  public RenderBudget(
      long maxOutputCharacters, long maxExecutionTimeMillis, int maxLoopIterations) {
    this.maxOutputCharacters = maxOutputCharacters > 0 ? maxOutputCharacters : Long.MAX_VALUE;
    this.maxExecutionTimeMillis = maxExecutionTimeMillis;
    this.deadlineNano =
        maxExecutionTimeMillis > 0 ? System.nanoTime() + (maxExecutionTimeMillis * 1_000_000L) : 0L;
    this.maxLoopIterations = maxLoopIterations > 0 ? maxLoopIterations : Integer.MAX_VALUE;
  }

  public static RenderBudget unlimited() {
    return new RenderBudget(Long.MAX_VALUE, 0L, Integer.MAX_VALUE);
  }

  public void consumeCharacters(int count, TemplateId templateId, SourceSpan span) {
    if (count <= 0) {
      return;
    }
    checkDeadline(templateId, span);
    long current = characterCount.addAndGet(count);
    if (current > maxOutputCharacters) {
      throw new TemplateLimitException(
          "Exceeded maximum rendered output characters limit: " + maxOutputCharacters,
          Objects.requireNonNullElse(templateId, TemplateId.of("unknown")),
          Objects.requireNonNullElse(span, SourceSpan.UNKNOWN),
          CODE_LIMIT_EXCEEDED);
    }
  }

  public void countLoopIteration(TemplateId templateId, SourceSpan span) {
    checkDeadline(templateId, span);
    long current = loopIterationCount.incrementAndGet();
    if (current > maxLoopIterations) {
      throw new TemplateLimitException(
          "Exceeded maximum foreach iterations: " + maxLoopIterations,
          Objects.requireNonNullElse(templateId, TemplateId.of("unknown")),
          Objects.requireNonNullElse(span, SourceSpan.UNKNOWN),
          CODE_LIMIT_EXCEEDED);
    }
  }

  public void checkDeadline(TemplateId templateId, SourceSpan span) {
    if (deadlineNano > 0 && System.nanoTime() > deadlineNano) {
      throw new TemplateLimitException(
          "Exceeded maximum template execution time limit (" + maxExecutionTimeMillis + " ms)",
          Objects.requireNonNullElse(templateId, TemplateId.of("unknown")),
          Objects.requireNonNullElse(span, SourceSpan.UNKNOWN),
          CODE_TIME_LIMIT);
    }
  }

  public long charactersWritten() {
    return characterCount.get();
  }

  public long loopIterations() {
    return loopIterationCount.get();
  }

  public long maxOutputCharacters() {
    return maxOutputCharacters;
  }

  public long maxExecutionTimeMillis() {
    return maxExecutionTimeMillis;
  }

  public int maxLoopIterations() {
    return maxLoopIterations;
  }
}
