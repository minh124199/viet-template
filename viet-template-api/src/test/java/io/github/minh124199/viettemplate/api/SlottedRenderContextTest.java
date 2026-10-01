package io.github.minh124199.viettemplate.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SlottedRenderContext} and {@link RenderContext#slotted}. */
class SlottedRenderContextTest {

  @Test
  void getBySlot_returnsCorrectValues() {
    SlottedRenderContext ctx =
        RenderContext.slotted(
            new String[] {"user", "age", "active"}, new Object[] {"Alice", 30, true});

    assertEquals("Alice", ctx.getBySlot(0));
    assertEquals(30, ctx.getBySlot(1));
    assertEquals(true, ctx.getBySlot(2));
  }

  @Test
  void getByName_stillWorks() {
    SlottedRenderContext ctx =
        RenderContext.slotted(new String[] {"user", "age"}, new Object[] {"Alice", 30});

    assertEquals("Alice", ctx.get("user"));
    assertEquals(30, ctx.get("age"));
    assertNull(ctx.get("missing"));
    assertNull(ctx.get(null));
  }

  @Test
  void contains_returnsCorrectResults() {
    SlottedRenderContext ctx =
        RenderContext.slotted(new String[] {"user", "age"}, new Object[] {"Alice", 30});

    assertTrue(ctx.contains("user"));
    assertTrue(ctx.contains("age"));
    assertFalse(ctx.contains("missing"));
    assertFalse(ctx.contains(null));
  }

  @Test
  void keys_returnsCorrectSet() {
    SlottedRenderContext ctx =
        RenderContext.slotted(
            new String[] {"user", "age", "active"}, new Object[] {"Alice", 30, true});

    assertEquals(Set.of("user", "age", "active"), ctx.keys());
  }

  @Test
  void slotCount_returnsCorrectValue() {
    SlottedRenderContext ctx =
        RenderContext.slotted(new String[] {"a", "b", "c"}, new Object[] {1, 2, 3});

    assertEquals(3, ctx.slotCount());
  }

  @Test
  void slotCount_emptyContext() {
    SlottedRenderContext ctx = RenderContext.slotted(new String[] {}, new Object[] {});

    assertEquals(0, ctx.slotCount());
  }

  @Test
  void getBySlot_throwsForInvalidSlot() {
    SlottedRenderContext ctx = RenderContext.slotted(new String[] {"user"}, new Object[] {"Alice"});

    assertThrows(ArrayIndexOutOfBoundsException.class, () -> ctx.getBySlot(1));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> ctx.getBySlot(-1));
  }

  @Test
  void nullValues_arePreserved() {
    SlottedRenderContext ctx =
        RenderContext.slotted(new String[] {"user", "email"}, new Object[] {"Alice", null});

    assertEquals("Alice", ctx.getBySlot(0));
    assertNull(ctx.getBySlot(1));
    assertNull(ctx.get("email"));
    assertTrue(ctx.contains("email"));
  }

  @Test
  void singleSlot() {
    SlottedRenderContext ctx = RenderContext.slotted(new String[] {"x"}, new Object[] {42});

    assertEquals(42, ctx.getBySlot(0));
    assertEquals(42, ctx.get("x"));
    assertEquals(1, ctx.slotCount());
  }

  @Test
  void isAlsoRenderContext() {
    SlottedRenderContext ctx = RenderContext.slotted(new String[] {"user"}, new Object[] {"Alice"});

    // Should be assignable to RenderContext
    RenderContext rc = ctx;
    assertEquals("Alice", rc.get("user"));
    assertTrue(rc.contains("user"));
  }

  @Test
  void factoryValidation_nullKeys() {
    assertThrows(NullPointerException.class, () -> RenderContext.slotted(null, new Object[] {}));
  }

  @Test
  void factoryValidation_nullValues() {
    assertThrows(NullPointerException.class, () -> RenderContext.slotted(new String[] {}, null));
  }

  @Test
  void factoryValidation_lengthMismatch() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RenderContext.slotted(new String[] {"a"}, new Object[] {}));
  }

  @Test
  void factoryValidation_nullKeyElement() {
    assertThrows(
        NullPointerException.class,
        () -> RenderContext.slotted(new String[] {null}, new Object[] {"v"}));
  }

  @Test
  void defensiveCopy_keysAndValues() {
    String[] keys = {"user"};
    Object[] values = {"Alice"};
    SlottedRenderContext ctx = RenderContext.slotted(keys, values);

    // Mutating original arrays should not affect the context
    keys[0] = "modified";
    values[0] = "Bob";

    assertEquals("Alice", ctx.getBySlot(0));
    assertEquals("Alice", ctx.get("user"));
    assertNull(ctx.get("modified"));
  }
}
