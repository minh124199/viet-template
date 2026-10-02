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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class OutputEscapingPolicyParityTest {

  public record UserProfile(String displayName) {}

  public record UserAccount(UserProfile profile) {}

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

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName("autoEscape(true) covers all special characters, primitives, and null across tiers")
  void escapingMatrixCoversAllSpecialCharacters(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "matrix.vm",
        "text: $text\n"
            + "ampersand: $ampersand\n"
            + "quotes: $quotes\n"
            + "alreadyEscaped: $alreadyEscaped\n"
            + "numeric: $numeric\n"
            + "boolean: $boolean\n"
            + "nullQuiet: [$!nullVal]\n"
            + "nullStandard: $nullVal");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).autoEscape(true).build();

    Template template = engine.get("matrix.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(
        RenderContext.builder()
            .put("text", "<script>alert(1)</script>")
            .put("ampersand", "Tom & Jerry")
            .put("quotes", "\"quoted\" and 'apostrophe'")
            .put("alreadyEscaped", "&lt;script&gt;")
            .put("numeric", 42)
            .put("boolean", true)
            .put("nullVal", null)
            .build(),
        out);

    assertThat(out.toString())
        .isEqualTo(
            "text: &lt;script&gt;alert(1)&lt;/script&gt;\n"
                + "ampersand: Tom &amp; Jerry\n"
                + "quotes: &quot;quoted&quot; and &#39;apostrophe&#39;\n"
                + "alreadyEscaped: &amp;lt;script&amp;gt;\n"
                + "numeric: 42\n"
                + "boolean: true\n"
                + "nullQuiet: []\n"
                + "nullStandard: $nullVal");
    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName("autoEscape(true) escapes nested properties, loops, set directive, and macros")
  void nestedPropertyAndCollectionEscaping(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "nested.vm",
        "$user.profile.displayName\n"
            + "#foreach($item in $items)$item #end\n"
            + "#set($stored = $raw)$stored\n"
            + "#macro(card $text)<div>$text</div>#end#card($raw)");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).autoEscape(true).build();

    Template template = engine.get("nested.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(
        RenderContext.builder()
            .put("user", new UserAccount(new UserProfile("<admin>")))
            .put("items", List.of("<1>", "<2>"))
            .put("raw", "<unsafe>")
            .build(),
        out);

    assertThat(out.toString())
        .isEqualTo(
            "&lt;admin&gt;\n"
                + "&lt;1&gt; &lt;2&gt; \n"
                + "&lt;unsafe&gt;\n"
                + "<div>&lt;unsafe&gt;</div>");
    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName("autoEscape(true) documents HTML attribute and URL context boundaries")
  void attributeAndUrlContextBoundaries(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("boundary.vm", "<div title=\"$val\"> <a href=\"$url\">");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).autoEscape(true).build();

    Template template = engine.get("boundary.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(
        RenderContext.builder().put("val", "foo \"bar\"").put("url", "javascript:alert(1)").build(),
        out);

    assertThat(out.toString())
        .isEqualTo("<div title=\"foo &quot;bar&quot;\"> <a href=\"javascript:alert(1)\">");
    engine.close();
  }
}
