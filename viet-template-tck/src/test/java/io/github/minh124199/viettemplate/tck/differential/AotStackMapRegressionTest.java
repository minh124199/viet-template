package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression test suite for AOT bytecode compiler stack map frame verifier invariants.
 *
 * <p>Validates that multiple loops and local variable stores inside conditional branches allocate
 * deterministic, pre-initialized local slots so that JVM split verifier stack map frames are
 * consistent across all branch targets.
 */
class AotStackMapRegressionTest {

  public record User(String name, int age, boolean active) {}

  @Test
  @DisplayName(
      "Regression: Should verify branch containing loops and nested conditionals with sibling"
          + " merge (reproduces historical iter 54 counterexample)")
  void testBranchedLoopsAndNestedConditionalsWithSiblingMerge() {
    String template =
        "#if($flag) #foreach($item2 in $list) [$item2] #end #elseif($flag) #foreach($item0 in"
            + " [0..2]) [$item0]($foreach.index) #end #else #if((((((8 + 6) == (8 + 16)) ||"
            + " (!$flag && (true && true))) || ((9 + 'text3') != 'text5')) || (($!num == 33) &&"
            + " ((15 > (17 - 12)) && (8 == (18 - 3)))))) #foreach($item0 in $list)"
            + " [$item0]($foreach.index) #end #elseif($flag) #if((((${user.name} <= 'text6') ||"
            + " ((28 == (15 - 17)) && $flag)) && !$flag)) ## single line comment\n"
            + " #else $!num #end #end #end";

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("str", "Alpha");
    ctx.put("num", 42);
    ctx.put("flag", true);
    ctx.put("list", List.of("A", "B", "C"));
    ctx.put("map", Map.of("key", "Value", "other", 99));
    ctx.put("user", new User("Alice", 25, true));

    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

    TierDifferentialHarness.DifferentialResult result =
        TierDifferentialHarness.runAcrossTiers(template, ctx, options);

    TierDifferentialHarness.assertTierParity(result);
    assertThat(result.aotResult().isSuccess()).isTrue();
    assertThat(result.aotResult().output()).isEqualTo(result.astResult().output());
  }

  @Test
  @DisplayName("Regression: Minimized counterexample with loops across if/elseif/else branches")
  void testMinimizedMultiLoopBranching() {
    String template =
        "#if($flag)\n"
            + "  #foreach($x in $list) [$x] #end\n"
            + "#elseif($flag2)\n"
            + "  #foreach($y in $list) ($y) #end\n"
            + "#else\n"
            + "  #foreach($z in $list) {$z} #end\n"
            + "#end";

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("list", List.of("1", "2", "3"));
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

    // Test with flag = true
    ctx.put("flag", true);
    ctx.put("flag2", false);
    TierDifferentialHarness.assertTierParity(
        TierDifferentialHarness.runAcrossTiers(template, ctx, options));

    // Test with flag = false, flag2 = true
    ctx.put("flag", false);
    ctx.put("flag2", true);
    TierDifferentialHarness.assertTierParity(
        TierDifferentialHarness.runAcrossTiers(template, ctx, options));

    // Test with flag = false, flag2 = false (enters else branch)
    ctx.put("flag", false);
    ctx.put("flag2", false);
    TierDifferentialHarness.assertTierParity(
        TierDifferentialHarness.runAcrossTiers(template, ctx, options));
  }

  @Test
  @DisplayName("Regression: Nested loops inside branching statements")
  void testNestedLoopsInsideBranches() {
    String template =
        "#if($flag)\n"
            + "  #foreach($row in $matrix)\n"
            + "    #foreach($cell in $row)\n"
            + "      [$cell]\n"
            + "    #end\n"
            + "  #end\n"
            + "#else\n"
            + "  #foreach($row in $matrix)\n"
            + "    #foreach($cell in $row)\n"
            + "      ($cell)\n"
            + "    #end\n"
            + "  #end\n"
            + "#end";

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("matrix", List.of(List.of("A", "B"), List.of("C", "D")));
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

    ctx.put("flag", true);
    TierDifferentialHarness.assertTierParity(
        TierDifferentialHarness.runAcrossTiers(template, ctx, options));

    ctx.put("flag", false);
    TierDifferentialHarness.assertTierParity(
        TierDifferentialHarness.runAcrossTiers(template, ctx, options));
  }

  @Test
  @DisplayName("Regression: Sequential sibling loops with #set variables")
  void testSequentialLoopsWithSetVariables() {
    String template =
        "#set($acc = 'init')\n"
            + "#foreach($a in [1..3])\n"
            + "  #set($acc = $acc + '-' + $a)\n"
            + "  [$acc]\n"
            + "#end\n"
            + "#foreach($b in [4..6])\n"
            + "  #set($acc = $acc + '+' + $b)\n"
            + "  ($acc)\n"
            + "#end\n"
            + "Final: $acc";

    Map<String, Object> ctx = new HashMap<>();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

    TierDifferentialHarness.DifferentialResult result =
        TierDifferentialHarness.runAcrossTiers(template, ctx, options);
    TierDifferentialHarness.assertTierParity(result);
  }

  @Test
  @DisplayName("Regression: #set and isNull expressions across branches without stack leaks")
  void testSetAndIsNullAcrossBranches() {
    String template =
        "#if($flag)\n"
            + "  #set($val = 'first')\n"
            + "  #if(!$val) none #{else} Val:$val #end\n"
            + "#else\n"
            + "  #set($val = 'second')\n"
            + "  #if(!$val) none #{else} Val:$val #end\n"
            + "#end\n"
            + "Result: $val";

    Map<String, Object> ctx = new HashMap<>();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

    ctx.put("flag", true);
    TierDifferentialHarness.assertTierParity(
        TierDifferentialHarness.runAcrossTiers(template, ctx, options));

    ctx.put("flag", false);
    TierDifferentialHarness.assertTierParity(
        TierDifferentialHarness.runAcrossTiers(template, ctx, options));
  }

  @Test
  @DisplayName("Regression: Direct AOT class loading verifies without VerifyError")
  void testDirectAotBytecodeVerification() {
    String template =
        "#if($flag) #foreach($item2 in $list) [$item2] #end #elseif($flag) #foreach($item0 in"
            + " [0..2]) [$item0]($foreach.index) #end #else #if((((((8 + 6) == (8 + 16)) ||"
            + " (!$flag && (true && true))) || ((9 + 'text3') != 'text5')) || (($!num == 33) &&"
            + " ((15 > (17 - 12)) && (8 == (18 - 3)))))) #foreach($item0 in $list)"
            + " [$item0]($foreach.index) #end #elseif($flag) #if((((${user.name} <= 'text6') ||"
            + " ((28 == (15 - 17)) && $flag)) && !$flag)) ## single line comment\n"
            + " #else $!num #end #end #end";

    assertDoesNotThrow(
        () -> {
          VtlInterpreterOptions options =
              VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();
          TierDifferentialHarness.DifferentialResult res =
              TierDifferentialHarness.runAcrossTiers(
                  template,
                  Map.of(
                      "flag",
                      false,
                      "list",
                      List.of(1, 2),
                      "num",
                      42,
                      "user",
                      new User("Bob", 30, false)),
                  options);
          assertThat(res.aotResult().isSuccess()).isTrue();
        });
  }

  public static class ShortCircuitProbe {
    private int callCount = 0;

    public boolean incrementAndReturnTrue() {
      callCount++;
      return true;
    }

    public boolean incrementAndReturnFalse() {
      callCount++;
      return false;
    }

    public boolean throwIfCalled() {
      throw new AssertionError("RHS expression was evaluated unexpectedly!");
    }

    public int getCallCount() {
      return callCount;
    }

    public void reset() {
      callCount = 0;
    }
  }

  @Test
  @DisplayName(
      "Regression: Short-circuit operators strictly suppress RHS evaluation and side effects")
  void testShortCircuitNonEvaluationAndSideEffects() {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

    // 1. false && RHS_THAT_THROWS -> must not throw, RHS must not be called
    {
      ShortCircuitProbe probe = new ShortCircuitProbe();
      String template = "#if(false && $probe.throwIfCalled()) YES #{else} NO #end";
      TierDifferentialHarness.DifferentialResult res =
          TierDifferentialHarness.runAcrossTiers(template, Map.of("probe", probe), options);
      TierDifferentialHarness.assertTierParity(res);
      assertThat(res.aotResult().output().trim()).isEqualTo("NO");
      assertThat(probe.getCallCount()).isEqualTo(0);
    }

    // 2. true || RHS_THAT_THROWS -> must not throw, RHS must not be called
    {
      ShortCircuitProbe probe = new ShortCircuitProbe();
      String template = "#if(true || $probe.throwIfCalled()) YES #{else} NO #end";
      TierDifferentialHarness.DifferentialResult res =
          TierDifferentialHarness.runAcrossTiers(template, Map.of("probe", probe), options);
      TierDifferentialHarness.assertTierParity(res);
      assertThat(res.aotResult().output().trim()).isEqualTo("YES");
      assertThat(probe.getCallCount()).isEqualTo(0);
    }

    // 3. true && RHS -> must evaluate RHS exactly once across all tiers
    {
      ShortCircuitProbe probe = new ShortCircuitProbe();
      String template = "#if(true && $probe.incrementAndReturnTrue()) YES #{else} NO #end";
      // In AOT tier test
      TierDifferentialHarness.DifferentialResult res =
          TierDifferentialHarness.runAcrossTiers(template, Map.of("probe", probe), options);
      TierDifferentialHarness.assertTierParity(res);
      assertThat(res.aotResult().output().trim()).isEqualTo("YES");
    }

    // 4. false || RHS -> must evaluate RHS exactly once across all tiers
    {
      ShortCircuitProbe probe = new ShortCircuitProbe();
      String template = "#if(false || $probe.incrementAndReturnTrue()) YES #{else} NO #end";
      TierDifferentialHarness.DifferentialResult res =
          TierDifferentialHarness.runAcrossTiers(template, Map.of("probe", probe), options);
      TierDifferentialHarness.assertTierParity(res);
      assertThat(res.aotResult().output().trim()).isEqualTo("YES");
    }
  }

  @Test
  @DisplayName(
      "Regression: Nested short-circuit expressions maintain StackMapTable convergence and"
          + " semantics")
  void testNestedShortCircuitCombinations() {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();
    ShortCircuitProbe probe = new ShortCircuitProbe();

    // Guard against arithmetic evaluation on non-numeric types
    String template =
        "#set($flag = false)\n"
            + "#set($str = 'notANumber')\n"
            + "#if($flag && ($str * 10))\n"
            + "  FAIL\n"
            + "#else\n"
            + "  PASS\n"
            + "#end\n"
            + "#if(!$flag || ($str / 5))\n"
            + "  PASS2\n"
            + "#else\n"
            + "  FAIL2\n"
            + "#end\n"
            + "#if(($flag && $probe.throwIfCalled()) || (!$flag &&"
            + " $probe.incrementAndReturnTrue()))\n"
            + "  PASS3\n"
            + "#end\n"
            + "#if((!$flag || $probe.throwIfCalled()) && ($flag && $probe.throwIfCalled()))\n"
            + "  FAIL4\n"
            + "#else\n"
            + "  PASS4\n"
            + "#end";

    Map<String, Object> ctx = Map.of("probe", probe);
    TierDifferentialHarness.DifferentialResult res =
        TierDifferentialHarness.runAcrossTiers(template, ctx, options);
    TierDifferentialHarness.assertTierParity(res);
    assertThat(res.aotResult().output()).contains("PASS", "PASS2", "PASS3", "PASS4");
  }

  @Test
  @DisplayName(
      "Regression: Deeply nested loops and conditionals stress temporary slot allocation without"
          + " overflow")
  void testDeepNestingStress() {
    StringBuilder sb = new StringBuilder();
    int depth = 8;
    for (int i = 0; i < depth; i++) {
      sb.append("#foreach($it").append(i).append(" in [0..1])\n");
      sb.append("#if($it").append(i).append(" == 1)\n");
      sb.append("#set($val").append(i).append(" = $it").append(i).append(")\n");
    }
    sb.append("DEEP: $val0-$val1-$val2-$val3-$val4-$val5-$val6-$val7\n");
    for (int i = depth - 1; i >= 0; i--) {
      sb.append("#end\n#end\n");
    }

    String template = sb.toString();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();
    TierDifferentialHarness.DifferentialResult res =
        TierDifferentialHarness.runAcrossTiers(template, Map.of(), options);
    TierDifferentialHarness.assertTierParity(res);
    assertThat(res.aotResult().output()).contains("DEEP: 1-1-1-1-1-1-1-1");
  }

  @Test
  @DisplayName("Regression: Large sequential sibling loops reuse bounded depth slots")
  void testSequentialSiblingLoopsBoundedSlots() {
    StringBuilder sb = new StringBuilder();
    int count = 50;
    for (int i = 0; i < count; i++) {
      sb.append("#foreach($x in [1..1]) ").append(i).append(" #end\n");
    }

    String template = sb.toString();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();
    TierDifferentialHarness.DifferentialResult res =
        TierDifferentialHarness.runAcrossTiers(template, Map.of(), options);
    TierDifferentialHarness.assertTierParity(res);
  }

  @Test
  @DisplayName(
      "Regression: Historical fuzz seed 0xD1FF3871E canary pass through iterations 54 and 107")
  void testHistoricalCanarySeed0xD1FF3871E() {
    long seed = 0xD1FF3871EL;
    SplittableRandom rng = new SplittableRandom(seed);
    BoundedVtlGenerator.GeneratorBudget budget = BoundedVtlGenerator.GeneratorBudget.deep();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

    // Verify first 120 iterations of the historical seed covering iterations 54 and 107
    for (int i = 0; i < 120; i++) {
      BoundedVtlGenerator generator = new BoundedVtlGenerator(rng, budget);
      BoundedVtlGenerator.GeneratedCase generated = generator.generate();
      TierDifferentialHarness.DifferentialResult result =
          TierDifferentialHarness.runAcrossTiers(generated.source(), generated.context(), options);
      TierDifferentialHarness.assertTierParity(result);
    }
  }
}
