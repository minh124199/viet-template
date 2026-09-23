package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.Objects;

/**
 * Precomputed engine-invariant fingerprint capturing immutable compiler, optimization, execution,
 * security policy, and model schema descriptors at engine construction time.
 */
record EngineFingerprint(
    String compilerVersion,
    OptimizationLevel optimizationLevel,
    ExecutionTier executionTier,
    String accessPolicyId,
    String modelSignature,
    String backendHash) {

  EngineFingerprint {
    Objects.requireNonNull(compilerVersion, "compilerVersion must not be null");
    Objects.requireNonNull(optimizationLevel, "optimizationLevel must not be null");
    Objects.requireNonNull(executionTier, "executionTier must not be null");
    Objects.requireNonNull(accessPolicyId, "accessPolicyId must not be null");
    Objects.requireNonNull(modelSignature, "modelSignature must not be null");
    Objects.requireNonNull(backendHash, "backendHash must not be null");
  }
}
