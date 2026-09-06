package io.github.minh124199.viettemplate.language.vtl.parser;

import io.github.minh124199.viettemplate.language.vtl.ast.*;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;

/** Deterministic indented AST dumper for debugging, golden testing, and inspection. */
public final class VtlAstDumper {

  private VtlAstDumper() {}

  public static String dump(VtlNode root, SourceText source) {
    StringBuilder sb = new StringBuilder();
    dumpNode(root, 0, sb, source);
    return sb.toString();
  }

  private static void dumpNode(VtlNode node, int indent, StringBuilder sb, SourceText source) {
    String pad = "  ".repeat(indent);

    if (node instanceof VtlTemplate t) {
      sb.append(pad)
          .append("Template [")
          .append(t.span().startOffset())
          .append("..")
          .append(t.span().endOffset())
          .append("]\n");
      for (VtlNode child : t.children()) {
        dumpNode(child, indent + 1, sb, source);
      }
    } else if (node instanceof VtlTextNode txt) {
      sb.append(pad)
          .append("Text [")
          .append(txt.span().startOffset())
          .append("..")
          .append(txt.span().endOffset())
          .append("] \"")
          .append(escape(txt.text(source)))
          .append("\"\n");
    } else if (node instanceof VtlRawTextNode raw) {
      sb.append(pad)
          .append("RawText [")
          .append(raw.span().startOffset())
          .append("..")
          .append(raw.span().endOffset())
          .append("] \"")
          .append(escape(raw.text(source)))
          .append("\"\n");
    } else if (node instanceof VtlReferenceOutputNode ro) {
      sb.append(pad)
          .append("ReferenceOutput [")
          .append(ro.span().startOffset())
          .append("..")
          .append(ro.span().endOffset())
          .append("]\n");
      dumpReference(ro.reference(), indent + 1, sb, source);
    } else if (node instanceof VtlSetDirectiveNode set) {
      sb.append(pad)
          .append("SetDirective [")
          .append(set.span().startOffset())
          .append("..")
          .append(set.span().endOffset())
          .append("]\n");
      if (set.target() instanceof VtlAssignmentTarget.ReferenceTarget rt) {
        sb.append(pad).append("  Target:\n");
        dumpReference(rt.reference(), indent + 2, sb, source);
      }
      sb.append(pad).append("  Value:\n");
      dumpExpression(set.value(), indent + 2, sb, source);
    } else if (node instanceof VtlIfDirectiveNode ifDir) {
      sb.append(pad)
          .append("IfDirective [")
          .append(ifDir.span().startOffset())
          .append("..")
          .append(ifDir.span().endOffset())
          .append("]\n");
      for (int i = 0; i < ifDir.branches().size(); i++) {
        VtlIfBranch branch = ifDir.branches().get(i);
        sb.append(pad).append("  Branch ").append(i == 0 ? "(#if)" : "(#elseif)").append(":\n");
        sb.append(pad).append("    Condition:\n");
        dumpExpression(branch.condition(), indent + 3, sb, source);
        sb.append(pad).append("    Body:\n");
        for (VtlNode bNode : branch.body()) {
          dumpNode(bNode, indent + 3, sb, source);
        }
      }
      if (ifDir.elseBody().isPresent()) {
        sb.append(pad).append("  ElseBody:\n");
        for (VtlNode eNode : ifDir.elseBody().get()) {
          dumpNode(eNode, indent + 2, sb, source);
        }
      }
    } else if (node instanceof VtlForeachDirectiveNode fe) {
      sb.append(pad)
          .append("ForeachDirective [")
          .append(fe.span().startOffset())
          .append("..")
          .append(fe.span().endOffset())
          .append("] item=$")
          .append(fe.loopVariable().rootName())
          .append("\n");
      sb.append(pad).append("  Iterable:\n");
      dumpExpression(fe.iterable(), indent + 2, sb, source);
      sb.append(pad).append("  Body:\n");
      for (VtlNode bNode : fe.body()) {
        dumpNode(bNode, indent + 2, sb, source);
      }
      if (fe.elseBody().isPresent()) {
        sb.append(pad).append("  ElseBody:\n");
        for (VtlNode eNode : fe.elseBody().get()) {
          dumpNode(eNode, indent + 2, sb, source);
        }
      }
    } else if (node instanceof VtlIncludeDirectiveNode inc) {
      sb.append(pad)
          .append("IncludeDirective [")
          .append(inc.span().startOffset())
          .append("..")
          .append(inc.span().endOffset())
          .append("]\n");
      for (VtlExpression arg : inc.arguments()) {
        dumpExpression(arg, indent + 1, sb, source);
      }
    } else if (node instanceof VtlParseDirectiveNode parse) {
      sb.append(pad)
          .append("ParseDirective [")
          .append(parse.span().startOffset())
          .append("..")
          .append(parse.span().endOffset())
          .append("]\n");
      dumpExpression(parse.templateExpression(), indent + 1, sb, source);
    } else if (node instanceof VtlBreakDirectiveNode brk) {
      sb.append(pad)
          .append("BreakDirective [")
          .append(brk.span().startOffset())
          .append("..")
          .append(brk.span().endOffset())
          .append("]\n");
      brk.scopeExpression().ifPresent(e -> dumpExpression(e, indent + 1, sb, source));
    } else if (node instanceof VtlStopDirectiveNode stp) {
      sb.append(pad)
          .append("StopDirective [")
          .append(stp.span().startOffset())
          .append("..")
          .append(stp.span().endOffset())
          .append("]\n");
      stp.messageExpression().ifPresent(e -> dumpExpression(e, indent + 1, sb, source));
    } else if (node instanceof VtlEvaluateDirectiveNode eval) {
      sb.append(pad)
          .append("EvaluateDirective [")
          .append(eval.span().startOffset())
          .append("..")
          .append(eval.span().endOffset())
          .append("]\n");
      dumpExpression(eval.expression(), indent + 1, sb, source);
    } else if (node instanceof VtlDefineDirectiveNode def) {
      sb.append(pad)
          .append("DefineDirective [")
          .append(def.span().startOffset())
          .append("..")
          .append(def.span().endOffset())
          .append("] block=$")
          .append(def.targetReference().rootName())
          .append("\n");
      sb.append(pad).append("  Body:\n");
      for (VtlNode bNode : def.body()) {
        dumpNode(bNode, indent + 2, sb, source);
      }
    } else if (node instanceof VtlMacroDefinitionNode mdef) {
      sb.append(pad)
          .append("MacroDefinition [")
          .append(mdef.span().startOffset())
          .append("..")
          .append(mdef.span().endOffset())
          .append("] name=")
          .append(mdef.name())
          .append("\n");
      if (!mdef.parameters().isEmpty()) {
        sb.append(pad).append("  Parameters:\n");
        for (VtlMacroParameter param : mdef.parameters()) {
          sb.append(pad).append("    Param $").append(param.name());
          if (param.defaultValue().isPresent()) {
            sb.append(" (default):\n");
            dumpExpression(param.defaultValue().get(), indent + 3, sb, source);
          } else {
            sb.append("\n");
          }
        }
      }
      sb.append(pad).append("  Body:\n");
      for (VtlNode bNode : mdef.body()) {
        dumpNode(bNode, indent + 2, sb, source);
      }
    } else if (node instanceof VtlDirectiveCallNode call) {
      sb.append(pad)
          .append("DirectiveCall [")
          .append(call.span().startOffset())
          .append("..")
          .append(call.span().endOffset())
          .append("] name=")
          .append(call.name())
          .append("\n");
      for (VtlExpression arg : call.arguments()) {
        dumpExpression(arg, indent + 1, sb, source);
      }
    } else if (node instanceof VtlBlockDirectiveCallNode bcall) {
      sb.append(pad)
          .append("BlockDirectiveCall [")
          .append(bcall.span().startOffset())
          .append("..")
          .append(bcall.span().endOffset())
          .append("] name=")
          .append(bcall.name())
          .append("\n");
      if (!bcall.arguments().isEmpty()) {
        sb.append(pad).append("  Arguments:\n");
        for (VtlExpression arg : bcall.arguments()) {
          dumpExpression(arg, indent + 2, sb, source);
        }
      }
      sb.append(pad).append("  Body:\n");
      for (VtlNode bNode : bcall.body()) {
        dumpNode(bNode, indent + 2, sb, source);
      }
    } else if (node instanceof VtlErrorNode err) {
      sb.append(pad)
          .append("ErrorNode [")
          .append(err.span().startOffset())
          .append("..")
          .append(err.span().endOffset())
          .append("] \"")
          .append(escape(err.message()))
          .append("\"\n");
    }
  }

  private static void dumpReference(
      VtlReference ref, int indent, StringBuilder sb, SourceText source) {
    String pad = "  ".repeat(indent);
    sb.append(pad)
        .append("Reference [")
        .append(ref.span().startOffset())
        .append("..")
        .append(ref.span().endOffset())
        .append("] root=")
        .append(ref.rootName())
        .append(" quiet=")
        .append(ref.isQuiet())
        .append(" formal=")
        .append(ref.isFormal())
        .append("\n");

    for (VtlAccessStep step : ref.steps()) {
      if (step instanceof VtlAccessStep.PropertyAccess pa) {
        sb.append(pad).append("  Property: ").append(pa.propertyName()).append("\n");
      } else if (step instanceof VtlAccessStep.MethodCall mc) {
        sb.append(pad).append("  MethodCall: ").append(mc.methodName()).append("()\n");
        for (VtlExpression arg : mc.arguments()) {
          dumpExpression(arg, indent + 2, sb, source);
        }
      } else if (step instanceof VtlAccessStep.IndexAccess ia) {
        sb.append(pad).append("  IndexAccess:\n");
        dumpExpression(ia.indexExpression(), indent + 2, sb, source);
      }
    }

    if (ref.alternateValue().isPresent()) {
      sb.append(pad).append("  Alternate:\n");
      dumpExpression(ref.alternateValue().get(), indent + 2, sb, source);
    }
  }

  private static void dumpExpression(
      VtlExpression expr, int indent, StringBuilder sb, SourceText source) {
    String pad = "  ".repeat(indent);

    if (expr instanceof VtlReferenceExpression re) {
      dumpReference(re.reference(), indent, sb, source);
    } else if (expr instanceof VtlIntegerLiteralExpression intLit) {
      sb.append(pad)
          .append("Integer [")
          .append(intLit.span().startOffset())
          .append("..")
          .append(intLit.span().endOffset())
          .append("] ")
          .append(intLit.value())
          .append("\n");
    } else if (expr instanceof VtlDecimalLiteralExpression decLit) {
      sb.append(pad)
          .append("Decimal [")
          .append(decLit.span().startOffset())
          .append("..")
          .append(decLit.span().endOffset())
          .append("] ")
          .append(decLit.value())
          .append("\n");
    } else if (expr instanceof VtlBooleanLiteralExpression boolLit) {
      sb.append(pad)
          .append("Boolean [")
          .append(boolLit.span().startOffset())
          .append("..")
          .append(boolLit.span().endOffset())
          .append("] ")
          .append(boolLit.value())
          .append("\n");
    } else if (expr instanceof VtlNullLiteralExpression nullLit) {
      sb.append(pad)
          .append("Null [")
          .append(nullLit.span().startOffset())
          .append("..")
          .append(nullLit.span().endOffset())
          .append("]\n");
    } else if (expr instanceof VtlStringLiteralExpression strLit) {
      sb.append(pad)
          .append("StringLiteral [")
          .append(strLit.span().startOffset())
          .append("..")
          .append(strLit.span().endOffset())
          .append("] '")
          .append(escape(strLit.value()))
          .append("'\n");
    } else if (expr instanceof VtlInterpolatedStringExpression istr) {
      sb.append(pad)
          .append("InterpolatedString [")
          .append(istr.span().startOffset())
          .append("..")
          .append(istr.span().endOffset())
          .append("]\n");
      for (VtlInterpolatedStringExpression.VtlInterpolatedStringPart part : istr.parts()) {
        if (part instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.TextPart tp) {
          sb.append(pad).append("  Text \"").append(escape(tp.text())).append("\"\n");
        } else if (part
            instanceof VtlInterpolatedStringExpression.VtlInterpolatedStringPart.ReferencePart rp) {
          dumpReference(rp.reference(), indent + 1, sb, source);
        }
      }
    } else if (expr instanceof VtlListLiteralExpression list) {
      sb.append(pad)
          .append("ListLiteral [")
          .append(list.span().startOffset())
          .append("..")
          .append(list.span().endOffset())
          .append("]\n");
      for (VtlExpression elem : list.elements()) {
        dumpExpression(elem, indent + 1, sb, source);
      }
    } else if (expr instanceof VtlMapLiteralExpression map) {
      sb.append(pad)
          .append("MapLiteral [")
          .append(map.span().startOffset())
          .append("..")
          .append(map.span().endOffset())
          .append("]\n");
      for (VtlMapEntry entry : map.entries()) {
        sb.append(pad).append("  Entry:\n");
        sb.append(pad).append("    Key:\n");
        dumpExpression(entry.key(), indent + 3, sb, source);
        sb.append(pad).append("    Value:\n");
        dumpExpression(entry.value(), indent + 3, sb, source);
      }
    } else if (expr instanceof VtlRangeExpression range) {
      sb.append(pad)
          .append("Range [")
          .append(range.span().startOffset())
          .append("..")
          .append(range.span().endOffset())
          .append("]\n");
      sb.append(pad).append("  Start:\n");
      dumpExpression(range.start(), indent + 2, sb, source);
      sb.append(pad).append("  End:\n");
      dumpExpression(range.end(), indent + 2, sb, source);
    } else if (expr instanceof VtlUnaryExpression un) {
      sb.append(pad)
          .append("Unary [")
          .append(un.span().startOffset())
          .append("..")
          .append(un.span().endOffset())
          .append("] op=")
          .append(un.operator().name())
          .append("\n");
      dumpExpression(un.operand(), indent + 1, sb, source);
    } else if (expr instanceof VtlBinaryExpression bin) {
      sb.append(pad)
          .append("Binary [")
          .append(bin.span().startOffset())
          .append("..")
          .append(bin.span().endOffset())
          .append("] op=")
          .append(bin.operator().name())
          .append("\n");
      dumpExpression(bin.left(), indent + 1, sb, source);
      dumpExpression(bin.right(), indent + 1, sb, source);
    } else if (expr instanceof VtlGroupedExpression grp) {
      sb.append(pad)
          .append("Grouped [")
          .append(grp.span().startOffset())
          .append("..")
          .append(grp.span().endOffset())
          .append("]\n");
      dumpExpression(grp.expression(), indent + 1, sb, source);
    } else if (expr instanceof VtlErrorExpression err) {
      sb.append(pad)
          .append("ErrorExpression [")
          .append(err.span().startOffset())
          .append("..")
          .append(err.span().endOffset())
          .append("] \"")
          .append(escape(err.message()))
          .append("\"\n");
    }
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\"", "\\\"");
  }
}
