package io.github.minh124199.viettemplate.language.vtl.ir.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessPlanTest {

  record UserRecord(String name, int age) {}

  public static class UserBean {
    private String title = "manager";

    public String getTitle() {
      return title;
    }

    public boolean isActive() {
      return true;
    }
  }

  public static class UserFields {
    public int score = 42;
  }

  public static class Extensions {
    public static String upper(String s) {
      return s.toUpperCase();
    }
  }

  @Test
  @DisplayName("DirectRecord holds record component metadata")
  void directRecord() throws Exception {
    Method accessor = UserRecord.class.getMethod("name");
    AccessPlan.DirectRecord plan =
        new AccessPlan.DirectRecord(UserRecord.class, "name", String.class, accessor);

    assertThat(plan.owner()).isEqualTo(UserRecord.class);
    assertThat(plan.componentName()).isEqualTo("name");
    assertThat(plan.returnType()).isEqualTo(String.class);
    assertThat(plan.accessor()).isEqualTo(accessor);
  }

  @Test
  @DisplayName("DirectGetter holds method metadata")
  void directGetter() throws Exception {
    Method getter = UserBean.class.getMethod("getTitle");
    AccessPlan.DirectGetter plan =
        new AccessPlan.DirectGetter(UserBean.class, "getTitle", String.class, getter);

    assertThat(plan.owner()).isEqualTo(UserBean.class);
    assertThat(plan.methodName()).isEqualTo("getTitle");
    assertThat(plan.returnType()).isEqualTo(String.class);
    assertThat(plan.getter()).isEqualTo(getter);
  }

  @Test
  @DisplayName("DirectField holds field metadata")
  void directField() throws Exception {
    Field field = UserFields.class.getField("score");
    AccessPlan.DirectField plan =
        new AccessPlan.DirectField(UserFields.class, "score", int.class, field);

    assertThat(plan.owner()).isEqualTo(UserFields.class);
    assertThat(plan.fieldName()).isEqualTo("score");
    assertThat(plan.fieldType()).isEqualTo(int.class);
    assertThat(plan.field()).isEqualTo(field);
  }

  @Test
  @DisplayName("MapLookup holds constant key")
  void mapLookup() {
    AccessPlan.MapLookup plan = new AccessPlan.MapLookup("userKey");
    assertThat(plan.keyConstant()).isEqualTo("userKey");
  }

  @Test
  @DisplayName("DynamicCallSite holds call site ID and name")
  void dynamicCallSite() {
    AccessPlan.DynamicCallSite plan = new AccessPlan.DynamicCallSite(42, "dynamicProp");
    assertThat(plan.callSiteId()).isEqualTo(42);
    assertThat(plan.propertyName()).isEqualTo("dynamicProp");
  }

  @Test
  @DisplayName("ExtensionCall holds extension target method")
  void extensionCall() throws Exception {
    Method method = Extensions.class.getMethod("upper", String.class);
    AccessPlan.ExtensionCall plan = new AccessPlan.ExtensionCall(Extensions.class, "upper", method);

    assertThat(plan.targetClass()).isEqualTo(Extensions.class);
    assertThat(plan.methodName()).isEqualTo("upper");
    assertThat(plan.method()).isEqualTo(method);
  }
}
