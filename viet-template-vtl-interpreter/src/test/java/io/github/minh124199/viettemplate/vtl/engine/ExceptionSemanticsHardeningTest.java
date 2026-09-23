package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.InterpreterDiagnosticCodes;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.TemplateResourceResolver;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verification of Milestone M6.1 exception semantics hardening.
 *
 * <p>Ensures fatal JVM errors (VirtualMachineError/ThreadDeath) escape unmasked across IR and AOT
 * tiers, security denials during dynamic #parse fail closed, and dynamic call site evaluation
 * preserves original application causes unwrapped from InvocationTargetException.
 */
class ExceptionSemanticsHardeningTest {

  public static class FaultyTarget {
    public String throwOom() {
      throw new OutOfMemoryError("Simulated OOM in method");
    }

    public String throwStackOverflow() {
      throw new StackOverflowError("Simulated StackOverflow in method");
    }

    public String getOomProperty() {
      throw new OutOfMemoryError("Simulated OOM in property getter");
    }

    public String getStackOverflowProperty() {
      throw new StackOverflowError("Simulated StackOverflow in property getter");
    }

    public String throwSecurityException() {
      throw new TemplateSecurityException(
          "Direct security denial",
          TemplateId.of("faulty.vtl"),
          SourceSpan.UNKNOWN,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    public String throwAppException() {
      throw new IllegalStateException("Business logic failed");
    }

    public String getAppProperty() {
      throw new IllegalArgumentException("Invalid property value");
    }
  }

  public static class OverloadedTarget {
    public String run(Integer i) {
      return "int:" + i;
    }

    public String run(String s) {
      throw new IllegalStateException("Simulated failure in overloaded string method: " + s);
    }

    public String runFatal(Integer i) {
      return "int:" + i;
    }

    public String runFatal(String s) {
      throw new OutOfMemoryError("Simulated OOM in overloaded string method: " + s);
    }
  }

  @Test
  @DisplayName(
      "OutOfMemoryError escapes without being caught or wrapped in IR tier (LinkedReferenceAccess)")
  void fatalOutOfMemoryErrorEscapesInIrTier() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("oomMethod.vm", "$target.throwOom()");
    repo.put("oomProp.vm", "$target.oomProperty");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
    RenderContext ctx = RenderContext.builder().put("target", new FaultyTarget()).build();

    Template methodTmpl = engine.get("oomMethod.vm");
    assertThatThrownBy(() -> methodTmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in method");

    Template propTmpl = engine.get("oomProp.vm");
    assertThatThrownBy(() -> propTmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in property getter");

    engine.close();
  }

  @Test
  @DisplayName(
      "StackOverflowError escapes without being caught or wrapped in IR tier"
          + " (LinkedReferenceAccess)")
  void fatalStackOverflowErrorEscapesInIrTier() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("soeMethod.vm", "$target.throwStackOverflow()");
    repo.put("soeProp.vm", "$target.stackOverflowProperty");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
    RenderContext ctx = RenderContext.builder().put("target", new FaultyTarget()).build();

    Template methodTmpl = engine.get("soeMethod.vm");
    assertThatThrownBy(() -> methodTmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(StackOverflowError.class)
        .hasMessage("Simulated StackOverflow in method");

    Template propTmpl = engine.get("soeProp.vm");
    assertThatThrownBy(() -> propTmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(StackOverflowError.class)
        .hasMessage("Simulated StackOverflow in property getter");

    engine.close();
  }

  @Test
  @DisplayName("OutOfMemoryError escapes without being caught or wrapped in AOT bytecode tier")
  void fatalOutOfMemoryErrorEscapesInAotBytecodeTier() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("oomMethodAot.vm", "$target.throwOom()");
    repo.put("oomPropAot.vm", "$target.oomProperty");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();
    RenderContext ctx = RenderContext.builder().put("target", new FaultyTarget()).build();

    Template methodTmpl = engine.get("oomMethodAot.vm");
    assertThatThrownBy(() -> methodTmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in method");

    Template propTmpl = engine.get("oomPropAot.vm");
    assertThatThrownBy(() -> propTmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in property getter");

    engine.close();
  }

  @Test
  @DisplayName("OutOfMemoryError escapes from BytecodeRuntimeBridge direct dynamic dispatch")
  void fatalErrorsEscapeDirectBytecodeBridgeCalls() {
    FaultyTarget target = new FaultyTarget();
    DynamicCallSite methodSite =
        new DynamicCallSite(
            101,
            MemberKey.methodCall("throwOom", 0),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    assertThatThrownBy(
            () -> BytecodeRuntimeBridge.dynamicInvokeMethod(methodSite, target, new Object[0]))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in method");

    DynamicCallSite propSite =
        new DynamicCallSite(
            102,
            MemberKey.propertyGet("oomProperty"),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    assertThatThrownBy(() -> BytecodeRuntimeBridge.dynamicGetProperty(propSite, target))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in property getter");
  }

  @Test
  @DisplayName(
      "BytecodeRuntimeBridge reflection fallback unwraps InvocationTargetException for"
          + " RuntimeException")
  void bytecodeBridgeReflectionFallbackUnwrapsInvocationTargetExceptionForRuntimeException() {
    OverloadedTarget target = new OverloadedTarget();
    DynamicCallSite site =
        new DynamicCallSite(
            201,
            MemberKey.methodCall("run", 1),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    // First invoke monomorphically with Integer argument to cache (Integer) signature
    Object intResult =
        BytecodeRuntimeBridge.dynamicInvokeMethod(site, target, new Object[] {Integer.valueOf(10)});
    assertThat(intResult).isEqualTo("int:10");

    // Second invoke with String argument triggers ClassCastException/WrongMethodTypeException in
    // call site,
    // which falls back to reflection and must unwrap InvocationTargetException directly to
    // IllegalStateException
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.dynamicInvokeMethod(
                    site, target, new Object[] {"test-value"}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Simulated failure in overloaded string method: test-value");
  }

  @Test
  @DisplayName(
      "BytecodeRuntimeBridge reflection fallback unwraps InvocationTargetException for fatal Error")
  void bytecodeBridgeReflectionFallbackUnwrapsInvocationTargetExceptionForFatalError() {
    OverloadedTarget target = new OverloadedTarget();
    DynamicCallSite site =
        new DynamicCallSite(
            202,
            MemberKey.methodCall("runFatal", 1),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    // Pre-link monomorphically with Integer
    Object intResult =
        BytecodeRuntimeBridge.dynamicInvokeMethod(site, target, new Object[] {Integer.valueOf(10)});
    assertThat(intResult).isEqualTo("int:10");

    // Dynamic invoke with String falls back to reflection and unwraps OutOfMemoryError
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.dynamicInvokeMethod(
                    site, target, new Object[] {"test-value"}))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in overloaded string method: test-value");
  }

  @Test
  @DisplayName(
      "TemplateSecurityException is propagated directly without being wrapped in"
          + " TemplateRenderException")
  void templateSecurityExceptionDirectlyPropagatesWithoutBeingWrapped() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("securityMethod.vm", "$target.throwSecurityException()");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
    RenderContext ctx = RenderContext.builder().put("target", new FaultyTarget()).build();

    Template tmpl = engine.get("securityMethod.vm");
    assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining("Direct security denial");

    engine.close();
  }

  @Test
  @DisplayName(
      "Engine resource resolver propagates TemplateSecurityException fail-closed in production"
          + " mode")
  void engineResolverPropagatesTemplateSecurityExceptionInProductionMode() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId childId = TemplateId.of("dynamicChild.vm");
    repo.put(childId, "Child content: $data");

    // In hardened production engine with rejectRuntimeCompilation = true
    VtlTemplateEngine productionEngine =
        VtlTemplateEngine.builder().repository(repo).rejectRuntimeCompilation(true).build();

    TemplateResourceResolver resolver = productionEngine.interpreterOptions().resourceResolver();

    // Resolving uncompiled dynamic child in production mode must propagate
    // TemplateSecurityException
    assertThatThrownBy(() -> resolver.resolve(TemplateId.of("parent.vm"), "dynamicChild.vm"))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining(
            "Runtime template compilation is rejected in production mode: dynamicChild.vm");

    productionEngine.close();
  }

  @Test
  @DisplayName("Dynamic #parse propagates TemplateSecurityException during execution")
  void dynamicParsePropagatesTemplateSecurityExceptionDuringExecution() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("parent.vm", "Parent start: #parse($childPath) Parent end");
    repo.put("child.vm", "$target.throwSecurityException()");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
    Template parent = engine.get("parent.vm");

    RenderContext ctx =
        RenderContext.builder()
            .put("childPath", "child.vm")
            .put("target", new FaultyTarget())
            .build();

    assertThatThrownBy(() -> parent.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining("Direct security denial");

    engine.close();
  }

  @Test
  @DisplayName(
      "Dynamic call site evaluation unwraps InvocationTargetException and preserves application"
          + " cause")
  void dynamicCallSiteEvaluationPreservesApplicationCauseInIrTier() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("appMethod.vm", "$target.throwAppException()");
    repo.put("appProp.vm", "$target.appProperty");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
    RenderContext ctx = RenderContext.builder().put("target", new FaultyTarget()).build();

    Template methodTmpl = engine.get("appMethod.vm");
    assertThatThrownBy(() -> methodTmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(TemplateRenderException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .satisfies(
            e -> {
              TemplateRenderException tre = (TemplateRenderException) e;
              assertThat(tre.code())
                  .isPresent()
                  .contains(InterpreterDiagnosticCodes.INVALID_METHOD);
              assertThat(tre.getCause()).hasMessage("Business logic failed");
              assertThat(tre.getMessage()).contains("Business logic failed");
            });

    Template propTmpl = engine.get("appProp.vm");
    assertThatThrownBy(() -> propTmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(TemplateRenderException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .satisfies(
            e -> {
              TemplateRenderException tre = (TemplateRenderException) e;
              assertThat(tre.code())
                  .isPresent()
                  .contains(InterpreterDiagnosticCodes.INVALID_METHOD);
              assertThat(tre.getCause()).hasMessage("Invalid property value");
              assertThat(tre.getMessage()).contains("Invalid property value");
            });

    engine.close();
  }
}
