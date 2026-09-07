package io.github.minh124199.viettemplate.runtime.linker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InlineCacheTest {

  public static class ShapeA {
    public String getVal() {
      return "A";
    }
  }

  public static class ShapeB {
    public String getVal() {
      return "B";
    }
  }

  public static class ShapeC {
    public String getVal() {
      return "C";
    }
  }

  public static class ShapeD {
    public String getVal() {
      return "D";
    }
  }

  public static class ShapeE {
    public String getVal() {
      return "E";
    }
  }

  public static class ShapeF {
    public String getVal() {
      return "F";
    }
  }

  private DynamicLinker linker;
  private LinkerStatistics stats;
  private DynamicCallSite callSite;

  @BeforeEach
  void setUp() {
    linker = new DynamicLinker();
    stats = new LinkerStatistics();
    callSite =
        new DynamicCallSite(
            1, MemberKey.propertyGet("val"), LinkerAccessPolicy.standard(), linker, stats);
  }

  @Test
  void testInitialStateUnlinked() {
    assertEquals(DynamicCallSite.State.UNLINKED, callSite.state());
    assertEquals(0, stats.totalLinks());
    assertEquals(0, stats.picHits());
    assertEquals(0, stats.picMisses());
  }

  @Test
  void testMonomorphicTransitionAndHit() throws Throwable {
    ShapeA a1 = new ShapeA();
    ShapeA a2 = new ShapeA();

    // First call -> UNLINKED to MONOMORPHIC (1 miss, 1 link)
    Object res1 = callSite.invoke(a1);
    assertEquals("A", res1);
    assertEquals(DynamicCallSite.State.MONOMORPHIC, callSite.state());
    assertEquals(1, stats.totalLinks());
    assertEquals(1, stats.picMisses());
    assertEquals(0, stats.picHits());

    // Second call with same shape -> Monomorphic hit!
    Object res2 = callSite.invoke(a2);
    assertEquals("A", res2);
    assertEquals(DynamicCallSite.State.MONOMORPHIC, callSite.state());
    assertEquals(1, stats.picHits());
  }

  @Test
  void testPolymorphicTransitionUpToDepth4() throws Throwable {
    ShapeA a = new ShapeA();
    ShapeB b = new ShapeB();
    ShapeC c = new ShapeC();
    ShapeD d = new ShapeD();

    callSite.invoke(a);
    assertEquals(DynamicCallSite.State.MONOMORPHIC, callSite.state());

    callSite.invoke(b);
    assertEquals(DynamicCallSite.State.POLYMORPHIC, callSite.state());

    callSite.invoke(c);
    assertEquals(DynamicCallSite.State.POLYMORPHIC, callSite.state());

    callSite.invoke(d);
    assertEquals(DynamicCallSite.State.POLYMORPHIC, callSite.state());

    // Hits across all 4 shapes
    assertEquals("A", callSite.invoke(a));
    assertEquals("B", callSite.invoke(b));
    assertEquals("C", callSite.invoke(c));
    assertEquals("D", callSite.invoke(d));

    assertEquals(DynamicCallSite.State.POLYMORPHIC, callSite.state());
    assertEquals(4, stats.totalLinks());
    assertEquals(4, stats.picMisses());
    assertEquals(4, stats.picHits());
  }

  @Test
  void testMegamorphicTransitionOnFifthShape() throws Throwable {
    ShapeA a = new ShapeA();
    ShapeB b = new ShapeB();
    ShapeC c = new ShapeC();
    ShapeD d = new ShapeD();
    ShapeE e = new ShapeE();
    ShapeF f = new ShapeF();

    callSite.invoke(a);
    callSite.invoke(b);
    callSite.invoke(c);
    callSite.invoke(d);
    assertEquals(DynamicCallSite.State.POLYMORPHIC, callSite.state());

    // 5th shape triggers MEGAMORPHIC transition
    assertEquals("E", callSite.invoke(e));
    assertEquals(DynamicCallSite.State.MEGAMORPHIC, callSite.state());

    // Megamorphic hit for a previously seen shape
    assertEquals("A", callSite.invoke(a));
    assertEquals(1, stats.megamorphicHits());

    // 6th shape -> megamorphic miss and link
    assertEquals("F", callSite.invoke(f));
    assertEquals(1, stats.megamorphicMisses());

    // Now f is cached -> megamorphic hit
    assertEquals("F", callSite.invoke(f));
    assertEquals(2, stats.megamorphicHits());
  }

  @Test
  void testCallSiteRegistry() {
    CallSiteRegistry registry = new CallSiteRegistry(10, linker);

    DynamicCallSite s1 =
        registry.getOrCreate(42, MemberKey.propertyGet("val"), LinkerAccessPolicy.standard());
    DynamicCallSite s2 =
        registry.getOrCreate(42, MemberKey.propertyGet("val"), LinkerAccessPolicy.standard());
    assertSame(s1, s2);

    DynamicCallSite s3 =
        registry.getOrCreate(43, MemberKey.propertyGet("val"), LinkerAccessPolicy.standard());
    assertNotSame(s1, s3);

    assertEquals(2, registry.size());
  }
}
