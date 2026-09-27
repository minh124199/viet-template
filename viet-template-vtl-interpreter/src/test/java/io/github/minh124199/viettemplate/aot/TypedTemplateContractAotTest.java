package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypedTemplateContractAotTest {

  public record UserProfile(String username, int score, List<String> tags) {}

  public static class CustomerBean {
    private final String fullName;

    public CustomerBean(String fullName) {
      this.fullName = fullName;
    }

    public String getFullName() {
      return fullName;
    }
  }

  public static class AnotherCustomer {
    public String getFullName() {
      return "Dynamic John";
    }
  }

  @Test
  @DisplayName("1. TemplateContractReader reads simple and nullable parameters")
  void testTemplateContractReaderSimpleParameters(@TempDir Path tempDir) throws Exception {
    Path contractFile = tempDir.resolve("test.vtl.contract");
    Files.writeString(
        contractFile,
        "# Sample contract\n"
            + "name=String\n"
            + "count=int\n"
            + "nullable description=String\n"
            + "// Comment line\n",
        StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("test.vtl");
    TemplateContract contract =
        TemplateContractReader.read(contractFile, id, getClass().getClassLoader());

    assertThat(contract.templateId()).isEqualTo(id);
    assertThat(contract.parameters()).hasSize(3);

    TemplateParameter p1 = contract.parameter("name").orElseThrow();
    assertThat(p1.name()).isEqualTo("name");
    assertThat(p1.rawType()).isEqualTo(String.class);
    assertThat(p1.nullable()).isFalse();

    TemplateParameter p2 = contract.parameter("count").orElseThrow();
    assertThat(p2.name()).isEqualTo("count");
    assertThat(p2.rawType()).isEqualTo(int.class);
    assertThat(p2.nullable()).isFalse();

    TemplateParameter p3 = contract.parameter("description").orElseThrow();
    assertThat(p3.name()).isEqualTo("description");
    assertThat(p3.rawType()).isEqualTo(String.class);
    assertThat(p3.nullable()).isTrue();
  }

  @Test
  @DisplayName("2. TemplateContractReader reads generic collection parameters")
  void testTemplateContractReaderGenericParameters(@TempDir Path tempDir) throws Exception {
    Path contractFile = tempDir.resolve("collections.contract");
    Files.writeString(
        contractFile,
        "items=List<String>\n" + "config=Map<String, String>\n",
        StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("collections.vtl");
    TemplateContract contract =
        TemplateContractReader.read(contractFile, id, getClass().getClassLoader());

    TemplateParameter items = contract.parameter("items").orElseThrow();
    assertThat(items.rawType()).isEqualTo(List.class);
    assertThat(items.typeArguments()).containsExactly(String.class);

    TemplateParameter config = contract.parameter("config").orElseThrow();
    assertThat(config.rawType()).isEqualTo(Map.class);
    assertThat(config.typeArguments()).containsExactly(String.class, String.class);
  }

  @Test
  @DisplayName("3. TemplateContractReader extracts schema from record and class directives")
  void testTemplateContractReaderClassAndRecordDirectives(@TempDir Path tempDir) throws Exception {
    Path recordContract = tempDir.resolve("record.contract");
    Files.writeString(
        recordContract, "record=" + UserProfile.class.getName() + "\n", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("user.vtl");
    TemplateContract c1 =
        TemplateContractReader.read(recordContract, id, getClass().getClassLoader());
    assertThat(c1.parameters()).hasSize(3);
    assertThat(c1.hasParameter("username")).isTrue();
    assertThat(c1.hasParameter("score")).isTrue();
    assertThat(c1.hasParameter("tags")).isTrue();

    Path classContract = tempDir.resolve("class.contract");
    Files.writeString(
        classContract, "class=" + CustomerBean.class.getName() + "\n", StandardCharsets.UTF_8);

    TemplateContract c2 =
        TemplateContractReader.read(classContract, id, getClass().getClassLoader());
    assertThat(c2.parameters()).hasSize(1);
    assertThat(c2.hasParameter("fullName")).isTrue();
  }

  @Test
  @DisplayName("4. TemplateContractReader rejects malformed syntax and unresolvable classes")
  void testTemplateContractReaderMalformedSyntax(@TempDir Path tempDir) throws Exception {
    Path malformed = tempDir.resolve("bad.contract");
    Files.writeString(malformed, "missing_equals_sign\n", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("test.vtl");
    assertThatThrownBy(
            () -> TemplateContractReader.read(malformed, id, getClass().getClassLoader()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid contract line");

    Path badType = tempDir.resolve("bad_type.contract");
    Files.writeString(badType, "x=com.nonexistent.UnknownClass\n", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> TemplateContractReader.read(badType, id, getClass().getClassLoader()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot resolve parameter type");
  }

  @Test
  @DisplayName("5. TypedTemplateFacadeGenerator derives class name and generates compilable facade")
  void testTypedFacadeGenerationAndExecution(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Path genSrcDir = tempDir.resolve("gen-src");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("order-summary.vtl"),
        "Order for $username: score $score",
        StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("order-summary.vtl.contract"),
        "record=" + UserProfile.class.getName() + "\n",
        StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .generateTypedFacades(true)
            .generatedSourcesDirectory(genSrcDir)
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();

    Path facadeJava =
        genSrcDir.resolve("io/github/minh124199/viettemplate/generated/OrderSummaryView.java");
    assertThat(facadeJava).isRegularFile();
    String facadeCode = Files.readString(facadeJava, StandardCharsets.UTF_8);
    assertThat(facadeCode).contains("public final class OrderSummaryView");
    assertThat(facadeCode).contains("render(TemplateOutput output,");
    assertThat(facadeCode).contains("render(");

    // Compile generated Java facade using JDK JavaCompiler
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    if (javac != null) {
      Path facadeClassDir = tempDir.resolve("facade-classes");
      Files.createDirectories(facadeClassDir);

      String cp =
          System.getProperty("java.class.path") + File.pathSeparator + outDir.toAbsolutePath();

      List<String> options = List.of("-cp", cp, "-d", facadeClassDir.toString());
      javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diagnostics =
          new javax.tools.DiagnosticCollector<>();
      try (StandardJavaFileManager fm = javac.getStandardFileManager(diagnostics, null, null)) {
        var compilationUnits = fm.getJavaFileObjects(facadeJava.toFile());
        boolean success =
            javac.getTask(null, fm, diagnostics, options, null, compilationUnits).call();
        for (var d : diagnostics.getDiagnostics()) {
          System.err.println("JAVAC: " + d.getMessage(null));
        }
        assertThat(success).isTrue();
      }

      // Load compiled facade class and invoke static render method
      URLClassLoader facadeLoader =
          new URLClassLoader(
              new URL[] {facadeClassDir.toUri().toURL(), outDir.toUri().toURL()},
              getClass().getClassLoader());
      Class<?> facadeClass =
          facadeLoader.loadClass("io.github.minh124199.viettemplate.generated.OrderSummaryView");

      UserProfile profile = new UserProfile("Alice", 100, List.of("gold", "vip"));

      // Call render(TemplateOutput, ...) or render(...) -> String
      Method renderToString =
          Arrays.stream(facadeClass.getMethods())
              .filter(m -> m.getName().equals("render") && m.getReturnType() == String.class)
              .findFirst()
              .orElseThrow();

      String rendered =
          (String) renderToString.invoke(null, profile.username(), profile.score(), profile.tags());
      assertThat(rendered).isEqualTo("Order for Alice: score 100");
    }
  }

  @Test
  @DisplayName("6. Companion contract change invalidates incremental compilation cache")
  void testCompanionContractDiscoveryAndIncrementalCache(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path template = srcDir.resolve("page.vtl");
    Path companion = srcDir.resolve("page.vtl.contract");

    Files.writeString(template, "Page: $title", StandardCharsets.UTF_8);
    Files.writeString(companion, "title=String\n", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .incremental(true)
            .build();

    TemplateAotResult r1 = compiler.compile(request);
    assertThat(r1.compiledCount()).isEqualTo(1);
    assertThat(r1.skippedCount()).isEqualTo(0);

    // Second run without modification: template is skipped as up-to-date
    TemplateAotResult r2 = compiler.compile(request);
    assertThat(r2.compiledCount()).isEqualTo(0);
    assertThat(r2.skippedCount()).isEqualTo(1);

    // Modify the companion contract file: triggers re-compilation
    Files.writeString(companion, "title=String\nnullable extra=String\n", StandardCharsets.UTF_8);

    TemplateAotResult r3 = compiler.compile(request);
    assertThat(r3.compiledCount()).isEqualTo(1);
    assertThat(r3.skippedCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("7. Programmatic contracts produce specialized property access bytecode")
  void testProgrammaticContractSpecialization(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("user-card.vtl"),
        "User: $user.username, Score: $user.score",
        StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("user-card.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("user", UserProfile.class).build();

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(id, contract)
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate instance = clazz.getDeclaredConstructor().newInstance();

    UserProfile profile = new UserProfile("Bob", 42, List.of("silver"));
    StringTemplateOutput output = new StringTemplateOutput();
    instance.render(RenderContext.of("user", profile), output);
    assertThat(output.toString()).isEqualTo("User: Bob, Score: 42");
  }

  @Test
  @DisplayName("8. Static specialization gracefully handles null receiver without crashing")
  void testTypedSpecializationWithNullReceiver(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("null-rec.vtl"), "Start[$!user.username]End", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("null-rec.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("user", UserProfile.class, true).build();

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(id, contract)
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate instance = clazz.getDeclaredConstructor().newInstance();

    StringTemplateOutput output = new StringTemplateOutput();
    // Pass null for user
    instance.render(RenderContext.of("user", null), output);
    assertThat(output.toString()).isEqualTo("Start[]End");
  }

  @Test
  @DisplayName("9. Static specialization falls back safely when receiver type dynamically diverges")
  void testTypedSpecializationWithDynamicMismatch(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("mismatch.vtl"), "Val: $user.username", StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("mismatch.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("user", UserProfile.class).build();

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(id, contract)
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate instance = clazz.getDeclaredConstructor().newInstance();

    // Instead of UserProfile, pass a Map with key "username"
    StringTemplateOutput output = new StringTemplateOutput();
    instance.render(RenderContext.of("user", Map.of("username", "dynamic_alice")), output);
    assertThat(output.toString()).isEqualTo("Val: dynamic_alice");
  }

  @Test
  @DisplayName("10. Static specialization on method invocation handles null receiver safely")
  void testTypedSpecializationMethodWithNullReceiver(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("null-method.vtl"),
        "Name[$!customer.getFullName()]",
        StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("null-method.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("customer", CustomerBean.class, true).build();

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(id, contract)
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate instance = clazz.getDeclaredConstructor().newInstance();

    StringTemplateOutput output = new StringTemplateOutput();
    instance.render(RenderContext.of("customer", null), output);
    assertThat(output.toString()).isEqualTo("Name[]");
  }

  @Test
  @DisplayName("11. Static specialization on method invocation handles dynamic mismatch")
  void testTypedSpecializationMethodWithDynamicMismatch(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("mismatch-method.vtl"),
        "Name: $customer.getFullName()",
        StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("mismatch-method.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("customer", CustomerBean.class).build();

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(id, contract)
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate instance = clazz.getDeclaredConstructor().newInstance();

    // Pass an object with a getFullName() method that does not extend CustomerBean
    Object dynamicCustomer = new AnotherCustomer();

    StringTemplateOutput output = new StringTemplateOutput();
    instance.render(RenderContext.of("customer", dynamicCustomer), output);
    assertThat(output.toString()).isEqualTo("Name: Dynamic John");
  }

  @Test
  @DisplayName("12. TemplateContractReader handles complex nested generics and standard Java types")
  void testTemplateContractReaderComplexGenericsAndTypes(@TempDir Path tempDir) throws Exception {
    Path contractFile = tempDir.resolve("complex.contract");
    Files.writeString(
        contractFile,
        "nested=Map<String, List<String>>\n"
            + "bounded=List<? extends String>\n"
            + "amount=BigDecimal\n"
            + "id=UUID\n",
        StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("complex.vtl");
    TemplateContract contract =
        TemplateContractReader.read(contractFile, id, getClass().getClassLoader());

    TemplateParameter nested = contract.parameter("nested").orElseThrow();
    assertThat(nested.rawType()).isEqualTo(Map.class);
    assertThat(nested.typeArguments()).containsExactly(String.class, List.class);

    TemplateParameter bounded = contract.parameter("bounded").orElseThrow();
    assertThat(bounded.rawType()).isEqualTo(List.class);
    assertThat(bounded.typeArguments()).containsExactly(String.class);

    TemplateParameter amount = contract.parameter("amount").orElseThrow();
    assertThat(amount.rawType()).isEqualTo(java.math.BigDecimal.class);

    TemplateParameter uuid = contract.parameter("id").orElseThrow();
    assertThat(uuid.rawType()).isEqualTo(java.util.UUID.class);
  }
}
