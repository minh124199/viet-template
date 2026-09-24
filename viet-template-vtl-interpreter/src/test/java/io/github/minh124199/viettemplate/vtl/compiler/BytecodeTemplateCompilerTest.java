package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.SafeContent;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.vtl.internal.compiler.*;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.*;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BytecodeTemplateCompilerTest {

  public record UserRecord(String name, int age) {}

  public static class UserBean {
    private final String name;

    public UserBean(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public static class UserField {
    public final String name;

    public UserField(String name) {
      this.name = name;
    }
  }

  private CompiledTemplate compile(String templateText) {
    return compile(templateText, BackendOptions.defaults());
  }

  private CompiledTemplate compile(String templateText, BackendOptions options) {
    SourceText source = SourceText.of("test.vtl", templateText);
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
    BackendResult result = compiler.compile(ir, options);
    if (!result.isSuccess() || result.templateInstance() == null) {
      throw new IllegalStateException(
          "Compilation failed: " + result.status() + " " + result.diagnostics());
    }
    return result.templateInstance();
  }

  private String render(CompiledTemplate template, Map<String, Object> model) throws IOException {
    RenderContext ctx =
        new RenderContext() {
          @Override
          public Object get(String name) {
            return model.get(name);
          }

          @Override
          public boolean contains(String name) {
            return model.containsKey(name);
          }
        };
    StringTemplateOutput output = new StringTemplateOutput();
    template.render(ctx, output);
    return output.toString();
  }

  @Test
  @DisplayName("Compiles static text with pre-encoded UTF-8 constant chunks")
  void testStaticText() throws Exception {
    CompiledTemplate template = compile("Hello, World! 🚀 Special chars: àáảãạ.");
    String result = render(template, Map.of());
    assertThat(result).isEqualTo("Hello, World! 🚀 Special chars: àáảãạ.");
  }

  @Test
  @DisplayName("Compiles context variable references")
  void testVariableReferences() throws Exception {
    CompiledTemplate template = compile("Hello, $name! Score: $score.");
    String result = render(template, Map.of("name", "Antigravity", "score", 100));
    assertThat(result).isEqualTo("Hello, Antigravity! Score: 100.");
  }

  @Test
  @DisplayName("Direct accessor binding for Java Records")
  void testRecordComponent() throws Exception {
    CompiledTemplate template = compile("User: $user.name, Age: $user.age");
    String result = render(template, Map.of("user", new UserRecord("Alice", 30)));
    assertThat(result).isEqualTo("User: Alice, Age: 30");
  }

  @Test
  @DisplayName("Direct accessor binding for Java Bean Getters")
  void testGetterAccess() throws Exception {
    CompiledTemplate template = compile("Hello, $user.name!");
    String result = render(template, Map.of("user", new UserBean("Bob")));
    assertThat(result).isEqualTo("Hello, Bob!");
  }

  @Test
  @DisplayName("Direct accessor binding for public fields")
  void testFieldAccess() throws Exception {
    CompiledTemplate template = compile("Hello, $user.name!");
    String result = render(template, Map.of("user", new UserField("Charlie")));
    assertThat(result).isEqualTo("Hello, Charlie!");
  }

  @Test
  @DisplayName("Direct Map key lookup")
  void testMapLookup() throws Exception {
    CompiledTemplate template = compile("Key: $data.key1, Nested: $data.key2");
    String result = render(template, Map.of("data", Map.of("key1", "val1", "key2", "val2")));
    assertThat(result).isEqualTo("Key: val1, Nested: val2");
  }

  @Test
  @DisplayName("Compiles conditionals with truthiness logic")
  void testConditionals() throws Exception {
    String vtl = "#if($user) Active: $user.name #else Inactive #end";
    CompiledTemplate template = compile(vtl);

    assertThat(render(template, Map.of("user", new UserRecord("Dave", 25))))
        .isEqualTo(" Active: Dave ");
    assertThat(render(template, Map.of())).isEqualTo(" Inactive ");
  }

  @Test
  @DisplayName("Compiles loops (#foreach) over collections and arrays")
  void testLoops() throws Exception {
    String vtl = "#foreach($item in $items)[$item]#end";
    CompiledTemplate template = compile(vtl);

    assertThat(render(template, Map.of("items", List.of("A", "B", "C")))).isEqualTo("[A][B][C]");
    assertThat(render(template, Map.of("items", new String[] {"X", "Y"}))).isEqualTo("[X][Y]");
  }

  @Test
  @DisplayName("Supports #break in loops")
  void testLoopBreak() throws Exception {
    String vtl = "#foreach($item in $items)#if($item == 3)#break#end[$item]#end";
    CompiledTemplate template = compile(vtl);

    assertThat(render(template, Map.of("items", List.of(1, 2, 3, 4, 5)))).isEqualTo("[1][2]");
  }

  @Test
  @DisplayName("Supports local variables defined with #set")
  void testSetDirective() throws Exception {
    String vtl = "#set($greeting = 'Hello')#set($target = 'World')$greeting, $target!";
    CompiledTemplate template = compile(vtl);

    assertThat(render(template, Map.of())).isEqualTo("Hello, World!");
  }

  @Test
  @DisplayName("Default reference rendering preserves raw content")
  void testRawContent() throws Exception {
    CompiledTemplate template = compile("Raw: $content");
    String result = render(template, Map.of("content", "<script>alert(1)</script>"));
    assertThat(result).isEqualTo("Raw: <script>alert(1)</script>");
  }

  @Test
  @DisplayName("Safe content unwrapping preserves raw HTML")
  void testSafeContent() throws Exception {
    CompiledTemplate template = compile("Safe: $content");
    SafeContent safe = SafeHtml.of("<b>Bold</b>");
    String result = render(template, Map.of("content", safe));
    assertThat(result).isEqualTo("Safe: <b>Bold</b>");
  }

  @Test
  @DisplayName("Security policy denial blocks restricted members")
  void testSecurityDenial() throws Exception {
    CompiledTemplate template = compile("Class: $obj.getClass().getName()");
    LinkerAccessPolicy strictPolicy = LinkerAccessPolicy.standard();

    BackendOptions options = BackendOptions.builder().securityPolicy(strictPolicy).build();

    assertThatThrownBy(() -> render(template, Map.of("obj", "test")))
        .isInstanceOf(TemplateSecurityException.class);
  }

  @Test
  @DisplayName("Reports INTERPRETER_REQUIRED_EVALUATE when #evaluate is used")
  void testEvaluateRequiresInterpreter() {
    SourceText source = SourceText.of("eval.vtl", "#evaluate('$x')");
    var parseResult = VtlParser.parse(source);
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, BackendOptions.defaults());

    assertThat(result.status()).isEqualTo(CompilationStatus.INTERPRETER_REQUIRED_EVALUATE);
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  @DisplayName("Fails compilation when failOnDynamicFallback=true and dynamic sites exist")
  void testFailOnDynamicFallback() {
    BackendOptions options = BackendOptions.builder().failOnDynamicFallback(true).build();

    SourceText source = SourceText.of("dyn.vtl", "$unknown.foo()");
    var parseResult = VtlParser.parse(source);
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .allowArbitraryMethods(true)
            .build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, options);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.status()).isEqualTo(CompilationStatus.AOT_OK_WITH_DYNAMIC_SITES);
  }

  @Test
  @DisplayName(
      "Inspects generated bytecode via javap to verify StackMapTable and local frame invariants")
  void testGeneratedBytecodeStackMapInvariantsWithJavap() throws Exception {
    String templateText =
        "#if($flag)\n"
            + "  #foreach($x in $list) [$x] #end\n"
            + "#elseif($flag2)\n"
            + "  #foreach($y in $list) ($y) #end\n"
            + "#else\n"
            + "  #foreach($z in $list) {$z} #end\n"
            + "#end";

    SourceText source = SourceText.of("minimized.vtl", templateText);
    var parseResult = VtlParser.parse(source);
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

    assertThat(result.isSuccess()).isTrue();
    byte[] bytecode = result.artifact().classBytes();

    java.nio.file.Path tempClass =
        java.nio.file.Files.createTempFile("MinimizedTemplate", ".class");
    try {
      java.nio.file.Files.write(tempClass, bytecode);
      Process process;
      try {
        process =
            new ProcessBuilder("javap", "-v", "-p", tempClass.toString())
                .redirectErrorStream(true)
                .start();
      } catch (IOException e) {
        org.junit.jupiter.api.Assumptions.abort(
            "javap not available on system PATH: " + e.getMessage());
        return;
      }
      String javapOutput =
          new String(
              process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      assertThat(exitCode).isEqualTo(0);

      // Verify StackMapTable exists
      assertThat(javapOutput).contains("StackMapTable: number_of_entries = ");
      // Verify all branch target stack maps have 0 stack items
      assertThat(javapOutput).contains("stack = []");
      // Verify that no frame contains top for local variables
      assertThat(javapOutput).doesNotContain(", top", "[ top", " top,");
      // Metadata-free loops no longer reserve a generated local for $foreach state.
      assertThat(javapOutput).contains("locals=12").contains("stack=16");
    } finally {
      java.nio.file.Files.deleteIfExists(tempClass);
    }
  }
}
