package io.github.minh124199.viettemplate.api;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Service Provider Interface for locating and acquiring template source artifacts.
 *
 * <p>Implementations must ensure that template retrieval is thread-safe and traversal-confined to
 * configured resource roots.
 */
public interface TemplateRepository {

  /**
   * Finds and loads the {@link TemplateSource} for the specified {@link TemplateId}.
   *
   * @param id the normalized template identifier
   * @return an {@link Optional} containing the source if present, or empty if not found
   */
  Optional<TemplateSource> find(TemplateId id);

  /** Creates a classpath-backed {@link TemplateRepository} scanning the default classloader. */
  static TemplateRepository classpath(String resourcePrefix) {
    return ClasspathTemplateRepository.of(resourcePrefix);
  }

  /** Creates a classpath-backed {@link TemplateRepository} using the given classloader. */
  static TemplateRepository classpath(ClassLoader classLoader, String resourcePrefix) {
    return ClasspathTemplateRepository.of(classLoader, resourcePrefix);
  }

  /** Creates a filesystem-backed {@link TemplateRepository} rooted at the specified directory. */
  static TemplateRepository filesystem(Path rootDir) {
    return FilesystemTemplateRepository.of(rootDir);
  }

  /** Creates a composite {@link TemplateRepository} querying the provided delegates in order. */
  static TemplateRepository composite(TemplateRepository... repositories) {
    return CompositeTemplateRepository.of(repositories);
  }

  /** Creates an in-memory test {@link TemplateRepository}. */
  static InMemoryTemplateRepository inMemory() {
    return InMemoryTemplateRepository.create();
  }

  /** Creates an in-memory {@link TemplateRepository} populated with the provided source mapping. */
  static InMemoryTemplateRepository inMemory(Map<TemplateId, String> templates) {
    return InMemoryTemplateRepository.of(templates);
  }
}
