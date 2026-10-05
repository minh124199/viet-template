package io.github.minh124199.viettemplate.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateValidatorTest {

  @Test
  @DisplayName("Valid templates pass validation with zero errors and populated counts")
  void testValidateValidTemplates(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("hello.vtl"), "Hello, $name! Welcome.", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("footer.vtl"), "<p>Footer copyright 2026</p>", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .encoding(StandardCharsets.UTF_8)
            .build();

    TemplateValidationResult result = validator.validate(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.success()).isTrue();
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.hasWarnings()).isFalse();
    assertThat(result.validatedCount()).isEqualTo(2);
    assertThat(result.errorCount()).isZero();
    assertThat(result.warningCount()).isZero();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Syntax errors in templates emit syntax diagnostics and fail validation")
  void testValidateSyntaxError(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("invalid.vtl"), "#if($user) Unclosed block", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .encoding(StandardCharsets.UTF_8)
            .build();

    TemplateValidationResult result = validator.validate(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.validatedCount()).isEqualTo(1);
    assertThat(result.errorCount()).isGreaterThanOrEqualTo(1);
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
                    && "SYNTAX".equalsIgnoreCase(d.code().category()));
  }

  @Test
  @DisplayName("Missing static dependency emits RESOURCE:NOT_FOUND error and fails validation")
  void testValidateMissingStaticDependency(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("main.vtl"),
        "<html>#parse('partials/header.vtl')</html>",
        StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .validateDependencies(true)
            .build();

    TemplateValidationResult result = validator.validate(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
                    && "RESOURCE".equalsIgnoreCase(d.code().category())
                    && "NOT_FOUND".equalsIgnoreCase(d.code().id())
                    && d.message().contains("partials/header.vtl"));
    Set<TemplateDependency> deps =
        result.dependencyGraph().dependenciesOf(TemplateId.of("main.vtl"));
    assertThat(deps).isNotEmpty();
  }

  @Test
  @DisplayName("Existing static dependency passes validation and populates dependency graph")
  void testValidateExistingStaticDependency(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Path partialsDir = srcDir.resolve("partials");
    Files.createDirectories(partialsDir);

    Files.writeString(
        srcDir.resolve("main.vtl"),
        "<div>#parse('partials/header.vtl')</div>",
        StandardCharsets.UTF_8);
    Files.writeString(
        partialsDir.resolve("header.vtl"), "<header>Site Header</header>", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .validateDependencies(true)
            .build();

    TemplateValidationResult result = validator.validate(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.validatedCount()).isEqualTo(2);

    Set<TemplateDependency> deps =
        result.dependencyGraph().dependenciesOf(TemplateId.of("main.vtl"));
    assertThat(deps).hasSize(1);
    TemplateDependency dep = deps.iterator().next();
    assertThat(dep.target().value()).isEqualTo("partials/header.vtl");
  }

  @Test
  @DisplayName("Contract type checking in ERROR mode fails on type inconsistencies")
  void testValidateContractTypeCheckingError(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("profile.vtl"), "Hello $user.unknownProp", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("profile.vtl.contract"), "user=java.lang.String\n", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    TemplateValidationResult result = validator.validate(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
  }

  @Test
  @DisplayName("Contract type checking in WARN mode respects failOnWarning flag")
  void testValidateContractTypeCheckingWarnAndFailOnWarning(@TempDir Path tempDir)
      throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("profile.vtl"), "Hello $user.unknownProp", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("profile.vtl.contract"), "user=java.lang.String\n", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();

    // 1. failOnWarning = false -> succeeds despite warning
    TemplateValidationRequest requestNoFail =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .typeCheckingMode(TypeCheckingMode.WARN)
            .failOnWarning(false)
            .build();

    TemplateValidationResult resultNoFail = validator.validate(requestNoFail);
    assertThat(resultNoFail.isSuccess()).isTrue();
    assertThat(resultNoFail.hasWarnings()).isTrue();
    assertThat(resultNoFail.warningCount()).isGreaterThan(0);

    // 2. failOnWarning = true -> fails because of warning
    TemplateValidationRequest requestFail =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .typeCheckingMode(TypeCheckingMode.WARN)
            .failOnWarning(true)
            .build();

    TemplateValidationResult resultFail = validator.validate(requestFail);
    assertThat(resultFail.isSuccess()).isFalse();
    assertThat(resultFail.hasWarnings()).isTrue();
  }

  @Test
  @DisplayName("Empty source directory succeeds with zero validated count")
  void testValidateEmptySourceDirectory(@TempDir Path tempDir) throws Exception {
    Path emptyDir = tempDir.resolve("empty");
    Files.createDirectories(emptyDir);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationResult result =
        validator.validate(TemplateValidationRequest.builder().sourceDirectory(emptyDir).build());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.validatedCount()).isZero();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Include and exclude patterns correctly filter validation scope")
  void testValidateIncludeExcludePatterns(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("include.vtl"), "Valid $ok", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("ignored.txt"), "Ignored", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("excluded.vtl"), "#if(broken unclosed", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .includePattern("**/*.vtl")
            .excludePattern("**/excluded.vtl")
            .build();

    TemplateValidationResult result = validator.validate(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.validatedCount()).isEqualTo(1);
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Diagnostics are deterministically ordered by templateId, startLine, and code")
  void testDeterministicDiagnosticOrdering(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);

    // Template A has missing dep and syntax error
    Files.writeString(srcDir.resolve("b-template.vtl"), "#if(broken", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("a-template.vtl"),
        "#parse('missing.vtl')\n#if(broken2",
        StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .validateDependencies(true)
            .build();

    TemplateValidationResult result = validator.validate(request);

    assertThat(result.isSuccess()).isFalse();
    List<TemplateAotDiagnostic> diags = result.diagnostics();
    assertThat(diags).isNotEmpty();

    // Verify template A diagnostics precede template B diagnostics
    for (int i = 0; i < diags.size() - 1; i++) {
      TemplateAotDiagnostic cur = diags.get(i);
      TemplateAotDiagnostic next = diags.get(i + 1);
      int cmp = cur.templateId().value().compareTo(next.templateId().value());
      assertThat(cmp).isLessThanOrEqualTo(0);
    }
  }

  @Test
  @DisplayName("Missing global macro library emits RESOURCE:NOT_FOUND diagnostic")
  void testMissingGlobalMacroLibrary(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("page.vtl"), "Hello world", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .globalMacroLibrary(TemplateId.of("macros/global.vtl"))
            .validateDependencies(true)
            .build();

    TemplateValidationResult result = validator.validate(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
                    && "RESOURCE".equalsIgnoreCase(d.code().category())
                    && "NOT_FOUND".equalsIgnoreCase(d.code().id())
                    && d.message().contains("macros/global.vtl"));
  }
}
