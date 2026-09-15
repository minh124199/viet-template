package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeTemplateCompiler;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelfContainedBytecodeExecutionTest {

  public record User(String name, int age) {}

  private BackendResult compileTemplate(TemplateId id, String templateText) {
    SourceText source = SourceText.of(id, templateText);
    var parseResult = VtlParser.parse(source);
    if (parseResult.hasErrors()) {
      throw new IllegalArgumentException("Parse failed: " + parseResult.diagnostics());
    }

    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .allowArbitraryMethods(true)
            .build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, BackendOptions.defaults());
    if (!result.isSuccess() || result.optArtifact().isEmpty()) {
      throw new IllegalStateException(
          "Compilation failed: " + result.status() + " " + result.diagnostics());
    }
    return result;
  }

  @Test
  @DisplayName(
      "Compiles self-contained bytecode that executes in isolated ClassLoader without"
          + " initializeClassFields")
  void testSelfContainedBytecodeExecutionInIsolatedClassLoader() throws Exception {
    TemplateId id = TemplateId.of("self_contained.vtl");
    String vtl = "Hello $user.name! Count: $items.size(). Items:#foreach($i in $items)[$i]#end";

    BackendResult result = compileTemplate(id, vtl);
    CompiledArtifact artifact = result.optArtifact().orElseThrow();
    byte[] classBytes = artifact.classBytes();
    String fqcn = artifact.generatedClassName();

    // Isolated ClassLoader that defines only this class from raw bytes, never invoking
    // initializeClassFields
    ClassLoader isolatedLoader =
        new ClassLoader(BytecodeTemplateCompiler.class.getClassLoader()) {
          public Class<?> defineFromBytes(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
          }
        };

    Method defineMethod =
        isolatedLoader.getClass().getMethod("defineFromBytes", String.class, byte[].class);
    @SuppressWarnings("unchecked")
    Class<? extends CompiledTemplate> definedClass =
        (Class<? extends CompiledTemplate>) defineMethod.invoke(isolatedLoader, fqcn, classBytes);

    // Verify static fields before instantiation/execution
    Field templateIdField = definedClass.getDeclaredField("TEMPLATE_ID");
    Field sitesField = definedClass.getDeclaredField("SITES");
    Field utf8Field = definedClass.getDeclaredField("UTF8_CHUNKS");

    // Instantiation triggers <clinit>
    CompiledTemplate template = definedClass.getDeclaredConstructor().newInstance();

    // Validate that <clinit> initialized all required static arrays
    assertThat(templateIdField.get(null)).isEqualTo("self_contained.vtl");
    DynamicCallSite[] sites = (DynamicCallSite[]) sitesField.get(null);
    assertThat(sites).isNotNull().isNotEmpty();
    byte[][] utf8Chunks = (byte[][]) utf8Field.get(null);
    assertThat(utf8Chunks).isNotNull().isNotEmpty();

    // Execute template render
    Map<String, Object> model =
        Map.of("user", new User("Alice", 30), "items", List.of("apple", "banana"));
    StringTemplateOutput output = new StringTemplateOutput();
    template.render(MapRenderContext.of(model), output);

    assertThat(output.toString()).isEqualTo("Hello Alice! Count: 2. Items:[apple][banana]");
  }

  @Test
  @DisplayName(
      "VtlTemplateEngine discovers and executes precompiled template from templates.idx with absent"
          + " source and rejected runtime compilation")
  void testEngineDiscoversPrecompiledTemplateFromIndexWithAbsentSourceAndRejectRuntimeCompilation(
      @TempDir Path tempDir) throws Exception {
    TemplateId id = TemplateId.of("precompiled/welcome.vm");
    String vtl = "Welcome $user.name! Score: $score.";

    BackendResult result = compileTemplate(id, vtl);
    CompiledArtifact artifact = result.optArtifact().orElseThrow();
    byte[] classBytes = artifact.classBytes();
    String fqcn = artifact.generatedClassName();

    // Prepare temp classpath layout:
    // META-INF/viet-template/templates.idx
    Path metaInf = tempDir.resolve("META-INF/viet-template");
    Files.createDirectories(metaInf);
    Files.writeString(
        metaInf.resolve("templates.idx"), id.value() + "=" + fqcn + "\n", StandardCharsets.UTF_8);

    // Write compiled .class file
    Path classFilePath = tempDir.resolve(fqcn.replace('.', '/') + ".class");
    Files.createDirectories(classFilePath.getParent());
    Files.write(classFilePath, classBytes);

    // Notice: source file "precompiled/welcome.vm" is NOT created anywhere!

    URLClassLoader aotLoader =
        new URLClassLoader(
            new URL[] {tempDir.toUri().toURL()}, VtlTemplateEngine.class.getClassLoader());

    ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(aotLoader);

      // 1. Test discovery via Thread context class loader with empty repository
      InMemoryTemplateRepository emptyRepo = InMemoryTemplateRepository.create();
      VtlTemplateEngine engineWithContextCl =
          VtlTemplateEngine.builder().repository(emptyRepo).rejectRuntimeCompilation(true).build();

      Template template1 = engineWithContextCl.get(id);
      assertThat(template1).isNotNull();
      assertThat(template1.descriptor().id()).isEqualTo(id);
      assertThat(template1.descriptor().executionTier()).isEqualTo("AOT_BYTECODE");

      StringTemplateOutput out1 = new StringTemplateOutput();
      template1.render(MapRenderContext.of(Map.of("user", new User("Bob", 28), "score", 99)), out1);
      assertThat(out1.toString()).isEqualTo("Welcome Bob! Score: 99.");

      // 2. Test discovery via ClasspathTemplateRepository with repository ClassLoader
      ClasspathTemplateRepository repo = ClasspathTemplateRepository.of(aotLoader, "");
      VtlTemplateEngine engineWithRepoCl =
          VtlTemplateEngine.builder().repository(repo).rejectRuntimeCompilation(true).build();

      Template template2 = engineWithRepoCl.get(id);
      assertThat(template2).isNotNull();
      assertThat(template2.descriptor().id()).isEqualTo(id);

      StringTemplateOutput out2 = new StringTemplateOutput();
      template2.render(
          MapRenderContext.of(Map.of("user", new User("Charlie", 35), "score", 100)), out2);
      assertThat(out2.toString()).isEqualTo("Welcome Charlie! Score: 100.");
    } finally {
      Thread.currentThread().setContextClassLoader(originalContextLoader);
      aotLoader.close();
    }
  }
}
