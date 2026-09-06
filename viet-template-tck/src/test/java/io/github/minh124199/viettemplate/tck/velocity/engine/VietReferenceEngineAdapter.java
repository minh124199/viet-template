package io.github.minh124199.viettemplate.tck.velocity.engine;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.api.TemplateSyntaxException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.tck.velocity.model.Probe;
import io.github.minh124199.viettemplate.tck.velocity.result.EngineResult;
import io.github.minh124199.viettemplate.tck.velocity.result.ExceptionCategory;
import io.github.minh124199.viettemplate.tck.velocity.result.ExceptionObservation;
import io.github.minh124199.viettemplate.tck.velocity.result.ExecutionOutcome;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.vtl.interpreter.EvaluationValue;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionContext;
import io.github.minh124199.viettemplate.vtl.interpreter.InterpreterDiagnosticCodes;
import io.github.minh124199.viettemplate.vtl.interpreter.SpaceGobbler;
import io.github.minh124199.viettemplate.vtl.interpreter.TemplateResourceResolver;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.HashMap;
import java.util.Map;

/** Engine adapter executing scenarios against the Viet Template reference interpreter. */
public final class VietReferenceEngineAdapter implements EngineAdapter {

  @Override
  public EngineIdentity identity() {
    return EngineIdentity.VIET_TEMPLATE_REFERENCE;
  }

  @Override
  public EngineResult execute(
      CompatibilityScenario scenario,
      Map<String, Object> context,
      CompatibilityConfiguration configuration,
      Map<String, String> resources) {

    SourceText source = SourceText.of(scenario.id(), scenario.template());

    VtlProfile profile =
        scenario.tags().contains("dynamic") ? VtlProfile.VTL_DYNAMIC : VtlProfile.VTL_CORE;

    VtlParserOptions parserOptions =
        VtlParserOptions.ofDefaults()
            .withProfile(profile)
            .withAllowBareNullLiteral(configuration.allowBareNullLiteral());

    VtlParseResult parseResult = VtlParser.parse(source, parserOptions);

    // If parser encountered fatal syntax errors, report syntax failure immediately
    if (parseResult.hasErrors()) {
      var firstError =
          parseResult.diagnostics().stream()
              .filter(d -> d.severity().name().equals("ERROR"))
              .findFirst()
              .orElse(null);

      String errorMsg = firstError != null ? firstError.message() : "Syntax error parsing template";
      var span =
          firstError != null
              ? firstError.primarySpan()
              : io.github.minh124199.viettemplate.api.SourceSpan.UNKNOWN;
      var code = firstError != null ? firstError.code() : null;
      io.github.minh124199.viettemplate.api.TemplateSyntaxException parseEx =
          new io.github.minh124199.viettemplate.api.TemplateSyntaxException(
              errorMsg, source.templateId(), span, code);

      return new EngineResult(
          identity(),
          ExecutionOutcome.FAILURE,
          "",
          ExceptionObservation.of(ExceptionCategory.SYNTAX_ERROR, parseEx),
          Map.of(),
          Map.of());
    }

    VtlTemplate ast = parseResult.template();

    TemplateResourceResolver resourceResolver;
    if (resources != null && !resources.isEmpty()) {
      var builder = TemplateResourceResolver.inMemory();
      resources.forEach(builder::put);
      resourceResolver = builder.build();
    } else {
      resourceResolver = TemplateResourceResolver.empty();
    }

    SpaceGobbler.Mode spaceMode =
        configuration.spaceGobbling() == CompatibilityConfiguration.SpaceGobblingMode.NONE
            ? SpaceGobbler.Mode.NONE
            : SpaceGobbler.Mode.LINES;

    VtlInterpreterOptions interpreterOptions =
        VtlInterpreterOptions.builder()
            .profile(profile)
            .strictReferences(configuration.strictReferences())
            .emptyCheck(configuration.emptyCheck())
            .spaceGobbling(spaceMode)
            .allowBareNullLiteral(configuration.allowBareNullLiteral())
            .setNullAllowed(configuration.setNullAllowed())
            .resourceResolver(resourceResolver)
            .build();

    VtlInterpreter interpreter = new VtlInterpreter(interpreterOptions);
    RenderContext renderContext =
        io.github.minh124199.viettemplate.runtime.MapRenderContext.of(
            context != null ? context : Map.of());
    ExecutionContext executionContext = new ExecutionContext(renderContext);
    io.github.minh124199.viettemplate.runtime.StringTemplateOutput output =
        new io.github.minh124199.viettemplate.runtime.StringTemplateOutput();

    Throwable failure = null;
    try {
      interpreter.render(source, ast, executionContext, output);
    } catch (Throwable t) {
      failure = t;
    }

    String renderedText = output.toString();

    // Collect observable context mutations
    Map<String, Object> observedContext = new HashMap<>();
    for (String key : scenario.observableContextKeys()) {
      EvaluationValue val = executionContext.lookup(key);
      if (val.isDefined()) {
        observedContext.put(key, val.isNonNull() ? val.value() : null);
      }
    }

    // Collect side-effect observations from probes
    Map<String, Object> observations = new HashMap<>();
    if (context != null) {
      for (Map.Entry<String, Object> entry : context.entrySet()) {
        if (entry.getValue() instanceof Probe p) {
          observations.put(entry.getKey() + ".hitCount", p.getHitCount());
          observations.put(entry.getKey() + ".returnNullCount", p.getReturnNullCount());
        }
      }
    }

    if (failure == null) {
      return new EngineResult(
          identity(), ExecutionOutcome.SUCCESS, renderedText, null, observedContext, observations);
    }

    ExceptionCategory category = categorizeVietException(failure);
    ExceptionObservation observation = ExceptionObservation.of(category, failure);

    return new EngineResult(
        identity(),
        ExecutionOutcome.FAILURE,
        renderedText,
        observation,
        observedContext,
        observations);
  }

  private ExceptionCategory categorizeVietException(Throwable t) {
    if (t instanceof TemplateSyntaxException) {
      return ExceptionCategory.SYNTAX_ERROR;
    }
    if (t instanceof TemplateResourceException) {
      return ExceptionCategory.RESOURCE_NOT_FOUND;
    }
    if (t instanceof TemplateSecurityException) {
      return ExceptionCategory.SECURITY_DENIED;
    }
    if (t instanceof TemplateLimitException) {
      return ExceptionCategory.LIMIT_EXCEEDED;
    }
    if (t instanceof TemplateRenderException tre) {
      String msg = tre.getMessage() != null ? tre.getMessage() : "";
      if (tre.code().equals(InterpreterDiagnosticCodes.VARIABLE_UNDEFINED)
          || msg.contains("VARIABLE_UNDEFINED")
          || msg.contains("Undefined reference")
          || msg.contains("evaluated to null")
          || msg.contains("has not been set")
          || msg.contains("not set")) {
        return ExceptionCategory.UNDEFINED_REFERENCE;
      }
      if (tre.code().equals(InterpreterDiagnosticCodes.SECURITY_VIOLATION)
          || msg.contains("Security")
          || msg.contains("denied")) {
        return ExceptionCategory.SECURITY_DENIED;
      }
      if (msg.contains("Division by zero")
          || msg.contains("divide by zero")
          || msg.contains("arithmetic")) {
        return ExceptionCategory.ARITHMETIC_ERROR;
      }
      if (tre.code().equals(InterpreterDiagnosticCodes.INVALID_PROPERTY)
          || msg.contains("does not contain property")
          || msg.contains("Property")
          || msg.contains("Cannot resolve property")) {
        return ExceptionCategory.INVALID_PROPERTY;
      }
      if (tre.code().equals(InterpreterDiagnosticCodes.INVALID_METHOD)
          || msg.contains("does not contain method")
          || msg.contains("Method")
          || msg.contains("Cannot invoke method")
          || msg.contains("Method not found")) {
        return ExceptionCategory.INVALID_METHOD;
      }
      if (tre.code().equals(InterpreterDiagnosticCodes.LIMIT_EXCEEDED)
          || msg.contains("Limit exceeded")) {
        return ExceptionCategory.LIMIT_EXCEEDED;
      }
      Throwable cause = tre.getCause();
      if (cause != null) {
        if (cause instanceof ArithmeticException) {
          return ExceptionCategory.ARITHMETIC_ERROR;
        }
        if (cause instanceof SecurityException) {
          return ExceptionCategory.SECURITY_DENIED;
        }
        return ExceptionCategory.INVALID_METHOD;
      }
      return ExceptionCategory.OTHER_RENDER_ERROR;
    }
    if (t instanceof ArithmeticException) {
      return ExceptionCategory.ARITHMETIC_ERROR;
    }
    if (t instanceof SecurityException) {
      return ExceptionCategory.SECURITY_DENIED;
    }
    return ExceptionCategory.OTHER_RENDER_ERROR;
  }
}
