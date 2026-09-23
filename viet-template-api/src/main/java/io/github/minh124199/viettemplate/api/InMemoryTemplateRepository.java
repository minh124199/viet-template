package io.github.minh124199.viettemplate.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link TemplateRepository} designed for unit testing, dynamic string template
 * registration, and fixtures.
 */
public final class InMemoryTemplateRepository
    implements TemplateRepository, TemplateFreshnessProvider {

  private final ConcurrentMap<TemplateId, TemplateSource> templates = new ConcurrentHashMap<>();
  private final ConcurrentMap<TemplateId, Long> versions = new ConcurrentHashMap<>();
  private final AtomicLong globalVersion = new AtomicLong(1);

  public InMemoryTemplateRepository() {}

  public static InMemoryTemplateRepository create() {
    return new InMemoryTemplateRepository();
  }

  public static InMemoryTemplateRepository of(Map<TemplateId, String> sources) {
    Objects.requireNonNull(sources, "sources must not be null");
    InMemoryTemplateRepository repo = new InMemoryTemplateRepository();
    sources.forEach(repo::put);
    return repo;
  }

  public InMemoryTemplateRepository put(TemplateId id, String content) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(content, "content must not be null");
    TemplateSource source =
        TemplateSource.of(
            id,
            URI.create("memory:" + id.value()),
            StandardCharsets.UTF_8,
            content,
            System.currentTimeMillis());
    templates.put(id, source);
    long v = globalVersion.incrementAndGet();
    versions.put(id, v);
    return this;
  }

  public InMemoryTemplateRepository put(String name, String content) {
    return put(TemplateId.normalize(name), content);
  }

  public InMemoryTemplateRepository remove(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    templates.remove(id);
    globalVersion.incrementAndGet();
    versions.remove(id);
    return this;
  }

  public void clear() {
    templates.clear();
    globalVersion.incrementAndGet();
    versions.clear();
  }

  @Override
  public Optional<FreshnessToken> freshnessToken(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    Long v = versions.get(id);
    return v != null ? Optional.of(FreshnessToken.ofVersion(v)) : Optional.empty();
  }

  @Override
  public Optional<TemplateSource> find(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    return Optional.ofNullable(templates.get(id));
  }

  public int size() {
    return templates.size();
  }
}
