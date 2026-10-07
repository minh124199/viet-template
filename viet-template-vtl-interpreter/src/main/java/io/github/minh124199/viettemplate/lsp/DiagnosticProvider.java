package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.internal.CanonicalModelSchemaConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Diagnostic analysis provider emitting deterministic syntax and compiler-parity semantic
 * diagnostics.
 *
 * <p>Emits stable diagnostic codes backed by {@link VtlSemanticAnalyzer}:
 *
 * <ul>
 *   <li>{@code SYNTAX:PARSE_ERROR} - syntax errors
 *   <li>{@code VTLS:2101} - unresolved root variable under authoritative schema
 *   <li>{@code VTLS:2104} - property not found on declared type with Levenshtein suggestions
 *   <li>{@code VTLS:2107} - nullable receiver dereferenced without quiet reference notation
 *   <li>{@code VTLSEC:2401} - access denied by security member access policy
 * </ul>
 */
final class DiagnosticProvider {

  private static final DiagnosticCode CODE_SYNTAX_ERROR =
      DiagnosticCode.of("SYNTAX", "PARSE_ERROR");
  private static final DiagnosticCode CODE_SECURITY_DENIED = DiagnosticCode.of("VTLSEC", "2401");

  private static final Set<String> SENSITIVE_PROPERTIES =
      Set.of(
          "class",
          "declaringClass",
          "classLoader",
          "protectionDomain",
          "module",
          "securityManager");

  private DiagnosticProvider() {}

  public static List<Diagnostic> diagnostics(
      TemplateDocument doc, CanonicalSchemaResolver schemaResolver, MemberAccessPolicy policy) {
    Objects.requireNonNull(doc, "doc must not be null");
    Objects.requireNonNull(schemaResolver, "schemaResolver must not be null");

    List<Diagnostic> result = new ArrayList<>();
    VtlParseResult parsed;
    try {
      parsed = VtlParser.parse(doc.sourceText());
    } catch (IllegalArgumentException | IllegalStateException e) {
      result.add(
          Diagnostic.error(
              CODE_SYNTAX_ERROR,
              "Failed to parse template: " + e.getMessage(),
              SourceSpan.of(0, 0, 1, 1, 1, 1)));
      return sortDiagnostics(result);
    }

    // 1. Syntax diagnostics from parser
    for (Diagnostic d : parsed.diagnostics()) {
      DiagnosticCode code = d.code();
      if (code != null && "PARSER".equalsIgnoreCase(code.category())) {
        code = CODE_SYNTAX_ERROR;
      }
      result.add(new Diagnostic(d.severity(), code, d.message(), d.primarySpan()));
    }

    // 2. Semantic diagnostics via compiler analyzer if canonical schema is available
    try {
      Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(doc.uri());
      if (schema.isPresent()) {
        ClassLoader cl =
            schemaResolver.classLoader().orElse(Thread.currentThread().getContextClassLoader());
        ModelSchema modelSchema = CanonicalModelSchemaConverter.toModelSchema(schema.get(), cl);
        MemberAccessPolicy effectivePolicy =
            policy != null ? policy : MemberAccessPolicy.standard();
        VtlSemanticOptions options =
            VtlSemanticOptions.builder()
                .profile(VtlProfile.VTL_CORE)
                .modelSchema(modelSchema)
                .typeCheckingMode(TypeCheckingMode.ERROR)
                .memberAccessPolicy(effectivePolicy)
                .build();
        SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(parsed.template(), options);
        List<GuardedTarget> guards = collectGuards(parsed.template().children());

        for (Diagnostic d : analysis.diagnostics()) {
          // Check for security violation on sensitive or denied properties
          if (d.code() != null && "VTLS:2104".equals(d.code().qualifiedCode())) {
            Optional<String> prop = extractPropertyName(d.message());
            if (prop.isPresent()
                && (SENSITIVE_PROPERTIES.contains(prop.get())
                    || !effectivePolicy.isPropertyPermitted(Object.class, prop.get()))) {
              result.add(
                  Diagnostic.error(
                      CODE_SECURITY_DENIED,
                      "Access to property '" + prop.get() + "' is denied by security policy",
                      d.primarySpan()));
              continue;
            }
          }

          // Check for nullable dereferences guarded by #if
          if (d.code() != null && "VTLS:2107".equals(d.code().qualifiedCode())) {
            if (isGuarded(d, guards)) {
              continue;
            }
          }

          result.add(d);
        }
      }
    } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException ignored) {
      // Best-effort semantic diagnostics on malformed or partially invalid ASTs
    }

    return sortDiagnostics(result);
  }

  private static Optional<String> extractPropertyName(String msg) {
    if (msg != null && msg.startsWith("Property '")) {
      int end = msg.indexOf('\'', 10);
      if (end > 10) {
        return Optional.of(msg.substring(10, end));
      }
    }
    return Optional.empty();
  }

  private record GuardedTarget(int startOffset, int endOffset, String target) {}

  private static List<GuardedTarget> collectGuards(List<VtlNode> nodes) {
    List<GuardedTarget> list = new ArrayList<>();
    collectGuardsRecursive(nodes, list);
    return list;
  }

  private static void collectGuardsRecursive(List<VtlNode> nodes, List<GuardedTarget> list) {
    if (nodes == null) return;
    for (VtlNode node : nodes) {
      if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          Set<String> targets = extractGuardedTargets(branch.condition());
          if (!targets.isEmpty() && !branch.body().isEmpty()) {
            int start = branch.body().get(0).span().startOffset();
            int end = branch.body().get(branch.body().size() - 1).span().endOffset();
            for (String t : targets) {
              list.add(new GuardedTarget(start, end, t));
            }
          }
          collectGuardsRecursive(branch.body(), list);
        }
        if (ifNode.elseBody().isPresent()) {
          collectGuardsRecursive(ifNode.elseBody().get(), list);
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        collectGuardsRecursive(feNode.body(), list);
        if (feNode.elseBody().isPresent()) {
          collectGuardsRecursive(feNode.elseBody().get(), list);
        }
      }
    }
  }

  private static Set<String> extractGuardedTargets(VtlExpression condition) {
    Set<String> targets = new HashSet<>();
    collectGuardedTargets(condition, targets);
    return targets;
  }

  private static void collectGuardedTargets(VtlExpression expr, Set<String> targets) {
    if (expr instanceof VtlReferenceExpression refExpr) {
      targets.add(toTargetString(refExpr.reference()));
      return;
    }
    if (expr instanceof VtlBinaryExpression bin) {
      if (bin.operator() == VtlBinaryOperator.LOGICAL_AND) {
        collectGuardedTargets(bin.left(), targets);
        collectGuardedTargets(bin.right(), targets);
      } else if (bin.operator() == VtlBinaryOperator.NOT_EQUAL) {
        if (bin.left() instanceof VtlReferenceExpression ref && isNullLiteral(bin.right())) {
          targets.add(toTargetString(ref.reference()));
        } else if (bin.right() instanceof VtlReferenceExpression ref && isNullLiteral(bin.left())) {
          targets.add(toTargetString(ref.reference()));
        }
      }
    }
  }

  private static boolean isNullLiteral(VtlExpression expr) {
    if (expr instanceof VtlNullLiteralExpression) return true;
    if (expr instanceof VtlReferenceExpression ref && ref.reference().steps().isEmpty()) {
      return "null".equals(ref.reference().rootName());
    }
    return false;
  }

  private static String toTargetString(VtlReference ref) {
    StringBuilder sb = new StringBuilder("$").append(ref.rootName());
    for (VtlAccessStep step : ref.steps()) {
      if (step instanceof VtlAccessStep.PropertyAccess pa) {
        sb.append('.').append(pa.propertyName());
      }
    }
    return sb.toString();
  }

  private static boolean isGuarded(Diagnostic d, List<GuardedTarget> guards) {
    String msg = d.message();
    int idx = msg.indexOf("nullable target '");
    if (idx < 0) return false;
    int start = idx + "nullable target '".length();
    int end = msg.indexOf('\'', start);
    if (end < 0) return false;
    String target = msg.substring(start, end);
    int diagStart = d.primarySpan().startOffset();
    int diagEnd = d.primarySpan().endOffset();
    for (GuardedTarget g : guards) {
      if (diagStart >= g.startOffset() && diagEnd <= g.endOffset()) {
        if (target.equals(g.target()) || target.startsWith(g.target() + ".")) {
          return true;
        }
      }
    }
    return false;
  }

  private static List<Diagnostic> sortDiagnostics(List<Diagnostic> diags) {
    diags.sort(
        Comparator.comparing((Diagnostic d) -> d.primarySpan().startLine())
            .thenComparing(d -> d.primarySpan().startColumn())
            .thenComparing(d -> d.severity().ordinal())
            .thenComparing(d -> d.code() != null ? d.code().qualifiedCode() : ""));
    return Collections.unmodifiableList(diags);
  }
}
