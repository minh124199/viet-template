package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.ContextCollisionException;
import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.ValueOrigin;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ContextCollisionTest {

  @Test
  void modelWinsPolicyFavorsUserModelVariables() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("profile.vm", "Role: $role");

    RenderContextContributor contributor = (target, request) -> target.put("role", "guest");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .addContextContributor(contributor)
            .contextCollisionPolicy(ContextCollisionPolicy.MODEL_WINS)
            .build();

    RenderContext model = RenderContext.builder().put("role", "admin").build();
    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("profile.vm"), model, out);

    assertThat(out.toString()).isEqualTo("Role: admin");
    engine.close();
  }

  @Test
  void contributorWinsPolicyFavorsContributorVariables() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("profile.vm", "Role: $role");

    RenderContextContributor contributor =
        (target, request) -> target.put("role", "enforced_guest");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .addContextContributor(contributor)
            .contextCollisionPolicy(ContextCollisionPolicy.CONTRIBUTOR_WINS)
            .build();

    RenderContext model = RenderContext.builder().put("role", "user_override").build();
    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("profile.vm"), model, out);

    assertThat(out.toString()).isEqualTo("Role: enforced_guest");
    engine.close();
  }

  @Test
  void errorOnCollisionPolicyFailsFastWithOriginDetails() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("profile.vm", "Role: $role");

    RenderContextContributor contributor =
        (target, request) -> target.put("role", "contributor_val");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .addContextContributor(contributor)
            .contextCollisionPolicy(ContextCollisionPolicy.ERROR_ON_COLLISION)
            .build();

    RenderContext model = RenderContext.builder().put("role", "model_val").build();
    StringTemplateOutput out = new StringTemplateOutput();

    assertThatThrownBy(() -> engine.render(TemplateId.of("profile.vm"), model, out))
        .isInstanceOf(ContextCollisionException.class)
        .satisfies(
            ex -> {
              ContextCollisionException cce = (ContextCollisionException) ex;
              assertThat(cce.variableName()).isEqualTo("role");
              assertThat(cce.existingOrigin()).isEqualTo(ValueOrigin.MODEL);
              assertThat(cce.collidingOrigin()).isEqualTo(ValueOrigin.CONTRIBUTOR);
            });

    engine.close();
  }

  @Test
  void protectsEngineReservedVariablesFromContributorOverwrite() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("view.vm", "Content");

    RenderContextContributor maliciousContributor =
        (target, request) -> target.put("screen_content", "hijacked");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .addContextContributor(maliciousContributor)
            .layoutConfiguration(
                LayoutConfiguration.builder().screenContentKey("screen_content").build())
            .build();

    StringTemplateOutput out = new StringTemplateOutput();

    assertThatThrownBy(() -> engine.render(TemplateId.of("view.vm"), RenderContext.empty(), out))
        .isInstanceOf(ContextCollisionException.class)
        .satisfies(
            ex -> {
              ContextCollisionException cce = (ContextCollisionException) ex;
              assertThat(cce.variableName()).isEqualTo("screen_content");
              assertThat(cce.existingOrigin()).isEqualTo(ValueOrigin.ENGINE_INTERNAL);
              assertThat(cce.collidingOrigin()).isEqualTo(ValueOrigin.CONTRIBUTOR);
            });

    engine.close();
  }
}
