package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.internal.ir.plan.BudgetKind;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBudgetCheck;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.CountingTemplateOutput;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MacroWorkExhaustionParityTest {

  private static String renderWithTier(
      String templateText,
      ExecutionTier tier,
      VtlInterpreterOptions customOptions,
      TemplateOutput output)
      throws IOException {
    SourceText source = SourceText.of("test.vm", templateText);
    VtlTemplate ast = VtlParser.parse(source).template();
    VtlInterpreterOptions options =
        customOptions != null
            ? customOptions.toBuilder().executionTier(tier).build()
            : VtlInterpreterOptions.builder().executionTier(tier).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);
    interpreter.interpret(ast, source, RenderContext.empty(), output);
    return output.toString();
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName(
      "Zero-output binary macro recursion aborts gracefully across AST, IR, and Bytecode tiers")
  void binaryMacroRecursionAbortsAcrossTiers(ExecutionTier tier) {
    String template = "#macro(bin $n)#if($n > 0)#bin($n - 1)#bin($n - 1)#end#end#bin(20)";

    StringTemplateOutput output = new StringTemplateOutput();

    // Verify execution terminates with TemplateLimitException rapidly (well within timeout)
    assertThatThrownBy(() -> renderWithTier(template, tier, null, output))
        .isInstanceOf(TemplateLimitException.class);
  }

  @Test
  @DisplayName(
      "Linear macro recursion exceeding maxMacroInvocations limit throws TemplateLimitException in"
          + " AST tier")
  void linearMacroRecursionExceedsLimitInAst() {
    String template = "#macro(lin $n)#if($n > 0)#lin($n - 1)#end#end#lin(15)";
    RenderBudget budget = new RenderBudget(10_000_000L, 0L, 100_000, 10);
    CountingTemplateOutput countingOutput =
        new CountingTemplateOutput(new StringTemplateOutput(), budget, TemplateId.of("test.vm"));

    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().executionTier(ExecutionTier.AST).build();

    assertThatThrownBy(() -> renderWithTier(template, ExecutionTier.AST, options, countingOutput))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("Exceeded maximum macro invocations limit: 10");

    assertThat(budget.macroInvocations()).isGreaterThan(10);
  }

  @Test
  @DisplayName(
      "Linear macro recursion exceeding maxMacroInvocations limit throws TemplateLimitException in"
          + " IR tier")
  void linearMacroRecursionExceedsLimitInIr() {
    String template = "#macro(lin $n)#if($n > 0)#lin($n - 1)#end#end#lin(15)";
    RenderBudget budget = new RenderBudget(10_000_000L, 0L, 100_000, 10);
    CountingTemplateOutput countingOutput =
        new CountingTemplateOutput(new StringTemplateOutput(), budget, TemplateId.of("test.vm"));

    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().executionTier(ExecutionTier.IR).build();

    assertThatThrownBy(() -> renderWithTier(template, ExecutionTier.IR, options, countingOutput))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("Exceeded maximum macro invocations limit: 10");

    assertThat(budget.macroInvocations()).isGreaterThan(10);
  }

  @Test
  @DisplayName("Linear macro recursion within maxMacroInvocations limit succeeds across tiers")
  void linearMacroRecursionWithinLimitSucceeds() throws IOException {
    String template = "#macro(lin $n)#if($n > 0)x#lin($n - 1)#end#end#lin(5)";

    for (ExecutionTier tier : ExecutionTier.values()) {
      StringTemplateOutput output = new StringTemplateOutput();
      String result = renderWithTier(template, tier, null, output);
      assertThat(result).as(tier.name()).isEqualTo("xxxxx");
    }
  }

  @Test
  @DisplayName("VTL_SAFE profile defaults to 5000ms wall-clock deadline when unspecified")
  void vtlSafeDefaultsTo5000msDeadline() {
    VtlInterpreterOptions defaultSafeOptions =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_SAFE).build();
    assertThat(defaultSafeOptions.limits().maxExecutionTimeMillis()).isEqualTo(5000L);

    // Verify explicit deadline is not overwritten
    ExecutionLimits explicitLimits =
        ExecutionLimits.builder().maxExecutionTimeMillis(10_000L).build();
    VtlInterpreterOptions explicitSafeOptions =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_SAFE).limits(explicitLimits).build();
    assertThat(explicitSafeOptions.limits().maxExecutionTimeMillis()).isEqualTo(10_000L);

    // Verify non-SAFE profile preserves 0L
    VtlInterpreterOptions coreOptions =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_CORE).build();
    assertThat(coreOptions.limits().maxExecutionTimeMillis()).isEqualTo(0L);
  }

  @Test
  @DisplayName("VTL_SAFE wall-clock deadline aborts long-running execution")
  void vtlSafeWallClockDeadlineAborts() {
    // 20ms deadline
    ExecutionLimits tightLimits = ExecutionLimits.builder().maxExecutionTimeMillis(20L).build();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_SAFE).limits(tightLimits).build();

    // A template with many loop iterations that exceeds 20ms
    String template = "#foreach($i in [1..10000])#set($x = $i * $i)#end";

    for (ExecutionTier tier : ExecutionTier.values()) {
      CountingTemplateOutput output =
          new CountingTemplateOutput(
              new StringTemplateOutput(),
              options.limits().createRenderBudget(),
              TemplateId.of("deadline_test"));

      // Pre-expire deadline to ensure deterministic deadline check
      try {
        Thread.sleep(25);
      } catch (InterruptedException ignored) {
      }

      assertThatThrownBy(() -> renderWithTier(template, tier, options, output))
          .isInstanceOf(TemplateLimitException.class)
          .satisfies(
              ex -> {
                TemplateLimitException tle = (TemplateLimitException) ex;
                assertThat(tle.code()).contains(RenderBudget.CODE_TIME_LIMIT);
              });
    }
  }

  @Test
  @DisplayName("IrBudgetCheck checks deadline in bytecode execution")
  void irBudgetCheckChecksDeadlineInBytecode() throws IOException {
    IrBlock root =
        new IrBlock(
            List.of(new IrBudgetCheck(BudgetKind.LOOP_ITERATIONS, SourceSpan.UNKNOWN)),
            SourceSpan.UNKNOWN);

    IrTemplate irTemplate =
        new IrTemplate(
            TemplateId.of("budget_check_test"),
            List.of(),
            root,
            new IrConstantPool(),
            TemplateCapabilities.empty(),
            List.of(),
            SourceSpan.UNKNOWN);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendOptions backendOptions = BackendOptions.builder().build();
    BackendResult result = compiler.compile(irTemplate, backendOptions);

    assertThat(result.isSuccess()).isTrue();
    CompiledTemplate compiled = result.compiledTemplate().orElseThrow();

    // 1. Valid budget: passes without exception
    RenderBudget validBudget = new RenderBudget(1000L, 5000L, 100);
    CountingTemplateOutput validOutput =
        new CountingTemplateOutput(new StringTemplateOutput(), validBudget, irTemplate.id());
    assertThatCode(() -> compiled.render(RenderContext.empty(), validOutput))
        .doesNotThrowAnyException();
    assertThat(validBudget.loopIterations()).isEqualTo(1L);

    // 2. Expired deadline: throws TemplateLimitException with CODE_TIME_LIMIT
    RenderBudget expiredBudget = new RenderBudget(1000L, 1L, 100);
    try {
      Thread.sleep(10);
    } catch (InterruptedException ignored) {
    }

    CountingTemplateOutput expiredOutput =
        new CountingTemplateOutput(new StringTemplateOutput(), expiredBudget, irTemplate.id());
    assertThatThrownBy(() -> compiled.render(RenderContext.empty(), expiredOutput))
        .isInstanceOf(TemplateLimitException.class)
        .satisfies(
            ex -> {
              TemplateLimitException tle = (TemplateLimitException) ex;
              assertThat(tle.code()).contains(RenderBudget.CODE_TIME_LIMIT);
            });
  }
}
