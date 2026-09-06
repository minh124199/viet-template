package io.github.minh124199.viettemplate.language.vtl.semantics.resolve;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Utility for computing string edit distance and suggesting typo corrections. */
public final class LevenshteinDistance {

  private static final int DEFAULT_MAX_DISTANCE = 3;

  private LevenshteinDistance() {}

  public static int compute(String s1, String s2) {
    Objects.requireNonNull(s1, "s1 must not be null");
    Objects.requireNonNull(s2, "s2 must not be null");

    int len1 = s1.length();
    int len2 = s2.length();

    int[] prev = new int[len2 + 1];
    int[] curr = new int[len2 + 1];

    for (int j = 0; j <= len2; j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= len1; i++) {
      curr[0] = i;
      char c1 = s1.charAt(i - 1);
      for (int j = 1; j <= len2; j++) {
        char c2 = s2.charAt(j - 1);
        int cost = (Character.toLowerCase(c1) == Character.toLowerCase(c2)) ? 0 : 1;
        curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      System.arraycopy(curr, 0, prev, 0, len2 + 1);
    }

    return prev[len2];
  }

  public static Optional<String> findClosestMatch(String target, Collection<String> candidates) {
    return findClosestMatch(target, candidates, DEFAULT_MAX_DISTANCE);
  }

  public static Optional<String> findClosestMatch(
      String target, Collection<String> candidates, int maxDistance) {
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(candidates, "candidates must not be null");

    return candidates.stream()
        .filter(c -> !c.equalsIgnoreCase(target))
        .map(c -> new Match(c, compute(target, c)))
        .filter(m -> m.distance <= maxDistance && m.distance <= (target.length() / 2 + 1))
        .min(Comparator.comparingInt(m -> m.distance))
        .map(m -> m.candidate);
  }

  private record Match(String candidate, int distance) {}
}
