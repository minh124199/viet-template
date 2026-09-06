package io.github.minh124199.viettemplate.tck.velocity.engine;

import io.github.minh124199.viettemplate.tck.velocity.model.Probe;
import io.github.minh124199.viettemplate.tck.velocity.result.EngineResult;
import io.github.minh124199.viettemplate.tck.velocity.result.ExceptionCategory;
import io.github.minh124199.viettemplate.tck.velocity.result.ExceptionObservation;
import io.github.minh124199.viettemplate.tck.velocity.result.ExecutionOutcome;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;

/**
 * Engine adapter executing scenarios against Apache Velocity Engine 2.4.1. All Velocity classes and
 * dependencies are isolated strictly to this class.
 */
public final class Velocity241EngineAdapter implements EngineAdapter {

  @Override
  public EngineIdentity identity() {
    return EngineIdentity.APACHE_VELOCITY_2_4_1;
  }

  @Override
  public EngineResult execute(
      CompatibilityScenario scenario,
      Map<String, Object> context,
      CompatibilityConfiguration configuration,
      Map<String, String> resources) {

    VelocityEngine engine = new VelocityEngine();
    String repoName = "repo_" + UUID.randomUUID().toString().replace("-", "");

    // Configure logging (silent)
    engine.setProperty(
        RuntimeConstants.RUNTIME_LOG_INSTANCE,
        org.slf4j.LoggerFactory.getLogger("Velocity241EngineAdapter"));

    // Space gobbling (Velocity 2.x uses space.gobbling)
    String spaceGobblingStr =
        switch (configuration.spaceGobbling()) {
          case NONE -> "none";
          case LINES -> "lines";
          case BC -> "bc";
          case STRUCTURED -> "structured";
        };
    engine.setProperty("space.gobbling", spaceGobblingStr);
    engine.setProperty("space_gobbling", spaceGobblingStr);

    // References & truthiness
    engine.setProperty("runtime.references.strict", configuration.strictReferences());
    engine.setProperty("directive.if.empty_check", configuration.emptyCheck());
    engine.setProperty(
        "parser.allow_hyphen_in_identifiers", configuration.allowHyphenIdentifiers());
    engine.setProperty("runtime.immutable_ranges", configuration.immutableRanges());
    engine.setProperty("directive.set.null.allowed", configuration.setNullAllowed());

    // In-memory StringResourceLoader for #parse and #include
    engine.setProperty("resource.loaders", "string");
    engine.setProperty(
        "resource.loader.string.class",
        "org.apache.velocity.runtime.resource.loader.StringResourceLoader");
    engine.setProperty("resource.loader.string.repository.name", repoName);

    var repo = new org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl();
    if (resources != null) {
      resources.forEach(repo::putStringResource);
    }
    StringResourceLoader.setRepository(repoName, repo);

    try {
      engine.init();
    } catch (Throwable t) {
      StringResourceLoader.removeRepository(repoName);
      return new EngineResult(
          identity(),
          ExecutionOutcome.FAILURE,
          "",
          ExceptionObservation.of(ExceptionCategory.OTHER_RENDER_ERROR, t),
          Map.of(),
          Map.of());
    }

    VelocityContext velocityContext = new VelocityContext();
    if (context != null) {
      context.forEach(velocityContext::put);
    }

    StringWriter writer = new StringWriter();
    Throwable failure = null;

    try {
      engine.evaluate(velocityContext, writer, scenario.id(), scenario.template());
    } catch (Throwable t) {
      failure = t;
    } finally {
      StringResourceLoader.removeRepository(repoName);
    }

    String output = writer.toString();

    // Collect observable context mutations
    Map<String, Object> observedContext = new HashMap<>();
    for (String key : scenario.observableContextKeys()) {
      if (velocityContext.containsKey(key)) {
        observedContext.put(key, velocityContext.get(key));
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
          identity(), ExecutionOutcome.SUCCESS, output, null, observedContext, observations);
    }

    ExceptionCategory category = categorizeVelocityException(failure);
    ExceptionObservation observation = ExceptionObservation.of(category, failure);

    return new EngineResult(
        identity(), ExecutionOutcome.FAILURE, output, observation, observedContext, observations);
  }

  private ExceptionCategory categorizeVelocityException(Throwable t) {
    if (t instanceof ParseErrorException) {
      return ExceptionCategory.SYNTAX_ERROR;
    }
    if (t instanceof ResourceNotFoundException) {
      return ExceptionCategory.RESOURCE_NOT_FOUND;
    }
    if (t instanceof MethodInvocationException mie) {
      String msg = mie.getMessage() != null ? mie.getMessage() : "";
      if (msg.contains("Variable") && msg.contains("has not been set")) {
        return ExceptionCategory.UNDEFINED_REFERENCE;
      }
      if (msg.contains("does not contain property") || msg.contains("Property")) {
        return ExceptionCategory.INVALID_PROPERTY;
      }
      Throwable cause = mie.getCause();
      if (cause != null) {
        if (cause instanceof ArithmeticException) {
          return ExceptionCategory.ARITHMETIC_ERROR;
        }
        if (cause instanceof SecurityException) {
          return ExceptionCategory.SECURITY_DENIED;
        }
      }
      return ExceptionCategory.INVALID_METHOD;
    }
    if (t instanceof VelocityException ve) {
      String msg = ve.getMessage() != null ? ve.getMessage().toLowerCase() : "";
      if (msg.contains("evaluated to null") || msg.contains("has not been set")) {
        return ExceptionCategory.UNDEFINED_REFERENCE;
      }
      if (msg.contains("security") || msg.contains("denied")) {
        return ExceptionCategory.SECURITY_DENIED;
      }
      if (msg.contains("property") || msg.contains("cannot find")) {
        return ExceptionCategory.INVALID_PROPERTY;
      }
      if (msg.contains("method") || msg.contains("not found in class")) {
        return ExceptionCategory.INVALID_METHOD;
      }
      if (msg.contains("divide by zero") || msg.contains("arithmetic")) {
        return ExceptionCategory.ARITHMETIC_ERROR;
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
