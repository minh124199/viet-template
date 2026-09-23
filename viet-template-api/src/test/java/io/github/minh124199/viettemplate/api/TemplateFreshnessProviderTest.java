package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateFreshnessProviderTest {

  @Test
  void freshnessTokenValueSemanticsAndToString() {
    FreshnessToken imm1 = FreshnessToken.immutable();
    FreshnessToken imm2 = FreshnessToken.immutable();
    assertThat(imm1).isEqualTo(imm2);
    assertThat(imm1.hashCode()).isEqualTo(imm2.hashCode());
    assertThat(imm1.toString()).isEqualTo("FreshnessToken[immutable]");

    FreshnessToken ver1 = FreshnessToken.ofVersion(42L);
    FreshnessToken ver2 = FreshnessToken.ofVersion(42L);
    FreshnessToken ver3 = FreshnessToken.ofVersion(43L);
    assertThat(ver1).isEqualTo(ver2);
    assertThat(ver1).isNotEqualTo(ver3);
    assertThat(ver1.hashCode()).isEqualTo(ver2.hashCode());
    assertThat(ver1.toString()).isEqualTo("FreshnessToken[version=42]");

    FreshnessToken file1 = FreshnessToken.ofFile(1000L, 500L);
    FreshnessToken file2 = FreshnessToken.ofFile(1000L, 500L);
    FreshnessToken file3 = FreshnessToken.ofFile(1001L, 500L);
    FreshnessToken file4 = FreshnessToken.ofFile(1000L, 501L);
    assertThat(file1).isEqualTo(file2);
    assertThat(file1).isNotEqualTo(file3);
    assertThat(file1).isNotEqualTo(file4);
    assertThat(file1.hashCode()).isEqualTo(file2.hashCode());
    assertThat(file1.toString())
        .isEqualTo("FreshnessToken[lastModifiedMillis=1000, sizeBytes=500]");

    assertThat(imm1).isNotEqualTo(ver1);
    assertThat(ver1).isNotEqualTo(file1);
  }

  @Test
  void classpathTemplateRepositoryFreshness() {
    ClassLoader cl =
        new ClassLoader(ClasspathTemplateRepository.class.getClassLoader()) {
          @Override
          public URL getResource(String name) {
            if ("templates/hello.vtl".equals(name)) {
              return super.getResource(
                  "io/github/minh124199/viettemplate/api/TemplateRepository.class");
            }
            return null;
          }
        };

    ClasspathTemplateRepository repo = new ClasspathTemplateRepository(cl, "templates");
    TemplateId presentId = TemplateId.of("hello.vtl");
    TemplateId absentId = TemplateId.of("missing.vtl");

    Optional<FreshnessToken> presentToken = repo.freshnessToken(presentId);
    assertThat(presentToken).isPresent();
    assertThat(presentToken.get()).isEqualTo(FreshnessToken.immutable());

    // Subsequent retrieval returns the same cached token
    Optional<FreshnessToken> subsequentToken = repo.freshnessToken(presentId);
    assertThat(subsequentToken).isPresent();
    assertThat(subsequentToken).isSameAs(presentToken);

    assertThat(repo.freshnessToken(absentId)).isEmpty();
  }

  @Test
  void inMemoryTemplateRepositoryVersionTracking() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id1 = TemplateId.of("page1.vm");
    TemplateId id2 = TemplateId.of("page2.vm");

    // Initially absent
    assertThat(repo.freshnessToken(id1)).isEmpty();

    // 1. Initial put
    repo.put(id1, "Hello 1");
    Optional<FreshnessToken> tokenV1 = repo.freshnessToken(id1);
    assertThat(tokenV1).isPresent();

    // 2. Put second template gets distinct version
    repo.put(id2, "Hello 2");
    Optional<FreshnessToken> token2 = repo.freshnessToken(id2);
    assertThat(token2).isPresent();
    assertThat(token2.get()).isNotEqualTo(tokenV1.get());

    // 3. Update existing template increments version
    repo.put(id1, "Hello 1 updated");
    Optional<FreshnessToken> tokenV2 = repo.freshnessToken(id1);
    assertThat(tokenV2).isPresent();
    assertThat(tokenV2.get()).isNotEqualTo(tokenV1.get());

    // 4. Remove template clears token and increments global version
    repo.remove(id1);
    assertThat(repo.freshnessToken(id1)).isEmpty();

    // Re-adding the template gives a newer version than tokenV2
    repo.put(id1, "Hello 1 re-added");
    Optional<FreshnessToken> tokenV3 = repo.freshnessToken(id1);
    assertThat(tokenV3).isPresent();
    assertThat(tokenV3.get()).isNotEqualTo(tokenV2.get());

    // 5. Clear repository clears all tokens
    repo.clear();
    assertThat(repo.freshnessToken(id1)).isEmpty();
    assertThat(repo.freshnessToken(id2)).isEmpty();
  }

  @Test
  void repositoryImplementationsFreshnessCapabilityDeclaration(@TempDir Path tempDir) {
    // 1. Providers that implement TemplateFreshnessProvider
    TemplateRepository inMemory = InMemoryTemplateRepository.create();
    TemplateRepository classpath = ClasspathTemplateRepository.of("templates");
    TemplateRepository composite = CompositeTemplateRepository.of(inMemory);

    assertThat(inMemory).isInstanceOf(TemplateFreshnessProvider.class);
    assertThat(classpath).isInstanceOf(TemplateFreshnessProvider.class);
    assertThat(composite).isInstanceOf(TemplateFreshnessProvider.class);

    // 2. Repositories that do NOT implement TemplateFreshnessProvider (falling back safely)
    TemplateRepository filesystem = FilesystemTemplateRepository.of(tempDir);
    TemplateRepository customRepo = id -> Optional.empty();

    assertThat(filesystem).isNotInstanceOf(TemplateFreshnessProvider.class);
    assertThat(customRepo).isNotInstanceOf(TemplateFreshnessProvider.class);
  }

  @Test
  void compositeTemplateRepositoryMultiTierResolutionAndFallback(@TempDir Path tempDir) {
    InMemoryTemplateRepository tier1 = InMemoryTemplateRepository.create();
    InMemoryTemplateRepository tier2 = InMemoryTemplateRepository.create();

    TemplateId sharedId = TemplateId.of("shared.vm");
    TemplateId tier2OnlyId = TemplateId.of("tier2_only.vm");
    TemplateId absentId = TemplateId.of("absent.vm");

    tier1.put(sharedId, "Tier 1 Shared");
    tier2.put(sharedId, "Tier 2 Shared");
    tier2.put(tier2OnlyId, "Tier 2 Exclusive");

    CompositeTemplateRepository composite = CompositeTemplateRepository.of(tier1, tier2);

    // 1. Shared template resolves from Tier 1 (repositoryIndex = 0)
    Optional<FreshnessToken> sharedToken = composite.freshnessToken(sharedId);
    assertThat(sharedToken).isPresent();
    assertThat(sharedToken.get().toString()).contains("repositoryIndex=0");

    // 2. Exclusive template resolves from Tier 2 (repositoryIndex = 1)
    Optional<FreshnessToken> tier2Token = composite.freshnessToken(tier2OnlyId);
    assertThat(tier2Token).isPresent();
    assertThat(tier2Token.get().toString()).contains("repositoryIndex=1");

    // 3. Modifying shared template in Tier 1 updates composite token
    tier1.put(sharedId, "Tier 1 Shared Updated");
    Optional<FreshnessToken> updatedSharedToken = composite.freshnessToken(sharedId);
    assertThat(updatedSharedToken).isPresent();
    assertThat(updatedSharedToken.get()).isNotEqualTo(sharedToken.get());

    // 4. Absent template returns empty
    assertThat(composite.freshnessToken(absentId)).isEmpty();

    // 5. Fallback when any delegate does NOT implement TemplateFreshnessProvider (custom repo)
    TemplateRepository nonProviderRepo = id -> Optional.empty();
    CompositeTemplateRepository compositeWithNonProvider =
        new CompositeTemplateRepository(List.of(tier1, nonProviderRepo));
    assertThat(compositeWithNonProvider.freshnessToken(sharedId)).isEmpty();

    // 6. Fallback when delegate is FilesystemTemplateRepository
    FilesystemTemplateRepository fsRepo = FilesystemTemplateRepository.of(tempDir);
    CompositeTemplateRepository compositeWithFs = CompositeTemplateRepository.of(tier1, fsRepo);
    assertThat(compositeWithFs.freshnessToken(sharedId)).isEmpty();
  }
}
