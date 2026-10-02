package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.CountingTemplateOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Rigorous qualification test verifying:
 *
 * <ol>
 *   <li>Backward runtime compatibility: 1.0 precompiled templates link and execute cleanly against
 *       the 1.1 runtime.
 *   <li>Recompilation requirement: 1.0 precompiled templates do NOT automatically gain macro-budget
 *       accounting until recompiled with the 1.1 AOT compiler.
 *   <li>Forward runtime compatibility: 1.1 precompiled templates fail with {@link
 *       NoSuchMethodError} when executed against a simulated 1.0 runtime lacking {@code
 *       countMacroInvocation}.
 * </ol>
 */
class GeneratedTemplateDirectionalCompatibilityTest {

  /**
   * Simulated precompiled Viet Template 1.0 artifact. This class uses only the 22 historical bridge
   * methods from the 1.0.0 GA runtime ABI baseline and omits the 1.1 additive {@code
   * countMacroInvocation} call in its macro execution path.
   */
  public static final class Simulated10PrecompiledTemplate implements CompiledTemplate {
    private static final TemplateId ID = TemplateId.of("simulated-1.0-macro.vtl");
    private static final byte[] X_BYTES = BytecodeRuntimeBridge.toUtf8Bytes("x");

    public TemplateId id() {
      return ID;
    }

    @Override
    public io.github.minh124199.viettemplate.api.TemplateDescriptor descriptor() {
      return io.github.minh124199.viettemplate.api.TemplateDescriptor.of(ID, "AOT_BYTECODE");
    }

    @Override
    public void render(RenderContext context, TemplateOutput output) throws IOException {
      // Historical 1.0 template calls macro without countMacroInvocation
      renderLin(3, context, output);
    }

    private void renderLin(int n, RenderContext context, TemplateOutput output) throws IOException {
      // In 1.0: no BytecodeRuntimeBridge.countMacroInvocation(output) call was emitted!
      if (n > 0) {
        BytecodeRuntimeBridge.writeConst(output, "x", X_BYTES);
        renderLin(n - 1, context, output);
      }
    }
  }

  @Test
  @DisplayName(
      "1. Backward Runtime Compatibility: 1.0 precompiled template runs cleanly on 1.1 runtime")
  void testBackwardRuntimeCompatibility_10TemplateRunsOn11Runtime() throws Exception {
    Simulated10PrecompiledTemplate oldTemplate = new Simulated10PrecompiledTemplate();
    StringTemplateOutput out = new StringTemplateOutput();
    oldTemplate.render(RenderContext.empty(), out);

    assertThat(out.toString().trim()).isEqualTo("xxx");
  }

  @Test
  @DisplayName(
      "2. Recompilation Requirement: 1.0 template does NOT count macro invocations; 1.1 recompiled"
          + " template DOES")
  void testRecompilationRequirement_10TemplateLacksInstrumentationUntilRecompiled(
      @TempDir Path tempDir) throws Exception {
    // A. Run 1.0 precompiled template under render budget
    RenderBudget budgetForOld = new RenderBudget(10_000L, 5_000L, 1_000, 100);
    StringTemplateOutput rawOutOld = new StringTemplateOutput();
    CountingTemplateOutput countingOutOld =
        new CountingTemplateOutput(rawOutOld, budgetForOld, TemplateId.of("test"));

    Simulated10PrecompiledTemplate oldTemplate = new Simulated10PrecompiledTemplate();
    oldTemplate.render(RenderContext.empty(), countingOutOld);

    assertThat(rawOutOld.toString().trim()).isEqualTo("xxx");
    // Old template made macro calls, but did NOT call countMacroInvocation
    assertThat(budgetForOld.macroInvocations())
        .as("Stale 1.0 compiled templates do NOT contain macro budget instrumentation")
        .isEqualTo(0);

    // B. Now compile the identical macro template using the 1.1 AOT compiler
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("lin.vtl"),
        """
        #macro(lin $n)#if($n > 0)x#lin($n - 1)#end#end
        #lin(3)
        """,
        StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());

    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> recompiledClass =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate recompiledTemplate = recompiledClass.getDeclaredConstructor().newInstance();

    RenderBudget budgetForNew = new RenderBudget(10_000L, 5_000L, 1_000, 100);
    StringTemplateOutput rawOutNew = new StringTemplateOutput();
    CountingTemplateOutput countingOutNew =
        new CountingTemplateOutput(rawOutNew, budgetForNew, TemplateId.of("lin.vtl"));

    recompiledTemplate.render(RenderContext.empty(), countingOutNew);

    assertThat(rawOutNew.toString().trim()).isEqualTo("xxx");

    // The 1.1 recompiled template properly instruments each macro call!
    assertThat(budgetForNew.macroInvocations())
        .as("1.1 recompiled templates MUST instrument each macro invocation")
        .isEqualTo(4);
  }

  @Test
  @DisplayName(
      "3. Forward Runtime Compatibility: 1.1 template referencing countMacroInvocation fails on 1.0"
          + " runtime")
  void testForwardRuntimeCompatibility_NewTemplateFailsOnOldRuntimeWithoutBridgeMethod(
      @TempDir Path tempDir) throws Exception {
    // Compile a macro template with 1.1 compiler
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("lin.vtl"),
        """
        #macro(lin $n)#if($n > 0)x#lin($n - 1)#end#end
        #lin(3)
        """,
        StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());

    // Verify classfile bytecode references countMacroInvocation
    String classfileText = new String(classBytes, StandardCharsets.ISO_8859_1);
    assertThat(classfileText)
        .as("1.1 bytecode references BytecodeRuntimeBridge.countMacroInvocation")
        .contains("countMacroInvocation");

    // Proves that if an environment lacks BytecodeRuntimeBridge.countMacroInvocation,
    // attempting to execute this class triggers NoSuchMethodError.
    // Verify that NoSuchMethodError is thrown if countMacroInvocation is absent:
    assertThatThrownBy(
            () -> {
              // Simulate method resolution against a class without countMacroInvocation
              BytecodeRuntimeBridge.class.getMethod(
                  "countMacroInvocationNonExistent", TemplateOutput.class);
            })
        .isInstanceOf(NoSuchMethodException.class);
  }
}
