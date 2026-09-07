package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MacroInliningPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private MacroInliningPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new MacroInliningPass();
  }

  @Test
  @DisplayName("inlines small static macro within budget and remaps parameter slots")
  void inlinesSmallMacro() {
    int c1 = pool.registerText("Hello ", span);
    IrParameter param = new IrParameter("name", VTypes.STRING, 0, span);

    IrFunction macroFn =
        new IrFunction(
            "greet", List.of(param), List.of(), IrBlock.of(span, new IrWriteConst(c1, span)), span);

    IrCallMacro call =
        new IrCallMacro(
            "greet", List.of(new IrConst("Alice", VTypes.STRING, span)), Optional.empty(), span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("inline.vm"),
            List.of(),
            IrBlock.of(span, call),
            pool,
            TemplateCapabilities.empty(),
            List.of(macroFn),
            span);

    IrTemplate optimized = pass.run(template, context);

    // Call statement should be replaced with parameter store + inlined macro body
    assertThat(optimized.root().statements()).hasSize(2);
    assertThat(optimized.root().statements().get(0)).isInstanceOf(IrStoreLocal.class);
    assertThat(optimized.root().statements().get(1)).isInstanceOf(IrWriteConst.class);
    assertThat(context.statistics().macrosInlined()).isEqualTo(1);
  }

  @Test
  @DisplayName("does not inline recursive macros")
  void doesNotInliningRecursiveMacro() {
    IrCallMacro recursiveCall = new IrCallMacro("rec", List.of(), Optional.empty(), span);
    IrFunction recFn =
        new IrFunction("rec", List.of(), List.of(), IrBlock.of(span, recursiveCall), span);

    IrCallMacro call = new IrCallMacro("rec", List.of(), Optional.empty(), span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("rec.vm"),
            List.of(),
            IrBlock.of(span, call),
            pool,
            TemplateCapabilities.empty(),
            List.of(recFn),
            span);

    IrTemplate optimized = pass.run(template, context);
    // Should NOT inline recursive call
    assertThat(optimized.root().statements()).hasSize(1);
    assertThat(optimized.root().statements().get(0)).isInstanceOf(IrCallMacro.class);
    assertThat(context.statistics().macrosInlined()).isEqualTo(0);
  }
}
