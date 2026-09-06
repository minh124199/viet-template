package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.BooleanDuck;
import io.github.minh124199.viettemplate.tck.velocity.model.EmptyDuck;
import io.github.minh124199.viettemplate.tck.velocity.model.SizeDuck;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Systematic truthiness matrix covering all primitive types, numbers, collections, and duck typing
 * under both empty_check=true and empty_check=false.
 */
public final class TruthinessCorpus {

  private TruthinessCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // We test both empty_check=true (default) and empty_check=false
    boolean[] emptyCheckModes = {true, false};

    for (boolean emptyCheck : emptyCheckModes) {
      String suffix = emptyCheck ? "empty-check-true" : "empty-check-false";
      CompatibilityConfiguration config =
          CompatibilityConfiguration.defaultConfiguration().withEmptyCheck(emptyCheck);

      // 1. Undefined and null
      list.add(
          CompatibilityScenario.builder(
                  "truthiness.undefined." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($missing)T#{else}F#end")
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.defined-null." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($nullVal)T#{else}F#end")
              .contextFactory(
                  () -> {
                    Map<String, Object> ctx = new HashMap<>();
                    ctx.put("nullVal", null);
                    return ctx;
                  })
              .configuration(config)
              .build());

      // 2. Booleans
      list.add(
          CompatibilityScenario.builder(
                  "truthiness.boolean.true." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", Boolean.TRUE))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.boolean.false." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", Boolean.FALSE))
              .configuration(config)
              .build());

      // 3. Numbers (Byte, Short, Integer, Long, Float, Double, BigInteger, BigDecimal)
      record NumCase(String name, Object val) {}
      List<NumCase> numbers =
          List.of(
              new NumCase("int.zero", 0),
              new NumCase("int.one", 1),
              new NumCase("int.neg", -1),
              new NumCase("long.zero", 0L),
              new NumCase("long.one", 1L),
              new NumCase("double.zero", 0.0d),
              new NumCase("double.one", 1.0d),
              new NumCase("float.zero", 0.0f),
              new NumCase("float.one", 1.0f),
              new NumCase("bigint.zero", BigInteger.ZERO),
              new NumCase("bigint.one", BigInteger.ONE),
              new NumCase("bigdec.zero", BigDecimal.ZERO),
              new NumCase("bigdec.one", BigDecimal.ONE));

      for (NumCase nc : numbers) {
        list.add(
            CompatibilityScenario.builder(
                    "truthiness.number." + nc.name + "." + suffix, ScenarioCategory.TRUTHINESS)
                .template("#if($val)T#{else}F#end")
                .contextFactory(ContextFactory.of("val", nc.val))
                .configuration(config)
                .build());
      }

      // 4. Strings
      list.add(
          CompatibilityScenario.builder(
                  "truthiness.string.empty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", ""))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.string.whitespace." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", "   "))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.string.nonempty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", "hello"))
              .configuration(config)
              .build());

      // 5. Collections & Maps
      list.add(
          CompatibilityScenario.builder(
                  "truthiness.list.empty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", Collections.emptyList()))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.list.nonempty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", List.of("element")))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.set.empty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", Collections.emptySet()))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.map.empty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", Collections.emptyMap()))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.map.nonempty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", Map.of("k", "v")))
              .configuration(config)
              .build());

      // 6. Arrays
      list.add(
          CompatibilityScenario.builder(
                  "truthiness.array.empty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", new Object[0]))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.array.nonempty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", new String[] {"item"}))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.array.primitive-empty." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", new int[0]))
              .configuration(config)
              .build());

      // 7. Duck typing & Method Precedence
      list.add(
          CompatibilityScenario.builder(
                  "truthiness.duck.getasboolean-false." + suffix, ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", new BooleanDuck(false, false)))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.duck.getasboolean-takes-precedence-over-empty." + suffix,
                  ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", new BooleanDuck(true, true)))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.duck.isempty-takes-precedence-over-size-true." + suffix,
                  ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", new EmptyDuck(true, 5)))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.duck.isempty-takes-precedence-over-size-false." + suffix,
                  ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", new EmptyDuck(false, 0)))
              .configuration(config)
              .build());

      list.add(
          CompatibilityScenario.builder(
                  "truthiness.duck.size-takes-precedence-over-length." + suffix,
                  ScenarioCategory.TRUTHINESS)
              .template("#if($val)T#{else}F#end")
              .contextFactory(ContextFactory.of("val", new SizeDuck(0, 5)))
              .configuration(config)
              .build());
    }

    return list;
  }
}
