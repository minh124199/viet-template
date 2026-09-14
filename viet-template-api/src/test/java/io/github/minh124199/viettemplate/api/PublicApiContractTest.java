package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Dedicated contract test suite for public API classes and 3-state context semantics. */
class PublicApiContractTest {

  @Test
  void renderContextPreservesDefinedNullValuesFromMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("presentKey", "hello");
    map.put("nullKey", null);

    RenderContext ctx = RenderContext.of(map);

    assertThat(ctx.contains("presentKey")).isTrue();
    assertThat(ctx.get("presentKey")).isEqualTo("hello");

    // 3-state evaluation: defined null is contained and returns null
    assertThat(ctx.contains("nullKey")).isTrue();
    assertThat(ctx.get("nullKey")).isNull();

    // Undefined key
    assertThat(ctx.contains("absentKey")).isFalse();
    assertThat(ctx.get("absentKey")).isNull();

    assertThat(ctx.keys()).containsExactlyInAnyOrder("presentKey", "nullKey");
  }

  @Test
  void renderContextPreservesDefinedNullInBuilder() {
    RenderContext ctx = RenderContext.builder().put("greeting", "hi").put("nullVar", null).build();

    assertThat(ctx.contains("nullVar")).isTrue();
    assertThat(ctx.get("nullVar")).isNull();
    assertThat(ctx.find("nullVar")).isEqualTo(Optional.empty());

    assertThat(ctx.contains("greeting")).isTrue();
    assertThat(ctx.get("greeting")).isEqualTo("hi");
    assertThat(ctx.find("greeting")).contains("hi");

    assertThat(ctx.contains("missing")).isFalse();
    assertThat(ctx.find("missing")).isEmpty();
  }

  @Test
  void renderContextSingleKeyValueWithNull() {
    RenderContext ctx = RenderContext.of("nullKey", null);
    assertThat(ctx.contains("nullKey")).isTrue();
    assertThat(ctx.get("nullKey")).isNull();
    assertThat(ctx.keys()).containsExactly("nullKey");
  }

  @Test
  void renderContextRejectsNullKeys() {
    assertThatThrownBy(() -> RenderContext.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("map must not be null");

    assertThatThrownBy(() -> RenderContext.of(null, "val"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("key must not be null");

    assertThatThrownBy(() -> RenderContext.builder().put(null, "val"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("key must not be null");

    Map<String, Object> mapWithNullKey = new HashMap<>();
    mapWithNullKey.put(null, "value");

    assertThatThrownBy(() -> RenderContext.of(mapWithNullKey))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("key must not be null");

    assertThatThrownBy(() -> RenderContext.builder().putAll(mapWithNullKey))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("key must not be null");
  }

  @Test
  void mutableRenderContextPreservesDefinedNullSemantics() {
    MutableRenderContext ctx = MutableRenderContext.of();

    ctx.put("definedNull", null);
    assertThat(ctx.contains("definedNull")).isTrue();
    assertThat(ctx.get("definedNull")).isNull();
    assertThat(ctx.keys()).contains("definedNull");

    // Overwrite existing non-null with null
    ctx.put("counter", 42);
    assertThat(ctx.get("counter")).isEqualTo(42);
    ctx.put("counter", null);
    assertThat(ctx.contains("counter")).isTrue();
    assertThat(ctx.get("counter")).isNull();

    // Remove explicitly unsets variable
    ctx.remove("counter");
    assertThat(ctx.contains("counter")).isFalse();
    assertThat(ctx.get("counter")).isNull();

    // Map view contains null mapping
    Map<String, Object> map = ctx.asMap();
    assertThat(map.containsKey("definedNull")).isTrue();
    assertThat(map.get("definedNull")).isNull();
    assertThatThrownBy(() -> map.put("newKey", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void mutableRenderContextInitialMapWithNulls() {
    Map<String, Object> initial = new HashMap<>();
    initial.put("nullVar", null);
    initial.put("active", true);

    MutableRenderContext ctx = MutableRenderContext.of(initial);
    assertThat(ctx.contains("nullVar")).isTrue();
    assertThat(ctx.get("nullVar")).isNull();
    assertThat(ctx.contains("active")).isTrue();
    assertThat(ctx.get("active")).isEqualTo(true);
  }

  @Test
  void mutableRenderContextEnforcesProtectedKeys() {
    MutableRenderContext ctx =
        MutableRenderContext.of(Map.of("readOnly", "initial"), Set.of("readOnly"));

    assertThatThrownBy(() -> ctx.put("readOnly", "modified"))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining("Cannot overwrite protected context variable")
        .matches(
            e ->
                ((TemplateSecurityException) e).code().isPresent()
                    && ((TemplateSecurityException) e)
                        .code()
                        .get()
                        .id()
                        .equals("PROTECTED_VARIABLE"));

    assertThatThrownBy(() -> ctx.put("readOnly", null))
        .isInstanceOf(TemplateSecurityException.class);

    assertThatThrownBy(() -> ctx.remove("readOnly"))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining("Cannot remove protected context variable");

    assertThatThrownBy(() -> ctx.put(null, "val")).isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> ctx.remove(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void templateIdContracts() {
    TemplateId id1 = TemplateId.of("views/home.vm");
    TemplateId id2 = TemplateId.normalize("/views//home.vm");
    TemplateId id3 = TemplateId.of("views/about.vm");

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    assertThat(id1.value()).isEqualTo("views/home.vm");
    assertThat(id1.toString()).isEqualTo("views/home.vm");

    // Comparable contract
    assertThat(id3.compareTo(id1)).isLessThan(0);
    assertThat(id1.compareTo(id3)).isGreaterThan(0);
    assertThat(id1.compareTo(id2)).isEqualTo(0);

    // Rejection of null, blanks, and null bytes
    assertThatThrownBy(() -> TemplateId.of(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> TemplateId.of("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TemplateId.of("   ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TemplateId.of("path/to\0/template.vm"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null byte");

    // Path traversal rejection
    assertThatThrownBy(() -> TemplateId.of("../secret.vm"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TemplateId.of("a/../b.vm"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sourceSpanContracts() {
    SourceSpan unknown = SourceSpan.UNKNOWN;
    assertThat(unknown.isKnown()).isFalse();
    assertThat(unknown.length()).isEqualTo(0);

    SourceSpan span = SourceSpan.of(10, 25, 2, 5, 2, 20);
    assertThat(span.isKnown()).isTrue();
    assertThat(span.length()).isEqualTo(15);
    assertThat(span.startOffset()).isEqualTo(10);
    assertThat(span.endOffset()).isEqualTo(25);
    assertThat(span.startLine()).isEqualTo(2);
    assertThat(span.startColumn()).isEqualTo(5);
    assertThat(span.endLine()).isEqualTo(2);
    assertThat(span.endColumn()).isEqualTo(20);

    // Negative coordinates rejected
    assertThatThrownBy(() -> SourceSpan.of(-2, 10, 1, 1, 1, 5))
        .isInstanceOf(IllegalArgumentException.class);

    // startOffset > endOffset rejected
    assertThatThrownBy(() -> SourceSpan.of(20, 10, 1, 1, 1, 5))
        .isInstanceOf(IllegalArgumentException.class);

    // start position after end position rejected
    assertThatThrownBy(() -> SourceSpan.of(0, 10, 2, 5, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void diagnosticCodeAndDiagnosticContracts() {
    DiagnosticCode code = DiagnosticCode.of("SECURITY", "ACCESS_DENIED");
    assertThat(code.category()).isEqualTo("SECURITY");
    assertThat(code.id()).isEqualTo("ACCESS_DENIED");
    assertThat(code.qualifiedCode()).isEqualTo("SECURITY:ACCESS_DENIED");
    assertThat(code.toString()).isEqualTo("SECURITY:ACCESS_DENIED");

    assertThatThrownBy(() -> DiagnosticCode.of(null, "ID"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> DiagnosticCode.of("CAT", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> DiagnosticCode.of(" ", "ID"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiagnosticCode.of("CAT", " "))
        .isInstanceOf(IllegalArgumentException.class);

    SourceSpan span = SourceSpan.of(0, 5, 1, 1, 1, 6);
    Diagnostic error = Diagnostic.error(code, "Denied access", span);
    assertThat(error.severity()).isEqualTo(DiagnosticSeverity.ERROR);
    assertThat(error.code()).isEqualTo(code);
    assertThat(error.message()).isEqualTo("Denied access");
    assertThat(error.primarySpan()).isEqualTo(span);

    Diagnostic warning = Diagnostic.warning(code, "Warn msg", span);
    assertThat(warning.severity()).isEqualTo(DiagnosticSeverity.WARNING);

    Diagnostic info = Diagnostic.info(code, "Info msg", span);
    assertThat(info.severity()).isEqualTo(DiagnosticSeverity.INFO);
  }

  @Test
  void templateExceptionHierarchyContracts() {
    TemplateId id = TemplateId.of("pages/index.vm");
    SourceSpan span = SourceSpan.of(0, 10, 1, 1, 1, 11);
    DiagnosticCode code = DiagnosticCode.of("SECURITY", "ACCESS_DENIED");

    TemplateSecurityException ex =
        new TemplateSecurityException("Unauthorized access", id, span, code);
    assertThat(ex).isInstanceOf(TemplateException.class);
    assertThat(ex.templateId()).contains(id);
    assertThat(ex.span()).isEqualTo(span);
    assertThat(ex.code()).contains(code);
    assertThat(ex.getMessage()).contains("[SECURITY:ACCESS_DENIED]");
    assertThat(ex.getMessage()).contains("pages/index.vm:1:1");
    assertThat(ex.getMessage()).contains("Unauthorized access");

    TemplateResourceException resEx =
        new TemplateResourceException(
            "Not found", id, SourceSpan.UNKNOWN, DiagnosticCode.of("RESOURCE", "NOT_FOUND"));
    assertThat(resEx).isInstanceOf(TemplateException.class);
    assertThat(resEx.code()).contains(DiagnosticCode.of("RESOURCE", "NOT_FOUND"));
  }
}
