package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.AccessLink;
import io.github.minh124199.viettemplate.runtime.linker.CallSiteRegistry;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.*;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlSecurityPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EngineAndPolicyIsolationTest {

  public record User(String name) {}

  public record SensitiveUser(String username, String secretToken) {
    public String revealToken() {
      return secretToken;
    }
  }

  @Test
  @DisplayName("Engine A and Engine B maintain isolated call site registries and lifecycles")
  void testEngineIsolationCallSiteRegistriesAndLifecycle() throws Exception {
    InMemoryTemplateRepository repoA = InMemoryTemplateRepository.create();
    TemplateId templateId = TemplateId.of("profile.vtl");
    repoA.put(templateId, "Hello $user.name");
    VtlTemplateEngine engineA =
        VtlTemplateEngine.builder().repository(repoA).executionTier(ExecutionTier.IR).build();

    InMemoryTemplateRepository repoB = InMemoryTemplateRepository.create();
    repoB.put(templateId, "Greetings $user.name");
    VtlTemplateEngine engineB =
        VtlTemplateEngine.builder().repository(repoB).executionTier(ExecutionTier.IR).build();

    assertThat(engineA.callSiteRegistry().size()).isZero();
    assertThat(engineB.callSiteRegistry().size()).isZero();

    // 1. Render on Engine A only
    RenderContext ctxA = RenderContext.builder().put("user", new User("Alice")).build();
    StringTemplateOutput outA = new StringTemplateOutput();
    engineA.render(RenderRequest.of(templateId, ctxA), outA);
    assertThat(outA.toString()).isEqualTo("Hello Alice");

    // Engine A registry is populated; Engine B registry remains untouched (0)
    assertThat(engineA.callSiteRegistry().size()).isGreaterThan(0);
    assertThat(engineB.callSiteRegistry().size()).isZero();

    // 2. Render on Engine B
    RenderContext ctxB = RenderContext.builder().put("user", new User("Bob")).build();
    StringTemplateOutput outB = new StringTemplateOutput();
    engineB.render(RenderRequest.of(templateId, ctxB), outB);
    assertThat(outB.toString()).isEqualTo("Greetings Bob");
    assertThat(engineB.callSiteRegistry().size()).isGreaterThan(0);

    // 3. Close Engine A -> Engine A registry clears, but Engine B remains untouched and operational
    int engineBSizeBefore = engineB.callSiteRegistry().size();
    engineA.close();
    assertThat(engineA.callSiteRegistry().size()).isZero();
    assertThat(engineB.callSiteRegistry().size()).isEqualTo(engineBSizeBefore);

    StringTemplateOutput outB2 = new StringTemplateOutput();
    engineB.render(RenderRequest.of(templateId, ctxB), outB2);
    assertThat(outB2.toString()).isEqualTo("Greetings Bob");

    engineB.close();
    assertThat(engineB.callSiteRegistry().size()).isZero();
  }

  @Test
  @DisplayName("Security Policy isolation prevents cache leakage across engines and policies")
  void testSecurityPolicyIsolationAcrossEnginesAndLinkers() throws Exception {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId templateId = TemplateId.of("sensitive.vtl");
    repo.put(
        templateId, "User: $user.username, Token: $user.secretToken, Call: $user.revealToken()");

    MemberAccessPolicy standardPolicy = MemberAccessPolicy.standard();
    MemberAccessPolicy restrictedPolicy =
        MemberAccessPolicy.builder().denyMethod("secretToken").denyMethod("revealToken").build();

    VtlTemplateEngine engine1 =
        VtlTemplateEngine.builder()
            .repository(repo)
            .memberAccessPolicy(standardPolicy)
            .executionTier(ExecutionTier.IR)
            .build();

    VtlTemplateEngine engine2 =
        VtlTemplateEngine.builder()
            .repository(repo)
            .memberAccessPolicy(restrictedPolicy)
            .executionTier(ExecutionTier.IR)
            .build();

    SensitiveUser user = new SensitiveUser("Alice", "SECRET-12345");
    RenderContext ctx = RenderContext.builder().put("user", user).build();

    // 1. Engine 1 allows member access and caches links
    StringTemplateOutput out1 = new StringTemplateOutput();
    engine1.render(RenderRequest.of(templateId, ctx), out1);
    assertThat(out1.toString()).isEqualTo("User: Alice, Token: SECRET-12345, Call: SECRET-12345");
    assertThat(engine1.callSiteRegistry().size()).isGreaterThan(0);

    // 2. Engine 2 with restricted policy denies access; caching in Engine 1 does NOT leak
    StringTemplateOutput out2 = new StringTemplateOutput();
    assertThatThrownBy(() -> engine2.render(RenderRequest.of(templateId, ctx), out2))
        .isInstanceOf(TemplateSecurityException.class);

    // 3. Engine 1 remains operational and allowed
    StringTemplateOutput out1Again = new StringTemplateOutput();
    engine1.render(RenderRequest.of(templateId, ctx), out1Again);
    assertThat(out1Again.toString())
        .isEqualTo("User: Alice, Token: SECRET-12345, Call: SECRET-12345");

    engine1.close();
    engine2.close();
  }

  @Test
  @DisplayName("DynamicLinker and CallSiteRegistry partition entries by policyId")
  void testDynamicLinkerAndCallSiteRegistryPartitionByPolicyId() {
    MemberAccessPolicy standardPolicy = MemberAccessPolicy.standard();
    MemberAccessPolicy restrictedPolicy =
        MemberAccessPolicy.builder().denyMethod("secretToken").denyMethod("revealToken").build();

    LinkerAccessPolicy p1 = VtlSecurityPolicy.of(standardPolicy).toLinkerAccessPolicy();
    LinkerAccessPolicy p2 = VtlSecurityPolicy.of(restrictedPolicy).toLinkerAccessPolicy();

    assertThat(p1.policyId()).isNotEqualTo(p2.policyId());

    // 1. DynamicLinker ClassLinkTable partitioning by policyId
    DynamicLinker linker = new DynamicLinker();
    MemberKey propKey = MemberKey.propertyGet("secretToken");
    MemberKey methodKey = MemberKey.methodCall("revealToken", 0);

    AccessLink p1PropLink = linker.link(SensitiveUser.class, propKey, p1);
    assertThat(p1PropLink.isOk()).isTrue();

    AccessLink p2PropLink = linker.link(SensitiveUser.class, propKey, p2);
    assertThat(p2PropLink.isDenied()).isTrue();

    // Verify p1 link remains cached as OK and p2 link remains cached as DENIED
    assertThat(linker.link(SensitiveUser.class, propKey, p1).isOk()).isTrue();
    assertThat(linker.link(SensitiveUser.class, propKey, p2).isDenied()).isTrue();

    AccessLink p1MethodLink = linker.link(SensitiveUser.class, methodKey, p1);
    assertThat(p1MethodLink.isOk()).isTrue();

    AccessLink p2MethodLink = linker.link(SensitiveUser.class, methodKey, p2);
    assertThat(p2MethodLink.isDenied()).isTrue();

    // 2. CallSiteRegistry partitioning by CallSiteKey(siteId, memberKey, policyId)
    CallSiteRegistry registry = new CallSiteRegistry(100, linker);
    DynamicCallSite cs1 = registry.getOrCreate(1, propKey, p1);
    DynamicCallSite cs2 = registry.getOrCreate(1, propKey, p2);

    assertThat(cs1).isNotSameAs(cs2);
    assertThat(cs1.policy().policyId()).isEqualTo(p1.policyId());
    assertThat(cs2.policy().policyId()).isEqualTo(p2.policyId());
    assertThat(registry.size()).isEqualTo(2);
  }
}
