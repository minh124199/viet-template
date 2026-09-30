package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.lsp.CanonicalSchemaModel.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Diagnostic analysis provider emitting deterministic syntax and schema-backed semantic
 * diagnostics.
 *
 * <p>Emits stable diagnostic codes:
 *
 * <ul>
 *   <li>{@code SYNTAX:PARSE_ERROR} - syntax errors
 *   <li>{@code VTLS:2101} - unresolved root variable under authoritative schema
 *   <li>{@code VTLS:2104} - property not found on declared type
 *   <li>{@code VTLS:2107} - nullable receiver dereferenced without quiet reference notation
 *   <li>{@code VTLSEC:2401} - access denied by security member access policy
 * </ul>
 */
final class DiagnosticProvider {

  private static final DiagnosticCode CODE_SYNTAX_ERROR =
      DiagnosticCode.of("SYNTAX", "PARSE_ERROR");
  private static final DiagnosticCode CODE_UNRESOLVED_ROOT = DiagnosticCode.of("VTLS", "2101");
  private static final DiagnosticCode CODE_PROPERTY_NOT_FOUND = DiagnosticCode.of("VTLS", "2104");
  private static final DiagnosticCode CODE_NULLABLE_DEREFERENCE = DiagnosticCode.of("VTLS", "2107");
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

    // 2. Semantic diagnostics if canonical schema is available
    Optional<SchemaEnvelope> schema = schemaResolver.resolveSchema(doc.uri());
    if (schema.isPresent()) {
      Set<String> inScopeVars = new HashSet<>();
      inScopeVars.add("foreach");
      collectScopeVariables(parsed.template().children(), inScopeVars);

      analyzeSemantics(
          parsed.template().children(),
          doc,
          schema.get(),
          schemaResolver,
          policy,
          inScopeVars,
          false,
          result);
    }

    return sortDiagnostics(result);
  }

  private static void collectScopeVariables(List<VtlNode> nodes, Set<String> inScopeVars) {
    if (nodes == null) return;
    for (VtlNode node : nodes) {
      if (node instanceof VtlSetDirectiveNode setNode) {
        if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
          inScopeVars.add(refTarget.reference().rootName());
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          collectScopeVariables(branch.body(), inScopeVars);
        }
        ifNode.elseBody().ifPresent(body -> collectScopeVariables(body, inScopeVars));
      }
    }
  }

  private static void analyzeSemantics(
      List<VtlNode> nodes,
      TemplateDocument doc,
      SchemaEnvelope schema,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy,
      Set<String> inScopeVars,
      boolean insideNullCheck,
      List<Diagnostic> out) {
    if (nodes == null) return;

    for (VtlNode node : nodes) {
      if (node instanceof VtlReferenceOutputNode refOut) {
        checkReference(
            refOut.reference(),
            doc,
            schema,
            schemaResolver,
            policy,
            inScopeVars,
            insideNullCheck,
            out);
      } else if (node instanceof VtlSetDirectiveNode setNode) {
        checkExpression(
            setNode.value(),
            doc,
            schema,
            schemaResolver,
            policy,
            inScopeVars,
            insideNullCheck,
            out);
        if (setNode.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
          inScopeVars.add(refTarget.reference().rootName());
        }
      } else if (node instanceof VtlForeachDirectiveNode feNode) {
        checkExpression(
            feNode.iterable(),
            doc,
            schema,
            schemaResolver,
            policy,
            inScopeVars,
            insideNullCheck,
            out);
        Set<String> loopScope = new HashSet<>(inScopeVars);
        loopScope.add(feNode.loopVariable().rootName());
        analyzeSemantics(
            feNode.body(), doc, schema, schemaResolver, policy, loopScope, insideNullCheck, out);
        if (feNode.elseBody().isPresent()) {
          analyzeSemantics(
              feNode.elseBody().get(),
              doc,
              schema,
              schemaResolver,
              policy,
              inScopeVars,
              insideNullCheck,
              out);
        }
      } else if (node instanceof VtlIfDirectiveNode ifNode) {
        for (VtlIfBranch branch : ifNode.branches()) {
          boolean guarded = isNullGuardCondition(branch.condition());
          checkExpression(
              branch.condition(),
              doc,
              schema,
              schemaResolver,
              policy,
              inScopeVars,
              guarded || insideNullCheck,
              out);
          analyzeSemantics(
              branch.body(),
              doc,
              schema,
              schemaResolver,
              policy,
              inScopeVars,
              guarded || insideNullCheck,
              out);
        }
        if (ifNode.elseBody().isPresent()) {
          analyzeSemantics(
              ifNode.elseBody().get(),
              doc,
              schema,
              schemaResolver,
              policy,
              inScopeVars,
              insideNullCheck,
              out);
        }
      }
    }
  }

  private static void checkExpression(
      VtlExpression expr,
      TemplateDocument doc,
      SchemaEnvelope schema,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy,
      Set<String> inScopeVars,
      boolean insideNullCheck,
      List<Diagnostic> out) {
    if (expr == null) return;
    if (expr instanceof VtlReferenceExpression refExpr) {
      checkReference(
          refExpr.reference(),
          doc,
          schema,
          schemaResolver,
          policy,
          inScopeVars,
          insideNullCheck,
          out);
    } else if (expr instanceof VtlBinaryExpression bin) {
      checkExpression(
          bin.left(), doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
      checkExpression(
          bin.right(), doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
    } else if (expr instanceof VtlUnaryExpression un) {
      checkExpression(
          un.operand(), doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
    } else if (expr instanceof VtlGroupedExpression grp) {
      checkExpression(
          grp.expression(), doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
    } else if (expr instanceof VtlListLiteralExpression list) {
      for (VtlExpression e : list.elements()) {
        checkExpression(e, doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
      }
    } else if (expr instanceof VtlMapLiteralExpression map) {
      for (VtlMapEntry entry : map.entries()) {
        checkExpression(
            entry.key(), doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
        checkExpression(
            entry.value(), doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
      }
    } else if (expr instanceof VtlRangeExpression range) {
      checkExpression(
          range.start(), doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
      checkExpression(
          range.end(), doc, schema, schemaResolver, policy, inScopeVars, insideNullCheck, out);
    } else if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : interp.parts()) {
        if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          checkReference(
              rp.reference(),
              doc,
              schema,
              schemaResolver,
              policy,
              inScopeVars,
              insideNullCheck,
              out);
        }
      }
    }
  }

  private static boolean isNullGuardCondition(VtlExpression condition) {
    if (condition instanceof VtlReferenceExpression) {
      return true; // e.g. #if($user)
    }
    if (condition instanceof VtlBinaryExpression bin) {
      return bin.operator() == VtlBinaryOperator.NOT_EQUAL; // e.g. #if($user != null)
    }
    return false;
  }

  private static void checkReference(
      VtlReference ref,
      TemplateDocument doc,
      SchemaEnvelope schema,
      CanonicalSchemaResolver schemaResolver,
      MemberAccessPolicy policy,
      Set<String> inScopeVars,
      boolean insideNullCheck,
      List<Diagnostic> out) {
    String root = ref.rootName();
    if (root.isEmpty()) {
      return;
    }

    // 1. Root variable check
    ParameterDef param = schema.parameters().get(root);
    if (param == null && !inScopeVars.contains(root)) {
      out.add(
          Diagnostic.error(CODE_UNRESOLVED_ROOT, "Unresolved root variable: $" + root, ref.span()));
      return;
    }

    if (param == null) {
      // Local variable, type unknown from schema
      return;
    }

    TypeRef currentType = param.type();
    boolean currentNullable = param.nullable();
    String receiverDesc = "$" + root;

    // 2. Step validation & nullable dereference checks
    for (VtlAccessStep step : ref.steps()) {
      if (currentNullable && !ref.isQuiet() && !insideNullCheck) {
        out.add(
            Diagnostic.warning(
                CODE_NULLABLE_DEREFERENCE,
                "Nullable receiver '"
                    + receiverDesc
                    + "' dereferenced without quiet reference notation",
                ref.span()));
        break;
      }

      if (step instanceof VtlAccessStep.MethodCall mc) {
        if (SENSITIVE_PROPERTIES.contains(mc.methodName())
            || "getClass".equals(mc.methodName())
            || "getClassLoader".equals(mc.methodName())) {
          out.add(
              Diagnostic.error(
                  CODE_SECURITY_DENIED,
                  "Access to method '" + mc.methodName() + "' is denied by security policy",
                  mc.span()));
          break;
        }
      } else if (step instanceof VtlAccessStep.PropertyAccess prop) {
        String typeName = extractTypeName(currentType);
        if (typeName == null) {
          break;
        }

        TypeDef typeDef = schema.types().get(typeName);
        if (typeDef == null) {
          break;
        }

        // Security check
        if (SENSITIVE_PROPERTIES.contains(prop.propertyName())) {
          out.add(
              Diagnostic.error(
                  CODE_SECURITY_DENIED,
                  "Access to property '" + prop.propertyName() + "' is denied by security policy",
                  prop.span()));
          break;
        }

        PropertyDef propDef = typeDef.properties().get(prop.propertyName());
        if (propDef == null) {
          out.add(
              Diagnostic.error(
                  CODE_PROPERTY_NOT_FOUND,
                  "Property '" + prop.propertyName() + "' not found on type '" + typeName + "'",
                  prop.span()));
          break;
        }

        if (policy != null) {
          try {
            Class<?> clazz =
                Class.forName(typeName, false, Thread.currentThread().getContextClassLoader());
            if (!policy.isClassPermitted(clazz)
                || !policy.isPropertyPermitted(clazz, prop.propertyName())) {
              out.add(
                  Diagnostic.error(
                      CODE_SECURITY_DENIED,
                      "Access to property '"
                          + prop.propertyName()
                          + "' is denied by security policy",
                      prop.span()));
              break;
            }
          } catch (ClassNotFoundException ignored) {
          }
        }

        currentType = propDef.type();
        currentNullable = propDef.nullable();
        receiverDesc = receiverDesc + "." + prop.propertyName();
      }
    }
  }

  private static String extractTypeName(TypeRef type) {
    if (type instanceof ClassTypeRef ctr) return ctr.name();
    if (type instanceof NamedTypeRef ntr) return ntr.name();
    if (type instanceof ParameterizedTypeRef ptr) return ptr.rawType();
    return null;
  }

  private static List<Diagnostic> sortDiagnostics(List<Diagnostic> diags) {
    List<Diagnostic> sorted = new ArrayList<>(diags);
    sorted.sort(
        Comparator.comparingInt((Diagnostic d) -> d.primarySpan().startOffset())
            .thenComparingInt(d -> d.primarySpan().endOffset())
            .thenComparing(d -> d.severity().name())
            .thenComparing(d -> d.code().qualifiedCode())
            .thenComparing(Diagnostic::message));
    return Collections.unmodifiableList(sorted);
  }
}
