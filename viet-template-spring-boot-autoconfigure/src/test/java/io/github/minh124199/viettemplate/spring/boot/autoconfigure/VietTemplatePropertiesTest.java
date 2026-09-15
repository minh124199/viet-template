package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateView;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

class VietTemplatePropertiesTest {

  @Test
  @DisplayName("Default properties match specified framework baselines")
  void defaultValues() {
    VietTemplateProperties properties = new VietTemplateProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getPrefix()).isEmpty();
    assertThat(properties.getSuffix()).isEmpty();
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
}
