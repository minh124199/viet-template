package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class OutputEscapingPolicyParityTest {

  static Stream<Arguments> tiersAndProfiles() {
    return Stream.of(
        Arguments.of(ExecutionTier.AST, VtlProfile.VTL_CORE),
        Arguments.of(ExecutionTier.IR, VtlProfile.VTL_CORE),
        Arguments.of(ExecutionTier.AOT_BYTECODE, VtlProfile.VTL_CORE),
        Arguments.of(ExecutionTier.AST, VtlProfile.VTL_MIGRATION),
        Arguments.of(ExecutionTier.IR, VtlProfile.VTL_MIGRATION),
        Arguments.of(ExecutionTier.AOT_BYTECODE, VtlProfile.VTL_MIGRATION));
  }

  @ParameterizedTest(name = "Tier: {0}, Profile: {1}")
  @MethodSource("tiersAndProfiles")
  @DisplayName("autoEscape(true) escapes raw HTML strings across all tiers and profiles")
  void autoEscapeTrueEscapesRawHtml(ExecutionTier tier, VtlProfile profile) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("page.vm", "Content: $payload");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(VtlInterpreterOptions.builder().profile(profile).build())
            .autoEscape(true)
            .build();

    Template template = engine.get("page.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(
        RenderContext.builder().put("payload", "<script>alert('xss')</script>").build(), out);

    assertThat(out.toString())
        .isEqualTo("Content: &lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}, Profile: {1}")
  @MethodSource("tiersAndProfiles")
  @DisplayName("autoEscape(true) preserves SafeHtml without escaping across all tiers and profiles")
  void autoEscapeTruePreservesSafeHtml(ExecutionTier tier, VtlProfile profile) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("page.vm", "Trusted: $payload");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(VtlInterpreterOptions.builder().profile(profile).build())
            .autoEscape(true)
            .build();

    Template template = engine.get("page.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(
        RenderContext.builder().put("payload", SafeHtml.of("<b>trusted</b>")).build(), out);

    assertThat(out.toString()).isEqualTo("Trusted: <b>trusted</b>");
    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}, Profile: {1}")
  @MethodSource("tiersAndProfiles")
  @DisplayName(
      "autoEscape(true) correctly handles mixed raw strings and SafeHtml in single template")
  void autoEscapeTrueHandlesMixedModel(ExecutionTier tier, VtlProfile profile) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("page.vm", "User: $user, Badge: $badge");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(VtlInterpreterOptions.builder().profile(profile).build())
            .autoEscape(true)
            .build();

    Template template = engine.get("page.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(
        RenderContext.builder()
            .put("user", "Tom & Jerry <cartoon>")
            .put("badge", SafeHtml.of("<span>VIP</span>"))
            .build(),
        out);

    assertThat(out.toString())
        .isEqualTo("User: Tom &amp; Jerry &lt;cartoon&gt;, Badge: <span>VIP</span>");
    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}, Profile: {1}")
  @MethodSource("tiersAndProfiles")
  @DisplayName("autoEscape(false) outputs raw strings unescaped in VTL_CORE and VTL_MIGRATION")
  void autoEscapeFalseEmitsRawStrings(ExecutionTier tier, VtlProfile profile) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("page.vm", "Content: $payload");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(VtlInterpreterOptions.builder().profile(profile).build())
            .autoEscape(false)
            .build();

    Template template = engine.get("page.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(
        RenderContext.builder().put("payload", "<script>alert('xss')</script>").build(), out);

    assertThat(out.toString()).isEqualTo("Content: <script>alert('xss')</script>");
    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName("VTL_SAFE profile defaults to HTML auto-escaping across all tiers")
  void vtlSafeDefaultsToAutoEscaping(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("page.vm", "Untrusted: $untrusted, Trusted: $trusted");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(
                VtlInterpreterOptions.builder().profile(VtlProfile.VTL_SAFE).build())
            .build();

    Template template = engine.get("page.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(
        RenderContext.builder()
            .put("untrusted", "<tag>")
            .put("trusted", SafeHtml.of("<i>ok</i>"))
            .build(),
        out);

    assertThat(out.toString()).isEqualTo("Untrusted: &lt;tag&gt;, Trusted: <i>ok</i>");
    engine.close();
  }
}
