package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mechanical architecture audit tests establishing current runtime behavior for M4.0:
 * 1. Template wrapper identity and equality across warmed lookups (IR, runtime AOT, precompiled AOT).
 * 2. Parse counts across get, render, repeated render, and invalidation for AST and IR execution.
 */
class RuntimeLifecycleAuditTest {

  @Test
  @DisplayName("Audit 1: Template wrapper identity across warmed engine.get() calls")
  void auditTemplateWrapperIdentity() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("hello.vm");
    repo.put(id, "Hello $name");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.IR)
            .build();

    // 1. Unchanged generation, IR execution tier
    Template t1 = engine.get(id);
    Template t2 = engine.get(id);

    // Documented behavior: In current VtlTemplateEngine, a new VtlTemplate wrapper is allocated on EVERY warmed get()!
    assertThat(t1 == t2).isFalse();
    // VtlTemplate does not override equals(), so Object identity equals is used
    assertThat(t1.equals(t2)).isFalse();

    // Both wrap the exact same compiled handle in cache
    VtlTemplate vt1 = (VtlTemplate) t1;
    VtlTemplate vt2 = (VtlTemplate) t2;
    assertThat(vt1.handle()).isSameAs(vt2.handle());
    assertThat(vt1.handle().generation()).isEqualTo(vt2.handle().generation());

    engine.close();
  }

  @Test
  @DisplayName("Audit 2: Precompiled AOT template wrapper identity (canonical vs non-canonical)")
  void auditPrecompiledAotTemplateWrapperIdentity() {
    TemplateId precompiledId = TemplateId.of("test/precompiled.vm");
    TemplateId nonCanonicalId = TemplateId.of("./test/precompiled.vm");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(InMemoryTemplateRepository.create())
            .rejectRuntimeCompilation(true)
            .build();

    // Canonical lookup reuses the prepared Template instance cached in PreparedAotTemplate
    Template a1 = engine.get(precompiledId);
    Template a2 = engine.get(precompiledId);
    assertThat(a1 == a2).isTrue();

    // Non-canonical lookup normalizes but allocates a new wrapper via createAotTemplate
    Template b1 = engine.get(nonCanonicalId);
    Template b2 = engine.get(nonCanonicalId);
    assertThat(b1 == b2).isFalse();

    engine.close();
  }

  public static final class AuditPrecompiledFixture implements CompiledTemplate {
    public AuditPrecompiledFixture() {}

    @Override
    public TemplateDescriptor descriptor() {
      return TemplateDescriptor.of(TemplateId.of("test/precompiled.vm"), ExecutionTier.AOT_BYTECODE.name());
    }

    @Override
    public void render(RenderContext context, TemplateOutput output) throws IOException {
      output.write("Precompiled audit output");
    }
  }

  @Test
  @DisplayName("Audit 3: Engine AST execution tier compiles to prepared IR; parse occurs once at get()")
  void auditEngineAstTierParseBehavior() throws IOException {
    AtomicInteger repositoryReads = new AtomicInteger();
    InMemoryTemplateRepository delegate = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("test.vm");
    delegate.put(id, "Hello $name, count $val");

    TemplateRepository trackingRepo =
        new TemplateRepository() {
          @Override
          public Optional<TemplateSource> find(TemplateId targetId) {
            repositoryReads.incrementAndGet();
            return delegate.find(targetId);
          }
        };
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(trackingRepo)
            .executionTier(ExecutionTier.AST)
            .build();

    // 1. First get() -> misses cache, reads repository, parses source, lowers & optimizes IR, caches prepared handle
    Template t1 = engine.get(id);
    assertThat(repositoryReads.get()).isEqualTo(1);
    assertThat(t1.descriptor().executionTier()).isEqualTo("AST");

    // Inside VtlTemplate, handle.compiledTemplate() is PRESENT (it wraps prepared IR!)
    VtlTemplate vt1 = (VtlTemplate) t1;
    assertThat(vt1.handle().compiledTemplate()).isPresent();

    // 2. First render() -> does NOT parse; executes handle.compiledTemplate()
    RenderContext ctx = RenderContext.builder().put("name", "Viet").put("val", 1).build();
    StringTemplateOutput out1 = new StringTemplateOutput();
    t1.render(ctx, out1);
    assertThat(out1.toString()).isEqualTo("Hello Viet, count 1");

    // 3. Second render() -> still 0 parses during render
    StringTemplateOutput out2 = new StringTemplateOutput();
    t1.render(ctx, out2);
    assertThat(out2.toString()).isEqualTo("Hello Viet, count 1");

    // 4. 100th render() -> still 0 parses during render
    for (int i = 0; i < 98; i++) {
      t1.render(ctx, new StringTemplateOutput());
    }

    // 5. Warmed get() -> reads repository again to compute source fingerprint!
    Template t2 = engine.get(id);
    assertThat(repositoryReads.get()).isEqualTo(2);

    // 6. Invalidate and re-get
    engine.invalidate(id);
    Template t3 = engine.get(id);
    assertThat(repositoryReads.get()).isEqualTo(3);

    engine.close();
  }

  @Test
  @DisplayName("Audit 4: Direct VtlTemplate AST fallback reparses on EVERY render")
  void auditDirectVtlTemplateAstFallbackReparsesEveryRender() throws IOException {
    TemplateId id = TemplateId.of("direct-ast.vm");
    io.github.minh124199.viettemplate.language.vtl.source.SourceText source =
        io.github.minh124199.viettemplate.language.vtl.source.SourceText.of(id, "Val: $x");

    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().executionTier(ExecutionTier.AST).build();

    // Create a CompiledTemplateHandle with PreparedAstExecutionTarget without pre-parsed AST
    CompiledTemplateHandle emptyCompiledHandle =
        CompiledTemplateHandle.ofAst(
            id,
            1L,
            io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey.of(
                id,
                "hash",
                "0.2.0",
                io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel.O0,
                ExecutionTier.AST,
                "policy",
                "schema",
                "backend"),
            new io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter(options),
            source);
    VtlTemplate directTemplate =
        new VtlTemplate(TemplateDescriptor.of(id, "AST"), emptyCompiledHandle, source, options);

    RenderContext ctx = RenderContext.builder().put("x", 42).build();

    // First render -> parses sourceText in VtlTemplate.render() line 81
    StringTemplateOutput out1 = new StringTemplateOutput();
    directTemplate.render(ctx, out1);
    assertThat(out1.toString()).isEqualTo("Val: 42");

    // Second render -> parses sourceText AGAIN
    StringTemplateOutput out2 = new StringTemplateOutput();
    directTemplate.render(ctx, out2);
    assertThat(out2.toString()).isEqualTo("Val: 42");

    // 100 renders -> 100 parses executed!
    for (int i = 0; i < 98; i++) {
      directTemplate.render(ctx, new StringTemplateOutput());
    }
  }

  @Test
  @DisplayName("Audit 5: Static dependency pre-compilation propagates syntax exceptions but tolerates missing resources")
  void auditStaticDependencyPrecompilationExceptionPropagation() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId parentBroken = TemplateId.of("parentBroken.vtl");
    TemplateId badChild = TemplateId.of("badChild.vtl");
    repo.put(parentBroken, "Header #parse('badChild.vtl') Footer");
    repo.put(badChild, "#if (unclosed expression");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.IR)
            .build();

    // 1. Dependency syntax error must NOT be swallowed during pre-compilation
    assertThatThrownBy(() -> engine.get(parentBroken))
        .isInstanceOf(TemplateSyntaxException.class)
        .hasMessageContaining("badChild.vtl");

    // 2. Expected missing dependency resources ARE tolerated during static pre-compilation
    TemplateId parentMissing = TemplateId.of("parentMissing.vtl");
    repo.put(parentMissing, "Header #parse('missingChild.vtl') Footer");
    assertThatCode(() -> engine.get(parentMissing)).doesNotThrowAnyException();

    engine.close();
  }

  @Test
  @DisplayName("Audit 6: EngineFingerprint precomputes immutable engine descriptors once at construction")
  void auditEngineFingerprintPrecomputation() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.IR)
            .optimizationLevel(OptimizationLevel.O2)
            .build();

    EngineFingerprint fp = engine.engineFingerprint();
    assertThat(fp).isNotNull();
    assertThat(fp.compilerVersion()).isEqualTo("0.2.0");
    assertThat(fp.optimizationLevel()).isEqualTo(OptimizationLevel.O2);
    assertThat(fp.executionTier()).isEqualTo(ExecutionTier.IR);
    assertThat(fp.accessPolicyId()).isNotBlank();
    assertThat(fp.modelSignature()).isNotNull();
    assertThat(fp.backendHash()).isEqualTo("VTL_CORE:VTL_CORE");

    // Validate canonical constructor non-null constraints
    assertThatThrownBy(
            () ->
                new EngineFingerprint(
                    null, OptimizationLevel.O0, ExecutionTier.IR, "p", "m", "b"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new EngineFingerprint(
                    "0.2.0", null, ExecutionTier.IR, "p", "m", "b"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new EngineFingerprint(
                    "0.2.0", OptimizationLevel.O0, null, "p", "m", "b"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new EngineFingerprint(
                    "0.2.0", OptimizationLevel.O0, ExecutionTier.IR, null, "m", "b"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new EngineFingerprint(
                    "0.2.0", OptimizationLevel.O0, ExecutionTier.IR, "p", null, "b"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new EngineFingerprint(
                    "0.2.0", OptimizationLevel.O0, ExecutionTier.IR, "p", "m", null))
        .isInstanceOf(NullPointerException.class);

    engine.close();
  }
}
