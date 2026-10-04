package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SecurityPolicyFingerprint;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.InterpreterDiagnosticCodes;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlSecurityPolicy;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RenderSecurityParityDifferentialTest {

  public static final class DeniedFixture {
    @Override
    public String toString() {
      return "<b>denied & fixture</b>";
    }
  }

  static final class DenyFixtureMemberAccessPolicy implements MemberAccessPolicy {
    private final MemberAccessPolicy delegate = MemberAccessPolicy.standard();

    @Override
    public boolean isClassPermitted(Class<?> clazz) {
      if (clazz == DeniedFixture.class) {
        return false;
      }
      return delegate.isClassPermitted(clazz);
    }

    @Override
    public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
      return delegate.isMethodPermitted(receiverClass, methodName, arity);
    }

    @Override
    public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
      return delegate.isPropertyPermitted(receiverClass, propertyName);
    }

    @Override
    public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
      return delegate.isFieldPermitted(receiverClass, fieldName);
    }

    @Override
    public SecurityPolicyFingerprint fingerprint() {
      return delegate.fingerprint();
    }
  }

  static Stream<Arguments> parityMatrix() {
    return Stream.of(
        // VTL_CORE + RAW -> allows rendering fixture toString()
        Arguments.of(
            ExecutionTier.AST, VtlProfile.VTL_CORE, false, "<b>denied & fixture</b>", false),
        Arguments.of(
            ExecutionTier.IR, VtlProfile.VTL_CORE, false, "<b>denied & fixture</b>", false),
        Arguments.of(
            ExecutionTier.AOT_BYTECODE,
            VtlProfile.VTL_CORE,
            false,
            "<b>denied & fixture</b>",
            false),

        // VTL_CORE + HTML_TEXT -> allows rendering fixture and HTML-escapes its string
        // representation
        Arguments.of(
            ExecutionTier.AST,
            VtlProfile.VTL_CORE,
            true,
            "&lt;b&gt;denied &amp; fixture&lt;/b&gt;",
            false),
        Arguments.of(
            ExecutionTier.IR,
            VtlProfile.VTL_CORE,
            true,
            "&lt;b&gt;denied &amp; fixture&lt;/b&gt;",
            false),
        Arguments.of(
            ExecutionTier.AOT_BYTECODE,
            VtlProfile.VTL_CORE,
            true,
            "&lt;b&gt;denied &amp; fixture&lt;/b&gt;",
            false),

        // VTL_SAFE + RAW -> throws TemplateSecurityException
        Arguments.of(ExecutionTier.AST, VtlProfile.VTL_SAFE, false, null, true),
        Arguments.of(ExecutionTier.IR, VtlProfile.VTL_SAFE, false, null, true),
        Arguments.of(ExecutionTier.AOT_BYTECODE, VtlProfile.VTL_SAFE, false, null, true),

        // VTL_SAFE + HTML_TEXT -> throws TemplateSecurityException
        Arguments.of(ExecutionTier.AST, VtlProfile.VTL_SAFE, true, null, true),
        Arguments.of(ExecutionTier.IR, VtlProfile.VTL_SAFE, true, null, true),
        Arguments.of(ExecutionTier.AOT_BYTECODE, VtlProfile.VTL_SAFE, true, null, true));
  }

  @ParameterizedTest(name = "Tier: {0}, Profile: {1}, AutoEscape: {2}")
  @MethodSource("parityMatrix")
  @DisplayName(
      "SEC-02/ARCH-01: Differential parity across all tiers for render security and auto-escaping")
  void differentialParityAcrossTiers(
      ExecutionTier tier,
      VtlProfile profile,
      boolean autoEscape,
      String expectedOutput,
      boolean expectSecurityException)
      throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("page.vm", "$fixture");

    VtlSecurityPolicy policy = VtlSecurityPolicy.of(new DenyFixtureMemberAccessPolicy());

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(
                VtlInterpreterOptions.builder().profile(profile).securityPolicy(policy).build())
            .autoEscape(autoEscape)
            .build()) {

      Template template = engine.get("page.vm");
      RenderContext context = RenderContext.of(Map.of("fixture", new DeniedFixture()));
      StringTemplateOutput out = new StringTemplateOutput();

      if (expectSecurityException) {
        assertThatThrownBy(() -> template.render(context, out))
            .isInstanceOf(TemplateSecurityException.class)
            .hasMessageContaining(
                "Rendering class "
                    + DeniedFixture.class.getName()
                    + " is denied by security policy")
            .satisfies(
                e -> {
                  TemplateSecurityException tse = (TemplateSecurityException) e;
                  assertThat(tse.code()).contains(InterpreterDiagnosticCodes.SECURITY_VIOLATION);
                });
      } else {
        template.render(context, out);
        assertThat(out.toString()).isEqualTo(expectedOutput);
      }
    }
  }
}
