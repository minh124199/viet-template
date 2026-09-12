package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.api.TemplateSyntaxException;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable test-only harness for executing template sources across AST, IR, and AOT_BYTECODE
 * execution tiers and asserting semantic equivalence (AST == IR == AOT).
 */
public final class TierDifferentialHarness {

  public record TierResult(
      ExecutionTier tier,
      String output,
      Class<? extends Throwable> exceptionClass,
      String diagnosticCode,
      String errorMessage,
      Map<String, Object> finalContext) {

    public boolean isSuccess() {
      return exceptionClass == null;
    }
  }

  public record DifferentialResult(
      String source,
      Map<String, Object> initialContext,
      TierResult astResult,
      TierResult irResult,
      TierResult aotResult) {

    public boolean allSucceeded() {
      return astResult.isSuccess() && irResult.isSuccess() && aotResult.isSuccess();
    }

    public boolean allFailed() {
      return !astResult.isSuccess() && !irResult.isSuccess() && !aotResult.isSuccess();
    }
  }

  private TierDifferentialHarness() {}

  public static DifferentialResult runAcrossTiers(
      String source, Map<String, Object> context, VtlInterpreterOptions baseOptions) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(context, "context must not be null");
    VtlInterpreterOptions options =
        baseOptions != null ? baseOptions : VtlInterpreterOptions.DEFAULT;

    SourceText src = SourceText.of("differential.vtl", source);
    VtlParserOptions parserOptions = VtlParserOptions.ofDefaults().withProfile(options.profile());
    VtlParseResult parseResult = VtlParser.parse(src, parserOptions);

    if (parseResult.hasErrors()) {
      TemplateSyntaxException syntaxEx =
          new TemplateSyntaxException(
              "Parse failed: " + parseResult.diagnostics(),
              src.templateId(),
              parseResult.template().span(),
              DiagnosticCode.of("SYNTAX", "PARSE_ERROR"));
      TierResult parseFail =
          new TierResult(
              ExecutionTier.AST,
              null,
              syntaxEx.getClass(),
              "SYNTAX:PARSE_ERROR",
              syntaxEx.getMessage(),
              copyContext(context));
      return new DifferentialResult(source, context, parseFail, parseFail, parseFail);
    }

    VtlTemplate ast = parseResult.template();

    TierResult astResult = executeTier(ExecutionTier.AST, ast, src, context, options);
    TierResult irResult = executeTier(ExecutionTier.IR, ast, src, context, options);
    TierResult aotResult = executeTier(ExecutionTier.AOT_BYTECODE, ast, src, context, options);

    return new DifferentialResult(source, context, astResult, irResult, aotResult);
  }

  public static void assertTierParity(DifferentialResult result) {
    TierResult ast = result.astResult();
    TierResult ir = result.irResult();
    TierResult aot = result.aotResult();

    if (ast.isSuccess()) {
      // If AST succeeded, IR and AOT must succeed and produce identical outputs
      assertThat(ir.isSuccess())
          .as(
              "IR tier must succeed when AST succeeds.%nSource:%n%s%nIR error: %s",
              result.source(), ir.errorMessage())
          .isTrue();

      assertThat(aot.isSuccess())
          .as(
              "AOT tier must succeed when AST succeeds.%nSource:%n%s%nAOT error: %s",
              result.source(), aot.errorMessage())
          .isTrue();

      assertThat(ir.output())
          .as("IR output must match AST output.%nSource:%n%s", result.source())
          .isEqualTo(ast.output());

      assertThat(aot.output())
          .as("AOT output must match AST output.%nSource:%n%s", result.source())
          .isEqualTo(ast.output());

      // Context side effects (if any mutated keys exist)
      for (Map.Entry<String, Object> entry : ast.finalContext().entrySet()) {
        if (!Objects.equals(result.initialContext().get(entry.getKey()), entry.getValue())) {
          assertThat(ir.finalContext().get(entry.getKey()))
              .as("IR mutated context key '%s' must match AST", entry.getKey())
              .isEqualTo(entry.getValue());
          assertThat(aot.finalContext().get(entry.getKey()))
              .as("AOT mutated context key '%s' must match AST", entry.getKey())
              .isEqualTo(entry.getValue());
        }
      }
    } else {
      // If AST failed, IR and AOT must fail with semantically compatible exceptions
      assertThat(ir.isSuccess())
          .as(
              "IR tier must fail when AST fails with %s.%nSource:%n%s%nIR output: %s",
              ast.exceptionClass().getSimpleName(), result.source(), ir.output())
          .isFalse();

      assertThat(aot.isSuccess())
          .as(
              "AOT tier must fail when AST fails with %s.%nSource:%n%s%nAOT output: %s",
              ast.exceptionClass().getSimpleName(), result.source(), aot.output())
          .isFalse();

      // Semantic exception compatibility
      Class<? extends Throwable> astSemantic = getSemanticCategory(ast.exceptionClass());
      Class<? extends Throwable> irSemantic = getSemanticCategory(ir.exceptionClass());
      Class<? extends Throwable> aotSemantic = getSemanticCategory(aot.exceptionClass());

      assertThat(irSemantic)
          .as(
              "IR semantic exception category must match AST (%s vs %s)",
              ast.exceptionClass(), ir.exceptionClass())
          .isEqualTo(astSemantic);

      assertThat(aotSemantic)
          .as(
              "AOT semantic exception category must match AST (%s vs %s)",
              ast.exceptionClass(), aot.exceptionClass())
          .isEqualTo(astSemantic);
    }
  }

  private static TierResult executeTier(
      ExecutionTier tier,
      VtlTemplate template,
      SourceText source,
      Map<String, Object> context,
      VtlInterpreterOptions baseOptions) {

    Map<String, Object> freshContext = copyContext(context);
    MapRenderContext renderContext = MapRenderContext.of(freshContext);
    StringTemplateOutput output = new StringTemplateOutput();

    VtlInterpreterOptions tierOptions = baseOptions.toBuilder().executionTier(tier).build();
    VtlInterpreter interpreter = new VtlInterpreter(tierOptions);

    try {
      interpreter.render(source, template, renderContext, output);
      return new TierResult(tier, output.toString(), null, null, null, freshContext);
    } catch (Throwable t) {
      Throwable semantic = unwrapSemanticException(t);
      String diagCode = extractDiagnosticCode(semantic);
      return new TierResult(
          tier, null, semantic.getClass(), diagCode, semantic.getMessage(), freshContext);
    }
  }

  public static Throwable unwrapSemanticException(Throwable t) {
    if (t == null) {
      return null;
    }
    if (t instanceof TemplateRenderException tre
        && tre.getCause() instanceof TemplateException te) {
      return te;
    }
    if (t.getCause() instanceof TemplateLimitException tle) {
      return tle;
    }
    if (t.getCause() instanceof TemplateSecurityException tse) {
      return tse;
    }
    return t;
  }

  private static Class<? extends Throwable> getSemanticCategory(
      Class<? extends Throwable> exClass) {
    if (TemplateLimitException.class.isAssignableFrom(exClass)) {
      return TemplateLimitException.class;
    }
    if (TemplateSecurityException.class.isAssignableFrom(exClass)) {
      return TemplateSecurityException.class;
    }
    if (TemplateSyntaxException.class.isAssignableFrom(exClass)) {
      return TemplateSyntaxException.class;
    }
    if (TemplateException.class.isAssignableFrom(exClass)) {
      return TemplateException.class;
    }
    return exClass;
  }

  private static String extractDiagnosticCode(Throwable t) {
    if (t instanceof TemplateException te && te.code().isPresent()) {
      return te.code().get().toString();
    }
    return "UNKNOWN";
  }

  private static Map<String, Object> copyContext(Map<String, Object> orig) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : orig.entrySet()) {
      Object v = entry.getValue();
      if (v instanceof Map<?, ?> m) {
        copy.put(entry.getKey(), new LinkedHashMap<>(m));
      } else {
        copy.put(entry.getKey(), v);
      }
    }
    return copy;
  }
}
