package io.github.minh124199.viettemplate.tck.differential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Deterministic test case minimizer using delta-debugging reduction steps to shrink failing
 * templates and contexts into minimal reproductions.
 */
public final class TemplateMinimizer {

  public record MinimizedCase(String source, Map<String, Object> context) {}

  private TemplateMinimizer() {}

  public static MinimizedCase minimize(
      String source, Map<String, Object> context, Predicate<String> failsPredicate) {
    if (!failsPredicate.test(source)) {
      return new MinimizedCase(source, context);
    }

    String currentSource = source;
    Map<String, Object> currentContext = new LinkedHashMap<>(context);

    // 1. Line-by-line delta debugging
    List<String> lines = new ArrayList<>(Arrays.asList(currentSource.split("\n", -1)));
    int lineIdx = 0;
    while (lineIdx < lines.size()) {
      String removed = lines.remove(lineIdx);
      String candidate = String.join("\n", lines);
      if (candidate.isBlank() || !failsPredicate.test(candidate)) {
        // Putting line back
        lines.add(lineIdx, removed);
        lineIdx++;
      }
    }
    currentSource = String.join("\n", lines);

    // 2. Context entry minimization
    List<String> keys = new ArrayList<>(currentContext.keySet());
    for (String key : keys) {
      Object val = currentContext.remove(key);
      // Re-test with removed key
      if (!failsPredicate.test(currentSource)) {
        currentContext.put(key, val);
      }
    }

    return new MinimizedCase(currentSource, currentContext);
  }
}
