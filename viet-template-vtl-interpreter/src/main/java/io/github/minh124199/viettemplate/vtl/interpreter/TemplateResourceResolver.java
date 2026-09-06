package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Strategy for resolving resources requested by {@code #include} and {@code #parse}. */
@FunctionalInterface
public interface TemplateResourceResolver {

  Optional<TemplateResource> resolve(TemplateId currentTemplate, String requestedPath);

  static TemplateResourceResolver empty() {
    return (current, path) -> Optional.empty();
  }

  static InMemoryBuilder inMemory() {
    return new InMemoryBuilder();
  }

  final class InMemoryBuilder {
    private final Map<String, String> resources = new HashMap<>();

    public InMemoryBuilder put(String path, String content) {
      Objects.requireNonNull(path, "path must not be null");
      Objects.requireNonNull(content, "content must not be null");
      resources.put(path, content);
      return this;
    }

    public InMemoryBuilder add(String path, String content) {
      return put(path, content);
    }

    public TemplateResourceResolver build() {
      Map<String, String> copy = Collections.unmodifiableMap(new HashMap<>(resources));
      return (current, path) -> {
        String content = copy.get(path);
        if (content != null) {
          return Optional.of(new TemplateResource(TemplateId.of(path), content));
        }
        return Optional.empty();
      };
    }
  }
}
