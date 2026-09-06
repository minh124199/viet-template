package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bounded grammar-based differential generator creating deterministic templates from a fixed seed
 * to uncover edge cases.
 */
public final class RandomDifferentialCorpus {

  private static final long FIXED_SEED = 0x5EED124199L;

  private RandomDifferentialCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    return scenarios(20, FIXED_SEED);
  }

  public static List<CompatibilityScenario> scenarios(int count, long seed) {
    Random random = new Random(seed);
    List<CompatibilityScenario> list = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      int a = random.nextInt(50) + 1;
      int b = random.nextInt(50) + 1;
      int threshold = random.nextInt(100);

      String template =
          String.format(
              "#set($res = %d + %d * 2)#if($res > %d)HIGH:$res#{else}LOW:$res#end",
              a, b, threshold);

      list.add(
          CompatibilityScenario.builder(
                  String.format("random.differential.seed-%02d", i), ScenarioCategory.EXPRESSION)
              .template(template)
              .tags("random", "fuzz")
              .build());
    }

    return list;
  }
}
