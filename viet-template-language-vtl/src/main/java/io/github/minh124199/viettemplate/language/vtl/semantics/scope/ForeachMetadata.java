package io.github.minh124199.viettemplate.language.vtl.semantics.scope;

import io.github.minh124199.viettemplate.api.TemplateCallable;
import io.github.minh124199.viettemplate.api.TemplateData;

/** Standard loop metadata contract exposed as the {@code $foreach} variable in loops. */
@TemplateData
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

  @TemplateCallable
  void stop();
}
