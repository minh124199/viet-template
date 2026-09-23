package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AssignVariableSlots} (O45 mandatory slot-assignment pass).
 *
 * <p>This test lives in the same package as {@link AssignVariableSlots} to exercise the
 * package-private class directly while preserving encapsulation at the module boundary.
 */
class AssignVariableSlotsTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private AssignVariableSlots pass;
  private OptimizationContext ctx;

  @BeforeEach
  void setUp() {
    pass = new AssignVariableSlots();
    IrConstantPool pool = new IrConstantPool();
    ctx = new OptimizationContext(IrOptimizationOptions.o0(), pool);
  }

  @Test
  @DisplayName("pass name is 'AssignVariableSlots'")
  void passNameIsCorrect() {
    assertThat(pass.name()).isEqualTo("AssignVariableSlots");
  }

  @Test
  @DisplayName("well-formed template with unique parameter slots passes validation unchanged")
  void wellFormedTemplatePassesValidation() {
    IrParameter paramX = new IrParameter("x", VTypes.STRING, 0, span);
    IrParameter paramY = new IrParameter("y", VTypes.INT, 1, span);
    IrTemplate template =
        new IrTemplate(
            TemplateId.of("valid.vtl"),
            List.of(paramX, paramY),
            IrBlock.of(span, new IrNoOp(span)),
            new IrConstantPool(),
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate result = pass.run(template, ctx);
    assertThat(result).isSameAs(template);
  }

  @Test
  @DisplayName("empty parameter list passes validation")
  void emptyParameterListPasses() {
    IrTemplate template =
        new IrTemplate(
            TemplateId.of("empty.vtl"),
            List.of(),
            IrBlock.of(span, new IrNoOp(span)),
            new IrConstantPool(),
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate result = pass.run(template, ctx);
    assertThat(result).isSameAs(template);
  }

  @Test
  @DisplayName("duplicate slot for distinct parameters throws IllegalStateException")
  void duplicateSlotForDistinctParametersThrows() {
    // Both 'x' and 'y' share slot 0 — malformed IR
    IrParameter paramA = new IrParameter("x", VTypes.STRING, 0, span);
    IrParameter paramB = new IrParameter("y", VTypes.STRING, 0, span);
    IrTemplate malformed =
        new IrTemplate(
            TemplateId.of("malformed.vtl"),
            List.of(paramA, paramB),
            IrBlock.of(span, new IrNoOp(span)),
            new IrConstantPool(),
            TemplateCapabilities.empty(),
            List.of(),
            span);

    assertThatThrownBy(() -> pass.run(malformed, ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("aliases bindings 'x' and 'y'");
  }

  @Test
  @DisplayName("single parameter with slot 0 is accepted")
  void singleParameterSlotZeroAccepted() {
    IrParameter param = new IrParameter("data", VTypes.OBJECT, 0, span);
    IrTemplate template =
        new IrTemplate(
            TemplateId.of("single.vtl"),
            List.of(param),
            IrBlock.of(span, new IrNoOp(span)),
            new IrConstantPool(),
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate result = pass.run(template, ctx);
    assertThat(result).isSameAs(template);
  }
}
