package io.github.minh124199.viettemplate.spring.web.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.GlobalMacroPrecedence;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VietTemplateEngineCustomizerTest {

  static final class RecordingEngineBuilder implements TemplateEngine.Builder {
    boolean hotReload;
    int maxCacheEntries;
    final List<RenderContextContributor> contributors = new ArrayList<>();

    @Override
    public TemplateEngine.Builder repository(TemplateRepository repository) {
      return this;
    }

    @Override
    public TemplateEngine.Builder rejectRuntimeCompilation(boolean reject) {
      return this;
    }

    @Override
    public TemplateEngine.Builder maxCacheEntries(int maxEntries) {
      this.maxCacheEntries = maxEntries;
      return this;
    }

    @Override
    public TemplateEngine.Builder negativeCacheTtlMillis(long ttlMillis) {
      return this;
    }

    @Override
    public TemplateEngine.Builder hotReload(boolean enabled) {
      this.hotReload = enabled;
      return this;
    }

    @Override
    public TemplateEngine.Builder watchDebounceMillis(long millis) {
      return this;
    }

    @Override
    public TemplateEngine.Builder globalMacroLibraries(List<TemplateId> libraries) {
      return this;
    }

    @Override
    public TemplateEngine.Builder globalMacroPrecedence(GlobalMacroPrecedence precedence) {
      return this;
    }

    @Override
    public TemplateEngine.Builder addContextContributor(RenderContextContributor contributor) {
      this.contributors.add(contributor);
      return this;
    }

    @Override
    public TemplateEngine.Builder contextContributors(List<RenderContextContributor> contributors) {
      this.contributors.addAll(contributors);
      return this;
    }

    @Override
    public TemplateEngine.Builder contextCollisionPolicy(ContextCollisionPolicy policy) {
      return this;
    }

    @Override
    public TemplateEngine.Builder layoutConfiguration(LayoutConfiguration configuration) {
      return this;
    }

    @Override
    public TemplateEngine.Builder memberAccessPolicy(MemberAccessPolicy policy) {
      return this;
    }

    @Override
    public TemplateEngine build() {
      return null;
    }
  }

  @Test
  @DisplayName("VietTemplateEngineCustomizer callback can customize TemplateEngine.Builder")
  void customizerCustomizesBuilder() {
    VietTemplateEngineCustomizer customizer =
        builder -> {
          builder.hotReload(true);
          builder.maxCacheEntries(1000);
        };

    RecordingEngineBuilder builder = new RecordingEngineBuilder();
    customizer.customize(builder);

    assertThat(builder.hotReload).isTrue();
    assertThat(builder.maxCacheEntries).isEqualTo(1000);
  }
}
