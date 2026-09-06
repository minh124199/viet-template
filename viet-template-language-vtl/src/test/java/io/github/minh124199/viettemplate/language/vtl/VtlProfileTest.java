package io.github.minh124199.viettemplate.language.vtl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VtlProfileTest {

  @Test
  void verifiesDefaultProfileCapabilities() {
    VtlProfile defaultProfile = VtlFrontend.defaultProfile();
    assertThat(defaultProfile).isEqualTo(VtlProfile.VTL_CORE);
    assertThat(defaultProfile.isArbitraryMethodsAllowed()).isFalse();
    assertThat(defaultProfile.isEvaluateAllowed()).isFalse();
  }

  @Test
  void verifiesSafeProfileCapabilities() {
    VtlProfile safe = VtlProfile.VTL_SAFE;
    assertThat(safe.isSandboxEnforced()).isTrue();
    assertThat(safe.isArbitraryMethodsAllowed()).isFalse();
    assertThat(safe.isEvaluateAllowed()).isFalse();
  }

  @Test
  void verifiesDynamicProfileCapabilities() {
    VtlProfile dynamic = VtlProfile.VTL_DYNAMIC;
    assertThat(dynamic.isArbitraryMethodsAllowed()).isTrue();
    assertThat(dynamic.isEvaluateAllowed()).isTrue();
  }

  @Test
  void frontendDescriptorSupportsAllProfiles() {
    for (VtlProfile profile : VtlProfile.values()) {
      assertThat(VtlFrontend.supportsProfile(profile)).isTrue();
    }
  }
}
