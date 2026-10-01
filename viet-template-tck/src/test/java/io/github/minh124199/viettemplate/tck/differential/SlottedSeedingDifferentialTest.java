package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SlottedRenderContext;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Differential test ensuring that rendering with {@link SlottedRenderContext} produces identical
 * output to rendering with name-based {@link RenderContext} across AOT and interpreter tiers.
 */
class SlottedSeedingDifferentialTest {

  /** Simple data class for testing property access. */
  public static class User {
    private final String name;
    private final int age;
    private final String email;

    public User(String name, int age, String email) {
      this.name = name;
      this.age = age;
      this.email = email;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }

    public String getEmail() {
      return email;
    }
  }

  public static class Item {
    private final String label;
    private final double price;

    public Item(String label, double price) {
      this.label = label;
      this.price = price;
    }

    public String getLabel() {
      return label;
    }

    public double getPrice() {
      return price;
    }
  }

  @Nested
  @DisplayName("AOT tier: slotted vs name-based context")
  class AotTier {

    @Test
    void simpleVariable() throws IOException {
      assertAotEquivalence(
          "simple.vtl",
          "Hello $name!",
          TemplateContract.builder(TemplateId.of("simple.vtl"))
              .parameter("name", String.class)
              .build(),
          new String[] {"name"},
          new Object[] {"Alice"});
    }

    @Test
    void multipleVariables() throws IOException {
      assertAotEquivalence(
          "multi.vtl",
          "$greeting $name, you are $age years old.",
          TemplateContract.builder(TemplateId.of("multi.vtl"))
              .parameter("greeting", String.class)
              .parameter("name", String.class)
              .parameter("age", Integer.class)
              .build(),
          new String[] {"greeting", "name", "age"},
          new Object[] {"Hello", "Bob", 25});
    }

    @Test
    void propertyAccess() throws IOException {
      User user = new User("Charlie", 35, "charlie@example.com");
      assertAotEquivalence(
          "prop.vtl",
          "$user.name is $user.age years old, email: $user.email",
          TemplateContract.builder(TemplateId.of("prop.vtl")).parameter("user", User.class).build(),
          new String[] {"user"},
          new Object[] {user});
    }

    @Test
    void nullValue() throws IOException {
      assertAotEquivalence(
          "nullv.vtl",
          "#if($email)$email#{else}no email#{end}",
          TemplateContract.builder(TemplateId.of("nullv.vtl"))
              .parameter("email", String.class)
              .build(),
          new String[] {"email"},
          new Object[] {null});
    }

    @Test
    void conditional() throws IOException {
      assertAotEquivalence(
          "cond.vtl",
          "#if($active)ACTIVE#{else}INACTIVE#{end}",
          TemplateContract.builder(TemplateId.of("cond.vtl"))
              .parameter("active", Boolean.class)
              .build(),
          new String[] {"active"},
          new Object[] {true});
    }

    @Test
    void foreachLoop() throws IOException {
      assertAotEquivalence(
          "loop.vtl",
          "#foreach($item in $items)$item #end",
          TemplateContract.builder(TemplateId.of("loop.vtl"))
              .parameter("items", List.class)
              .build(),
          new String[] {"items"},
          new Object[] {List.of("a", "b", "c")});
    }

    @Test
    void nestedPropertyChain() throws IOException {
      User user = new User("Diana", 28, "diana@test.com");
      Item item = new Item("Widget", 19.99);
      assertAotEquivalence(
          "chain.vtl",
          "$user.name bought $item.label for $$item.price",
          TemplateContract.builder(TemplateId.of("chain.vtl"))
              .parameter("user", User.class)
              .parameter("item", Item.class)
              .build(),
          new String[] {"user", "item"},
          new Object[] {user, item});
    }

    @Test
    void setDirective() throws IOException {
      assertAotEquivalence(
          "setd.vtl",
          "#set($x = $a + $b)$x",
          TemplateContract.builder(TemplateId.of("setd.vtl"))
              .parameter("a", Integer.class)
              .parameter("b", Integer.class)
              .build(),
          new String[] {"a", "b"},
          new Object[] {10, 20});
    }

    @Test
    void manyParameters() throws IOException {
      assertAotEquivalence(
          "many.vtl",
          "$p0$p1$p2$p3$p4$p5$p6$p7",
          TemplateContract.builder(TemplateId.of("many.vtl"))
              .parameter("p0", String.class)
              .parameter("p1", String.class)
              .parameter("p2", String.class)
              .parameter("p3", String.class)
              .parameter("p4", String.class)
              .parameter("p5", String.class)
              .parameter("p6", String.class)
              .parameter("p7", String.class)
              .build(),
          new String[] {"p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7"},
          new Object[] {"a", "b", "c", "d", "e", "f", "g", "h"});
    }
  }

  @Nested
  @DisplayName("Interpreter tier: slotted vs name-based context")
  class InterpreterTier {

    @Test
    void simpleVariable() throws IOException {
      assertInterpreterEquivalence(
          "isimple.vtl",
          "Hello $name!",
          TemplateContract.builder(TemplateId.of("isimple.vtl"))
              .parameter("name", String.class)
              .build(),
          new String[] {"name"},
          new Object[] {"Alice"});
    }

    @Test
    void propertyAccess() throws IOException {
      User user = new User("Eve", 22, "eve@test.com");
      assertInterpreterEquivalence(
          "iprop.vtl",
          "$user.name ($user.age)",
          TemplateContract.builder(TemplateId.of("iprop.vtl"))
              .parameter("user", User.class)
              .build(),
          new String[] {"user"},
          new Object[] {user});
    }

    @Test
    void nullValue() throws IOException {
      assertInterpreterEquivalence(
          "inullv.vtl",
          "#if($email)$email#{else}none#{end}",
          TemplateContract.builder(TemplateId.of("inullv.vtl"))
              .parameter("email", String.class)
              .build(),
          new String[] {"email"},
          new Object[] {null});
    }

    @Test
    void foreachLoop() throws IOException {
      assertInterpreterEquivalence(
          "iloop.vtl",
          "#foreach($n in $nums)$n,#end",
          TemplateContract.builder(TemplateId.of("iloop.vtl"))
              .parameter("nums", List.class)
              .build(),
          new String[] {"nums"},
          new Object[] {List.of(1, 2, 3)});
    }
  }

  // --- Helpers ---

  private static void assertAotEquivalence(
      String id, String template, TemplateContract contract, String[] keys, Object[] values)
      throws IOException {
    CompiledTemplate compiled = compileAot(id, template, contract);

    // Render with name-based context
    RenderContext nameCtx = RenderContext.of(buildMap(keys, values));
    StringTemplateOutput nameOut = new StringTemplateOutput();
    compiled.render(nameCtx, nameOut);

    // Render with slotted context
    SlottedRenderContext slottedCtx = RenderContext.slotted(keys, values);
    StringTemplateOutput slottedOut = new StringTemplateOutput();
    compiled.render(slottedCtx, slottedOut);

    assertThat(slottedOut.toString())
        .as("AOT: slotted output must match name-based output for template '%s'", id)
        .isEqualTo(nameOut.toString());
  }

  private static void assertInterpreterEquivalence(
      String id, String template, TemplateContract contract, String[] keys, Object[] values)
      throws IOException {
    SourceText source = SourceText.of(id, template);
    VtlSemanticOptions semOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(ModelSchema.fromContract(contract))
            .build();
    VtlInterpreterOptions interpOptions =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_CORE).build();

    VtlParseResult parseResult = VtlParser.parse(source);
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semOptions);

    VtlInterpreter interpreter = new VtlInterpreter(interpOptions);
    CompiledTemplate compiled = EngineInterpreterBridge.prepareIr(interpreter, ir, source);

    // Render with name-based context
    RenderContext nameCtx = RenderContext.of(buildMap(keys, values));
    StringTemplateOutput nameOut = new StringTemplateOutput();
    compiled.render(nameCtx, nameOut);

    // Render with slotted context
    SlottedRenderContext slottedCtx = RenderContext.slotted(keys, values);
    StringTemplateOutput slottedOut = new StringTemplateOutput();
    compiled.render(slottedCtx, slottedOut);

    assertThat(slottedOut.toString())
        .as("Interpreter: slotted output must match name-based output for template '%s'", id)
        .isEqualTo(nameOut.toString());
  }

  private static CompiledTemplate compileAot(
      String id, String templateText, TemplateContract contract) {
    SourceText source = SourceText.of(id, templateText);
    VtlParseResult parseResult = VtlParser.parse(source);
    VtlSemanticOptions semOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(ModelSchema.fromContract(contract))
            .build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, BackendOptions.defaults());
    if (!result.isSuccess() || result.templateInstance() == null) {
      throw new IllegalStateException("Compilation failed for " + id + ": " + result.diagnostics());
    }
    return result.templateInstance();
  }

  private static Map<String, Object> buildMap(String[] keys, Object[] values) {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    for (int i = 0; i < keys.length; i++) {
      map.put(keys[i], values[i]);
    }
    return map;
  }
}
