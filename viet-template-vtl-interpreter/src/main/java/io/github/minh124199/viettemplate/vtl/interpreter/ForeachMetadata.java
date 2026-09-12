package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.TemplateCallable;
import io.github.minh124199.viettemplate.api.TemplateData;

/** Loop metadata exposed to templates as {@code $foreach}. */
@TemplateData
public final class ForeachMetadata
    implements io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata {

  private final int index;
  private final int count;
  private final boolean first;
  private final boolean last;
  private final boolean hasNext;
  private final ForeachMetadata parent;
  private final ForeachMetadata topmost;

  public ForeachMetadata(
      int index, int count, boolean first, boolean last, boolean hasNext, ForeachMetadata parent) {
    this.index = index;
    this.count = count;
    this.first = first;
    this.last = last;
    this.hasNext = hasNext;
    this.parent = parent;
    this.topmost = parent == null ? this : parent.topmost();
  }

  public int index() {
    return index;
  }

  public int getIndex() {
    return index;
  }

  public int count() {
    return count;
  }

  public int getCount() {
    return count;
  }

  public boolean first() {
    return first;
  }

  public boolean isFirst() {
    return first;
  }

  public boolean last() {
    return last;
  }

  public boolean isLast() {
    return last;
  }

  public boolean hasNext() {
    return hasNext;
  }

  public boolean getHasNext() {
    return hasNext;
  }

  public ForeachMetadata parent() {
    return parent;
  }

  public ForeachMetadata getParent() {
    return parent;
  }

  public ForeachMetadata topmost() {
    return topmost;
  }

  public ForeachMetadata getTopmost() {
    return topmost;
  }

  /** Stops the current #foreach loop when invoked via {@code $foreach.stop()}. */
  @TemplateCallable
  public void stop() throws BreakSignal {
    throw BreakSignal.INSTANCE;
  }

  @Override
  public String toString() {
    return "[Foreach index=" + index + ", count=" + count + ", hasNext=" + hasNext + "]";
  }
}
