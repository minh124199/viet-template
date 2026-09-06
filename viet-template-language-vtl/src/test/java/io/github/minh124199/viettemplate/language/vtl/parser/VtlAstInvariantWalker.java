package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;

/** Test utility verifying structural invariants and span containment on VTL AST nodes. */
public final class VtlAstInvariantWalker {

  private VtlAstInvariantWalker() {}

  public static void assertInvariants(VtlTemplate template, SourceText source) {
    assertSpanValid(template.span(), source);
    for (VtlNode child : template.children()) {
      assertNodeInvariants(child, template.span(), source);
    }
  }

  private static void assertNodeInvariants(VtlNode node, SourceSpan parentSpan, SourceText source) {
    assertSpanValid(node.span(), source);
    assertContained(parentSpan, node.span(), "Node within parent");

    if (node instanceof VtlReferenceOutputNode refOut) {
      assertReferenceInvariants(refOut.reference(), node.span(), source);
    } else if (node instanceof VtlSetDirectiveNode set) {
      if (set.target() instanceof VtlAssignmentTarget.ReferenceTarget refTarget) {
        assertReferenceInvariants(refTarget.reference(), node.span(), source);
      }
      assertExpressionInvariants(set.valueExpression(), node.span(), source);
    } else if (node instanceof VtlIfDirectiveNode ifNode) {
      for (VtlIfBranch branch : ifNode.branches()) {
        assertSpanValid(branch.span(), source);
        assertContained(node.span(), branch.span(), "IfBranch within IfDirective");
        assertExpressionInvariants(branch.condition(), branch.span(), source);
        for (VtlNode bChild : branch.body()) {
          assertNodeInvariants(bChild, branch.span(), source);
        }
      }
      ifNode
          .elseBody()
          .ifPresent(
              elseNodes -> {
                for (VtlNode eNode : elseNodes) {
                  assertNodeInvariants(eNode, node.span(), source);
                }
              });
    } else if (node instanceof VtlForeachDirectiveNode fe) {
      assertReferenceInvariants(fe.loopVariable(), node.span(), source);
      assertExpressionInvariants(fe.iterable(), node.span(), source);
      for (VtlNode bNode : fe.body()) {
        assertNodeInvariants(bNode, node.span(), source);
      }
      fe.elseBody()
          .ifPresent(
              elseNodes -> {
                for (VtlNode eNode : elseNodes) {
                  assertNodeInvariants(eNode, node.span(), source);
                }
              });
    } else if (node instanceof VtlIncludeDirectiveNode inc) {
      for (VtlExpression arg : inc.arguments()) {
        assertExpressionInvariants(arg, node.span(), source);
      }
    } else if (node instanceof VtlParseDirectiveNode parse) {
      assertExpressionInvariants(parse.templateExpression(), node.span(), source);
    } else if (node instanceof VtlBreakDirectiveNode brk) {
      brk.scopeExpression().ifPresent(e -> assertExpressionInvariants(e, node.span(), source));
    } else if (node instanceof VtlStopDirectiveNode stop) {
      stop.messageExpression().ifPresent(e -> assertExpressionInvariants(e, node.span(), source));
    } else if (node instanceof VtlEvaluateDirectiveNode eval) {
      assertExpressionInvariants(eval.expression(), node.span(), source);
    } else if (node instanceof VtlDefineDirectiveNode def) {
      assertReferenceInvariants(def.targetReference(), node.span(), source);
      for (VtlNode bNode : def.body()) {
        assertNodeInvariants(bNode, node.span(), source);
      }
    } else if (node instanceof VtlMacroDefinitionNode mdef) {
      for (VtlMacroParameter param : mdef.parameters()) {
        assertSpanValid(param.span(), source);
        assertContained(node.span(), param.span(), "Parameter within MacroDefinition");
        param
            .defaultValue()
            .ifPresent(def -> assertExpressionInvariants(def, param.span(), source));
      }
      for (VtlNode bNode : mdef.body()) {
        assertNodeInvariants(bNode, node.span(), source);
      }
    } else if (node instanceof VtlDirectiveCallNode call) {
      for (VtlExpression arg : call.arguments()) {
        assertExpressionInvariants(arg, node.span(), source);
      }
    } else if (node instanceof VtlBlockDirectiveCallNode bcall) {
      for (VtlExpression arg : bcall.arguments()) {
        assertExpressionInvariants(arg, node.span(), source);
      }
      for (VtlNode bNode : bcall.body()) {
        assertNodeInvariants(bNode, node.span(), source);
      }
    }
  }

  private static void assertReferenceInvariants(
      VtlReference ref, SourceSpan parentSpan, SourceText source) {
    assertSpanValid(ref.span(), source);
    assertContained(parentSpan, ref.span(), "Reference within parent");
    for (VtlAccessStep step : ref.accessSteps()) {
      assertSpanValid(step.span(), source);
      assertContained(ref.span(), step.span(), "AccessStep within Reference");
      if (step instanceof VtlAccessStep.MethodCall mc) {
        for (VtlExpression arg : mc.arguments()) {
          assertExpressionInvariants(arg, step.span(), source);
        }
      } else if (step instanceof VtlAccessStep.IndexAccess ia) {
        assertExpressionInvariants(ia.indexExpression(), step.span(), source);
      }
    }
    ref.alternateValue().ifPresent(alt -> assertExpressionInvariants(alt, ref.span(), source));
  }

  private static void assertExpressionInvariants(
      VtlExpression expr, SourceSpan parentSpan, SourceText source) {
    assertSpanValid(expr.span(), source);
    assertContained(parentSpan, expr.span(), "Expression within parent");

    if (expr instanceof VtlReferenceExpression refExpr) {
      assertReferenceInvariants(refExpr.reference(), expr.span(), source);
    } else if (expr instanceof VtlUnaryExpression un) {
      assertExpressionInvariants(un.operand(), expr.span(), source);
    } else if (expr instanceof VtlBinaryExpression bin) {
      assertExpressionInvariants(bin.left(), expr.span(), source);
      assertExpressionInvariants(bin.right(), expr.span(), source);
    } else if (expr instanceof VtlGroupedExpression grp) {
      assertExpressionInvariants(grp.expression(), expr.span(), source);
    } else if (expr instanceof VtlListLiteralExpression list) {
      for (VtlExpression item : list.elements()) {
        assertExpressionInvariants(item, expr.span(), source);
      }
    } else if (expr instanceof VtlRangeExpression range) {
      assertExpressionInvariants(range.start(), expr.span(), source);
      assertExpressionInvariants(range.end(), expr.span(), source);
    } else if (expr instanceof VtlMapLiteralExpression map) {
      for (VtlMapEntry entry : map.entries()) {
        assertSpanValid(entry.span(), source);
        assertContained(expr.span(), entry.span(), "MapEntry within MapLiteral");
        assertExpressionInvariants(entry.key(), entry.span(), source);
        assertExpressionInvariants(entry.value(), entry.span(), source);
      }
    } else if (expr instanceof VtlInterpolatedStringExpression interp) {
      for (var part : interp.parts()) {
        assertSpanValid(part.span(), source);
        assertContained(expr.span(), part.span(), "InterpolatedPart within InterpolatedString");
        if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          assertReferenceInvariants(rp.reference(), part.span(), source);
        }
      }
    }
  }

  private static void assertSpanValid(SourceSpan span, SourceText source) {
    assertTrue(span.startOffset() >= 0, "startOffset >= 0: " + span);
    assertTrue(span.endOffset() >= span.startOffset(), "endOffset >= startOffset: " + span);
    assertTrue(
        span.endOffset() <= source.length(),
        "endOffset <= source.length(): " + span + " vs " + source.length());
  }

  private static void assertContained(SourceSpan parent, SourceSpan child, String msg) {
    assertTrue(
        parent.startOffset() <= child.startOffset(),
        msg
            + " startOffset ("
            + child.startOffset()
            + ") >= parent startOffset ("
            + parent.startOffset()
            + ")");
    assertTrue(
        parent.endOffset() >= child.endOffset(),
        msg
            + " endOffset ("
            + child.endOffset()
            + ") <= parent endOffset ("
            + parent.endOffset()
            + ")");
  }
}
