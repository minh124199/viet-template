package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PropertyAndMethodAccessTest extends AbstractInterpreterTest {

  public static class TestBean {
    private String name = "BeanName";
    private boolean active = true;
    public String publicField = "FieldValue";

    public String getName() {
      return name;
    }

    public boolean isActive() {
      return active;
    }

    public String echo(String input) {
      return "Echo:" + input;
    }

    public String overload(int count) {
      return "int:" + count;
    }

    public String overload(String str) {
      return "String:" + str;
    }

    public void failingMethod() {
      throw new IllegalStateException("Deliberate method failure");
    }
  }

  public record UserRecord(String id, String displayName, int age) {}

  @Test
  void resolvesJavaBeanProperties() {
    TestBean bean = new TestBean();
    Map<String, Object> ctx = Map.of("bean", bean);

    assertThat(render("$bean.name", ctx)).isEqualTo("BeanName");
    assertThat(render("$bean.active", ctx)).isEqualTo("true");
    assertThat(render("$bean.publicField", ctx)).isEqualTo("FieldValue");
  }

  @Test
  void resolvesRecordComponents() {
    UserRecord user = new UserRecord("USR-1", "Bob Smith", 30);
    Map<String, Object> ctx = Map.of("user", user);

    assertThat(render("User: $user.displayName (Age: $user.age)", ctx))
        .isEqualTo("User: Bob Smith (Age: 30)");
  }

  @Test
  void resolvesMapProperties() {
    Map<String, Object> map = new HashMap<>();
    map.put("title", "Viet Template Engine");
    map.put("version", "0.1.0");

    Map<String, Object> ctx = Map.of("data", map);
    assertThat(render("$data.title - $data.version", ctx))
        .isEqualTo("Viet Template Engine - 0.1.0");
  }

  @Test
  void invokesMethodsAndResolvesOverloads() {
    TestBean bean = new TestBean();
    Map<String, Object> ctx = Map.of("bean", bean);

    assertThat(render("$bean.echo('Hello')", ctx)).isEqualTo("Echo:Hello");
    assertThat(render("$bean.overload(42)", ctx)).isEqualTo("int:42");
    assertThat(render("$bean.overload('test')", ctx)).isEqualTo("String:test");
  }

  @Test
  void evaluatesArgumentsLeftToRight() {
    List<String> order = new ArrayList<>();
    class OrderTracker {
      public String record(String name) {
        order.add(name);
        return name;
      }

      public String join(String a, String b, String c) {
        return a + "-" + b + "-" + c;
      }
    }

    Map<String, Object> ctx = Map.of("t", new OrderTracker());
    render("$t.join($t.record('one'), $t.record('two'), $t.record('three'))", ctx);
    assertThat(order).containsExactly("one", "two", "three");
  }

  @Test
  void unwrapsInvocationTargetException() {
    TestBean bean = new TestBean();
    Map<String, Object> ctx = Map.of("bean", bean);

    assertThatThrownBy(() -> render("$bean.failingMethod()", ctx))
        .isInstanceOf(TemplateRenderException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Deliberate method failure");
  }

  @Test
  void deniesSecuritySensitiveClassesAndMethods() {
    TestBean bean = new TestBean();
    Map<String, Object> ctx = Map.of("bean", bean, "runtime", Runtime.getRuntime());

    // Access to getClass()
    assertThatThrownBy(() -> render("$bean.class", ctx))
        .isInstanceOf(TemplateSecurityException.class);
    assertThatThrownBy(() -> render("$bean.getClass().getName()", ctx))
        .isInstanceOf(TemplateSecurityException.class);

    // Access to Runtime
    assertThatThrownBy(() -> render("$runtime.availableProcessors()", ctx))
        .isInstanceOf(TemplateSecurityException.class);
  }
}
