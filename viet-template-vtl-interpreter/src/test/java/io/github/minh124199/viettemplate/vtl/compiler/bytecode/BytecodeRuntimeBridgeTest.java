package io.github.minh124199.viettemplate.vtl.compiler.bytecode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.UndefinedReferencePolicy;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.runtime.EscapeMode;
import io.github.minh124199.viettemplate.runtime.StandardEscapers;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.MemberOperation;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BytecodeRuntimeBridgeTest {

  @Test
  @DisplayName("IrEscapeMode maps to expected EscapeMode and Escaper for every valid ordinal")
  void testIrEscapeModeMapping() {
    for (IrEscapeMode irMode : IrEscapeMode.values()) {
      int ordinal = irMode.ordinal();
      EscapeMode expectedEscapeMode =
          switch (irMode) {
            case RAW -> EscapeMode.RAW;
            case HTML_TEXT -> EscapeMode.HTML_TEXT;
            case HTML_ATTRIBUTE_QUOTED -> EscapeMode.HTML_ATTRIBUTE_QUOTED;
            case URL_COMPONENT -> EscapeMode.URL_COMPONENT;
          };
      assertThat(BytecodeRuntimeBridge.ESCAPE_MODES_BY_IR_MODE[ordinal])
          .as("EscapeMode for " + irMode)
          .isEqualTo(expectedEscapeMode);

      assertThat(BytecodeRuntimeBridge.ESCAPERS_BY_IR_MODE[ordinal])
          .as("Escaper for " + irMode)
          .isSameAs(StandardEscapers.get(expectedEscapeMode));
    }
  }

  @Test
  @DisplayName("NullRenderMode array matches all valid ordinals")
  void testNullRenderModeMapping() {
    for (NullRenderMode nullMode : NullRenderMode.values()) {
      assertThat(BytecodeRuntimeBridge.NULL_RENDER_MODES[nullMode.ordinal()])
          .as("NullRenderMode for " + nullMode)
          .isEqualTo(nullMode);
    }
  }

  @Test
  @DisplayName("BinaryOpKind array matches all valid ordinals")
  void testBinaryOpMapping() {
    for (BinaryOpKind op : BinaryOpKind.values()) {
      assertThat(BytecodeRuntimeBridge.BINARY_OP_KINDS[op.ordinal()])
          .as("BinaryOpKind for " + op)
          .isEqualTo(op);
    }
  }

  @Test
  @DisplayName("UnaryOpKind array matches all valid ordinals")
  void testUnaryOpMapping() {
    for (UnaryOpKind op : UnaryOpKind.values()) {
      assertThat(BytecodeRuntimeBridge.UNARY_OP_KINDS[op.ordinal()])
          .as("UnaryOpKind for " + op)
          .isEqualTo(op);
    }
  }

  @Test
  @DisplayName("MemberOperation array matches all valid ordinals")
  void testMemberOperationMapping() {
    for (MemberOperation op : MemberOperation.values()) {
      assertThat(BytecodeRuntimeBridge.MEMBER_OPERATIONS[op.ordinal()])
          .as("MemberOperation for " + op)
          .isEqualTo(op);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, -5, 4, 10, 100})
  @DisplayName("writeValue throws ArrayIndexOutOfBoundsException on invalid escapeModeOrdinal")
  void testWriteValueInvalidEscapeModeOrdinalThrows(int invalidEscapeModeOrdinal) {
    TemplateOutput output = new StringTemplateOutput();
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.writeValue(
                    "test",
                    output,
                    invalidEscapeModeOrdinal,
                    NullRenderMode.EMPTY_STRING.ordinal(),
                    "$test",
                    UndefinedReferencePolicy.SILENT,
                    "test.vtl",
                    1,
                    1,
                    1,
                    5,
                    null))
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, -5, 3, 10, 100})
  @DisplayName("writeValue throws ArrayIndexOutOfBoundsException on invalid nullModeOrdinal")
  void testWriteValueInvalidNullModeOrdinalThrows(int invalidNullModeOrdinal) {
    TemplateOutput output = new StringTemplateOutput();
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.writeValue(
                    "test",
                    output,
                    IrEscapeMode.RAW.ordinal(),
                    invalidNullModeOrdinal,
                    "$test",
                    UndefinedReferencePolicy.SILENT,
                    "test.vtl",
                    1,
                    1,
                    1,
                    5,
                    null))
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 50, 100})
  @DisplayName("binaryOp throws ArrayIndexOutOfBoundsException on invalid opOrdinal")
  void testBinaryOpInvalidOrdinalThrows(int invalidOpOrdinal) {
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.binaryOp(
                    1, 2, invalidOpOrdinal, "test.vtl", 1, 1, 1, 5, null))
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 50, 100})
  @DisplayName("unaryOp throws ArrayIndexOutOfBoundsException on invalid opOrdinal")
  void testUnaryOpInvalidOrdinalThrows(int invalidOpOrdinal) {
    assertThatThrownBy(
            () -> BytecodeRuntimeBridge.unaryOp(1, invalidOpOrdinal, "test.vtl", 1, 1, 1, 5))
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 50, 100})
  @DisplayName("createCallSite throws ArrayIndexOutOfBoundsException on invalid operationOrdinal")
  void testCreateCallSiteInvalidOrdinalThrows(int invalidOperationOrdinal) {
    assertThatThrownBy(
            () -> BytecodeRuntimeBridge.createCallSite(1, "prop", invalidOperationOrdinal, 0, null))
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }

  @Test
  @DisplayName("writeValue correctly formats and escapes output across all IrEscapeMode variants")
  void testWriteValueEscapeOutput() throws IOException {
    String input = "<test>&'\"";

    // RAW
    StringTemplateOutput rawOut = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeValue(
        input,
        rawOut,
        IrEscapeMode.RAW.ordinal(),
        NullRenderMode.EMPTY_STRING.ordinal(),
        null,
        UndefinedReferencePolicy.SILENT,
        "test.vtl",
        1,
        1,
        1,
        5,
        null);
    assertThat(rawOut.toString()).isEqualTo("<test>&'\"");

    // HTML_TEXT
    StringTemplateOutput htmlTextOut = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeValue(
        input,
        htmlTextOut,
        IrEscapeMode.HTML_TEXT.ordinal(),
        NullRenderMode.EMPTY_STRING.ordinal(),
        null,
        UndefinedReferencePolicy.SILENT,
        "test.vtl",
        1,
        1,
        1,
        5,
        null);
    assertThat(htmlTextOut.toString()).isEqualTo("&lt;test&gt;&amp;&#39;&quot;");

    // HTML_ATTRIBUTE_QUOTED
    StringTemplateOutput htmlAttrOut = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeValue(
        input,
        htmlAttrOut,
        IrEscapeMode.HTML_ATTRIBUTE_QUOTED.ordinal(),
        NullRenderMode.EMPTY_STRING.ordinal(),
        null,
        UndefinedReferencePolicy.SILENT,
        "test.vtl",
        1,
        1,
        1,
        5,
        null);
    assertThat(htmlAttrOut.toString()).isEqualTo("&lt;test&gt;&amp;&#39;&quot;");

    // URL_COMPONENT
    StringTemplateOutput urlOut = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeValue(
        "hello world&foo=bar",
        urlOut,
        IrEscapeMode.URL_COMPONENT.ordinal(),
        NullRenderMode.EMPTY_STRING.ordinal(),
        null,
        UndefinedReferencePolicy.SILENT,
        "test.vtl",
        1,
        1,
        1,
        5,
        null);
    assertThat(urlOut.toString()).isEqualTo("hello%20world%26foo%3Dbar");
  }

  @Test
  @DisplayName("writeValue handles NullRenderMode correctly")
  void testWriteValueNullHandling() throws IOException {
    // EMPTY_STRING with null
    StringTemplateOutput emptyOut = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeValue(
        null,
        emptyOut,
        IrEscapeMode.RAW.ordinal(),
        NullRenderMode.EMPTY_STRING.ordinal(),
        "$test",
        UndefinedReferencePolicy.SILENT,
        "test.vtl",
        1,
        1,
        1,
        5,
        null);
    assertThat(emptyOut.toString()).isEmpty();

    // LITERAL_EXPRESSION with null
    StringTemplateOutput literalOut = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeValue(
        null,
        literalOut,
        IrEscapeMode.RAW.ordinal(),
        NullRenderMode.LITERAL_EXPRESSION.ordinal(),
        "$test",
        UndefinedReferencePolicy.SILENT,
        "test.vtl",
        1,
        1,
        1,
        5,
        null);
    assertThat(literalOut.toString()).isEqualTo("$test");
  }
}
