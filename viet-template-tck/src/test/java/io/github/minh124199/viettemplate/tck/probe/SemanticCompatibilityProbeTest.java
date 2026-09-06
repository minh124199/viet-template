package io.github.minh124199.viettemplate.tck.probe;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Diagnostic probe test executing against Apache Velocity 2.4.1 to determine authoritative runtime
 * behavior for M3.1 semantic corrections.
 */
class SemanticCompatibilityProbeTest {

  public static class ProbeService {
    public Object returnNull() {
      return null;
    }

    public Object getNullProperty() {
      return null;
    }
  }

  @Test
  void probeNumericTruthiness() {
    System.out.println("=== 1. NUMERIC TRUTHINESS IN VELOCITY 2.4.1 ===");

    // 1. Literal numbers
    String tplLiteral0 = "#if(0)T#{else}F#end";
    String tplLiteral00 = "#if(0.0)T#{else}F#end";
    String tplLiteral1 = "#if(1)T#{else}F#end";
    String tplLiteralNeg1 = "#if(-1)T#{else}F#end";

    System.out.println("Literal 0: " + Velocity241Probe.render(tplLiteral0).output());
    System.out.println("Literal 0.0: " + Velocity241Probe.render(tplLiteral00).output());
    System.out.println("Literal 1: " + Velocity241Probe.render(tplLiteral1).output());
    System.out.println("Literal -1: " + Velocity241Probe.render(tplLiteralNeg1).output());

    // 2. Context numbers with empty_check = true (default)
    Properties defaultProps = new Properties();
    Properties noEmptyCheckProps = new Properties();
    noEmptyCheckProps.setProperty("directive.if.empty_check", "false");

    Map<String, Object> nums = new HashMap<>();
    nums.put("int0", Integer.valueOf(0));
    nums.put("int1", Integer.valueOf(1));
    nums.put("intNeg1", Integer.valueOf(-1));
    nums.put("long0", Long.valueOf(0L));
    nums.put("long1", Long.valueOf(1L));
    nums.put("longNeg1", Long.valueOf(-1L));
    nums.put("float0", Float.valueOf(0.0f));
    nums.put("float1", Float.valueOf(1.0f));
    nums.put("double0", Double.valueOf(0.0));
    nums.put("double1", Double.valueOf(1.0));
    nums.put("bigInt0", BigInteger.ZERO);
    nums.put("bigInt1", BigInteger.ONE);
    nums.put("bigDec0", BigDecimal.ZERO);
    nums.put("bigDec1", BigDecimal.ONE);

    System.out.println("--- Context numbers with empty_check = true (default) ---");
    for (String k : nums.keySet()) {
      String out =
          Velocity241Probe.render("#if($" + k + ")T#{else}F#end", nums, defaultProps).output();
      System.out.println(k + " (" + nums.get(k) + "): " + out);
    }

    System.out.println("--- Context numbers with empty_check = false ---");
    for (String k : nums.keySet()) {
      String out =
          Velocity241Probe.render("#if($" + k + ")T#{else}F#end", nums, noEmptyCheckProps).output();
      System.out.println(k + " (" + nums.get(k) + "): " + out);
    }
  }

  @Test
  void probeEmptyCheckVariants() {
    System.out.println("=== 2. EMPTY CHECK VARIANTS IN VELOCITY 2.4.1 ===");

    Properties defaultProps = new Properties();
    Properties noEmptyCheckProps = new Properties();
    noEmptyCheckProps.setProperty("directive.if.empty_check", "false");

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("emptyStr", "");
    ctx.put("blankStr", "   ");
    ctx.put("nonEmptyStr", "abc");
    ctx.put("emptyList", Collections.emptyList());
    ctx.put("nonEmptyList", List.of("x"));
    ctx.put("emptyMap", Collections.emptyMap());
    ctx.put("nonEmptyMap", Map.of("k", "v"));
    ctx.put("emptyArr", new String[0]);
    ctx.put("nonEmptyArr", new String[] {"x"});

    for (String k : ctx.keySet()) {
      String outTrue =
          Velocity241Probe.render("#if($" + k + ")T#{else}F#end", ctx, defaultProps).output();
      String outFalse =
          Velocity241Probe.render("#if($" + k + ")T#{else}F#end", ctx, noEmptyCheckProps).output();
      System.out.println(
          k + " -> empty_check=true: " + outTrue + ", empty_check=false: " + outFalse);
    }
  }

  @Test
  void probeSetNullRhs() {
    System.out.println("=== 3. #SET NULL RHS IN VELOCITY 2.4.1 ===");

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("probe", new ProbeService());

    // 1. #set with missing reference
    String tpl1 = "#set($x = 'before')\n#set($x = $missing)\n[$x]";
    Velocity241Probe.Result r1 = Velocity241Probe.render(tpl1, ctx);
    System.out.println(
        "set from missing: output='"
            + r1.output()
            + "', finalCtx.get(x)="
            + r1.finalContext().get("x"));

    // 2. #set with method returning null
    String tpl2 = "#set($x = 'before')\n#set($x = $probe.returnNull())\n[$x]";
    Velocity241Probe.Result r2 = Velocity241Probe.render(tpl2, ctx);
    System.out.println(
        "set from returnNull(): output='"
            + r2.output()
            + "', finalCtx.get(x)="
            + r2.finalContext().get("x"));

    // 3. #set with property returning null
    String tpl3 = "#set($x = 'before')\n#set($x = $probe.nullProperty)\n[$x]";
    Velocity241Probe.Result r3 = Velocity241Probe.render(tpl3, ctx);
    System.out.println(
        "set from nullProperty: output='"
            + r3.output()
            + "', finalCtx.get(x)="
            + r3.finalContext().get("x"));
  }

  @Test
  void probeBareNull() {
    System.out.println("=== 4. BARE NULL SYNTAX IN VELOCITY 2.4.1 ===");

    String tpl = "#set($x = null)\n[$x]";
    Velocity241Probe.Result r = Velocity241Probe.render(tpl);
    if (r.isSuccess()) {
      System.out.println(
          "Bare null succeeded: output='" + r.output() + "', x=" + r.finalContext().get("x"));
    } else {
      System.out.println(
          "Bare null failed: "
              + r.exception().getClass().getName()
              + " - "
              + r.exception().getMessage());
    }
  }

  @Test
  void probeNullReferenceIdiom() {
    System.out.println("=== 5. $null REFERENCE IN VELOCITY 2.4.1 ===");

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("nullVal", null);
    ctx.put("probe", new ProbeService());

    String tpl1 = "#if($missing == $null)EQUAL#{else}NOT_EQUAL#end";
    String tpl2 = "#if($probe.nullProperty == $null)EQUAL#{else}NOT_EQUAL#end";
    String tpl3 = "#if('abc' == $null)EQUAL#{else}NOT_EQUAL#end";

    System.out.println("$missing == $null: " + Velocity241Probe.render(tpl1, ctx).output());
    System.out.println(
        "$probe.nullProperty == $null: " + Velocity241Probe.render(tpl2, ctx).output());
    System.out.println("'abc' == $null: " + Velocity241Probe.render(tpl3, ctx).output());
  }

  @Test
  void probeUndefinedVsDefinedNullOutput() {
    System.out.println("=== 6. UNDEFINED VS DEFINED NULL OUTPUT IN VELOCITY 2.4.1 ===");

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("probe", new ProbeService());

    System.out.println(
        "[$missing] -> '" + Velocity241Probe.render("[$missing]", ctx).output() + "'");
    System.out.println(
        "[$!missing] -> '" + Velocity241Probe.render("[$!missing]", ctx).output() + "'");
    System.out.println(
        "[${missing}] -> '" + Velocity241Probe.render("[${missing}]", ctx).output() + "'");
    System.out.println(
        "[$!{missing}] -> '" + Velocity241Probe.render("[$!{missing}]", ctx).output() + "'");

    System.out.println(
        "[$probe.nullProperty] -> '"
            + Velocity241Probe.render("[$probe.nullProperty]", ctx).output()
            + "'");
    System.out.println(
        "[$!probe.nullProperty] -> '"
            + Velocity241Probe.render("[$!probe.nullProperty]", ctx).output()
            + "'");
    System.out.println(
        "[$probe.returnNull()] -> '"
            + Velocity241Probe.render("[$probe.returnNull()]", ctx).output()
            + "'");
    System.out.println(
        "[$!probe.returnNull()] -> '"
            + Velocity241Probe.render("[$!probe.returnNull()]", ctx).output()
            + "'");
  }

  @Test
  void probeAlternateValue() {
    System.out.println("=== 7. ALTERNATE VALUE IN VELOCITY 2.4.1 ===");

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("zero", 0);
    ctx.put("one", 1);
    ctx.put("emptyStr", "");
    ctx.put("str", "hello");
    ctx.put("probe", new ProbeService());

    String[] vars = {"missing", "zero", "one", "emptyStr", "str", "probe.nullProperty"};
    for (String v : vars) {
      String tpl = "[${" + v + "|'fallback'}]";
      Velocity241Probe.Result res = Velocity241Probe.render(tpl, ctx);
      if (res.isSuccess()) {
        System.out.println(tpl + " -> '" + res.output() + "'");
      } else {
        System.out.println(tpl + " -> Exception: " + res.exception().getMessage());
      }
    }
  }

  @Test
  void probeStrictMode() {
    System.out.println("=== 8. STRICT MODE IN VELOCITY 2.4.1 ===");

    Properties strictProps = new Properties();
    strictProps.setProperty("runtime.strict_mode.enable", "true");

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("probe", new ProbeService());

    String[] templates = {
      "[$missing]",
      "[$!missing]",
      "#if($missing)YES#{else}NO#end",
      "[$probe.nullProperty]",
      "[$probe.returnNull()]",
      "#if($probe.nullProperty)YES#{else}NO#end"
    };

    for (String t : templates) {
      Velocity241Probe.Result r = Velocity241Probe.render(t, ctx, strictProps);
      if (r.isSuccess()) {
        System.out.println(t + " -> SUCCESS: '" + r.output() + "'");
      } else {
        System.out.println(
            t
                + " -> THREW: "
                + r.exception().getClass().getSimpleName()
                + ": "
                + r.exception().getMessage());
      }
    }
  }

  public static class CustomBooleanEmpty {
    private final boolean boolVal;
    private final boolean emptyVal;

    public CustomBooleanEmpty(boolean boolVal, boolean emptyVal) {
      this.boolVal = boolVal;
      this.emptyVal = emptyVal;
    }

    public boolean getAsBoolean() {
      return boolVal;
    }

    public boolean isEmpty() {
      return emptyVal;
    }
  }

  public static class CustomEmptySize {
    private final boolean emptyVal;
    private final int sizeVal;

    public CustomEmptySize(boolean emptyVal, int sizeVal) {
      this.emptyVal = emptyVal;
      this.sizeVal = sizeVal;
    }

    public boolean isEmpty() {
      return emptyVal;
    }

    public int size() {
      return sizeVal;
    }
  }

  @Test
  void probeCustomMethodsPrecedence() {
    System.out.println("=== 9. CUSTOM BOOLEAN / EMPTY PRECEDENCE IN VELOCITY 2.4.1 ===");

    // boolVal = false, emptyVal = false (so isEmpty is false -> not empty -> truthy)
    CustomBooleanEmpty obj1 = new CustomBooleanEmpty(false, false);
    // boolVal = true, emptyVal = true (so isEmpty is true -> empty -> falsy)
    CustomBooleanEmpty obj2 = new CustomBooleanEmpty(true, true);

    Map<String, Object> ctx = Map.of("obj1", obj1, "obj2", obj2);
    System.out.println(
        "obj1 (bool=false, empty=false): "
            + Velocity241Probe.render("#if($obj1)T#{else}F#end", ctx).output());
    System.out.println(
        "obj2 (bool=true, empty=true): "
            + Velocity241Probe.render("#if($obj2)T#{else}F#end", ctx).output());

    CustomEmptySize es1 = new CustomEmptySize(true, 5); // empty=true, size=5
    CustomEmptySize es2 = new CustomEmptySize(false, 0); // empty=false, size=0
    Map<String, Object> ctx2 = Map.of("es1", es1, "es2", es2);
    System.out.println(
        "es1 (empty=true, size=5): "
            + Velocity241Probe.render("#if($es1)T#{else}F#end", ctx2).output());
    System.out.println(
        "es2 (empty=false, size=0): "
            + Velocity241Probe.render("#if($es2)T#{else}F#end", ctx2).output());
  }
}
