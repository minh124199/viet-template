package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IndexAndAssignmentTest extends AbstractInterpreterTest {

  public static class Address {
    private String city = "Default City";

    public String getCity() {
      return city;
    }

    public void setCity(String city) {
      this.city = city;
    }
  }

  public static class Person {
    private Address address = new Address();

    public Address getAddress() {
      return address;
    }
  }

  @Test
  void indexAccessOnListArrayAndMap() {
    List<String> list = List.of("first", "second", "third");
    String[] array = new String[] {"alpha", "beta", "gamma"};
    Map<String, Object> map = Map.of("color", "red", "num", 42);

    Map<String, Object> ctx = Map.of("list", list, "arr", array, "map", map);

    assertThat(render("$list[0] $list[1]", ctx)).isEqualTo("first second");
    assertThat(render("$arr[0] $arr[2]", ctx)).isEqualTo("alpha gamma");
    assertThat(render("$map['color'] $map['num']", ctx)).isEqualTo("red 42");
  }

  @Test
  void assignmentToContextVariable() {
    String template = "#set($msg = 'Hello World')Result: $msg";
    assertThat(render(template)).isEqualTo("Result: Hello World");
  }

  @Test
  void assignmentToMapPropertyAndIndex() {
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> ctx = Map.of("map", map);

    render("#set($map.greeting = 'Hi')#set($map['target'] = 'There')", ctx);

    assertThat(map).containsEntry("greeting", "Hi");
    assertThat(map).containsEntry("target", "There");
  }

  @Test
  void assignmentToListAndArrayIndex() {
    List<String> list = new ArrayList<>(List.of("old0", "old1"));
    String[] array = new String[] {"oldA", "oldB"};

    Map<String, Object> ctx = Map.of("list", list, "arr", array);

    render("#set($list[1] = 'new1')#set($arr[0] = 'newA')", ctx);

    assertThat(list.get(1)).isEqualTo("new1");
    assertThat(array[0]).isEqualTo("newA");
  }

  @Test
  void navigatedAssignmentToNestedObject() {
    Person person = new Person();
    Map<String, Object> ctx = Map.of("person", person);

    render("#set($person.address.city = 'Da Nang')", ctx);

    assertThat(person.getAddress().getCity()).isEqualTo("Da Nang");
  }
}
