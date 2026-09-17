package io.github.minh124199.viettemplate.spring.security.aot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.spring.security.CsrfView;
import io.github.minh124199.viettemplate.spring.security.SecurityView;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

class VietTemplateSecurityRuntimeHintsTest {

  @Test
  void registersSecurityViewAndCsrfViewReflectionHints() {
    RuntimeHints hints = new RuntimeHints();
    VietTemplateSecurityRuntimeHints registrar = new VietTemplateSecurityRuntimeHints();

    registrar.registerHints(hints, getClass().getClassLoader());

    assertThat(hints.reflection().typeHints())
        .anyMatch(
            hint ->
                hint.getType().equals(TypeReference.of(SecurityView.class))
                    && hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_METHODS));

    assertThat(hints.reflection().typeHints())
        .anyMatch(
            hint ->
                hint.getType()
                        .equals(
                            TypeReference.of(
                                "io.github.minh124199.viettemplate.spring.security.DefaultSecurityView"))
                    && hint.getMemberCategories()
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
                    && hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_METHODS));

    assertThat(hints.reflection().typeHints())
        .anyMatch(
            hint ->
                hint.getType().equals(TypeReference.of(CsrfView.class))
                    && hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_METHODS));

    assertThat(hints.reflection().typeHints())
        .anyMatch(
            hint ->
                hint.getType()
                        .equals(
                            TypeReference.of(
                                "io.github.minh124199.viettemplate.spring.security.DefaultCsrfView"))
                    && hint.getMemberCategories()
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
                    && hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_METHODS));
  }
}
