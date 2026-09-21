package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateView;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.Ordered;

class VietTemplatePropertiesTest {

  @Test
  @DisplayName("Default properties match specified framework baselines")
  void defaultValues() {
    VietTemplateProperties properties = new VietTemplateProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getPrefix()).isEmpty();
    assertThat(properties.getSuffix()).isEmpty();
    assertThat(properties.getSuffixes()).isEmpty();
    assertThat(VietTemplateProperties.DEFAULT_SUFFIXES).isEmpty();
    assertThat(properties.getContentType()).isEqualTo(VietTemplateView.DEFAULT_CONTENT_TYPE);
    assertThat(properties.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(properties.isCache()).isTrue();
    assertThat(properties.isCheckTemplateLocation()).isTrue();
    assertThat(properties.isRuntimeCompilationEnabled()).isTrue();
    assertThat(properties.getMaxCacheEntries()).isEqualTo(500);
    assertThat(properties.getNegativeCacheTtlMillis()).isEqualTo(5000L);
    assertThat(properties.isHotReload()).isFalse();
    assertThat(properties.getWatchDebounceMillis()).isEqualTo(50L);
    assertThat(properties.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
  }

  @Test
  @DisplayName("Mutating properties updates getters correctly")
  void customValues() {
    VietTemplateProperties properties = new VietTemplateProperties();

    properties.setEnabled(false);
    properties.setPrefix("templates/");
    properties.setSuffix(".vtl");
    properties.setSuffixes(List.of(".vtl", ".vm"));
    properties.setContentType("application/json");
    properties.setCharset(StandardCharsets.ISO_8859_1);
    properties.setCache(false);
    properties.setCheckTemplateLocation(false);
    properties.setRuntimeCompilationEnabled(false);
    properties.setMaxCacheEntries(1000);
    properties.setNegativeCacheTtlMillis(2000L);
    properties.setHotReload(true);
    properties.setWatchDebounceMillis(100L);
    properties.setOrder(1);

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getPrefix()).isEqualTo("templates/");
    assertThat(properties.getSuffix()).isEqualTo(".vtl");
    assertThat(properties.getSuffixes()).containsExactly(".vtl", ".vm");
    assertThat(properties.getContentType()).isEqualTo("application/json");
    assertThat(properties.getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
    assertThat(properties.isCache()).isFalse();
    assertThat(properties.isCheckTemplateLocation()).isFalse();
    assertThat(properties.isRuntimeCompilationEnabled()).isFalse();
    assertThat(properties.getMaxCacheEntries()).isEqualTo(1000);
    assertThat(properties.getNegativeCacheTtlMillis()).isEqualTo(2000L);
    assertThat(properties.isHotReload()).isTrue();
    assertThat(properties.getWatchDebounceMillis()).isEqualTo(100L);
    assertThat(properties.getOrder()).isEqualTo(1);
  }

  @Test
  @DisplayName("Null prefix and suffix normalize to empty string")
  void nullPrefixAndSuffix() {
    VietTemplateProperties properties = new VietTemplateProperties();

    properties.setPrefix(null);
    properties.setSuffix(null);

    assertThat(properties.getPrefix()).isEmpty();
    assertThat(properties.getSuffix()).isEmpty();
  }

  @Test
  @DisplayName("Suffixes list supports null reset, deduplication, and rejects null elements")
  void suffixesListHandling() {
    VietTemplateProperties properties = new VietTemplateProperties();

    properties.setSuffixes(List.of(".vtl", ".vm", ".vtl"));
    assertThat(properties.getSuffixes()).containsExactly(".vtl", ".vm");

    properties.setSuffixes(null);
    assertThat(properties.getSuffixes()).isEmpty();

    assertThatThrownBy(() -> properties.setSuffixes(Arrays.asList(".vtl", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }

  @ParameterizedTest(name = "Invalid suffix rejected in setSuffix and setSuffixes: {0}")
  @ValueSource(
      strings = {
        "../traversal",
        "sub/dir",
        "sub\\dir",
        "proto:dir",
        "null\0byte",
        "%2efoo",
        "%2ffoo",
        "%5cfoo",
        "%00foo"
      })
  void invalidSuffixRejection(String maliciousSuffix) {
    VietTemplateProperties properties = new VietTemplateProperties();

    assertThatIllegalArgumentException().isThrownBy(() -> properties.setSuffix(maliciousSuffix));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties.setSuffixes(List.of(maliciousSuffix)));
  }

  @ParameterizedTest(name = "Safe suffix accepted: {0}")
  @ValueSource(
      strings = {
        ".tar.gz",
        "-template",
        "_template",
        "",
        ".vtl",
        ".vm",
        ".html",
        ".min.html",
        ".en.vtl",
        ".template",
        "~template"
      })
  void safeSuffixesAccepted(String safeSuffix) {
    VietTemplateProperties properties = new VietTemplateProperties();
    properties.setSuffix(safeSuffix);
    assertThat(properties.getSuffix()).isEqualTo(safeSuffix);

    properties.setSuffixes(List.of(safeSuffix));
    assertThat(properties.getSuffixes()).containsExactly(safeSuffix);
  }

  @Test
  @DisplayName(
      "determineEffectiveSuffixes returns suffixes when non-empty, otherwise single suffix")
  void determineEffectiveSuffixes() {
    VietTemplateProperties properties = new VietTemplateProperties();

    // 1. Both empty -> [DEFAULT_SUFFIX] ("")
    assertThat(properties.determineEffectiveSuffixes()).containsExactly("");

    // 2. Only suffix configured
    properties.setSuffix(".vtl");
    assertThat(properties.determineEffectiveSuffixes()).containsExactly(".vtl");

    // 3. Suffixes configured takes precedence over suffix
    properties.setSuffixes(List.of(".html", ".xhtml"));
    assertThat(properties.determineEffectiveSuffixes()).containsExactly(".html", ".xhtml");

    // 4. Suffixes cleared falls back to suffix
    properties.setSuffixes(List.of());
    assertThat(properties.determineEffectiveSuffixes()).containsExactly(".vtl");
  }
}
