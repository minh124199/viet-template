package io.github.minh124199.viettemplate.tck.differential;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Deterministic, bounded generator producing valid VTL templates and matching test contexts for
 * cross-tier differential fuzzing without risking unbounded recursion or memory exhaustion.
 */
public final class BoundedVtlGenerator {

  public record GeneratorBudget(
      int maxSourceCharacters,
      int maxGeneratedNodes,
      int maxExpressionDepth,
      int maxDirectiveDepth,
      int maxReferenceDepth) {

    public static GeneratorBudget standard() {
      return new GeneratorBudget(1500, 30, 3, 2, 2);
    }

    public static GeneratorBudget deep() {
      return new GeneratorBudget(4000, 80, 5, 4, 3);
    }
  }

  public record GeneratedCase(String source, Map<String, Object> context) {}

  public record User(String name, int age, boolean active) {}

  private final SplittableRandom rng;
  private final GeneratorBudget budget;
  private int nodeCount = 0;

  public BoundedVtlGenerator(SplittableRandom rng, GeneratorBudget budget) {
    this.rng = rng;
    this.budget = budget;
  }

  public GeneratedCase generate() {
    nodeCount = 0;
    Map<String, Object> context = createStandardContext();
    StringBuilder sb = new StringBuilder();

    int blockCount = 1 + rng.nextInt(4);
    for (int b = 0; b < blockCount; b++) {
      if (sb.length() >= budget.maxSourceCharacters() || nodeCount >= budget.maxGeneratedNodes()) {
        break;
      }
      appendStatement(sb, 0, context);
      sb.append("\n");
    }

    return new GeneratedCase(sb.toString(), context);
  }

  private void appendStatement(StringBuilder sb, int directiveDepth, Map<String, Object> ctx) {
    if (nodeCount++ >= budget.maxGeneratedNodes()
        || sb.length() >= budget.maxSourceCharacters()
        || directiveDepth >= budget.maxDirectiveDepth()) {
      appendSimpleText(sb);
      return;
    }

    int choice = rng.nextInt(6);
    switch (choice) {
      case 0 -> appendSetDirective(sb, ctx);
      case 1 -> appendIfDirective(sb, directiveDepth, ctx);
      case 2 -> appendForeachDirective(sb, directiveDepth, ctx);
      case 3 -> appendReference(sb, 0);
      case 4 -> appendComment(sb);
      default -> appendSimpleText(sb);
    }
  }

  private void appendSetDirective(StringBuilder sb, Map<String, Object> ctx) {
    String varName = "var" + rng.nextInt(10);
    sb.append("#set($").append(varName).append(" = ");
    appendExpression(sb, 0);
    sb.append(")");
  }

  private void appendIfDirective(
      StringBuilder sb, int directiveDepth, Map<String, Object> ctx) {
    sb.append("#if(");
    appendBooleanExpression(sb, 0);
    sb.append(") ");
    appendStatement(sb, directiveDepth + 1, ctx);

    if (rng.nextBoolean() && directiveDepth + 1 < budget.maxDirectiveDepth()) {
      sb.append(" #elseif(");
      appendBooleanExpression(sb, 0);
      sb.append(") ");
      appendStatement(sb, directiveDepth + 1, ctx);
    }

    if (rng.nextBoolean() && directiveDepth + 1 < budget.maxDirectiveDepth()) {
      sb.append(" #else ");
      appendStatement(sb, directiveDepth + 1, ctx);
    }

    sb.append(" #end");
  }

  private void appendForeachDirective(
      StringBuilder sb, int directiveDepth, Map<String, Object> ctx) {
    String loopVar = "item" + rng.nextInt(5);
    sb.append("#foreach($").append(loopVar).append(" in ");
    if (rng.nextBoolean()) {
      int start = rng.nextInt(3);
      int end = start + rng.nextInt(4);
      sb.append("[").append(start).append("..").append(end).append("]");
    } else {
      sb.append("$list");
    }
    sb.append(") ");
    sb.append("[$").append(loopVar).append("]");
    if (rng.nextBoolean()) {
      sb.append("($foreach.index)");
    }
    sb.append(" #end");
  }

  private void appendReference(StringBuilder sb, int refDepth) {
    nodeCount++;
    int style = rng.nextInt(4);
    switch (style) {
      case 0 -> sb.append("$str");
      case 1 -> sb.append("$!num");
      case 2 -> sb.append("${user.name}");
      case 3 -> {
        if (refDepth < budget.maxReferenceDepth() && rng.nextBoolean()) {
          sb.append("$map['key']");
        } else {
          sb.append("$user.age");
        }
      }
    }
  }

  private void appendExpression(StringBuilder sb, int exprDepth) {
    nodeCount++;
    if (exprDepth >= budget.maxExpressionDepth()) {
      sb.append(rng.nextInt(20));
      return;
    }

    int choice = rng.nextInt(4);
    switch (choice) {
      case 0 -> sb.append(rng.nextInt(50));
      case 1 -> sb.append("'text").append(rng.nextInt(10)).append("'");
      case 2 -> appendReference(sb, 0);
      default -> {
        sb.append("(");
        appendExpression(sb, exprDepth + 1);
        String op = switch (rng.nextInt(4)) {
          case 0 -> " + ";
          case 1 -> " - ";
          case 2 -> " * ";
          default -> " + "; // Avoid accidental divide-by-zero
        };
        sb.append(op);
        appendExpression(sb, exprDepth + 1);
        sb.append(")");
      }
    }
  }

  private void appendBooleanExpression(StringBuilder sb, int exprDepth) {
    nodeCount++;
    if (exprDepth >= budget.maxExpressionDepth()) {
      sb.append(rng.nextBoolean() ? "true" : "$flag");
      return;
    }

    int choice = rng.nextInt(4);
    switch (choice) {
      case 0 -> sb.append("$flag");
      case 1 -> sb.append("!$flag");
      case 2 -> {
        sb.append("(");
        appendExpression(sb, exprDepth + 1);
        String comp = switch (rng.nextInt(4)) {
          case 0 -> " == ";
          case 1 -> " != ";
          case 2 -> " > ";
          default -> " <= ";
        };
        sb.append(comp);
        appendExpression(sb, exprDepth + 1);
        sb.append(")");
      }
      default -> {
        sb.append("(");
        appendBooleanExpression(sb, exprDepth + 1);
        sb.append(rng.nextBoolean() ? " && " : " || ");
        appendBooleanExpression(sb, exprDepth + 1);
        sb.append(")");
      }
    }
  }

  private void appendSimpleText(StringBuilder sb) {
    nodeCount++;
    String[] words = {"Text ", "Hello ", "Value: ", "Xin chào ", "🚀 ", "[OK] "};
    sb.append(words[rng.nextInt(words.length)]);
  }

  private void appendComment(StringBuilder sb) {
    nodeCount++;
    if (rng.nextBoolean()) {
      sb.append("## single line comment\n");
    } else {
      sb.append("#* block comment *#");
    }
  }

  public static Map<String, Object> createStandardContext() {
    Map<String, Object> ctx = new LinkedHashMap<>();
    ctx.put("str", "Alpha");
    ctx.put("num", 42);
    ctx.put("flag", true);
    ctx.put("list", List.of("A", "B", "C"));
    ctx.put("map", new HashMap<>(Map.of("key", "Value", "other", 99)));
    ctx.put("user", new User("Alice", 25, true));
    return ctx;
  }
}
