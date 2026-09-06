package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering comparison and equality operators (==, !=, <, <=, >, >=). */
public final class ComparisonCorpus {

  private ComparisonCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Numeric equality
    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.integer.same",
            ScenarioCategory.EXPRESSION,
            "#if(10 == 10)EQ#{else}NE#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.integer.diff",
            ScenarioCategory.EXPRESSION,
            "#if(10 == 20)EQ#{else}NE#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.alias.eq",
            ScenarioCategory.EXPRESSION,
            "#if(10 eq 10)EQ#{else}NE#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.alias.ne",
            ScenarioCategory.EXPRESSION,
            "#if(10 ne 20)NE#{else}EQ#end"));

    list.add(
        CompatibilityScenario.of(
            "comparison.equality.mixed-numbers",
            ScenarioCategory.EXPRESSION,
            "#if($a == $b)EQ#{else}NE#end",
            () -> Map.of("a", 10, "b", 10L)));

    // 2. String equality
    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.string.same",
            ScenarioCategory.EXPRESSION,
            "#if('hello' == 'hello')EQ#{else}NE#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.string.diff",
            ScenarioCategory.EXPRESSION,
            "#if('hello' == 'world')EQ#{else}NE#end"));

    // 3. String to number comparison
    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.string-to-number.equal",
            ScenarioCategory.EXPRESSION,
            "#if('10' == 10)EQ#{else}NE#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.string-to-number.float",
            ScenarioCategory.EXPRESSION,
            "#if('10.0' == 10.0)EQ#{else}NE#end"));

    // 4. Null and undefined equality
    list.add(
        CompatibilityScenario.simple(
            "comparison.equality.undefined-to-null-ref",
            ScenarioCategory.EXPRESSION,
            "#if($missing == $null)EQ#{else}NE#end"));

    list.add(
        CompatibilityScenario.of(
            "comparison.equality.string-to-null-ref",
            ScenarioCategory.EXPRESSION,
            "#if($str == $null)EQ#{else}NE#end",
            ContextFactory.of("str", "abc")));

    // 5. Relational comparisons (<, <=, >, >=)
    list.add(
        CompatibilityScenario.simple(
            "comparison.relational.less-than.true",
            ScenarioCategory.EXPRESSION,
            "#if(5 < 10)T#{else}F#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.relational.less-than.false",
            ScenarioCategory.EXPRESSION,
            "#if(10 < 5)T#{else}F#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.relational.less-equal.equal",
            ScenarioCategory.EXPRESSION,
            "#if(10 <= 10)T#{else}F#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.relational.greater-than.true",
            ScenarioCategory.EXPRESSION,
            "#if(10 > 5)T#{else}F#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.relational.greater-equal.equal",
            ScenarioCategory.EXPRESSION,
            "#if(10 >= 10)T#{else}F#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.relational.alias.lt",
            ScenarioCategory.EXPRESSION,
            "#if(5 lt 10)T#{else}F#end"));

    list.add(
        CompatibilityScenario.simple(
            "comparison.relational.alias.ge",
            ScenarioCategory.EXPRESSION,
            "#if(10 ge 10)T#{else}F#end"));

    return list;
  }
}
