package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SecurityPolicyFingerprint;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import org.junit.jupiter.api.Test;

/**
 * Contract test suite verifying that security enforcement fails closed against insecure or
 * misconfigured third-party {@link MemberAccessPolicy} implementations.
 */
class SecuritySpiContractTest {

  /** Insecure custom policy attempting to permit all classes and methods. */
  static class InsecureThirdPartyPolicy implements MemberAccessPolicy {

    @Override
    public boolean isClassPermitted(Class<?> clazz) {
      return true;
    }

    @Override
    public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
      return true;
    }

    @Override
    public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
      return true;
    }

    @Override
    public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
      return true;
    }

    @Override
    public SecurityPolicyFingerprint fingerprint() {
      return SecurityPolicyFingerprint.of("insecure-third-party-policy");
    }
  }

  public static class SafeBean {
    private final String name = "Test";

    public String getName() {
      return name;
    }
  }

  @Test
  void engineFailsClosedOnClassPropertyAccessEvenWithInsecurePolicy() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("attack-property.vm", "$bean.class");

    try (TemplateEngine engine =
        TemplateEngine.builder()
            .repository(repo)
            .memberAccessPolicy(new InsecureThirdPartyPolicy())
            .build()) {

      RenderContext ctx = RenderContext.of("bean", new SafeBean());

      assertThatThrownBy(() -> engine.render("attack-property.vm", ctx))
          .isInstanceOf(TemplateSecurityException.class);
    }
  }

  @Test
  void engineFailsClosedOnGetClassMethodInvocationEvenWithInsecurePolicy() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("attack-method.vm", "$bean.getClass()");

    try (TemplateEngine engine =
        TemplateEngine.builder()
            .repository(repo)
            .memberAccessPolicy(new InsecureThirdPartyPolicy())
            .build()) {

      RenderContext ctx = RenderContext.of("bean", new SafeBean());

      assertThatThrownBy(() -> engine.render("attack-method.vm", ctx))
          .isInstanceOf(TemplateSecurityException.class);
    }
  }

  @Test
  void engineFailsClosedOnDirectCoreDeniedClassTargetEvenWithInsecurePolicy() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("attack-runtime.vm", "$runtime.freeMemory()");

    try (TemplateEngine engine =
        TemplateEngine.builder()
            .repository(repo)
            .memberAccessPolicy(new InsecureThirdPartyPolicy())
            .build()) {

      RenderContext ctx = RenderContext.of("runtime", Runtime.getRuntime());

      assertThatThrownBy(() -> engine.render("attack-runtime.vm", ctx))
          .isInstanceOf(TemplateSecurityException.class);
    }
  }
}
