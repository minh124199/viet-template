package io.github.minh124199.viettemplate.vtl.compiler.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullAccessMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.OutputSpecializationContext;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.OutputSpecializationDecider;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.WriteDispatchDecision;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.WriteDispatchKind;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutputSpecializationDeciderTest {

  private static final SourceSpan DUMMY_SPAN = SourceSpan.UNKNOWN;

  public record User(String name, int age) {}

  private static OutputSpecializationContext createContext(
      boolean strict, boolean safeProfile, boolean typed, Set<Integer> nonIntSlots) {
    return new OutputSpecializationContext() {
      @Override
      public boolean isStrict() {
        return strict;
      }

      @Override
      public boolean isSafeProfile() {
        return safeProfile;
      }

      @Override
      public boolean isTyped() {
        return typed;
      }

      @Override
      public boolean isNonIntLocal(int slot) {
        return nonIntSlots != null && nonIntSlots.contains(slot);
      }
    };
  }

  @Test
  @DisplayName("Decides String specialization for string constants, parameters, and access plans")
  void testStringSpecialization() throws Exception {
    OutputSpecializationContext context = createContext(false, false, true, Set.of());

    IrConst stringConst = new IrConst("hello", VTypes.STRING, DUMMY_SPAN);
    WriteDispatchDecision dec1 =
        OutputSpecializationDecider.decide(stringConst, NullRenderMode.EMPTY_STRING, context);
    assertThat(dec1.kind()).isEqualTo(WriteDispatchKind.WRITE_STRING_SPECIALIZED);
    assertThat(dec1.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_STRING);
    assertThat(dec1.specializationRejections()).isEmpty();

    IrLoadParam param = new IrLoadParam("name", 0, VTypes.STRING, DUMMY_SPAN);
    WriteDispatchDecision dec2 =
        OutputSpecializationDecider.decide(param, NullRenderMode.LITERAL_EXPRESSION, context);
    assertThat(dec2.kind()).isEqualTo(WriteDispatchKind.WRITE_STRING_SPECIALIZED);
    assertThat(dec2.specializationRejections()).isEmpty();

    Method nameMethod = User.class.getMethod("name");
    IrGetProperty prop =
        new IrGetProperty(
            param,
            "name",
            VTypes.STRING,
            new AccessPlan.DirectRecord(User.class, "name", String.class, nameMethod),
            NullAccessMode.PROPAGATE_NULL,
            DUMMY_SPAN);
    WriteDispatchDecision dec3 =
        OutputSpecializationDecider.decide(prop, NullRenderMode.EMPTY_STRING, context);
    assertThat(dec3.kind()).isEqualTo(WriteDispatchKind.WRITE_STRING_SPECIALIZED);
    assertThat(dec3.specializationRejections()).isEmpty();
  }

  @Test
  @DisplayName("Decides Integer specialization for int constants, parameters, and local variables")
  void testIntegerSpecialization() {
    OutputSpecializationContext context = createContext(false, false, true, Set.of());

    IrConst intConst = new IrConst(42, VTypes.INT, DUMMY_SPAN);
    WriteDispatchDecision dec1 =
        OutputSpecializationDecider.decide(intConst, NullRenderMode.EMPTY_STRING, context);
    assertThat(dec1.kind()).isEqualTo(WriteDispatchKind.WRITE_INTEGER_SPECIALIZED);
    assertThat(dec1.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_INTEGER);
    assertThat(dec1.specializationRejections()).isEmpty();

    IrLoadLocal local = new IrLoadLocal("count", 1, VTypes.INT, DUMMY_SPAN);
    WriteDispatchDecision dec2 =
        OutputSpecializationDecider.decide(local, NullRenderMode.LITERAL_EXPRESSION, context);
    assertThat(dec2.kind()).isEqualTo(WriteDispatchKind.WRITE_INTEGER_SPECIALIZED);
    assertThat(dec2.specializationRejections()).isEmpty();
  }

  @Test
  @DisplayName("Rejects specialization when strict references or error null handling is active")
  void testStrictReferencesRejection() {
    OutputSpecializationContext strictContext = createContext(true, false, true, Set.of());
    IrConst stringConst = new IrConst("hello", VTypes.STRING, DUMMY_SPAN);

    WriteDispatchDecision dec =
        OutputSpecializationDecider.decide(stringConst, NullRenderMode.EMPTY_STRING, strictContext);
    assertThat(dec.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(dec.selectedPath()).isEqualTo(OutputSpecializationDecider.PATH_WRITE_VALUE);
    assertThat(dec.specializationRejections())
        .contains(OutputSpecializationDecider.REJECTION_STRICT_REFERENCES_OR_ERROR_NULL_HANDLING);

    OutputSpecializationContext nonStrictContext = createContext(false, false, true, Set.of());
    WriteDispatchDecision throwDec =
        OutputSpecializationDecider.decide(
            stringConst, NullRenderMode.THROW_ERROR, nonStrictContext);
    assertThat(throwDec.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(throwDec.specializationRejections())
        .contains(
            OutputSpecializationDecider.REJECTION_STRICT_REFERENCES_OR_ERROR_NULL_HANDLING,
            OutputSpecializationDecider.REJECTION_NULL_MODE_REQUIRES_GENERIC_PATH);
  }

  @Test
  @DisplayName("Rejects specialization when safe profile is active")
  void testSafeProfileRejection() {
    OutputSpecializationContext safeContext = createContext(false, true, true, Set.of());
    IrConst intConst = new IrConst(100, VTypes.INT, DUMMY_SPAN);

    WriteDispatchDecision dec =
        OutputSpecializationDecider.decide(intConst, NullRenderMode.EMPTY_STRING, safeContext);
    assertThat(dec.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(dec.specializationRejections())
        .contains(OutputSpecializationDecider.REJECTION_VTL_SAFE_PROFILE);
  }

  @Test
  @DisplayName("Rejects Integer specialization in untyped templates")
  void testUntypedTemplateRejection() {
    OutputSpecializationContext untypedContext = createContext(false, false, false, Set.of());
    IrConst intConst = new IrConst(100, VTypes.INT, DUMMY_SPAN);

    WriteDispatchDecision dec =
        OutputSpecializationDecider.decide(intConst, NullRenderMode.EMPTY_STRING, untypedContext);
    assertThat(dec.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(dec.specializationRejections())
        .contains(OutputSpecializationDecider.REJECTION_UNTYPED_TEMPLATE);
  }

  @Test
  @DisplayName("Rejects Integer specialization for local variables not proven integer")
  void testNonIntLocalRejection() {
    OutputSpecializationContext context = createContext(false, false, true, Set.of(3));
    IrLoadLocal local = new IrLoadLocal("x", 3, VTypes.INT, DUMMY_SPAN);

    WriteDispatchDecision dec =
        OutputSpecializationDecider.decide(local, NullRenderMode.EMPTY_STRING, context);
    assertThat(dec.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(dec.specializationRejections())
        .contains(OutputSpecializationDecider.REJECTION_LOCAL_VARIABLE_NOT_PROVEN_INTEGER);
  }

  @Test
  @DisplayName("Reports type rejections for non-string, non-integer types and unknown types")
  void testTypeMismatchRejection() {
    OutputSpecializationContext context = createContext(false, false, true, Set.of());

    IrConst doubleConst = new IrConst(3.14, VTypes.DOUBLE, DUMMY_SPAN);
    WriteDispatchDecision dec1 =
        OutputSpecializationDecider.decide(doubleConst, NullRenderMode.EMPTY_STRING, context);
    assertThat(dec1.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(dec1.specializationRejections())
        .contains(
            OutputSpecializationDecider.REJECTION_STATIC_TYPE_NOT_STRING,
            OutputSpecializationDecider.REJECTION_STATIC_TYPE_NOT_INTEGER);

    WriteDispatchDecision dec2 =
        OutputSpecializationDecider.decide(null, NullRenderMode.EMPTY_STRING, context);
    assertThat(dec2.kind()).isEqualTo(WriteDispatchKind.GENERIC_WRITE_VALUE);
    assertThat(dec2.specializationRejections())
        .contains(OutputSpecializationDecider.REJECTION_STATIC_TYPE_UNKNOWN);
  }

  @Test
  @DisplayName("computeNonIntLocalSlots correctly identifies slots with non-int assignments")
  void testComputeNonIntLocalSlots() {
    IrLocal intLocal = new IrLocal("i", VTypes.INT, 1, DUMMY_SPAN);
    IrLocal strLocal = new IrLocal("s", VTypes.STRING, 2, DUMMY_SPAN);

    IrStoreLocal storeInt =
        new IrStoreLocal(intLocal, new IrConst(10, VTypes.INT, DUMMY_SPAN), DUMMY_SPAN);
    IrStoreLocal storeStr =
        new IrStoreLocal(strLocal, new IrConst("text", VTypes.STRING, DUMMY_SPAN), DUMMY_SPAN);

    IrBlock root = new IrBlock(List.of(storeInt, storeStr), DUMMY_SPAN);
    IrTemplate template =
        new IrTemplate(
            TemplateId.of("test.vtl"),
            List.of(),
            root,
            new IrConstantPool(),
            TemplateCapabilities.empty(),
            List.of(),
            DUMMY_SPAN);

    Set<Integer> nonIntSlots = OutputSpecializationDecider.computeNonIntLocalSlots(template, true);
    assertThat(nonIntSlots).contains(2);
    assertThat(nonIntSlots).doesNotContain(1);

    Set<Integer> untypedSlots =
        OutputSpecializationDecider.computeNonIntLocalSlots(template, false);
    assertThat(untypedSlots).isEmpty();
  }
}
