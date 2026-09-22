package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateSuffixConfigurationTest {

  @Test
  @DisplayName("of(primary) creates valid single-suffix configuration")
  void createsSingleSuffixConfiguration() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm");

    assertThat(config.primarySuffix()).isEqualTo(".vm");
    assertThat(config.additionalSuffixes()).isEmpty();
    assertThat(config.effectiveSuffixes()).containsExactly(".vm");
  }

  @Test
  @DisplayName("of(primary, additional...) preserves order and removes duplicates")
  void createsMultipleSuffixesWithDeduplication() {
    TemplateSuffixConfiguration config =
        TemplateSuffixConfiguration.of(".vm", ".vtl", ".vm", ".html", ".vtl");

    assertThat(config.primarySuffix()).isEqualTo(".vm");
    assertThat(config.additionalSuffixes()).containsExactly(".vtl", ".html");
    assertThat(config.effectiveSuffixes()).containsExactly(".vm", ".vtl", ".html");
  }

  @Test
  @DisplayName("of(primary, List<String>) preserves order and deduplicates")
  void createsWithListFactory() {
    TemplateSuffixConfiguration config =
        TemplateSuffixConfiguration.of(".vm", List.of(".vtl", ".html", ".vtl"));

    assertThat(config.primarySuffix()).isEqualTo(".vm");
    assertThat(config.additionalSuffixes()).containsExactly(".vtl", ".html");
    assertThat(config.effectiveSuffixes()).containsExactly(".vm", ".vtl", ".html");
  }

  @Test
  @DisplayName("Returned lists are immutable")
  void returnedListsAreImmutable() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm", ".vtl");

    assertThatThrownBy(() -> config.additionalSuffixes().add(".html"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> config.effectiveSuffixes().add(".html"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "views/page.vm",
        "..",
        "../vm",
        ".vm/test",
        ".vm\\test",
        ".vm:test",
        ".vm%2e",
        ".vm\0"
      })
  @DisplayName("Rejects invalid primary suffix")
  void rejectsInvalidPrimarySuffix(String invalidSuffix) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TemplateSuffixConfiguration.of(invalidSuffix));
  }

  @Test
  @DisplayName("Rejects null primary suffix")
  void rejectsNullPrimarySuffix() {
    assertThatIllegalArgumentException().isThrownBy(() -> TemplateSuffixConfiguration.of(null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/page.vtl",
        "../vtl",
        ".vtl/test",
        ".vtl\\test",
        ".vtl:test",
        ".vtl%00",
        ".vtl\0"
      })
  @DisplayName("Rejects invalid additional suffix")
  void rejectsInvalidAdditionalSuffix(String invalidSuffix) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TemplateSuffixConfiguration.of(".vm", invalidSuffix));
  }

  @Test
  @DisplayName("Rejects null in additional suffixes array or list")
  void rejectsNullInAdditionalSuffixes() {
    assertThatNullPointerException()
        .isThrownBy(() -> TemplateSuffixConfiguration.of(".vm", (String[]) null));
    assertThatNullPointerException()
        .isThrownBy(() -> TemplateSuffixConfiguration.of(".vm", (List<String>) null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TemplateSuffixConfiguration.of(".vm", (String) null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> {
              List<String> listWithNull = java.util.Arrays.asList(".vtl", null);
              TemplateSuffixConfiguration.of(".vm", listWithNull);
            });
  }

  @Test
  @DisplayName("findMatchingSuffix matches suffix from effective suffixes")
  void findMatchingSuffixMatchesCorrectly() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm", ".vtl", ".html");

    assertThat(config.findMatchingSuffix("home.vm")).contains(".vm");
    assertThat(config.findMatchingSuffix("views/dashboard.vtl")).contains(".vtl");
    assertThat(config.findMatchingSuffix("/WEB-INF/templates/layout.html")).contains(".html");
    assertThat(config.findMatchingSuffix("index.php")).isEqualTo(Optional.empty());
    assertThat(config.findMatchingSuffix("vm")).isEqualTo(Optional.empty());

    assertThatNullPointerException().isThrownBy(() -> config.findMatchingSuffix(null));
  }

  @Test
  @DisplayName("findMatchingSuffix prefers longest matching suffix if overlap occurs")
  void findMatchingSuffixPrefersLongestMatch() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm.html", ".html");

    assertThat(config.findMatchingSuffix("index.vm.html")).contains(".vm.html");
    assertThat(config.findMatchingSuffix("index.html")).contains(".html");
  }

  @Test
  @DisplayName("resolveCandidates(templateName) with existing suffix returns single candidate")
  void resolveCandidatesSingleWhenSuffixPresent() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm", ".vtl");

    List<TemplateId> candidates = config.resolveCandidates("views/home.vtl");
    assertThat(candidates).containsExactly(TemplateId.of("views/home.vtl"));
  }

  @Test
  @DisplayName(
      "resolveCandidates(templateName) without suffix returns candidates for all effective"
          + " suffixes")
  void resolveCandidatesMultipleWhenSuffixAbsent() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm", ".vtl", ".html");

    List<TemplateId> candidates = config.resolveCandidates("views/home");
    assertThat(candidates)
        .containsExactly(
            TemplateId.of("views/home.vm"),
            TemplateId.of("views/home.vtl"),
            TemplateId.of("views/home.html"));
  }

  @Test
  @DisplayName("resolveCandidates(prefix, viewName) resolves with prefix prepended")
  void resolveCandidatesWithPrefix() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm", ".vtl");

    List<TemplateId> candidates = config.resolveCandidates("/templates/", "user/profile");
    assertThat(candidates)
        .containsExactly(
            TemplateId.of("templates/user/profile.vm"),
            TemplateId.of("templates/user/profile.vtl"));
  }

  @Test
  @DisplayName("resolveCandidates(prefix, viewName) handles already suffixed viewName with prefix")
  void resolveCandidatesWithPrefixAndSuffix() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm", ".vtl");

    List<TemplateId> candidates = config.resolveCandidates("/templates", "user/profile.vtl");
    assertThat(candidates).containsExactly(TemplateId.of("templates/user/profile.vtl"));
  }

  @Test
  @DisplayName("resolveCandidates rejects null or path traversal attempts")
  void resolveCandidatesRejectsSecurityViolations() {
    TemplateSuffixConfiguration config = TemplateSuffixConfiguration.of(".vm");

    assertThatNullPointerException().isThrownBy(() -> config.resolveCandidates(null));
    assertThatNullPointerException().isThrownBy(() -> config.resolveCandidates(null, "view"));
    assertThatNullPointerException().isThrownBy(() -> config.resolveCandidates("prefix", null));

    assertThatIllegalArgumentException().isThrownBy(() -> config.resolveCandidates("../escape"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.resolveCandidates("/templates", "../escape"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.resolveCandidates("../secret", "view"));
    assertThatIllegalArgumentException().isThrownBy(() -> config.resolveCandidates("view\0"));
  }

  @Test
  @DisplayName("Equals and hashCode contract")
  void equalsAndHashCodeContract() {
    TemplateSuffixConfiguration config1 = TemplateSuffixConfiguration.of(".vm", ".vtl");
    TemplateSuffixConfiguration config2 = TemplateSuffixConfiguration.of(".vm", List.of(".vtl"));
    TemplateSuffixConfiguration config3 = TemplateSuffixConfiguration.of(".vm", ".html");

    assertThat(config1).isEqualTo(config2);
    assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    assertThat(config1).isNotEqualTo(config3);
    assertThat(config1.toString()).contains(".vm").contains(".vtl");
  }
}
