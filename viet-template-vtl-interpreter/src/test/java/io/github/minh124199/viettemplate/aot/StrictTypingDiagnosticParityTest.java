package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrictTypingDiagnosticParityTest {

  public static class Customer {
    private final String name;

    public Customer(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public int calculate(int x) {
      return x * 2;
    }
  }

  @Test
  @DisplayName(
      "Diagnostic determinism: multiple consecutive compilations yield identical diagnostics")
  void testDiagnosticDeterminism(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path templatePath = srcDir.resolve("customer.vtl");
    Files.writeString(
        templatePath,
        "Customer: $user.nmae - $user.calculate('bad') - $unknownRoot",
        StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("customer.vtl");
    TemplateContract contract =
        TemplateContract.of(id, List.of(TemplateParameter.of("user", Customer.class)));

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contracts(Map.of(id, contract))
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    TemplateAotResult run1 = compiler.compile(request);
    TemplateAotResult run2 = compiler.compile(request);

    assertThat(run1.diagnostics()).isNotEmpty();
    assertThat(run1.diagnostics()).hasSameSizeAs(run2.diagnostics());

    for (int i = 0; i < run1.diagnostics().size(); i++) {
      TemplateAotDiagnostic d1 = run1.diagnostics().get(i);
      TemplateAotDiagnostic d2 = run2.diagnostics().get(i);
      assertThat(d1.code()).isEqualTo(d2.code());
      assertThat(d1.severity()).isEqualTo(d2.severity());
      assertThat(d1.message()).isEqualTo(d2.message());
      assertThat(d1.startLine()).isEqualTo(d2.startLine());
      assertThat(d1.startColumn()).isEqualTo(d2.startColumn());
    }
  }

  @Test
  @DisplayName(
      "Semantic and AOT parity: semantic analysis and AOT compilation report equivalent"
          + " diagnostics")
  void testSemanticAndAotDiagnosticParity(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    String content = "Hello $user.nmae and $unknown";
    Path templatePath = srcDir.resolve("test.vtl");
    Files.writeString(templatePath, content, StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("test.vtl");
    TemplateContract contract =
        TemplateContract.of(id, List.of(TemplateParameter.of("user", Customer.class, false)));

    // 1. Direct semantic analysis
    SourceText source = SourceText.from(content, id);
    VtlParseResult parseResult = VtlParser.parse(source, VtlParserOptions.DEFAULT);
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(Customer.class, Nullability.NON_NULL)));
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();
    SemanticAnalysisResult semanticResult =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);

    // 2. AOT compilation
    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contracts(Map.of(id, contract))
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();
    TemplateAotResult aotResult = compiler.compile(request);

    // Assert parity
    assertThat(aotResult.diagnostics()).hasSameSizeAs(semanticResult.diagnostics());
    for (int i = 0; i < semanticResult.diagnostics().size(); i++) {
      Diagnostic semDiag = semanticResult.diagnostics().get(i);
      TemplateAotDiagnostic aotDiag = aotResult.diagnostics().get(i);
      assertThat(aotDiag.code()).isEqualTo(semDiag.code());
      assertThat(aotDiag.severity()).isEqualTo(semDiag.severity());
      assertThat(aotDiag.message()).isEqualTo(semDiag.message());
      assertThat(aotDiag.startLine()).isGreaterThan(0);
    }
  }
}
