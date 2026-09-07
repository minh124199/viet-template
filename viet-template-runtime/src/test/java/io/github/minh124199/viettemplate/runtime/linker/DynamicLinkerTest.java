package io.github.minh124199.viettemplate.runtime.linker;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DynamicLinkerTest {

  public record PersonRecord(String name, int age) {}

  public static class BeanPerson {
    private String name;
    private boolean active;
    public String publicField = "fieldValue";

    public BeanPerson(String name, boolean active) {
      this.name = name;
      this.active = active;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    public int add(int a, int b) {
      return a + b;
    }

    public String greet(String prefix) {
      return prefix + " " + name;
    }
  }

  private DynamicLinker linker;

  @BeforeEach
  void setUp() {
    linker = new DynamicLinker(LinkerAccessPolicy.standard());
  }

  @Test
  void testRecordComponentAccess() throws Throwable {
    PersonRecord person = new PersonRecord("Alice", 30);
    MemberKey key = MemberKey.propertyGet("name");
    AccessLink link = linker.link(person.getClass(), key);

    assertTrue(link.isOk());
    assertEquals("Alice", link.invokeGet(person));

    MemberKey ageKey = MemberKey.propertyGet("age");
    AccessLink ageLink = linker.link(person.getClass(), ageKey);
    assertEquals(30, ageLink.invokeGet(person));
  }

  @Test
  void testJavaBeanGetterAndBooleanIs() throws Throwable {
    BeanPerson person = new BeanPerson("Bob", true);

    AccessLink nameLink = linker.link(person.getClass(), MemberKey.propertyGet("name"));
    assertTrue(nameLink.isOk());
    assertEquals("Bob", nameLink.invokeGet(person));

    AccessLink activeLink = linker.link(person.getClass(), MemberKey.propertyGet("active"));
    assertTrue(activeLink.isOk());
    assertEquals(true, activeLink.invokeGet(person));
  }

  @Test
  void testPublicFieldAccess() throws Throwable {
    BeanPerson person = new BeanPerson("Charlie", false);
    AccessLink link = linker.link(person.getClass(), MemberKey.propertyGet("publicField"));

    assertTrue(link.isOk());
    assertEquals("fieldValue", link.invokeGet(person));
  }

  @Test
  void testMapPropertyAccess() throws Throwable {
    Map<String, Object> map = new HashMap<>();
    map.put("key1", "val1");

    AccessLink link = linker.link(map.getClass(), MemberKey.propertyGet("key1"));
    assertTrue(link.isOk());
    assertEquals("val1", link.invokeGet(map));
  }

  @Test
  void testPseudoProperties() throws Throwable {
    List<String> list = List.of("a", "b", "c");
    AccessLink sizeLink = linker.link(list.getClass(), MemberKey.propertyGet("size"));
    assertEquals(3, sizeLink.invokeGet(list));

    String text = "hello";
    AccessLink lengthLink = linker.link(text.getClass(), MemberKey.propertyGet("length"));
    assertEquals(5, lengthLink.invokeGet(text));

    String[] array = new String[] {"x", "y"};
    AccessLink arrayLength = linker.link(array.getClass(), MemberKey.propertyGet("length"));
    assertEquals(2, arrayLength.invokeGet(array));
  }

  @Test
  void testMethodCall() throws Throwable {
    BeanPerson person = new BeanPerson("David", true);
    AccessLink addLink = linker.link(person.getClass(), MemberKey.methodCall("add", 2));

    assertTrue(addLink.isOk());
    assertEquals(7, addLink.invokeMethod(person, 3, 4));

    AccessLink greetLink = linker.link(person.getClass(), MemberKey.methodCall("greet", 1));
    assertEquals("Hello David", greetLink.invokeMethod(person, "Hello"));
  }

  @Test
  void testPropertySet() throws Throwable {
    BeanPerson person = new BeanPerson("Eve", false);
    AccessLink setNameLink = linker.link(person.getClass(), MemberKey.propertySet("name"));

    assertTrue(setNameLink.isOk());
    setNameLink.invokeSet(person, "Eve Updated");
    assertEquals("Eve Updated", person.getName());

    AccessLink setActiveLink = linker.link(person.getClass(), MemberKey.propertySet("active"));
    setActiveLink.invokeSet(person, true);
    assertTrue(person.isActive());
  }

  @Test
  void testIndexGetAndSetOnList() throws Throwable {
    List<String> list = new ArrayList<>(List.of("first", "second"));

    AccessLink indexGet = linker.link(list.getClass(), MemberKey.indexGet());
    assertTrue(indexGet.isOk());
    assertEquals("first", indexGet.invokeIndexGet(list, 0));
    assertEquals("second", indexGet.invokeIndexGet(list, 1));

    AccessLink indexSet = linker.link(list.getClass(), MemberKey.indexSet());
    assertTrue(indexSet.isOk());
    indexSet.invokeIndexSet(list, 1, "replaced");
    assertEquals("replaced", list.get(1));
  }

  @Test
  void testIndexGetAndSetOnArray() throws Throwable {
    String[] arr = new String[] {"a", "b"};

    AccessLink indexGet = linker.link(arr.getClass(), MemberKey.indexGet());
    assertEquals("a", indexGet.invokeIndexGet(arr, 0));

    AccessLink indexSet = linker.link(arr.getClass(), MemberKey.indexSet());
    indexSet.invokeIndexSet(arr, 0, "z");
    assertEquals("z", arr[0]);
  }

  @Test
  void testIndexGetAndSetOnMap() throws Throwable {
    Map<String, Object> map = new HashMap<>();
    map.put("k1", "v1");

    AccessLink indexGet = linker.link(map.getClass(), MemberKey.indexGet());
    assertEquals("v1", indexGet.invokeIndexGet(map, "k1"));

    AccessLink indexSet = linker.link(map.getClass(), MemberKey.indexSet());
    indexSet.invokeIndexSet(map, "k2", "v2");
    assertEquals("v2", map.get("k2"));
  }

  @Test
  void testMissingMember() {
    BeanPerson person = new BeanPerson("Frank", true);
    AccessLink link = linker.link(person.getClass(), MemberKey.propertyGet("nonExistent"));

    assertTrue(link.isMissing());
    assertNull(link.methodHandle());
  }

  @Test
  void testDeniedMemberThrowsSecurityException() {
    AccessLink link = linker.link(Runtime.class, MemberKey.methodCall("getRuntime", 0));
    assertTrue(link.isDenied());

    assertThrows(TemplateSecurityException.class, () -> link.invokeMethod(null));
  }

  @Test
  void testGetClassDeniedOnAllowedObject() {
    BeanPerson person = new BeanPerson("George", true);
    AccessLink link = linker.link(person.getClass(), MemberKey.methodCall("getClass", 0));
    assertTrue(link.isDenied());

    assertThrows(TemplateSecurityException.class, () -> link.invokeMethod(person));
  }
}
