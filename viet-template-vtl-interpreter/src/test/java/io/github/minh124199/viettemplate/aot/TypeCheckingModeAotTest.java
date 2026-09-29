package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeCheckingModeAotTest {

  public static class CustomerBean {
    private final String fullName;

    public CustomerBean(String fullName) {
      this.fullName = fullName;
    }

    public String getFullName() {
      return fullName;
    }
  }

  @Test
  @DisplayName("TypeCheckingMode.OFF permits dynamic fallback and compiles successfully")
  void testTypeCheckingOffPermitsDynamicFallback(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path template = srcDir.resolve("customer.vtl");
    Files.writeString(template, "Customer: $user.unknownProp", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("customer.vtl");
    TemplateContract contract =
        TemplateContract.of(id, List.of(TemplateParameter.of("user", CustomerBean.class)));

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contracts(Map.of(id, contract))
            .typeCheckingMode(TypeCheckingMode.OFF)
            .build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.compiledCount()).isEqualTo(1);
    assertThat(result.artifacts()).hasSize(1);
  }

  @Test
  @DisplayName("TypeCheckingMode.WARN emits warning and compiles successfully")
  void testTypeCheckingWarnEmitsWarningAndSucceeds(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path template = srcDir.resolve("customer.vtl");
    Files.writeString(template, "Customer: $user.unknownProp", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("customer.vtl");
    TemplateContract contract =
        TemplateContract.of(id, List.of(TemplateParameter.of("user", CustomerBean.class)));

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contracts(Map.of(id, contract))
            .typeCheckingMode(TypeCheckingMode.WARN)
            .build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasWarnings()).isTrue();
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.WARNING
                    && d.code().qualifiedCode().equals("VTLS:2104"));
    assertThat(result.compiledCount()).isEqualTo(1);
    assertThat(result.artifacts()).hasSize(1);
  }

  @Test
  @DisplayName("TypeCheckingMode.ERROR fails compilation on provable contract mismatch")
  void testTypeCheckingErrorFailsCompilation(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path template = srcDir.resolve("customer.vtl");
    Files.writeString(template, "Customer: $user.unknownProp", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("customer.vtl");
    TemplateContract contract =
        TemplateContract.of(id, List.of(TemplateParameter.of("user", CustomerBean.class)));

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contracts(Map.of(id, contract))
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
                    && d.code().qualifiedCode().equals("VTLS:2104"));
    assertThat(result.compiledCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("TypeCheckingMode changes invalidate incremental cache")
  void testTypeCheckingModeInvalidatesCache(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Path stateFile = tempDir.resolve("cache.state");
    Files.createDirectories(srcDir);

    Path template = srcDir.resolve("test.vtl");
    Files.writeString(template, "Hello $name", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();

    // 1. Initial compilation with OFF
    TemplateAotRequest req1 =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .stateFile(stateFile)
            .incremental(true)
            .typeCheckingMode(TypeCheckingMode.OFF)
            .build();
    TemplateAotResult res1 = compiler.compile(req1);
    assertThat(res1.compiledCount()).isEqualTo(1);

    // 2. Incremental compilation with unchanged OFF -> skipped
    TemplateAotResult res2 = compiler.compile(req1);
    assertThat(res2.compiledCount()).isEqualTo(0);
    assertThat(res2.skippedCount()).isEqualTo(1);

    // 3. Recompilation with WARN -> recompiled due to cache invalidation
    TemplateAotRequest req2 =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .stateFile(stateFile)
            .incremental(true)
            .typeCheckingMode(TypeCheckingMode.WARN)
            .build();
    TemplateAotResult res3 = compiler.compile(req2);
    assertThat(res3.compiledCount()).isEqualTo(1);
    assertThat(res3.skippedCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("Builder typeChecking string overload parses case-insensitively")
  void testBuilderTypeCheckingString() {
    TemplateAotRequest r1 =
        TemplateAotRequest.builder()
            .sourceDirectory(Path.of("."))
            .outputDirectory(Path.of("."))
            .typeChecking("warn")
            .build();
    assertThat(r1.typeCheckingMode()).isEqualTo(TypeCheckingMode.WARN);

    TemplateAotRequest r2 =
        TemplateAotRequest.builder()
            .sourceDirectory(Path.of("."))
            .outputDirectory(Path.of("."))
            .typeChecking("ERROR")
            .build();
    assertThat(r2.typeCheckingMode()).isEqualTo(TypeCheckingMode.ERROR);

    TemplateAotRequest r3 =
        TemplateAotRequest.builder()
            .sourceDirectory(Path.of("."))
            .outputDirectory(Path.of("."))
            .typeChecking("off")
            .build();
    assertThat(r3.typeCheckingMode()).isEqualTo(TypeCheckingMode.OFF);

    TemplateAotRequest r4 =
        TemplateAotRequest.builder()
            .sourceDirectory(Path.of("."))
            .outputDirectory(Path.of("."))
            .build();
    assertThat(r4.typeCheckingMode()).isEqualTo(TypeCheckingMode.OFF);
  }
}
