package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.UndefinedReferencePolicy;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UndefinedReferencePolicyIntegrationTest extends AbstractInterpreterTest {

  @Test
  @DisplayName(
      "VtlInterpreterOptions builder correctly maps undefinedReferencePolicy and strictReferences")
  void builderOptionsMapping() {
    VtlInterpreterOptions silentOptions =
        VtlInterpreterOptions.builder()
            .undefinedReferencePolicy(UndefinedReferencePolicy.SILENT)
            .build();
    assertThat(silentOptions.undefinedReferencePolicy()).isEqualTo(UndefinedReferencePolicy.SILENT);
    assertThat(silentOptions.strictReferences()).isFalse();

    VtlInterpreterOptions warnOptions =
        VtlInterpreterOptions.builder()
            .undefinedReferencePolicy(UndefinedReferencePolicy.WARN)
            .build();
    assertThat(warnOptions.undefinedReferencePolicy()).isEqualTo(UndefinedReferencePolicy.WARN);
    assertThat(warnOptions.strictReferences()).isFalse();

    VtlInterpreterOptions errorOptions =
        VtlInterpreterOptions.builder()
            .undefinedReferencePolicy(UndefinedReferencePolicy.ERROR)
            .build();
    assertThat(errorOptions.undefinedReferencePolicy()).isEqualTo(UndefinedReferencePolicy.ERROR);
    assertThat(errorOptions.strictReferences()).isTrue();

    // Legacy strictReferences(true) maps to ERROR
    VtlInterpreterOptions legacyStrict =
        VtlInterpreterOptions.builder().strictReferences(true).build();
    assertThat(legacyStrict.undefinedReferencePolicy()).isEqualTo(UndefinedReferencePolicy.ERROR);
    assertThat(legacyStrict.strictReferences()).isTrue();

    // Legacy strictReferences(false) maps to SILENT
    VtlInterpreterOptions legacyNonStrict =
        VtlInterpreterOptions.builder().strictReferences(false).build();
    assertThat(legacyNonStrict.undefinedReferencePolicy())
        .isEqualTo(UndefinedReferencePolicy.SILENT);
    assertThat(legacyNonStrict.strictReferences()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = ExecutionTier.class,
      names = {"AST", "IR"})
  @DisplayName(
      "SILENT policy renders literal or quiet empty without throwing or recording warnings")
  void silentPolicyRendersSafely(ExecutionTier tier) {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder()
            .executionTier(tier)
            .undefinedReferencePolicy(UndefinedReferencePolicy.SILENT)
            .build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    List<Diagnostic> diagnostics = new ArrayList<>();
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("diagnostics", diagnostics);
    ctx.put("definedNull", null);

    assertThat(render("Val: $missing, Quiet: [$!missing]", ctx, interpreter))
        .isEqualTo("Val: $missing, Quiet: []");
    assertThat(render("Null: $definedNull, Quiet: [$!definedNull]", ctx, interpreter))
        .isEqualTo("Null: $definedNull, Quiet: []");
    assertThat(diagnostics).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = ExecutionTier.class,
      names = {"AST", "IR"})
  @DisplayName("WARN policy renders literal or quiet empty AND emits warning diagnostics")
  void warnPolicyEmitsDiagnostics(ExecutionTier tier) {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder()
            .executionTier(tier)
            .undefinedReferencePolicy(UndefinedReferencePolicy.WARN)
            .build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    List<Diagnostic> diagnosticsList = new ArrayList<>();
    List<Diagnostic> consumedList = new ArrayList<>();
    Consumer<Diagnostic> consumer = consumedList::add;

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("diagnostics", diagnosticsList);
    ctx.put("diagnosticConsumer", consumer);
    ctx.put("definedNull", null);

    String rendered = render("Normal: $missingUser, Quiet: [$!missingQuiet]", ctx, interpreter);

    assertThat(rendered).isEqualTo("Normal: $missingUser, Quiet: []");
    assertThat(diagnosticsList).hasSize(2);
    assertThat(consumedList).hasSize(2);

    for (Diagnostic d : diagnosticsList) {
      assertThat(d.severity()).isEqualTo(DiagnosticSeverity.WARNING);
      assertThat(d.code().qualifiedCode()).isEqualTo("INTERPRETER:VARIABLE_UNDEFINED");
      assertThat(d.message()).containsAnyOf("missingUser", "missingQuiet");
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = ExecutionTier.class,
      names = {"AST", "IR"})
  @DisplayName("ERROR policy throws TemplateRenderException on undefined reference")
  void errorPolicyThrowsOnUndefined(ExecutionTier tier) {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder()
            .executionTier(tier)
            .undefinedReferencePolicy(UndefinedReferencePolicy.ERROR)
            .build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    assertThatThrownBy(() -> render("Hello $missingUser!", Map.of(), interpreter))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("missingUser");

    assertThatThrownBy(() -> render("Hello $!missingUser!", Map.of(), interpreter))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("missingUser");
  }

  @ParameterizedTest
  @EnumSource(
      value = ExecutionTier.class,
      names = {"AST", "IR"})
  @DisplayName("ERROR policy throws TemplateRenderException on navigating undefined or null")
  void errorPolicyThrowsOnNavigation(ExecutionTier tier) {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder()
            .executionTier(tier)
            .undefinedReferencePolicy(UndefinedReferencePolicy.ERROR)
            .build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("nullObj", null);

    assertThatThrownBy(() -> render("Val: $nullObj.property", ctx, interpreter))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("Cannot navigate property/method on null or undefined reference");

    assertThatThrownBy(() -> render("Val: $missingObj.property", Map.of(), interpreter))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("Cannot navigate property/method on null or undefined reference");
  }

  @Test
  @DisplayName("BytecodeRuntimeBridge handleUndefinedReference behaviors")
  void bytecodeRuntimeBridgeHandleUndefinedReference() {
    TemplateId templateId = TemplateId.of("test.vm");
    SourceSpan span = SourceSpan.UNKNOWN;

    // SILENT does nothing
    BytecodeRuntimeBridge.handleUndefinedReference(
        "silentVar", templateId, span, UndefinedReferencePolicy.SILENT);

    // WARN logs warning without throwing
    BytecodeRuntimeBridge.handleUndefinedReference(
        "warnVar", templateId, span, UndefinedReferencePolicy.WARN);

    // ERROR throws TemplateRenderException
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.handleUndefinedReference(
                    "errorVar", templateId, span, UndefinedReferencePolicy.ERROR))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("errorVar");
  }
}
