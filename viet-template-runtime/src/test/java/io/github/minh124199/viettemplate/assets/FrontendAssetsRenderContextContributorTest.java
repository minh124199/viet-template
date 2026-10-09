package io.github.minh124199.viettemplate.assets;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.ContributorContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.assets.vite.ViteAssetResolver;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrontendAssetsRenderContextContributorTest {

  static class SimpleContributorContext implements ContributorContext {
    final Map<String, Object> storage = new HashMap<>();

    @Override
    public ContributorContext put(String key, Object value) {
      storage.put(key, value);
      return this;
    }

    @Override
    public ContributorContext putAll(Map<String, ?> entries) {
      storage.putAll(entries);
      return this;
    }

    @Override
    public boolean contains(String key) {
      return storage.containsKey(key);
    }

    @Override
    public Object get(String key) {
      return storage.get(key);
    }
  }

  @Test
  void contributesAssetsAndClientData() {
    ViteAssetResolver resolver = ViteAssetResolver.builder().manifest("{}").build();
    FrontendAssets assets = new FrontendAssets(resolver);
    ClientData clientData = new ClientData();

    FrontendAssetsRenderContextContributor contributor =
        new FrontendAssetsRenderContextContributor(assets, clientData);

    SimpleContributorContext context = new SimpleContributorContext();
    RenderRequest request =
        RenderRequest.of(
            TemplateId.of("test.vtl"), io.github.minh124199.viettemplate.api.RenderContext.empty());

    contributor.contribute(context, request);

    assertThat(context.get("assets")).isSameAs(assets);
    assertThat(context.get("clientData")).isSameAs(clientData);
  }
}
