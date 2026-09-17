package io.github.minh124199.viettemplate.spring.security.aot;

import io.github.minh124199.viettemplate.spring.security.CsrfView;
import io.github.minh124199.viettemplate.spring.security.SecurityView;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * {@link RuntimeHintsRegistrar} for Viet Template Spring Security integration.
 *
 * <p>Registers reflection hints for {@link SecurityView}, {@code DefaultSecurityView}, {@link
 * CsrfView}, and {@code DefaultCsrfView} facades so dynamic template property evaluation (e.g.
 * {@code $security.name}, {@code $security.authenticated}, {@code $csrf.token}) works seamlessly in
 * GraalVM Native Images.
 */
public class VietTemplateSecurityRuntimeHints implements RuntimeHintsRegistrar {

  private static final String DEFAULT_SECURITY_VIEW_CLASS =
      "io.github.minh124199.viettemplate.spring.security.DefaultSecurityView";
  private static final String DEFAULT_CSRF_VIEW_CLASS =
      "io.github.minh124199.viettemplate.spring.security.DefaultCsrfView";

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints.reflection().registerType(SecurityView.class, MemberCategory.INVOKE_PUBLIC_METHODS);
    hints
        .reflection()
        .registerType(
            TypeReference.of(DEFAULT_SECURITY_VIEW_CLASS),
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS);
    hints.reflection().registerType(CsrfView.class, MemberCategory.INVOKE_PUBLIC_METHODS);
    hints
        .reflection()
        .registerType(
            TypeReference.of(DEFAULT_CSRF_VIEW_CLASS),
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS);
  }
}
