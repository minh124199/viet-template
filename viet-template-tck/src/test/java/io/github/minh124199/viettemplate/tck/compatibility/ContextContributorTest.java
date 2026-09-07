package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextContributorTest {

  @Test
  void contributesVariablesAccessibleInTemplate() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("app.vm", "App: $appName (v$appVersion) User: $username");

    RenderContextContributor appInfoContributor =
        (target, request) -> {
          target.put("appName", "VietShop");
          target.put("appVersion", "2.5.0");
        };

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .addContextContributor(appInfoContributor)
            .build();

    RenderContext userModel = RenderContext.builder().put("username", "alice").build();
    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("app.vm"), userModel, out);

    assertThat(out.toString()).isEqualTo("App: VietShop (v2.5.0) User: alice");
    engine.close();
  }

  @Test
  void multipleContributorsComposeDeterministicallyWithRequestAttributes() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("dash.vm", "[$auth:$csrfToken] reqParam=$paramVal");

    RenderContextContributor authContributor =
        (target, request) -> {
          Object user = request.attributes().get("sessionUser");
          target.put("auth", user != null ? user : "anonymous");
        };

    RenderContextContributor securityContributor =
        (target, request) -> target.put("csrfToken", "token-xyz-789");

    RenderContextContributor paramsContributor =
        (target, request) -> {
          Object param = request.attributes().get("queryParam");
          target.put("paramVal", param != null ? param : "default");
        };

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .addContextContributor(authContributor)
            .addContextContributor(securityContributor)
            .addContextContributor(paramsContributor)
            .build();

    RenderRequest request =
        new RenderRequest(
            TemplateId.of("dash.vm"),
            RenderContext.empty(),
            Map.of("sessionUser", "admin", "queryParam", "search123"));

    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(request, out);

    assertThat(out.toString()).isEqualTo("[admin:token-xyz-789] reqParam=search123");
    engine.close();
  }
}
