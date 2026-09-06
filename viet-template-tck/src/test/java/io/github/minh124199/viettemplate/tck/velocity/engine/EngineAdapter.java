package io.github.minh124199.viettemplate.tck.velocity.engine;

import io.github.minh124199.viettemplate.tck.velocity.result.EngineResult;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import java.util.Map;

/** Common SPI adapter for executing a compatibility scenario on a specific engine. */
public interface EngineAdapter {

  /** Identifies the underlying template engine. */
  EngineIdentity identity();

  /**
   * Executes the scenario using the provided independent context and configuration.
   *
   * @param scenario The scenario being tested.
   * @param context Fresh variable bindings for this execution.
   * @param configuration Engine settings.
   * @param resources In-memory resources for sub-templates.
   * @return Normalized execution outcome and observations.
   */
  EngineResult execute(
      CompatibilityScenario scenario,
      Map<String, Object> context,
      CompatibilityConfiguration configuration,
      Map<String, String> resources);
}
