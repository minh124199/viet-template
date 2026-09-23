package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.vtl.engine.EngineFingerprint;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.Objects;

/**
 * Multi-dimensional cache key uniquely capturing every semantic, optimization, and security input
 * that can affect the compiled representation of a template.
 *
 * <p>Engine-invariant parameters are composed in {@link EngineFingerprint}, which captures compiler
 * version, optimization level, execution tier, access policy identifier, model schema signature,
 * and backend options hash.
 *
 * @param templateId normalized template identifier
 * @param sourceFingerprint SHA-256 hex digest of the template source text
 * @param engineFingerprint precomputed engine-invariant fingerprint composing compiler version,
 *     optimization level, execution tier, access policy ID, model signature, and backend hash
 * @param globalMacrosFingerprint cryptographic fingerprint of configured global macro libraries
 */
public record CompileCacheKey(
    TemplateId templateId,
    String sourceFingerprint,
    EngineFingerprint engineFingerprint,
    String globalMacrosFingerprint) {

  public CompileCacheKey {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(sourceFingerprint, "sourceFingerprint must not be null");
    Objects.requireNonNull(engineFingerprint, "engineFingerprint must not be null");
    Objects.requireNonNull(globalMacrosFingerprint, "globalMacrosFingerprint must not be null");
  }

  public CompileCacheKey(
      TemplateId templateId,
      String sourceFingerprint,
      String compilerVersion,
      OptimizationLevel optimizationLevel,
      ExecutionTier executionTier,
      String accessPolicyId,
      String modelSignature,
      String backendOptionsHash,
      String globalMacrosFingerprint) {
    this(
        templateId,
        sourceFingerprint,
        new EngineFingerprint(
            compilerVersion,
            optimizationLevel,
            executionTier,
            accessPolicyId,
            modelSignature,
            backendOptionsHash),
        globalMacrosFingerprint);
  }

  public String compilerVersion() {
    return engineFingerprint.compilerVersion();
  }

  public OptimizationLevel optimizationLevel() {
    return engineFingerprint.optimizationLevel();
  }

  public ExecutionTier executionTier() {
    return engineFingerprint.executionTier();
  }

  public String accessPolicyId() {
    return engineFingerprint.accessPolicyId();
  }

  public String modelSignature() {
    return engineFingerprint.modelSignature();
  }

  public String backendOptionsHash() {
    return engineFingerprint.backendHash();
  }

  public static CompileCacheKey of(
      TemplateId templateId,
      String sourceFingerprint,
      EngineFingerprint engineFingerprint,
      String globalMacrosFingerprint) {
    return new CompileCacheKey(
        templateId, sourceFingerprint, engineFingerprint, globalMacrosFingerprint);
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
        backendOptionsHash,
        "");
  }

  public static CompileCacheKey of(
      TemplateId templateId,
      String sourceFingerprint,
      String compilerVersion,
      OptimizationLevel optimizationLevel,
      ExecutionTier executionTier,
      String accessPolicyId,
      String modelSignature,
      String backendOptionsHash,
      String globalMacrosFingerprint) {
    return new CompileCacheKey(
        templateId,
        sourceFingerprint,
        compilerVersion,
        optimizationLevel,
        executionTier,
        accessPolicyId,
        modelSignature,
        backendOptionsHash,
        globalMacrosFingerprint);
  }
}
