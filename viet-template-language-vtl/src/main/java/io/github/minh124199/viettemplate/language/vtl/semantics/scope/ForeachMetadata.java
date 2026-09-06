package io.github.minh124199.viettemplate.language.vtl.semantics.scope;

/** Standard loop metadata contract exposed as the {@code $foreach} variable in loops. */
public interface ForeachMetadata {
  int index();

  int getIndex();

  int count();

  int getCount();

  boolean first();

  boolean isFirst();

  boolean last();

  boolean isLast();

  boolean hasNext();

  boolean getHasNext();

  ForeachMetadata parent();

  ForeachMetadata getParent();

  void stop();
}
