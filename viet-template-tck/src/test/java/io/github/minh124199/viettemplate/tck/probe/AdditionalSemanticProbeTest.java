package io.github.minh124199.viettemplate.tck.probe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdditionalSemanticProbeTest {

  public static class Counter {
    private final AtomicInteger count = new AtomicInteger();

    public int hit() {
      return count.incrementAndGet();
    }

    public int getCount() {
      return count.get();
    }
  }

  @Test
  void probeContextNullAndAssignments() {
    System.out.println("=== ADDITIONAL PROBES: CONTEXT NULL & ASSIGNMENTS ===");

    // 1. Context containing null value
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("myNull", null);
    System.out.println(
        "[$myNull] with ctx.put('myNull', null): "
            + Velocity241Probe.render("[$myNull]", ctx).output());
    System.out.println("[$!myNull]: " + Velocity241Probe.render("[$!myNull]", ctx).output());

    // 2. Property assignment to null
    Map<String, Object> map = new HashMap<>();
    map.put("k", "initial");
    Map<String, Object> ctx2 = new HashMap<>();
    ctx2.put("map", map);
    ctx2.put("probe", new SemanticCompatibilityProbeTest.ProbeService());
    Velocity241Probe.Result rMap =
        Velocity241Probe.render("#set($map.k = $probe.returnNull())\n[$map.k]", ctx2);
    System.out.println(
        "map property set to null: output='" + rMap.output() + "', map.get('k')=" + map.get("k"));

    // 3. List index assignment to null
    List<Object> list = new ArrayList<>();
    list.add("initial");
    Map<String, Object> ctx3 = new HashMap<>();
    ctx3.put("list", list);
    ctx3.put("probe", new SemanticCompatibilityProbeTest.ProbeService());
    Velocity241Probe.Result rList =
        Velocity241Probe.render("#set($list[0] = $probe.returnNull())\n[$list[0]]", ctx3);
    System.out.println(
        "list index set to null: output='" + rList.output() + "', list.get(0)=" + list.get(0));
  }

  @Test
  void probeAlternateValueLaziness() {
    System.out.println("=== ADDITIONAL PROBES: ALTERNATE VALUE LAZINESS ===");

    Counter c1 = new Counter();
    Map<String, Object> ctx1 = Map.of("truthy", "value", "counter", c1);
    Velocity241Probe.Result r1 = Velocity241Probe.render("[${truthy|$counter.hit()}]", ctx1);
    System.out.println(
        "truthy fallback output='" + r1.output() + "', counterHits=" + c1.getCount());

    Counter c2 = new Counter();
    Map<String, Object> ctx2 = Map.of("falsy", "", "counter", c2);
    Velocity241Probe.Result r2 = Velocity241Probe.render("[${falsy|$counter.hit()}]", ctx2);
    System.out.println("falsy fallback output='" + r2.output() + "', counterHits=" + c2.getCount());

    Counter c3 = new Counter();
    Map<String, Object> ctx3 = Map.of("counter", c3);
    Velocity241Probe.Result r3 = Velocity241Probe.render("[${missing|$counter.hit()}]", ctx3);
    System.out.println(
        "missing fallback output='" + r3.output() + "', counterHits=" + c3.getCount());
  }

  @Test
  void probeStrictModeDetails() {
    System.out.println("=== ADDITIONAL PROBES: STRICT MODE DETAILS ===");

    Properties strictProps = new Properties();
    strictProps.setProperty("runtime.strict_mode.enable", "true");

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("probe", new SemanticCompatibilityProbeTest.ProbeService());

    // Check alternate value in strict mode
    System.out.println(
        "[${missing|'fallback'}] in strict mode: "
            + Velocity241Probe.render("[${missing|'fallback'}]", ctx, strictProps).output());

    ctx.put("myNull", null);
    String[] strictQuietTemplates = {
      "[$missing]",
      "[$!missing]",
      "[$myNull]",
      "[$!myNull]",
      "[$probe.nullProperty]",
      "[$!probe.nullProperty]",
      "[$probe.returnNull()]",
      "[$!probe.returnNull()]",
      "#if($!missing)YES#{else}NO#end",
      "#if($!probe.nullProperty)YES#{else}NO#end"
    };
    for (String t : strictQuietTemplates) {
      Velocity241Probe.Result r = Velocity241Probe.render(t, ctx, strictProps);
      if (r.isSuccess()) {
        System.out.println(t + " in strict mode -> SUCCESS: '" + r.output() + "'");
      } else {
        System.out.println(
            t
                + " in strict mode -> THREW: "
                + r.exception().getClass().getSimpleName()
                + ": "
                + r.exception().getMessage());
      }
    }
  }

  @Test
  void probeEscapingWithDefinedNullVsUndefined() {
    System.out.println("=== ADDITIONAL PROBES: ESCAPING WITH NULL & UNDEFINED ===");

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("myNull", null);
    ctx.put("str", "hello");

    String[] templates = {
      "\\$missing",
      "\\\\$missing",
      "\\\\\\$missing",
      "\\\\\\\\$missing",
      "\\\\\\\\\\$missing",
      "\\\\\\\\\\\\$missing",
      "\\$myNull",
      "\\\\$myNull",
      "\\\\\\$myNull",
      "\\$str",
      "\\\\$str",
      "\\\\\\$str"
    };

    for (String t : templates) {
      Velocity241Probe.Result r = Velocity241Probe.render(t, ctx);
      System.out.println(t + " -> '" + r.output() + "'");
    }
  }
}
