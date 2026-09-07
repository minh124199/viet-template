package io.github.minh124199.viettemplate.runtime.linker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MemberKeyTest {

  @Test
  void testFactoryMethods() {
    MemberKey propGet = MemberKey.propertyGet("name");
    assertEquals(MemberOperation.PROPERTY_GET, propGet.operation());
    assertEquals("name", propGet.name());
    assertEquals(0, propGet.arity());

    MemberKey propSet = MemberKey.propertySet("name");
    assertEquals(MemberOperation.PROPERTY_SET, propSet.operation());
    assertEquals("name", propSet.name());
    assertEquals(1, propSet.arity());

    MemberKey methodCall = MemberKey.methodCall("calculate", 2);
    assertEquals(MemberOperation.METHOD_CALL, methodCall.operation());
    assertEquals("calculate", methodCall.name());
    assertEquals(2, methodCall.arity());

    MemberKey indexGet = MemberKey.indexGet();
    assertEquals(MemberOperation.INDEX_GET, indexGet.operation());
    assertEquals("[]", indexGet.name());
    assertEquals(1, indexGet.arity());

    MemberKey indexSet = MemberKey.indexSet();
    assertEquals(MemberOperation.INDEX_SET, indexSet.operation());
    assertEquals("[]", indexSet.name());
    assertEquals(2, indexSet.arity());
  }

  @Test
  void testValidation() {
    assertThrows(NullPointerException.class, () -> new MemberKey(null, "foo", 0));
    assertThrows(
        NullPointerException.class, () -> new MemberKey(MemberOperation.PROPERTY_GET, null, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MemberKey(MemberOperation.PROPERTY_GET, "foo", -1));
  }

  @Test
  void testEqualityAndHashCode() {
    MemberKey key1 = MemberKey.propertyGet("age");
    MemberKey key2 = MemberKey.propertyGet("age");
    MemberKey key3 = MemberKey.propertySet("age");

    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1, key3);
  }

  @Test
  void testToString() {
    MemberKey key = MemberKey.methodCall("test", 3);
    assertTrue(key.toString().contains("METHOD_CALL"));
    assertTrue(key.toString().contains("test"));
    assertTrue(key.toString().contains("3"));
  }
}
