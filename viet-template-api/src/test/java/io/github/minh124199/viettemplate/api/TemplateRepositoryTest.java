package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateRepositoryTest {

  @Test
  void inMemoryRepositoryCrud() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("hello.vm");

    assertThat(repo.find(id)).isEmpty();

    repo.put(id, "Hello $name!");
    assertThat(repo.size()).isEqualTo(1);

    Optional<TemplateSource> found = repo.find(id);
    assertThat(found).isPresent();
    assertThat(found.get().content()).isEqualTo("Hello $name!");
    assertThat(found.get().fingerprint()).isNotBlank();
    assertThat(found.get().origin().getScheme()).isEqualTo("memory");

    repo.remove(id);
    assertThat(repo.find(id)).isEmpty();
    assertThat(repo.size()).isEqualTo(0);
  }

  @Test
  void filesystemRepositoryLoadsFileAndEnforcesConfinement(@TempDir Path tempDir)
      throws IOException {
    Path templatesDir = tempDir.resolve("templates");
    Files.createDirectories(templatesDir);
    Path testFile = templatesDir.resolve("user.vm");
    Files.writeString(testFile, "User Profile: $user");

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(templatesDir);
    TemplateId id = TemplateId.of("user.vm");

    Optional<TemplateSource> source = repo.find(id);
    assertThat(source).isPresent();
    assertThat(source.get().content()).isEqualTo("User Profile: $user");
    assertThat(source.get().origin().getScheme()).isEqualTo("file");
    assertThat(source.get().lastModifiedEpochMillis()).isGreaterThan(0L);

    // Missing file
    assertThat(repo.find(TemplateId.of("nonexistent.vm"))).isEmpty();
  }

  @Test
  void filesystemRepositoryRejectsPathTraversalOutsideRoot(@TempDir Path tempDir)
      throws IOException {
    Path rootDir = tempDir.resolve("root");
    Files.createDirectories(rootDir);

    // External file outside root
    Path secretFile = tempDir.resolve("secret.txt");
    Files.writeString(secretFile, "confidential");

    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(rootDir);

    // Direct traversal with normalize
    assertThatThrownBy(() -> repo.find(TemplateId.normalize("../secret.txt")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void compositeRepositoryMaintainsDeterministicPrecedence() {
    InMemoryTemplateRepository repo1 = InMemoryTemplateRepository.create();
    InMemoryTemplateRepository repo2 = InMemoryTemplateRepository.create();

    TemplateId sharedId = TemplateId.of("shared.vm");
    TemplateId uniqueId2 = TemplateId.of("unique2.vm");

    repo1.put(sharedId, "From Repo 1");
    repo2.put(sharedId, "From Repo 2");
    repo2.put(uniqueId2, "Unique 2");

    CompositeTemplateRepository composite = CompositeTemplateRepository.of(repo1, repo2);

    // Repo 1 has precedence over Repo 2
    Optional<TemplateSource> sharedSource = composite.find(sharedId);
    assertThat(sharedSource).isPresent();
    assertThat(sharedSource.get().content()).isEqualTo("From Repo 1");

    // Fallback to Repo 2 when Repo 1 misses
    Optional<TemplateSource> uniqueSource = composite.find(uniqueId2);
    assertThat(uniqueSource).isPresent();
    assertThat(uniqueSource.get().content()).isEqualTo("Unique 2");

    // Missing from both
    assertThat(composite.find(TemplateId.of("missing.vm"))).isEmpty();
  }

  @Test
  void classpathRepositoryResolvesResources() {
    // We test with a known resource or prefix
    ClasspathTemplateRepository repo = ClasspathTemplateRepository.of("test-templates");
    assertThat(repo.prefix()).isEqualTo("test-templates");
    assertThat(repo.find(TemplateId.of("nonexistent.vm"))).isEmpty();
  }
}
