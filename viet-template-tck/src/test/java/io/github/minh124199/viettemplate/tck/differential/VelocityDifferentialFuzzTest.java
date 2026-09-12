package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.minh124199.viettemplate.tck.velocity.engine.Velocity241EngineAdapter;
import io.github.minh124199.viettemplate.tck.velocity.engine.VietReferenceEngineAdapter;
import io.github.minh124199.viettemplate.tck.velocity.result.EngineResult;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VelocityDifferentialFuzzTest {

  private static final long SEED = 0x7E10C177L;

  private static boolean isDeepMode() {
    return "deep".equalsIgnoreCase(System.getProperty("vietTemplate.fuzz.mode"))
        || "deep".equalsIgnoreCase(System.getenv("VIET_FUZZ_MODE"));
  }

  @Test
  @DisplayName("P3: Supported compatibility templates match Apache Velocity 2.4.1")
  void testVelocityDifferentialFuzzing() {
    SplittableRandom rng = new SplittableRandom(SEED);
    Velocity241EngineAdapter velocityEngine = new Velocity241EngineAdapter();
    VietReferenceEngineAdapter vietEngine = new VietReferenceEngineAdapter();

    int iterations = isDeepMode() ? 500 : 80;

    for (int i = 0; i < iterations; i++) {
      int a = rng.nextInt(50) + 1;
      int b = rng.nextInt(50) + 1;
      int threshold = rng.nextInt(100);

      String template;
      Map<String, Object> ctx = new HashMap<>();

      int templateType = rng.nextInt(5);
      switch (templateType) {
        case 0 -> {
          // Arithmetic and conditionals
          template =
              String.format(
                  "#set($res = %d + %d * 2)#if($res > %d)HIGH:$res#{else}LOW:$res#end",
                  a, b, threshold);
        }
        case 1 -> {
          // Foreach loops over range
          template =
              String.format(
                  "#foreach($i in [%d..%d])[$i]#end",
                  Math.min(a, 5), Math.min(a, 5) + 3);
        }
        case 2 -> {
          // List iteration and conditionals
          ctx.put("items", List.of("alpha", "beta", "gamma"));
          template =
              "#foreach($it in $items)#if($it == 'beta')BINGO#{else}$it#end#end";
        }
        case 3 -> {
          // Strings and references
          ctx.put("prefix", "Title");
          ctx.put("val", a);
          template =
              "Result: $prefix - $val - #if($val > 25)GREAT#{else}SMALL#end";
        }
        default -> {
          // Logical expressions
          ctx.put("t", true);
          ctx.put("f", false);
          template =
              "#if($t && !$f)OK#{else}FAIL#end #if($t || $f)YES#{else}NO#end";
        }
      }

      CompatibilityScenario scenario =
          CompatibilityScenario.builder("fuzz.velocity." + i, ScenarioCategory.EXPRESSION)
              .template(template)
              .contextFactory(() -> new HashMap<>(ctx))
              .configuration(CompatibilityConfiguration.defaultConfiguration())
              .build();

      EngineResult velRes =
          velocityEngine.execute(scenario, new HashMap<>(ctx), scenario.configuration(), Map.of());
      EngineResult vietRes =
          vietEngine.execute(scenario, new HashMap<>(ctx), scenario.configuration(), Map.of());

      // P3 Invariant: Supported templates match Velocity 2.4.1 identically
      if (!Objects.equals(velRes.output(), vietRes.output())) {
        fail(
            String.format(
                "Velocity 2.4.1 differential mismatch!%n"
                    + "Iteration: %d, Seed: 0x%X%n"
                    + "Template:%n%s%n"
                    + "Velocity output: [%s]%n"
                    + "Viet output:     [%s]",
                i, SEED, template, velRes.output(), vietRes.output()));
      }
    }
  }
}
