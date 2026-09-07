package io.github.minh124199.viettemplate.api;

import java.util.Map;

/** Context builder interface exposed to {@link RenderContextContributor} instances. */
public interface ContributorContext {

  ContributorContext put(String key, Object value);

  ContributorContext putAll(Map<String, ?> entries);

  boolean contains(String key);

  Object get(String key);
}
