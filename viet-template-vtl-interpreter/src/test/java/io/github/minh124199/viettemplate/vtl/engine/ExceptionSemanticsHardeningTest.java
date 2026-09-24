package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.FilesystemTemplateRepository;
import io.github.minh124199.viettemplate.api.FreshnessToken;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateCompilationException;
import io.github.minh124199.viettemplate.api.TemplateFreshnessProvider;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.api.TemplateSource;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.InterpreterDiagnosticCodes;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.TemplateResourceResolver;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    public String throwLimitException() {
      throw new TemplateLimitException(
          "Direct limit exceeded",
          TemplateId.of("faulty.vtl"),
          SourceSpan.UNKNOWN,
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
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
      if (i < 0) {
        throw new IllegalStateException("Simulated failure in overloaded integer method: " + i);
      }
      return "int:" + i;
    }

    public String run(String s) {
      if ("error".equals(s)) {
        throw new IllegalStateException("Simulated failure in overloaded string method: " + s);
      }
      return "str:" + s;
    }

    public String runFatal(Integer i) {
      if (i < 0) {
        throw new OutOfMemoryError("Simulated OOM in overloaded integer method: " + i);
      }
      return "int:" + i;
    }

    public String runFatal(String s) {
      if ("error".equals(s)) {
        throw new OutOfMemoryError("Simulated OOM in overloaded string method: " + s);
      }
      return "str:" + s;
    }

    public String runStackOverflow(Integer i) {
      if (i < 0) {
        throw new StackOverflowError("Simulated StackOverflow in overloaded integer method: " + i);
      }
      return "int:" + i;
    }

    public String runStackOverflow(String s) {
      if ("error".equals(s)) {
        throw new StackOverflowError("Simulated StackOverflow in overloaded string method: " + s);
      }
      return "str:" + s;
    }

    public String runSuccess(Integer i) {
      return "int:" + i;
    }

    public String runSuccess(String s) {
      return "str:" + s;
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
  @DisplayName("Fatal JVM errors escape from BytecodeRuntimeBridge direct dynamic dispatch")
  void fatalErrorsEscapeDirectBytecodeBridgeCalls() {
    FaultyTarget target = new FaultyTarget();
    DynamicCallSite methodSiteOom =
        new DynamicCallSite(
            101,
            MemberKey.methodCall("throwOom", 0),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    assertThatThrownBy(
            () -> BytecodeRuntimeBridge.dynamicInvokeMethod(methodSiteOom, target, new Object[0]))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in method");

    DynamicCallSite propSiteOom =
        new DynamicCallSite(
            102,
            MemberKey.propertyGet("oomProperty"),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    assertThatThrownBy(() -> BytecodeRuntimeBridge.dynamicGetProperty(propSiteOom, target))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in property getter");

    DynamicCallSite methodSiteSoe =
        new DynamicCallSite(
            103,
            MemberKey.methodCall("throwStackOverflow", 0),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    assertThatThrownBy(
            () -> BytecodeRuntimeBridge.dynamicInvokeMethod(methodSiteSoe, target, new Object[0]))
        .isInstanceOf(StackOverflowError.class)
        .hasMessage("Simulated StackOverflow in method");

    DynamicCallSite propSiteSoe =
        new DynamicCallSite(
            104,
            MemberKey.propertyGet("stackOverflowProperty"),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    assertThatThrownBy(() -> BytecodeRuntimeBridge.dynamicGetProperty(propSiteSoe, target))
        .isInstanceOf(StackOverflowError.class)
        .hasMessage("Simulated StackOverflow in property getter");
  }

  @Test
  @DisplayName(
      "BytecodeRuntimeBridge direct MethodHandle dispatch wraps RuntimeException in"
          + " TemplateRenderException")
  void bytecodeBridgeDirectMethodHandleDispatchWrapsRuntimeExceptionInTemplateRenderException() {
    FaultyTarget target = new FaultyTarget();
    DynamicCallSite appSite =
        new DynamicCallSite(
            105,
            MemberKey.methodCall("throwAppException", 0),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    assertThatThrownBy(
            () -> BytecodeRuntimeBridge.dynamicInvokeMethod(appSite, target, new Object[0]))
        .isInstanceOf(TemplateRenderException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .satisfies(
            e -> {
              TemplateRenderException tre = (TemplateRenderException) e;
              assertThat(tre.code())
                  .isPresent()
                  .contains(InterpreterDiagnosticCodes.INVALID_METHOD);
              assertThat(tre.getCause()).hasMessage("Business logic failed");
            });
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

    // Determine the overload linked initially by DynamicLinker
    Class<?> linkedType = site.resolveLink(target.getClass()).handle().type().parameterType(1);
    Object mhArg = linkedType == Integer.class ? Integer.valueOf(10) : "test";
    String expectedMhResult = linkedType == Integer.class ? "int:10" : "str:test";
    Object fallbackFailArg = linkedType == Integer.class ? "error" : Integer.valueOf(-1);
    String expectedFallbackError =
        linkedType == Integer.class
            ? "Simulated failure in overloaded string method: error"
            : "Simulated failure in overloaded integer method: -1";

    // 1. Initial invoke matches cached signature (MethodHandle path)
    Object initialResult =
        BytecodeRuntimeBridge.dynamicInvokeMethod(site, target, new Object[] {mhArg});
    assertThat(initialResult).isEqualTo(expectedMhResult);

    // 2. Invoke with incompatible argument type triggers ClassCastException in call site,
    // which falls back to reflection and normalizes InvocationTargetException into
    // TemplateRenderException
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.dynamicInvokeMethod(
                    site, target, new Object[] {fallbackFailArg}))
        .isInstanceOf(TemplateRenderException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .satisfies(
            e -> {
              TemplateRenderException tre = (TemplateRenderException) e;
              assertThat(tre.code()).contains(InterpreterDiagnosticCodes.INVALID_METHOD);
              assertThat(tre.getCause()).hasMessage(expectedFallbackError);
            });
  }

  @Test
  @DisplayName(
      "BytecodeRuntimeBridge reflection fallback unwraps InvocationTargetException for fatal"
          + " Errors")
  void bytecodeBridgeReflectionFallbackUnwrapsInvocationTargetExceptionForFatalError() {
    OverloadedTarget target = new OverloadedTarget();
    DynamicCallSite site =
        new DynamicCallSite(
            202,
            MemberKey.methodCall("runFatal", 1),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    Class<?> linkedType = site.resolveLink(target.getClass()).handle().type().parameterType(1);
    Object mhArg = linkedType == Integer.class ? Integer.valueOf(10) : "test";
    String expectedMhResult = linkedType == Integer.class ? "int:10" : "str:test";
    Object fallbackFatalArg = linkedType == Integer.class ? "error" : Integer.valueOf(-1);
    String expectedOomMessage =
        linkedType == Integer.class
            ? "Simulated OOM in overloaded string method: error"
            : "Simulated OOM in overloaded integer method: -1";

    // Pre-link monomorphically with initial argument type
    Object initialResult =
        BytecodeRuntimeBridge.dynamicInvokeMethod(site, target, new Object[] {mhArg});
    assertThat(initialResult).isEqualTo(expectedMhResult);

    // Dynamic invoke with incompatible type falls back to reflection and unwraps OutOfMemoryError
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.dynamicInvokeMethod(
                    site, target, new Object[] {fallbackFatalArg}))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage(expectedOomMessage);

    DynamicCallSite soeSite =
        new DynamicCallSite(
            203,
            MemberKey.methodCall("runStackOverflow", 1),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    Class<?> soeLinkedType =
        soeSite.resolveLink(target.getClass()).handle().type().parameterType(1);
    Object soeMhArg = soeLinkedType == Integer.class ? Integer.valueOf(10) : "test";
    String expectedSoeMhResult = soeLinkedType == Integer.class ? "int:10" : "str:test";
    Object soeFallbackArg = soeLinkedType == Integer.class ? "error" : Integer.valueOf(-1);
    String expectedSoeMessage =
        soeLinkedType == Integer.class
            ? "Simulated StackOverflow in overloaded string method: error"
            : "Simulated StackOverflow in overloaded integer method: -1";

    // Pre-link monomorphically
    Object soeInitialResult =
        BytecodeRuntimeBridge.dynamicInvokeMethod(soeSite, target, new Object[] {soeMhArg});
    assertThat(soeInitialResult).isEqualTo(expectedSoeMhResult);

    // Dynamic invoke with incompatible type falls back to reflection and unwraps StackOverflowError
    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.dynamicInvokeMethod(
                    soeSite, target, new Object[] {soeFallbackArg}))
        .isInstanceOf(StackOverflowError.class)
        .hasMessage(expectedSoeMessage);
  }

  @Test
  @DisplayName("BytecodeRuntimeBridge reflection fallback selects valid overload correctly")
  void bytecodeBridgeReflectionFallbackSelectsValidOverloadCorrectly() {
    OverloadedTarget target = new OverloadedTarget();
    DynamicCallSite site =
        new DynamicCallSite(
            204,
            MemberKey.methodCall("runSuccess", 1),
            LinkerAccessPolicy.standard(),
            new DynamicLinker(),
            null);

    Class<?> linkedType = site.resolveLink(target.getClass()).handle().type().parameterType(1);
    Object mhArg = linkedType == Integer.class ? Integer.valueOf(42) : "first";
    String expectedMhResult = linkedType == Integer.class ? "int:42" : "str:first";
    Object fallbackArg = linkedType == Integer.class ? "fallback-value" : Integer.valueOf(99);
    String expectedFallbackResult = linkedType == Integer.class ? "str:fallback-value" : "int:99";

    // Pre-link monomorphically
    Object initialResult =
        BytecodeRuntimeBridge.dynamicInvokeMethod(site, target, new Object[] {mhArg});
    assertThat(initialResult).isEqualTo(expectedMhResult);

    // Dynamic invoke with incompatible argument type falls back to reflection and executes valid
    // overload
    Object fallbackResult =
        BytecodeRuntimeBridge.dynamicInvokeMethod(site, target, new Object[] {fallbackArg});
    assertThat(fallbackResult).isEqualTo(expectedFallbackResult);
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

  @Test
  @DisplayName("Repository I/O failure (TemplateResourceException) does not poison negative cache")
  void repositoryResourceExceptionDoesNotPoisonNegativeCache() throws IOException {
    AtomicBoolean shouldFail = new AtomicBoolean(true);
    TemplateId id = TemplateId.of("recoverableResource.vm");

    TemplateRepository repo =
        new TemplateRepository() {
          @Override
          public Optional<TemplateSource> find(TemplateId reqId) {
            if (reqId.equals(id)) {
              if (shouldFail.get()) {
                throw new TemplateResourceException(
                    "Simulated storage I/O failure: " + reqId.value(),
                    reqId,
                    SourceSpan.UNKNOWN,
                    DiagnosticCode.of("RESOURCE", "IO_FAILURE"));
              }
              return Optional.of(TemplateSource.fromString(id, "Recovered resource: $data"));
            }
            return Optional.empty();
          }
        };

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      // 1. Initial attempt fails with TemplateResourceException
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("Simulated storage I/O failure");

      // Verify that negative cache was NOT poisoned
      assertThat(engine.cache().isNegativelyCached(id)).isFalse();

      // 2. Fix the underlying repository issue
      shouldFail.set(false);

      // 3. Subsequent lookup succeeds immediately without waiting for negative cache TTL
      Template template = engine.get(id);
      assertThat(template).isNotNull();

      StringTemplateOutput out = new StringTemplateOutput();
      template.render(RenderContext.of(Map.of("data", "Success")), out);
      assertThat(out.toString()).isEqualTo("Recovered resource: Success");
      assertThat(engine.cache().isNegativelyCached(id)).isFalse();
    }
  }

  @Test
  @DisplayName(
      "Repository security denial (TemplateSecurityException) does not poison negative cache")
  void repositorySecurityExceptionDoesNotPoisonNegativeCache() throws IOException {
    AtomicBoolean denied = new AtomicBoolean(true);
    TemplateId id = TemplateId.of("recoverableSecurity.vm");

    TemplateRepository repo =
        new TemplateRepository() {
          @Override
          public Optional<TemplateSource> find(TemplateId reqId) {
            if (reqId.equals(id)) {
              if (denied.get()) {
                throw new TemplateSecurityException(
                    "Simulated permission denial: " + reqId.value(), reqId, SourceSpan.UNKNOWN);
              }
              return Optional.of(TemplateSource.fromString(id, "Authorized: $role"));
            }
            return Optional.empty();
          }
        };

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      // 1. Initial attempt fails with TemplateSecurityException
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateSecurityException.class)
          .hasMessageContaining("Simulated permission denial");

      // Verify negative cache is NOT poisoned
      assertThat(engine.cache().isNegativelyCached(id)).isFalse();

      // 2. Grant permission
      denied.set(false);

      // 3. Subsequent call succeeds immediately
      Template template = engine.get(id);
      assertThat(template).isNotNull();

      StringTemplateOutput out = new StringTemplateOutput();
      template.render(RenderContext.of(Map.of("role", "Admin")), out);
      assertThat(out.toString()).isEqualTo("Authorized: Admin");
      assertThat(engine.cache().isNegativelyCached(id)).isFalse();
    }
  }

  @Test
  @DisplayName("Ordinary repository not found (Optional.empty) is recorded in negative cache")
  void ordinaryNotFoundIsRecordedInNegativeCache() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("genuinelyMissing.vm");

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      // 1. Initial attempt fails with Template not found in repository
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("Template not found in repository");

      // Verify negative cache was recorded
      assertThat(engine.cache().isNegativelyCached(id)).isTrue();

      // 2. Subsequent call hits negative cache
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateResourceException.class)
          .hasMessageContaining("cached negative lookup");
    }
  }

  @Test
  @DisplayName(
      "FilesystemTemplateRepository distinguishes not found, access denied, and I/O error on"
          + " unreadable file")
  void filesystemRepositoryDistinguishesNotFoundFromAccessDeniedAndIoError(@TempDir Path tempDir)
      throws IOException {
    FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(tempDir);

    // 1. Genuinely absent file returns Optional.empty()
    TemplateId missingId = TemplateId.of("missing.vm");
    assertThat(repo.find(missingId)).isEmpty();

    // 2. Directory accessed as template file triggers I/O read failure ->
    // TemplateResourceException,
    // never Optional.empty()
    Path dirAsFile = tempDir.resolve("dir_as_file.vm");
    Files.createDirectory(dirAsFile);
    TemplateId dirId = TemplateId.of("dir_as_file.vm");
    assertThatThrownBy(() -> repo.find(dirId))
        .isInstanceOf(TemplateResourceException.class)
        .hasMessageContaining("Failed to read template file: dir_as_file.vm");

    // 3. Permission-based tests with POSIX capabilities
    boolean supportsPosix =
        Files.getFileStore(tempDir).supportsFileAttributeView(PosixFileAttributeView.class);
    if (supportsPosix) {
      Path unreadableFile = tempDir.resolve("unreadable.vm");
      Files.writeString(unreadableFile, "confidential payload");
      TemplateId unreadableId = TemplateId.of("unreadable.vm");
      try {
        Files.setPosixFilePermissions(unreadableFile, Collections.emptySet());
        boolean readDenied = false;
        try {
          Files.readString(unreadableFile);
        } catch (java.nio.file.AccessDeniedException e) {
          readDenied = true;
        } catch (IOException ignored) {
        }
        Assumptions.assumeTrue(
            readDenied, "Filesystem did not deny read access after removing POSIX permissions");
        assertThatThrownBy(() -> repo.find(unreadableId))
            .isInstanceOf(TemplateSecurityException.class)
            .hasMessageContaining("Access denied reading template file: unreadable.vm");
      } finally {
        try {
          Files.setPosixFilePermissions(
              unreadableFile, PosixFilePermissions.fromString("rw-r--r--"));
        } catch (IOException ignored) {
        }
      }

      Path restrictedDir = tempDir.resolve("restricted_dir");
      Files.createDirectory(restrictedDir);
      Path secretFile = restrictedDir.resolve("secret.vm");
      Files.writeString(secretFile, "secret under restricted dir");
      TemplateId restrictedId = TemplateId.of("restricted_dir/secret.vm");
      try {
        Files.setPosixFilePermissions(restrictedDir, Collections.emptySet());
        boolean accessDenied = false;
        try {
          restrictedDir.resolve("secret.vm").toRealPath();
        } catch (java.nio.file.AccessDeniedException e) {
          accessDenied = true;
        } catch (IOException ignored) {
        }
        Assumptions.assumeTrue(
            accessDenied,
            "Filesystem did not deny path traversal after removing POSIX permissions");
        assertThatThrownBy(() -> repo.find(restrictedId))
            .isInstanceOf(TemplateSecurityException.class)
            .hasMessageContaining("Access denied resolving real path: restricted_dir/secret.vm");
      } finally {
        try {
          Files.setPosixFilePermissions(
              restrictedDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (IOException ignored) {
        }
      }
    }
  }

  @Test
  @DisplayName("ClasspathTemplateRepository throws TemplateResourceException on I/O read failure")
  void classpathRepositoryThrowsTemplateResourceExceptionOnIoError() throws Exception {
    URLStreamHandler failingHandler =
        new URLStreamHandler() {
          @Override
          protected URLConnection openConnection(URL u) {
            return new URLConnection(u) {
              @Override
              public void connect() throws IOException {
                throw new IOException("Simulated network stream disconnect");
              }

              @Override
              public InputStream getInputStream() throws IOException {
                throw new IOException("Simulated classpath read stream failure");
              }
            };
          }
        };

    URL failingUrl =
        URL.of(URI.create("classpath-test://localhost/templates/corrupt.vm"), failingHandler);
    ClassLoader cl =
        new ClassLoader(ClasspathTemplateRepository.class.getClassLoader()) {
          @Override
          public URL getResource(String name) {
            if ("templates/corrupt.vm".equals(name)) {
              return failingUrl;
            }
            return null;
          }
        };

    ClasspathTemplateRepository repo = new ClasspathTemplateRepository(cl, "templates");
    TemplateId corruptId = TemplateId.of("corrupt.vm");

    assertThatThrownBy(() -> repo.find(corruptId))
        .isInstanceOf(TemplateResourceException.class)
        .hasMessageContaining("Failed to read classpath resource: templates/corrupt.vm");
  }

  @Test
  @DisplayName(
      "Security denials during freshness check fail closed across fast path and negative cache")
  void freshnessCheckSecurityDenialFailsClosed() throws IOException {
    AtomicBoolean revokeAccess = new AtomicBoolean(false);
    TemplateId id = TemplateId.of("secure_freshness.vm");

    class DynamicSecurityRepository implements TemplateRepository, TemplateFreshnessProvider {
      private final Map<TemplateId, String> templates = new ConcurrentHashMap<>();

      DynamicSecurityRepository() {
        templates.put(id, "Secure content: $v");
      }

      @Override
      public Optional<TemplateSource> find(TemplateId templateId) {
        String content = templates.get(templateId);
        if (content == null) {
          return Optional.empty();
        }
        return Optional.of(TemplateSource.fromString(templateId, content));
      }

      @Override
      public Optional<FreshnessToken> freshnessToken(TemplateId templateId) {
        if (revokeAccess.get()) {
          throw new TemplateSecurityException(
              "Access revoked during freshness verification: " + templateId.value(),
              templateId,
              SourceSpan.UNKNOWN);
        }
        return Optional.of(FreshnessToken.ofVersion(1L));
      }
    }

    DynamicSecurityRepository repo = new DynamicSecurityRepository();

    try (VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repo).build()) {
      // 1. Initial retrieval compiles and warms cache
      Template t1 = engine.get(id);
      assertThat(t1).isNotNull();

      // 2. Revoke access during freshness check
      revokeAccess.set(true);

      // Fast-path freshness check must rethrow TemplateSecurityException fail-closed
      assertThatThrownBy(() -> engine.get(id))
          .isInstanceOf(TemplateSecurityException.class)
          .hasMessageContaining("Access revoked during freshness verification");
    }
  }

  @Test
  @DisplayName(
      "AOT template registry fails with TemplateCompilationException when indexed class has"
          + " LinkageError")
  void aotRegistryFailsWithTemplateCompilationExceptionOnLinkageError()
      throws java.net.MalformedURLException {
    URLStreamHandler indexHandler =
        new URLStreamHandler() {
          @Override
          protected URLConnection openConnection(URL u) {
            return new URLConnection(u) {
              @Override
              public void connect() {}

              @Override
              public InputStream getInputStream() {
                return new ByteArrayInputStream(
                    "corrupt.vm = com.example.CorruptAotClass\n".getBytes(StandardCharsets.UTF_8));
              }
            };
          }
        };

    URL indexUrl =
        URL.of(
            URI.create("classpath-test://localhost/META-INF/viet-template/templates.idx"),
            indexHandler);
    ClassLoader cl =
        new ClassLoader(ClasspathTemplateRepository.class.getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            if ("META-INF/viet-template/templates.idx".equals(name)) {
              return Collections.enumeration(List.of(indexUrl));
            }
            return super.getResources(name);
          }

          @Override
          public Class<?> loadClass(String name) throws ClassNotFoundException {
            if ("com.example.CorruptAotClass".equals(name)) {
              throw new LinkageError("Corrupted bytecode or incompatible class format");
            }
            return super.loadClass(name);
          }
        };

    ClasspathTemplateRepository repo = new ClasspathTemplateRepository(cl, "templates");

    assertThatThrownBy(() -> VtlTemplateEngine.builder().repository(repo).build())
        .isInstanceOf(TemplateCompilationException.class)
        .hasMessageContaining(
            "Incompatible or corrupt AOT template class in templates.idx:"
                + " com.example.CorruptAotClass")
        .hasCauseInstanceOf(LinkageError.class);
  }

  @Test
  @DisplayName(
      "AOT execution in VtlInterpreter propagates TemplateSecurityException and"
          + " TemplateLimitException directly without wrapping in"
          + " TemplateRenderException(SYNTAX_ERROR)")
  void aotExecutionPropagatesDomainExceptionsDirectly() throws IOException {
    VtlInterpreter interpreter =
        new VtlInterpreter(
            VtlInterpreterOptions.builder().executionTier(ExecutionTier.AOT_BYTECODE).build());
    RenderContext ctx = RenderContext.builder().put("target", new FaultyTarget()).build();

    SourceText secSource =
        SourceText.of(TemplateId.of("secAot.vm"), "$target.throwSecurityException()");
    var secAst = VtlParser.parse(secSource).template();

    assertThatThrownBy(
            () ->
                EngineInterpreterBridge.render(
                    interpreter, secSource, secAst, ctx, new StringTemplateOutput()))
        .isInstanceOf(TemplateSecurityException.class)
        .isNotInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("Direct security denial");

    SourceText limSource =
        SourceText.of(TemplateId.of("limAot.vm"), "$target.throwLimitException()");
    var limAst = VtlParser.parse(limSource).template();

    assertThatThrownBy(
            () ->
                EngineInterpreterBridge.render(
                    interpreter, limSource, limAst, ctx, new StringTemplateOutput()))
        .isInstanceOf(TemplateLimitException.class)
        .isNotInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("Direct limit exceeded");
  }

  @Test
  @DisplayName(
      "AOT execution via VtlTemplateEngine propagates TemplateSecurityException and"
          + " TemplateLimitException directly")
  void aotEngineExecutionPropagatesDomainExceptionsDirectly() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("secEngineAot.vm", "$target.throwSecurityException()");
    repo.put("limEngineAot.vm", "$target.throwLimitException()");

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build()) {
      RenderContext ctx = RenderContext.builder().put("target", new FaultyTarget()).build();

      Template secTmpl = engine.get("secEngineAot.vm");
      assertThatThrownBy(() -> secTmpl.render(ctx, new StringTemplateOutput()))
          .isInstanceOf(TemplateSecurityException.class)
          .isNotInstanceOf(TemplateRenderException.class)
          .hasMessageContaining("Direct security denial");

      Template limTmpl = engine.get("limEngineAot.vm");
      assertThatThrownBy(() -> limTmpl.render(ctx, new StringTemplateOutput()))
          .isInstanceOf(TemplateLimitException.class)
          .isNotInstanceOf(TemplateRenderException.class)
          .hasMessageContaining("Direct limit exceeded");
    }
  }
}
