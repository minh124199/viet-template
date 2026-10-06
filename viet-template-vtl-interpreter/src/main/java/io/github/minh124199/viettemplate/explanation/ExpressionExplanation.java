package io.github.minh124199.viettemplate.explanation;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Detailed compiler explanation for an individual template expression or reference site.
 *
 * @param span source span of the expression
 * @param sourceText original template text of the expression
 * @param expressionKind syntactic kind of the expression (e.g. PROPERTY_ACCESS, METHOD_CALL,
 *     VARIABLE)
 * @param inferredType inferred semantic type name
 * @param typeConfidence confidence level of the type inference (e.g. EXACT, DYNAMIC, ERROR)
 * @param nullability nullability classification (e.g. NON_NULL, NULLABLE, UNKNOWN)
 * @param symbolOrigin origin scope of the root symbol (e.g. ROOT_MODEL, LOCAL, LOOP, MACRO)
 * @param receiverType type of the immediate receiver if this is an access step
 * @param resolvedMember name or signature of the resolved property or method
 * @param resolutionStrategy resolution and access strategy (e.g. DIRECT_RECORD, DIRECT_GETTER,
 *     DYNAMIC)
 * @param directAccess whether access is statically bound without dynamic linker fallback
 * @param aotEligible whether the expression qualifies for static AOT compilation
 * @param aotRejectionReasons structured reasons if static AOT compilation was rejected
 * @param outputDispatch output specialization target (e.g. WRITE_STRING_SPECIALIZED)
 * @param outputMethod runtime bridge output method invoked
 * @param escaping active escape mode (e.g. RAW, HTML_TEXT)
 * @param securityPolicy active member access security policy
 * @param optimizationRejections reasons why output specialization was bypassed
 */
public record ExpressionExplanation(
    SourceSpan span,
    String sourceText,
    String expressionKind,
    String inferredType,
    String typeConfidence,
    String nullability,
    String symbolOrigin,
    Optional<String> receiverType,
    Optional<String> resolvedMember,
    String resolutionStrategy,
    boolean directAccess,
    boolean aotEligible,
    List<String> aotRejectionReasons,
    Optional<String> outputDispatch,
    Optional<String> outputMethod,
    Optional<String> escaping,
    Optional<String> securityPolicy,
    List<String> optimizationRejections)
    implements Serializable {

  public ExpressionExplanation {
    Objects.requireNonNull(span, "span must not be null");
    Objects.requireNonNull(sourceText, "sourceText must not be null");
    Objects.requireNonNull(expressionKind, "expressionKind must not be null");
    Objects.requireNonNull(inferredType, "inferredType must not be null");
    Objects.requireNonNull(typeConfidence, "typeConfidence must not be null");
    Objects.requireNonNull(nullability, "nullability must not be null");
    Objects.requireNonNull(symbolOrigin, "symbolOrigin must not be null");
    receiverType = receiverType != null ? receiverType : Optional.empty();
    resolvedMember = resolvedMember != null ? resolvedMember : Optional.empty();
    Objects.requireNonNull(resolutionStrategy, "resolutionStrategy must not be null");
    aotRejectionReasons =
        aotRejectionReasons != null ? List.copyOf(aotRejectionReasons) : List.of();
    outputDispatch = outputDispatch != null ? outputDispatch : Optional.empty();
    outputMethod = outputMethod != null ? outputMethod : Optional.empty();
    escaping = escaping != null ? escaping : Optional.empty();
    securityPolicy = securityPolicy != null ? securityPolicy : Optional.empty();
    optimizationRejections =
        optimizationRejections != null ? List.copyOf(optimizationRejections) : List.of();
  }
}
