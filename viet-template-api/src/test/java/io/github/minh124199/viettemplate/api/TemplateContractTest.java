package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateContractTest {

  record SampleItem(String name, int qty) {}

  record SampleOrder(String id, SampleItem item, List<String> tags) {}

  static class SampleBean {
    private String title;
    private boolean active;

    public String getTitle() {
      return title;
    }

    public boolean isActive() {
      return active;
    }
  }

  @Test
  void createsContractWithExplicitParameters() {
    TemplateId id = TemplateId.of("order/summary.vtl");
    TemplateParameter p1 = TemplateParameter.of("orderId", String.class, false);
    TemplateParameter p2 =
        TemplateParameter.of("items", List.class, List.of(SampleItem.class), false);

    TemplateContract contract = TemplateContract.of(id, p1, p2);

    assertThat(contract.templateId()).isEqualTo(id);
    assertThat(contract.parameters()).containsExactly(p1, p2);
    assertThat(contract.size()).isEqualTo(2);
    assertThat(contract.isEmpty()).isFalse();
    assertThat(contract.hasParameter("orderId")).isTrue();
    assertThat(contract.hasParameter("items")).isTrue();
    assertThat(contract.hasParameter("missing")).isFalse();
    assertThat(contract.parameter("orderId")).contains(p1);
    assertThat(contract.parameter("items")).contains(p2);
    assertThat(contract.fingerprint()).isNotEmpty();
  }

  @Test
  void builderCreatesEquivalentContract() {
    TemplateId id = TemplateId.of("order/summary.vtl");
    TemplateContract c1 =
        TemplateContract.builder(id)
            .parameter("orderId", String.class, false)
            .parameter("items", List.class, List.of(SampleItem.class), false)
            .build();

    TemplateContract c2 =
        TemplateContract.of(
            id,
            TemplateParameter.of("orderId", String.class, false),
            TemplateParameter.of("items", List.class, List.of(SampleItem.class), false));

    assertThat(c1).isEqualTo(c2);
    assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    assertThat(c1.fingerprint()).isEqualTo(c2.fingerprint());
  }

  @Test
  void rejectsDuplicateParameterNames() {
    TemplateId id = TemplateId.of("test.vtl");
    assertThatThrownBy(
            () ->
                TemplateContract.of(
                    id,
                    TemplateParameter.of("x", String.class),
                    TemplateParameter.of("x", Integer.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate parameter name");
  }

  @Test
  void createsContractFromRecord() {
    TemplateId id = TemplateId.of("order/detail.vtl");
    TemplateContract contract = TemplateContract.fromRecord(id, SampleOrder.class);

    assertThat(contract.templateId()).isEqualTo(id);
    assertThat(contract.size()).isEqualTo(3);
    assertThat(contract.hasParameter("id")).isTrue();
    assertThat(contract.hasParameter("item")).isTrue();
    assertThat(contract.hasParameter("tags")).isTrue();

    TemplateParameter tagsParam = contract.parameter("tags").orElseThrow();
    assertThat(tagsParam.rawType()).isEqualTo(List.class);
    assertThat(tagsParam.typeArguments()).containsExactly(String.class);
  }

  @Test
  void createsContractFromClassWithGetters() {
    TemplateId id = TemplateId.of("bean.vtl");
    TemplateContract contract = TemplateContract.fromClass(id, SampleBean.class);

    assertThat(contract.hasParameter("title")).isTrue();
    assertThat(contract.hasParameter("active")).isTrue();
    assertThat(contract.parameter("title").get().rawType()).isEqualTo(String.class);
    assertThat(contract.parameter("active").get().rawType()).isEqualTo(boolean.class);
  }

  @Test
  void parameterFingerprintFragmentIsDeterministic() {
    TemplateParameter p1 = TemplateParameter.of("items", List.class, List.of(String.class), false);
    TemplateParameter p2 = TemplateParameter.of("items", List.class, List.of(String.class), false);

    assertThat(p1.fingerprintFragment()).isEqualTo("items:java.util.List<java.lang.String>!");
    assertThat(p1.fingerprintFragment()).isEqualTo(p2.fingerprintFragment());
  }

  @Test
  void fastRenderContextWorksWithoutMapAllocation() {
    RenderContext ctx2 = RenderContext.of("a", 1, "b", 2);
    assertThat(ctx2.get("a")).isEqualTo(1);
    assertThat(ctx2.get("b")).isEqualTo(2);
    assertThat(ctx2.contains("a")).isTrue();
    assertThat(ctx2.contains("c")).isFalse();
    assertThat(ctx2.keys()).containsExactlyInAnyOrder("a", "b");

    RenderContext ctx3 = RenderContext.of("a", 1, "b", 2, "c", 3);
    assertThat(ctx3.get("c")).isEqualTo(3);

    RenderContext ctx4 = RenderContext.of("a", 1, "b", 2, "c", 3, "d", 4);
    assertThat(ctx4.get("d")).isEqualTo(4);

    RenderContext ctxArr =
        RenderContext.of(new String[] {"x", "y"}, new Object[] {"hello", "world"});
    assertThat(ctxArr.get("x")).isEqualTo("hello");
    assertThat(ctxArr.get("y")).isEqualTo("world");
  }

  interface Identifiable {
    Object getId();
  }

  static class ItemEntity implements Identifiable {
    @Override
    public String getId() {
      return "item-1";
    }
  }

  @Test
  void createsContractFromClassWithCovariantOverride() {
    TemplateId id = TemplateId.of("item.vtl");
    TemplateContract contract = TemplateContract.fromClass(id, ItemEntity.class);
    assertThat(contract.hasParameter("id")).isTrue();
    assertThat(contract.parameter("id").orElseThrow().rawType()).isEqualTo(String.class);
  }

  @Test
  void primitiveParametersAreNeverNullable() {
    TemplateParameter p = TemplateParameter.of("num", int.class);
    assertThat(p.nullable()).isFalse();
    assertThat(p.fingerprintFragment()).endsWith("!");
  }

  @Test
  void wildcardUpperBoundInference() throws Exception {
    class Dummy {
      public List<? extends SampleItem> getWildItems() {
        return List.of();
      }
    }
    java.lang.reflect.Method m = Dummy.class.getMethod("getWildItems");
    TemplateParameter param = TemplateParameter.fromGenericType("items", m.getGenericReturnType());
    assertThat(param.typeArguments()).containsExactly(SampleItem.class);
  }

  @Test
  void typeVariableRetainsSymbolicName() throws Exception {
    class Box<T> {
      public T getValue() {
        return null;
      }
    }
    java.lang.reflect.Method m = Box.class.getMethod("getValue");
    TemplateType tt = TemplateType.fromGenericType(m.getGenericReturnType());
    assertThat(tt).isInstanceOf(TemplateType.NamedType.class);
    assertThat(tt.typeName()).isEqualTo("T");
    assertThat(tt.rawClass()).isEqualTo(Object.class);
  }

  @Test
  void genericArrayTypePreservesComponent() throws Exception {
    class ArrayHolder<E> {
      public List<E>[] getArray() {
        return null;
      }
    }
    java.lang.reflect.Method m = ArrayHolder.class.getMethod("getArray");
    TemplateType tt = TemplateType.fromGenericType(m.getGenericReturnType());
    assertThat(tt).isInstanceOf(TemplateType.ArrayType.class);
    assertThat(tt.typeName()).isEqualTo("java.util.List<E>[]");
  }

  @Test
  void primitiveAndMultiDimensionalArrays() {
    TemplateType intArr = TemplateType.of(int[].class);
    assertThat(intArr.typeName()).isEqualTo("int[]");
    assertThat(intArr.rawClass()).isEqualTo(int[].class);
    assertThat(intArr.isArray()).isTrue();

    TemplateType matrix = TemplateType.of(String[][].class);
    assertThat(matrix.typeName()).isEqualTo("java.lang.String[][]");
    assertThat(matrix.rawClass()).isEqualTo(String[][].class);
  }
}
