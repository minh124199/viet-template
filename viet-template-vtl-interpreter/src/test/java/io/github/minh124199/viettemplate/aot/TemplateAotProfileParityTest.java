package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateAotProfileParityTest {

  public static class UserBean {
    private final String name;

    public UserBean(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  @Test
  @DisplayName("TemplateAotRequest builder stores and retrieves profile and semanticOptions")
  void testRequestProfileAndSemanticOptionsAccessors(@TempDir Path tempDir) {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");

    TemplateAotRequest defaultReq =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();
    assertThat(defaultReq.profile()).isEmpty();
    assertThat(defaultReq.semanticOptions()).isEmpty();

    TemplateAotRequest migrationReq =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .profile(VtlProfile.VTL_MIGRATION)
            .build();
    assertThat(migrationReq.profile()).contains(VtlProfile.VTL_MIGRATION);
    assertThat(migrationReq.semanticOptions()).isEmpty();

    VtlSemanticOptions customOptions =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_SAFE).build();
    TemplateAotRequest optionsReq =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .semanticOptions(customOptions)
            .build();
    assertThat(optionsReq.profile()).isEmpty();
    assertThat(optionsReq.semanticOptions()).contains(customOptions);
  }

  @Test
  @DisplayName("VTL_MIGRATION profile permits method invocation during AOT compilation")
  void testVtlMigrationAllowsMethodCallsDuringAotCompilation(@TempDir Path tempDir)
      throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path templateFile = srcDir.resolve("greet.vtl");
    Files.writeString(templateFile, "Hello, $user.getName()!", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("greet.vtl");
    TemplateContract contract =
        TemplateContract.of(id, List.of(TemplateParameter.of("user", UserBean.class, false)));

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contracts(Map.of(id, contract))
            .profile(VtlProfile.VTL_MIGRATION)
            .typeChecking(TypeCheckingMode.ERROR)
            .build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.compiledCount()).isEqualTo(1);
    assertThat(result.artifacts()).hasSize(1);

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate instance = clazz.getDeclaredConstructor().newInstance();

    StringTemplateOutput output = new StringTemplateOutput();
    instance.render(RenderContext.of("user", new UserBean("Alice")), output);
    assertThat(output.toString()).isEqualTo("Hello, Alice!");
  }

  @Test
  @DisplayName("VTL_CORE profile disallows arbitrary method calls in strict AOT compilation")
  void testVtlCoreDisallowsArbitraryMethodCallsInStrictAotCompilation(@TempDir Path tempDir)
      throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path templateFile = srcDir.resolve("greet.vtl");
    Files.writeString(templateFile, "Hello, $user.getName()!", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("greet.vtl");
    TemplateContract contract =
        TemplateContract.of(id, List.of(TemplateParameter.of("user", UserBean.class, false)));

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contracts(Map.of(id, contract))
            .profile(VtlProfile.VTL_CORE)
            .typeChecking(TypeCheckingMode.ERROR)
            .build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
                    && d.code().qualifiedCode().equals("VTLSEC:2401")
                    && d.message().contains("Method calls are disabled by VTL_CORE policy"));
    assertThat(result.compiledCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("VTL_SAFE profile lowers auto-escaping semantics into compiled template")
  void testVtlSafeLowersAutoEscapingIntoCompiledTemplate(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path templateFile = srcDir.resolve("safe.vtl");
    Files.writeString(templateFile, "Safe: $content", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .profile(VtlProfile.VTL_SAFE)
            .build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.compiledCount()).isEqualTo(1);

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate instance = clazz.getDeclaredConstructor().newInstance();

    StringTemplateOutput output = new StringTemplateOutput();
    instance.render(RenderContext.of("content", "<script>alert(1)</script>"), output);
    assertThat(output.toString()).isEqualTo("Safe: &lt;script&gt;alert(1)&lt;/script&gt;");
  }

  @Test
  @DisplayName("TemplateAotRequest.semanticOptions directly overrides compiler defaults")
  void testDirectSemanticOptionsHonored(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path templateFile = srcDir.resolve("override.vtl");
    Files.writeString(templateFile, "Override: $user.getName()", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("override.vtl");
    TemplateContract contract =
        TemplateContract.of(id, List.of(TemplateParameter.of("user", UserBean.class, false)));

    TemplateAotCompiler compiler = TemplateAotCompiler.create();

    // 1. Direct semantic options permitting methods despite VTL_CORE
    VtlSemanticOptions allowMethodsOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .allowArbitraryMethods(true)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    TemplateAotRequest allowReq =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contracts(Map.of(id, contract))
            .semanticOptions(allowMethodsOptions)
            .build();

    TemplateAotResult allowResult = compiler.compile(allowReq);
    assertThat(allowResult.isSuccess()).isTrue();
    assertThat(allowResult.hasErrors()).isFalse();

    // 2. Direct semantic options disallowing methods despite VTL_MIGRATION
    Path outDir2 = tempDir.resolve("out2");
    VtlSemanticOptions disallowMethodsOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_MIGRATION)
            .allowArbitraryMethods(false)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    TemplateAotRequest disallowReq =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir2)
            .contracts(Map.of(id, contract))
            .semanticOptions(disallowMethodsOptions)
            .build();

    TemplateAotResult disallowResult = compiler.compile(disallowReq);
    assertThat(disallowResult.isSuccess()).isFalse();
    assertThat(disallowResult.hasErrors()).isTrue();
    assertThat(disallowResult.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
                    && d.code().qualifiedCode().equals("VTLSEC:2401")
                    && d.message().contains("Method calls are disabled by VTL_MIGRATION policy"));
  }

  @Test
  @DisplayName("VTL_MIGRATION rejects #evaluate across interpreter and AOT tiers")
  void vtlMigrationRejectsEvaluateAcrossTiers(@TempDir Path tempDir) throws Exception {
    String templateText = "#evaluate('1 + 1')";
    SourceText source = SourceText.of("eval.vtl", templateText);
    VtlTemplate ast = VtlParser.parse(source).template();

    // 1. Interpreted AST and IR paths throw TemplateSecurityException
    for (ExecutionTier tier : List.of(ExecutionTier.AST, ExecutionTier.IR)) {
      VtlInterpreterOptions options =
          VtlInterpreterOptions.builder()
              .executionTier(tier)
              .profile(VtlProfile.VTL_MIGRATION)
              .build();
      VtlInterpreter interpreter = new VtlInterpreter(options);
      StringTemplateOutput out = new StringTemplateOutput();
      assertThatThrownBy(
              () ->
                  EngineInterpreterBridge.render(
                      interpreter, source, ast, RenderContext.empty(), out))
          .isInstanceOf(TemplateSecurityException.class)
          .hasMessageContaining("#evaluate is disabled in profile VTL_MIGRATION");
    }

    // 2. AOT compilation fails with diagnostic VTLSEC:2401
    Path srcDir = tempDir.resolve("eval_src");
    Path outDir = tempDir.resolve("eval_out");
    Files.createDirectories(srcDir);
    Path templateFile = srcDir.resolve("eval.vtl");
    Files.writeString(templateFile, templateText, StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .profile(VtlProfile.VTL_MIGRATION)
            .typeChecking(TypeCheckingMode.ERROR)
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
                    && d.code().qualifiedCode().equals("VTLSEC:2401"));
  }

  @Test
  @DisplayName("VTL_MIGRATION blocks dangerous pivot chains across interpreter and AOT tiers")
  void vtlMigrationBlocksDangerousChainsAcrossTiers(@TempDir Path tempDir) throws Exception {
    List<String> dangerousTemplates =
        List.of("$user.getName().getClass()", "$user.getClass().getName()");
    UserBean user = new UserBean("Alice");
    RenderContext ctx = RenderContext.of("user", user);

    // 1. Interpreted AST and IR paths throw TemplateSecurityException
    for (String templateText : dangerousTemplates) {
      SourceText source = SourceText.of("chain.vtl", templateText);
      VtlTemplate ast = VtlParser.parse(source).template();

      for (ExecutionTier tier : List.of(ExecutionTier.AST, ExecutionTier.IR)) {
        VtlInterpreterOptions options =
            VtlInterpreterOptions.builder()
                .executionTier(tier)
                .profile(VtlProfile.VTL_MIGRATION)
                .build();
        VtlInterpreter interpreter = new VtlInterpreter(options);
        StringTemplateOutput out = new StringTemplateOutput();
        assertThatThrownBy(() -> EngineInterpreterBridge.render(interpreter, source, ast, ctx, out))
            .isInstanceOf(TemplateSecurityException.class);
      }
    }

    // 2. AOT compilation fails with diagnostic VTLSEC:2401
    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    for (int i = 0; i < dangerousTemplates.size(); i++) {
      String templateText = dangerousTemplates.get(i);
      Path srcDir = tempDir.resolve("chain_src_" + i);
      Path outDir = tempDir.resolve("chain_out_" + i);
      Files.createDirectories(srcDir);
      Path templateFile = srcDir.resolve("chain.vtl");
      Files.writeString(templateFile, templateText, StandardCharsets.UTF_8);

      TemplateId id = TemplateId.of("chain.vtl");
      TemplateContract contract =
          TemplateContract.of(id, List.of(TemplateParameter.of("user", UserBean.class, false)));

      TemplateAotRequest request =
          TemplateAotRequest.builder()
              .sourceDirectory(srcDir)
              .outputDirectory(outDir)
              .contracts(Map.of(id, contract))
              .profile(VtlProfile.VTL_MIGRATION)
              .typeChecking(TypeCheckingMode.ERROR)
              .build();

      TemplateAotResult result = compiler.compile(request);
      assertThat(result.isSuccess()).isFalse();
      assertThat(result.hasErrors()).isTrue();
      assertThat(result.diagnostics())
          .anyMatch(
              d ->
                  d.severity() == DiagnosticSeverity.ERROR
                      && d.code().qualifiedCode().equals("VTLSEC:2401"));
    }
  }
}
