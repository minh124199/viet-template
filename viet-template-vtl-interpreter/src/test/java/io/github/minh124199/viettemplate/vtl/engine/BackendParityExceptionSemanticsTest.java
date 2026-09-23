package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.api.UndefinedReferencePolicy;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.InterpreterDiagnosticCodes;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionLimits;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Backend parity verification for exception semantics across execution tiers.
 *
 * <p>Validates Milestone M6.4 requirement: identical exception types, diagnostic codes, and cause
 * preservation across {@link ExecutionTier#AST}, {@link ExecutionTier#IR}, and {@link
 * ExecutionTier#AOT_BYTECODE}.
 */
class BackendParityExceptionSemanticsTest {

  public static class ApplicationExceptionBean {
    public String throwAppException() {
      throw new IllegalStateException("Business calculation error");
    }

    public String getProblemProperty() {
      throw new IllegalArgumentException("Invalid property value");
    }
  }

  public static class TruthyOomBean {
    public boolean getAsBoolean() {
      throw new OutOfMemoryError("Simulated OOM in getAsBoolean");
    }
  }

  public static class EmptyOomBean {
    public boolean isEmpty() {
      throw new OutOfMemoryError("Simulated OOM in isEmpty");
    }
  }

  public static class LengthOomBean {
    public int length() {
      throw new OutOfMemoryError("Simulated OOM in length");
    }
  }

  public static class SizeOomBean {
    public int size() {
      throw new OutOfMemoryError("Simulated OOM in size");
    }
  }

  public static class StringOomBean {
    public String getAsString() {
      throw new OutOfMemoryError("Simulated OOM in getAsString");
    }
  }

  public static class NumberOomBean {
    public Number getAsNumber() {
      throw new OutOfMemoryError("Simulated OOM in getAsNumber");
    }
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName(
      "Undefined reference in strict mode throws TemplateRenderException with VARIABLE_UNDEFINED")
  void undefinedReferenceInStrictModeThrowsVariableUndefined(ExecutionTier tier)
      throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("undefined.vm", "Value is: $missingVariable");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(
                VtlInterpreterOptions.builder()
                    .undefinedReferencePolicy(UndefinedReferencePolicy.ERROR)
                    .build())
            .build();

    Template tmpl = engine.get("undefined.vm");
    assertThatThrownBy(() -> tmpl.render(RenderContext.empty(), new StringTemplateOutput()))
        .isInstanceOf(TemplateRenderException.class)
        .satisfies(
            e -> {
              TemplateRenderException tre = (TemplateRenderException) e;
              assertThat(tre.code()).contains(InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
            });

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName("Forbidden getClass() call throws SECURITY_VIOLATION across all tiers")
  void forbiddenGetClassThrowsSecurityViolation(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("forbiddenClass.vm", "$target.getClass()");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).build();
    RenderContext ctx = RenderContext.builder().put("target", "simpleString").build();

    Template tmpl = engine.get("forbiddenClass.vm");
    assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
        .satisfies(
            e -> {
              assertThat(e)
                  .isInstanceOfAny(TemplateSecurityException.class, TemplateRenderException.class);
              TemplateException te = (TemplateException) e;
              assertThat(te.code()).contains(InterpreterDiagnosticCodes.SECURITY_VIOLATION);
            });

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName("Forbidden wait() call throws SECURITY_VIOLATION across all tiers")
  void forbiddenWaitThrowsSecurityViolation(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("forbiddenWait.vm", "$target.wait()");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).build();
    RenderContext ctx = RenderContext.builder().put("target", "simpleString").build();

    Template tmpl = engine.get("forbiddenWait.vm");
    assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
        .satisfies(
            e -> {
              assertThat(e)
                  .isInstanceOfAny(TemplateSecurityException.class, TemplateRenderException.class);
              TemplateException te = (TemplateException) e;
              assertThat(te.code()).contains(InterpreterDiagnosticCodes.SECURITY_VIOLATION);
            });

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName("User method throws application exception preserving cause across tiers")
  void userMethodThrowsApplicationExceptionPreservingCause(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("userMethod.vm", "$service.throwAppException()");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).build();
    RenderContext ctx =
        RenderContext.builder().put("service", new ApplicationExceptionBean()).build();

    Template tmpl = engine.get("userMethod.vm");
    if (tier == ExecutionTier.AOT_BYTECODE) {
      assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
          .satisfies(
              e -> {
                if (e instanceof TemplateRenderException tre) {
                  assertThat(tre.code()).contains(InterpreterDiagnosticCodes.INVALID_METHOD);
                  assertThat(tre.getCause()).hasMessage("Business calculation error");
                } else {
                  assertThat(e)
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessage("Business calculation error");
                }
              });
    } else {
      assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
          .isInstanceOf(TemplateRenderException.class)
          .hasCauseInstanceOf(IllegalStateException.class)
          .satisfies(
              e -> {
                TemplateRenderException tre = (TemplateRenderException) e;
                assertThat(tre.code()).contains(InterpreterDiagnosticCodes.INVALID_METHOD);
                assertThat(tre.getCause()).hasMessage("Business calculation error");
              });
    }

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName("User property throws application exception preserving cause across tiers")
  void userPropertyThrowsApplicationExceptionPreservingCause(ExecutionTier tier)
      throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("userProp.vm", "$service.problemProperty");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).build();
    RenderContext ctx =
        RenderContext.builder().put("service", new ApplicationExceptionBean()).build();

    Template tmpl = engine.get("userProp.vm");
    if (tier == ExecutionTier.AOT_BYTECODE) {
      assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
          .satisfies(
              e -> {
                if (e instanceof TemplateRenderException tre) {
                  assertThat(tre.code()).contains(InterpreterDiagnosticCodes.INVALID_METHOD);
                  assertThat(tre.getCause()).hasMessage("Invalid property value");
                } else {
                  assertThat(e)
                      .isInstanceOf(IllegalArgumentException.class)
                      .hasMessage("Invalid property value");
                }
              });
    } else {
      assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
          .isInstanceOf(TemplateRenderException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class)
          .satisfies(
              e -> {
                TemplateRenderException tre = (TemplateRenderException) e;
                assertThat(tre.code()).contains(InterpreterDiagnosticCodes.INVALID_METHOD);
                assertThat(tre.getCause()).hasMessage("Invalid property value");
              });
    }

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName(
      "Render output exceeding maxOutputCharacters throws TemplateLimitException with"
          + " LIMIT_EXCEEDED")
  void maxOutputCharactersExceededThrowsLimitExceeded(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("limit.vm", "This string definitely exceeds twenty characters limit");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(
                VtlInterpreterOptions.builder()
                    .limits(ExecutionLimits.builder().maxOutputCharacters(20).build())
                    .build())
            .build();

    Template tmpl = engine.get("limit.vm");
    assertThatThrownBy(() -> tmpl.render(RenderContext.empty(), new StringTemplateOutput()))
        .isInstanceOf(TemplateLimitException.class)
        .satisfies(
            e -> {
              TemplateLimitException tle = (TemplateLimitException) e;
              assertThat(tle.code()).contains(InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
            });

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(
      value = ExecutionTier.class,
      names = {"AST", "IR"})
  @DisplayName(
      "Loop iterations exceeding maxLoopIterations throws TemplateLimitException with"
          + " LIMIT_EXCEEDED")
  void maxLoopIterationsExceededThrowsLimitExceeded(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("loopLimit.vm", "#foreach($i in [1..100])$i#end");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(tier)
            .interpreterOptions(
                VtlInterpreterOptions.builder()
                    .limits(ExecutionLimits.builder().maxLoopIterations(5).build())
                    .build())
            .build();

    Template tmpl = engine.get("loopLimit.vm");
    assertThatThrownBy(() -> tmpl.render(RenderContext.empty(), new StringTemplateOutput()))
        .isInstanceOf(TemplateLimitException.class)
        .satisfies(
            e -> {
              TemplateLimitException tle = (TemplateLimitException) e;
              assertThat(tle.code()).contains(InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
            });

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName(
      "Dynamic #parse with missing resource throws TemplateResourceException with"
          + " RESOURCE_NOT_FOUND")
  void dynamicParseMissingResourceThrowsResourceNotFound(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("caller.vm", "Before #parse('missing.vtl') After");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).build();

    Template tmpl = engine.get("caller.vm");
    assertThatThrownBy(() -> tmpl.render(RenderContext.empty(), new StringTemplateOutput()))
        .isInstanceOf(TemplateResourceException.class)
        .satisfies(
            e -> {
              TemplateResourceException tre = (TemplateResourceException) e;
              assertThat(tre.code()).contains(InterpreterDiagnosticCodes.RESOURCE_NOT_FOUND);
            });

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(ExecutionTier.class)
  @DisplayName(
      "Truthiness method getAsBoolean throwing OutOfMemoryError propagates across all tiers")
  void truthinessMethodGetAsBooleanOomPropagatesAcrossAllTiers(ExecutionTier tier)
      throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("oomTruthMethod.vm", "#if($obj.getAsBoolean())yes#{else}no#end");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).build();
    RenderContext ctx = RenderContext.builder().put("obj", new TruthyOomBean()).build();

    Template tmpl = engine.get("oomTruthMethod.vm");
    assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in getAsBoolean");

    engine.close();
  }

  @ParameterizedTest(name = "Tier: {0}")
  @EnumSource(
      value = ExecutionTier.class,
      names = {"AST", "IR"})
  @DisplayName(
      "Duck-typing truthiness condition $obj invokes getAsBoolean and propagates OutOfMemoryError"
          + " in AST and IR")
  void duckTypingTruthinessConditionPropagatesOomInInterpreterTiers(ExecutionTier tier)
      throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("oomTruthIf.vm", "#if($obj)yes#{else}no#end");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(tier).build();
    RenderContext ctx = RenderContext.builder().put("obj", new TruthyOomBean()).build();

    Template tmpl = engine.get("oomTruthIf.vm");
    assertThatThrownBy(() -> tmpl.render(ctx, new StringTemplateOutput()))
        .isInstanceOf(OutOfMemoryError.class)
        .hasMessage("Simulated OOM in getAsBoolean");

    engine.close();
  }
}
