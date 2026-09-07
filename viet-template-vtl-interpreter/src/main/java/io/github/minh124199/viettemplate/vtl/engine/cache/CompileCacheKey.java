package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.Serializable;
import java.util.Objects;

/**
 * Multi-dimensional cache key uniquely capturing every semantic, optimization, and security input
 * that can affect the compiled representation of a template.
 *
 * @param templateId normalized template identifier
 * @param sourceFingerprint SHA-256 hex digest of the template source text
 * @param compilerVersion compiler engine version (e.g. 0.1.1-SNAPSHOT)
 * @param optimizationLevel intermediate representation optimization level (O0..O3)
 * @param executionTier target execution tier (AOT_BYTECODE, IR, AST)
 * @param accessPolicyId unique identifier for the active linker access policy
 * @param modelSignature cryptographic signature or schema descriptor of the typed model
 * @param backendOptionsHash hash capturing backend compiler options
 */
public record CompileCacheKey(
    TemplateId templateId,
    String sourceFingerprint,
    String compilerVersion,
    OptimizationLevel optimizationLevel,
    ExecutionTier executionTier,
    String accessPolicyId,
    String modelSignature,
    String backendOptionsHash)
    implements Serializable {

  public CompileCacheKey {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(sourceFingerprint, "sourceFingerprint must not be null");
    Objects.requireNonNull(compilerVersion, "compilerVersion must not be null");
    Objects.requireNonNull(optimizationLevel, "optimizationLevel must not be null");
    Objects.requireNonNull(executionTier, "executionTier must not be null");
    Objects.requireNonNull(accessPolicyId, "accessPolicyId must not be null");
    Objects.requireNonNull(modelSignature, "modelSignature must not be null");
    Objects.requireNonNull(backendOptionsHash, "backendOptionsHash must not be null");
  }

  public static CompileCacheKey of(
      TemplateId templateId,
      String sourceFingerprint,
      String compilerVersion,
      OptimizationLevel optimizationLevel,
      ExecutionTier executionTier,
      String accessPolicyId,
      String modelSignature,
      String backendOptionsHash) {
    return new CompileCacheKey(
        templateId,
        sourceFingerprint,
        compilerVersion,
        optimizationLevel,
        executionTier,
        accessPolicyId,
        modelSignature,
        backendOptionsHash);
  }
}
