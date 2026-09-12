package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlSecurityPolicy;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SecurityPolicyDifferentialTest {

  public static class UnannotatedService {
    public String executeSensitiveTask() {
      return "taskExecuted";
    }
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("P13: Sequential rendering with different policies cannot contaminate permissions (Permissive -> Safe -> Permissive)")
  void permissiveThenSafeThenPermissive(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId tid = TemplateId.of("sensitive.vtl");
    repo.put(tid.value(), "Result:[$svc.executeSensitiveTask()]");

    VtlInterpreterOptions permissiveOpts =
        VtlInterpreterOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .securityPolicy(VtlSecurityPolicy.standard())
            .executionTier(tier)
            .build();

    VtlInterpreterOptions safeOpts =
        VtlInterpreterOptions.builder()
            .profile(VtlProfile.VTL_SAFE)
            .securityPolicy(VtlSecurityPolicy.standard())
            .executionTier(tier)
            .build();

    RenderContext ctx = RenderContext.of("svc", new UnannotatedService());

    // 1. First render permissively: succeeds
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .interpreterOptions(permissiveOpts)
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .build()) {
      StringTemplateOutput out1 = new StringTemplateOutput();
      engine.render(tid, ctx, out1);
      assertThat(out1.toString()).contains("Result:[taskExecuted]");
    }

    // 2. Second render under VTL_SAFE: must be DENIED (no cache/linker leakage)
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .interpreterOptions(safeOpts)
            .memberAccessPolicy(MemberAccessPolicy.safe())
            .build()) {
      StringTemplateOutput out2 = new StringTemplateOutput();
      assertThatThrownBy(() -> engine.render(tid, ctx, out2))
          .isInstanceOf(TemplateSecurityException.class);
    }

    // 3. Third render permissively again: succeeds
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .interpreterOptions(permissiveOpts)
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .build()) {
      StringTemplateOutput out3 = new StringTemplateOutput();
      engine.render(tid, ctx, out3);
      assertThat(out3.toString()).contains("Result:[taskExecuted]");
    }
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("P13: Reverse sequence (Safe -> Permissive -> Safe) enforces strict policy isolation")
  void safeThenPermissiveThenSafe(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId tid = TemplateId.of("sensitive_reverse.vtl");
    repo.put(tid.value(), "Output:[$svc.executeSensitiveTask()]");

    VtlInterpreterOptions permissiveOpts =
        VtlInterpreterOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .securityPolicy(VtlSecurityPolicy.standard())
            .executionTier(tier)
            .build();

    VtlInterpreterOptions safeOpts =
        VtlInterpreterOptions.builder()
            .profile(VtlProfile.VTL_SAFE)
            .securityPolicy(VtlSecurityPolicy.standard())
            .executionTier(tier)
            .build();

    RenderContext ctx = RenderContext.of("svc", new UnannotatedService());

    // 1. First under Safe: must be DENIED
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .interpreterOptions(safeOpts)
            .memberAccessPolicy(MemberAccessPolicy.safe())
            .build()) {
      StringTemplateOutput out1 = new StringTemplateOutput();
      assertThatThrownBy(() -> engine.render(tid, ctx, out1))
          .isInstanceOf(TemplateSecurityException.class);
    }

    // 2. Second under Permissive: must succeed
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .interpreterOptions(permissiveOpts)
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .build()) {
      StringTemplateOutput out2 = new StringTemplateOutput();
      engine.render(tid, ctx, out2);
      assertThat(out2.toString()).contains("Output:[taskExecuted]");
    }

    // 3. Third under Safe: must still be DENIED
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .interpreterOptions(safeOpts)
            .memberAccessPolicy(MemberAccessPolicy.safe())
            .build()) {
      StringTemplateOutput out3 = new StringTemplateOutput();
      assertThatThrownBy(() -> engine.render(tid, ctx, out3))
          .isInstanceOf(TemplateSecurityException.class);
    }
  }
}
