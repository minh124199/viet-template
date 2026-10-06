package io.github.minh124199.viettemplate.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  @Test
  @DisplayName("Request configuration is defensive and immutable against caller mutation")
  void testRequestImmutability(@TempDir Path tempDir) {
    List<Path> sources = new ArrayList<>(List.of(tempDir.resolve("src1")));
    List<String> includes = new ArrayList<>(List.of("**/*.vtl"));
    List<String> excludes = new ArrayList<>(List.of("**/ex.vtl"));
    Map<TemplateId, TemplateContract> contracts = new LinkedHashMap<>();
    TemplateId m1 = TemplateId.of("m1.vtl");
    List<TemplateId> macros = new ArrayList<>(List.of(m1));

    TemplateValidationRequest req =
        TemplateValidationRequest.builder()
            .sourceDirectories(sources)
            .includePatterns(includes)
            .excludePatterns(excludes)
            .contracts(contracts)
            .globalMacroLibraries(macros)
            .build();

    // Mutate source collections
    sources.add(tempDir.resolve("src2"));
    includes.add("**/*.vm");
    excludes.add("**/other.vtl");
    contracts.put(TemplateId.of("c.vtl"), TemplateContract.builder(TemplateId.of("c.vtl")).build());
    macros.add(TemplateId.of("m2.vtl"));

    assertThat(req.sourceDirectories()).containsExactly(tempDir.resolve("src1"));
    assertThat(req.includePatterns()).containsExactly("**/*.vtl");
    assertThat(req.excludePatterns()).containsExactly("**/ex.vtl");
    assertThat(req.contracts()).isEmpty();
    assertThat(req.globalMacroLibraries()).containsExactly(m1);

    // Returned collections are unmodifiable
    assertThatThrownBy(() -> req.sourceDirectories().add(tempDir.resolve("src3")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> req.includePatterns().add("**/*.html"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> req.excludePatterns().add("**/*.bak"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> req.contracts().put(m1, null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> req.globalMacroLibraries().add(m1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Result diagnostic collections are defensively copied and unmodifiable")
  void testResultImmutability(@TempDir Path tempDir) {
    TemplateValidationResult result =
        TemplateValidator.create()
            .validate(TemplateValidationRequest.builder().sourceDirectory(tempDir).build());

    assertThatThrownBy(() -> result.diagnostics().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Validation results are deterministic across repeated runs on the same input")
  void testValidationDeterminism(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir.resolve("sub"));
    Files.writeString(
        srcDir.resolve("a.vtl"), "#parse('missing.vtl')\n$foo", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("sub/b.vtl"), "#if(unclosed", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    TemplateValidationRequest request =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .validateDependencies(true)
            .build();

    TemplateValidationResult first = validator.validate(request);
    for (int i = 0; i < 4; i++) {
      TemplateValidationResult next = validator.validate(request);
      assertThat(next.isSuccess()).isEqualTo(first.isSuccess());
      assertThat(next.validatedCount()).isEqualTo(first.validatedCount());
      assertThat(next.errorCount()).isEqualTo(first.errorCount());
      assertThat(next.warningCount()).isEqualTo(first.warningCount());
      assertThat(next.diagnostics()).containsExactlyElementsOf(first.diagnostics());
      assertThat(next.dependencyGraph().size()).isEqualTo(first.dependencyGraph().size());
    }
  }

  @Test
  @DisplayName(
      "Non-existent source directory or directory without matching templates succeeds with 0"
          + " count")
  void testNoMatchingTemplatesSuccess(@TempDir Path tempDir) throws Exception {
    TemplateValidator validator = TemplateValidator.create();

    // 1. Non-existent directory
    TemplateValidationResult missingDirResult =
        validator.validate(
            TemplateValidationRequest.builder()
                .sourceDirectory(tempDir.resolve("non-existent"))
                .build());
    assertThat(missingDirResult.isSuccess()).isTrue();
    assertThat(missingDirResult.validatedCount()).isZero();
    assertThat(missingDirResult.errorCount()).isZero();
    assertThat(missingDirResult.diagnostics()).isEmpty();

    // 2. Directory with only non-matching files
    Path srcDir = tempDir.resolve("non-matching");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("notes.txt"), "hello", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("data.json"), "{}", StandardCharsets.UTF_8);

    TemplateValidationResult nonMatchingResult =
        validator.validate(TemplateValidationRequest.builder().sourceDirectory(srcDir).build());
    assertThat(nonMatchingResult.isSuccess()).isTrue();
    assertThat(nonMatchingResult.validatedCount()).isZero();
    assertThat(nonMatchingResult.errorCount()).isZero();
    assertThat(nonMatchingResult.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Path safety handles nested templates and deduplicates identical source roots")
  void testPathSafetyAndDuplicateRoots(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir.resolve("deep/nested/pkg"));
    Files.writeString(
        srcDir.resolve("deep/nested/pkg/view.vtl"), "Nested view: $title", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();
    // Pass duplicate source directories
    TemplateValidationRequest request =
        TemplateValidationRequest.builder().sourceDirectories(srcDir, srcDir).build();

    TemplateValidationResult result = validator.validate(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.validatedCount()).isEqualTo(1);
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Security profile VTL_SAFE rejects arbitrary member invocations at validation time")
  void testSecurityProfileVtlSafeRejectsRestrictedOperations(@TempDir Path tempDir)
      throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);
    // Method invocation on arbitrary variable in template
    Files.writeString(srcDir.resolve("unsafe.vtl"), "$obj.someAction()", StandardCharsets.UTF_8);

    TemplateValidator validator = TemplateValidator.create();

    // VTL_SAFE profile forbids arbitrary method invocation
    TemplateValidationRequest safeRequest =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .profile(VtlProfile.VTL_SAFE)
            .build();

    TemplateValidationResult safeResult = validator.validate(safeRequest);
    assertThat(safeResult.isSuccess()).isFalse();
    assertThat(safeResult.hasErrors()).isTrue();
    assertThat(safeResult.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
                    && "VTLSEC".equalsIgnoreCase(d.code().category())
                    && "2401".equalsIgnoreCase(d.code().id()));

    // VTL_MIGRATION allows domain method calls without security denial
    TemplateValidationRequest migrationRequest =
        TemplateValidationRequest.builder()
            .sourceDirectory(srcDir)
            .profile(VtlProfile.VTL_MIGRATION)
            .build();

    TemplateValidationResult migrationResult = validator.validate(migrationRequest);
    assertThat(migrationResult.isSuccess()).isTrue();
    assertThat(migrationResult.errorCount()).isZero();
  }
}
