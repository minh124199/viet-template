package io.github.minh124199.viettemplate.api;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Composite {@link TemplateRepository} that delegates to an ordered list of repositories with
 * deterministic first-match precedence.
 */
public final class CompositeTemplateRepository implements TemplateRepository {

  private final List<TemplateRepository> repositories;

  public CompositeTemplateRepository(List<TemplateRepository> repositories) {
    Objects.requireNonNull(repositories, "repositories must not be null");
    for (TemplateRepository repo : repositories) {
      Objects.requireNonNull(repo, "repository elements must not be null");
    }
    this.repositories = List.copyOf(repositories);
  }

  public static CompositeTemplateRepository of(TemplateRepository... repositories) {
    Objects.requireNonNull(repositories, "repositories must not be null");
    return new CompositeTemplateRepository(Arrays.asList(repositories));
  }

  public static CompositeTemplateRepository of(List<TemplateRepository> repositories) {
    return new CompositeTemplateRepository(repositories);
  }

  @Override
  public Optional<TemplateSource> find(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    for (TemplateRepository repository : repositories) {
      Optional<TemplateSource> source = repository.find(id);
      if (source.isPresent()) {
        return source;
      }
    }
    return Optional.empty();
  }

  public List<TemplateRepository> repositories() {
    return repositories;
  }
}
