package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompositeTemplateRepository;
import io.github.minh124199.viettemplate.api.FilesystemTemplateRepository;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.runtime.WriterTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Executable test fixture verifying the migration patterns and code snippets documented in {@code
 * docs/migration/velocity-migration-guide.md} and {@code docs/migration/velocity-differences.md}.
 */
class VelocityMigrationPatternsTest {

  @Test
  @DisplayName("Core API: Basic engine setup, context creation, and template rendering to String")
  void testCoreEngineSetupAndStringRendering() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("greeting.vm", "Hello, $name! Welcome to $system.");

    TemplateEngine engine = TemplateEngine.builder().repository(repo).build();

    RenderContext context =
        RenderContext.builder().put("name", "World").put("system", "Viet Template").build();

    String result = engine.render("greeting.vm", context);
    assertThat(result).isEqualTo("Hello, World! Welcome to Viet Template.");
  }

  @Test
  @DisplayName("Core API: Streaming output via WriterTemplateOutput")
  void testWriterStreamingOutput() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("report.vm", "Items: #foreach($item in $items)[$item]#end");

    TemplateEngine engine = TemplateEngine.builder().repository(repo).build();
    Template template = engine.get("report.vm");

    RenderContext context = RenderContext.builder().put("items", List.of("A", "B", "C")).build();

    StringWriter writer = new StringWriter();
    template.render(context, new WriterTemplateOutput(writer));

    assertThat(writer.toString()).isEqualTo("Items: [A][B][C]");
  }

  @Test
  @DisplayName("Core API: Zero-allocation binary streaming via Utf8OutputStreamTemplateOutput")
  void testBinaryStreamingOutput() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("export.vm", "Row count: $count");

    TemplateEngine engine = TemplateEngine.builder().repository(repo).build();
    Template template = engine.get("export.vm");

    RenderContext context = RenderContext.builder().put("count", 42).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(baos)) {
      template.render(context, output);
    }

    assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("Row count: 42");
  }

  @Test
  @DisplayName("Repositories: Filesystem, Classpath, and Composite resolution")
  void testRepositoryPatterns(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("fs-template.vm");
    Files.writeString(templateFile, "Filesystem Content: $msg");

    FilesystemTemplateRepository fsRepo = FilesystemTemplateRepository.of(tempDir);
    InMemoryTemplateRepository memRepo = InMemoryTemplateRepository.create();
    memRepo.put("mem-template.vm", "Memory Content: $msg");

    CompositeTemplateRepository compositeRepo = CompositeTemplateRepository.of(fsRepo, memRepo);

    TemplateEngine engine = TemplateEngine.builder().repository(compositeRepo).build();
    RenderContext ctx = RenderContext.builder().put("msg", "OK").build();

    assertThat(engine.render("fs-template.vm", ctx)).isEqualTo("Filesystem Content: OK");
    assertThat(engine.render("mem-template.vm", ctx)).isEqualTo("Memory Content: OK");
  }

  @Test
  @DisplayName("Layouts: Two-stage layout rendering with default, override, and bypass")
  void testTwoStageLayoutRendering() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "layouts/default.vm",
        "<header>App</header><main>$screen_content</main><footer>2026</footer>");
    repo.put("layouts/custom.vm", "<dialog>$screen_content</dialog>");
    repo.put("views/dashboard.vm", "<h1>Dashboard: $user</h1>");
    repo.put("views/modal.vm", "#set($layout = 'layouts/custom.vm')<p>Modal Body</p>");
    repo.put("views/fragment.vm", "#set($layout = 'none')<span>Raw Fragment</span>");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder().defaultLayout(TemplateId.of("layouts/default.vm")).build();

    TemplateEngine engine =
        TemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    RenderContext dashboardCtx = RenderContext.builder().put("user", "Alice").build();
    String dashboardHtml = engine.render("views/dashboard.vm", dashboardCtx);
    assertThat(dashboardHtml)
        .isEqualTo(
            "<header>App</header><main><h1>Dashboard: Alice</h1></main><footer>2026</footer>");

    String modalHtml = engine.render("views/modal.vm", RenderContext.empty());
    assertThat(modalHtml).isEqualTo("<dialog><p>Modal Body</p></dialog>");

    String fragmentHtml = engine.render("views/fragment.vm", RenderContext.empty());
    assertThat(fragmentHtml).isEqualTo("<span>Raw Fragment</span>");
  }

  public static record DateFormatterTool() {
    public String formatYear(int year) {
      return "AD " + year;
    }
  }

  public static record UserAccount(String username) {}

  @Test
  @DisplayName("Tools & Helpers: Migrating custom tools via RenderContextContributor")
  void testCustomToolMigrationViaContributor() throws IOException {
    RenderContextContributor helperContributor =
        (contributorContext, request) -> {
          contributorContext.put("dateTool", new DateFormatterTool());
          contributorContext.put("currentYear", 2026);
        };

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("tools.vm", "Year: $dateTool.formatYear($currentYear)");

    TemplateEngine engine =
        TemplateEngine.builder().repository(repo).addContextContributor(helperContributor).build();

    String output = engine.render("tools.vm", RenderContext.empty());
    assertThat(output).isEqualTo("Year: AD 2026");
  }

  @Test
  @DisplayName("References: Quiet vs undefined vs null references and fallback evaluation")
  void testReferenceEvaluationSemantics() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "ref-test.vm",
        "MissingRaw:[$missing]|MissingQuiet:[$!missing]|NullQuiet:[$!nullVal]|Fallback:[${missing|'Guest'}]");

    TemplateEngine engine = TemplateEngine.builder().repository(repo).build();
    RenderContext ctx = RenderContext.builder().put("nullVal", null).build();

    String output = engine.render("ref-test.vm", ctx);
    assertThat(output)
        .isEqualTo("MissingRaw:[$missing]|MissingQuiet:[]|NullQuiet:[]|Fallback:[Guest]");
  }

  @Test
  @DisplayName("DIFF-001: Fail-fast division by zero throws TemplateRenderException")
  void testDiff001FailFastDivisionByZero() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("divzero.vm", "#set($val = 100 / 0)");

    TemplateEngine engine = TemplateEngine.builder().repository(repo).build();

    assertThatThrownBy(() -> engine.render("divzero.vm", RenderContext.empty()))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("Division by zero");
  }

  @Test
  @DisplayName("DIFF-002: Default MemberAccessPolicy blocks reflection and getClass() access")
  void testDiff002ReflectionAccessDeniedByDefault() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("reflect.vm", "Class: [$user.getClass()]");

    TemplateEngine engine =
        TemplateEngine.builder()
            .repository(repo)
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .build();

    RenderContext ctx = RenderContext.builder().put("user", new UserAccount("Alice")).build();

    // In standard defense-in-depth policy, getClass() is denied by policy and throws
    // TemplateSecurityException
    assertThatThrownBy(() -> engine.render("reflect.vm", ctx))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining("getClass");
  }

  @Test
  @DisplayName("DIFF-003: #set null-RHS legacy 1.x preservation vs modern 2.x null assignment")
  void testDiff003SetNullRhsSemantics() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("set-test.vm", "#set($val = $missing)$val");

    RenderContext ctx = RenderContext.builder().put("val", "initial").build();

    // Default Velocity 2.x mode: setNullAllowed = true (overwrites with null/undefined, rendering
    // "$val" in standard mode)
    TemplateEngine defaultEngine = TemplateEngine.builder().repository(repo).build();
    String defaultResult = defaultEngine.render("set-test.vm", ctx);
    assertThat(defaultResult.trim()).isEqualTo("$val");

    // Legacy Velocity 1.x mode: setNullAllowed = false (preserves existing variable)
    VtlInterpreterOptions legacyOptions =
        VtlInterpreterOptions.builder().setNullAllowed(false).build();
    VtlTemplateEngine legacyEngine =
        VtlTemplateEngine.builder().repository(repo).interpreterOptions(legacyOptions).build();

    String legacyResult = legacyEngine.render("set-test.vm", ctx);
    assertThat(legacyResult.trim()).isEqualTo("initial");
  }

  @Test
  @DisplayName("Global Macros: Global macro library configuration and evaluation")
  void testGlobalMacroLibraries() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("macros/global.vm", "#macro(badge $label)<span class='badge'>$label</span>#end");
    repo.put("page.vm", "#badge('Active')");

    TemplateEngine engine =
        TemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(TemplateId.of("macros/global.vm")))
            .build();

    String output = engine.render("page.vm", RenderContext.empty());
    assertThat(output).isEqualTo("<span class='badge'>Active</span>");
  }
}
