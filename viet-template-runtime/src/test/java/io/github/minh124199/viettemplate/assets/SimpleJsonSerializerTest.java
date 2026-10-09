package io.github.minh124199.viettemplate.assets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleJsonSerializerTest {

  record Employee(int id, String name, List<String> roles) {}

  @Test
  void serializesPrimitivesAndCollections() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    serializer.serialize(null, sb);
    assertThat(sb.toString()).isEqualTo("null");

    sb.setLength(0);
    serializer.serialize(42, sb);
    assertThat(sb.toString()).isEqualTo("42");

    sb.setLength(0);
    serializer.serialize(true, sb);
    assertThat(sb.toString()).isEqualTo("true");

    sb.setLength(0);
    serializer.serialize("Hello World", sb);
    assertThat(sb.toString()).isEqualTo("\"Hello World\"");

    sb.setLength(0);
    serializer.serialize(List.of("a", "b"), sb);
    assertThat(sb.toString()).isEqualTo("[\"a\",\"b\"]");

    sb.setLength(0);
    serializer.serialize(Map.of("key", "value"), sb);
    assertThat(sb.toString()).isEqualTo("{\"key\":\"value\"}");
  }

  @Test
  void serializesRecord() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    Employee emp = new Employee(101, "Alice", List.of("admin", "user"));
    serializer.serialize(emp, sb);

    assertThat(sb.toString())
        .isEqualTo("{\"id\":101,\"name\":\"Alice\",\"roles\":[\"admin\",\"user\"]}");
  }

  @Test
  void escapesSpecialCharactersInStrings() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    serializer.serialize("Line 1\nLine 2\tTab \"Quotes\" and \\Backslash\\", sb);
    assertThat(sb.toString())
        .isEqualTo("\"Line 1\\nLine 2\\tTab \\\"Quotes\\\" and \\\\Backslash\\\\\"");
  }

  enum Status {
    ACTIVE,
    INACTIVE
  }

  static class SelfRef {
    private final String name;
    private SelfRef next;

    SelfRef(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public SelfRef getNext() {
      return next;
    }

    public void setNext(SelfRef next) {
      this.next = next;
    }
  }

  static class MultiPropertyBean {
    public String getZ() {
      return "3";
    }

    public String getA() {
      return "1";
    }

    public String getM() {
      return "2";
    }
  }

  @Test
  void serializesEnumAsName() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    serializer.serialize(Status.ACTIVE, sb);
    assertThat(sb.toString()).isEqualTo("\"ACTIVE\"");
  }

  @Test
  void serializesCharacter() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    serializer.serialize('A', sb);
    assertThat(sb.toString()).isEqualTo("\"A\"");
  }

  @Test
  void serializesUuidAndTemporalAndDate() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    java.util.UUID uuid = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc");
    serializer.serialize(uuid, sb);
    assertThat(sb.toString()).isEqualTo("\"12345678-1234-1234-1234-123456789abc\"");

    sb.setLength(0);
    java.time.Instant instant = java.time.Instant.parse("2026-10-09T00:00:00Z");
    serializer.serialize(instant, sb);
    assertThat(sb.toString()).isEqualTo("\"2026-10-09T00:00:00Z\"");

    sb.setLength(0);
    java.util.Date date = new java.util.Date(1700000000000L);
    serializer.serialize(date, sb);
    assertThat(sb.toString()).isEqualTo("\"2023-11-14T22:13:20Z\"");
  }

  @Test
  void serializesNanAndInfinitiesAsNull() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    serializer.serialize(Double.NaN, sb);
    assertThat(sb.toString()).isEqualTo("null");

    sb.setLength(0);
    serializer.serialize(Double.POSITIVE_INFINITY, sb);
    assertThat(sb.toString()).isEqualTo("null");

    sb.setLength(0);
    serializer.serialize(Float.NaN, sb);
    assertThat(sb.toString()).isEqualTo("null");
  }

  @Test
  void detectsCircularReferencesGracefully() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    SelfRef a = new SelfRef("A");
    SelfRef b = new SelfRef("B");
    a.setNext(b);
    b.setNext(a);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> serializer.serialize(a, sb))
        .isInstanceOf(ClientDataSerializationException.class)
        .satisfies(
            e -> {
              ClientDataSerializationException ex = (ClientDataSerializationException) e;
              assertThat(ex.code()).isEqualTo(AssetDiagnosticCode.VT_CLIENT_002);
              assertThat(ex.getMessage()).contains("Circular reference");
            });
  }

  @Test
  void serializesBeanPropertiesInDeterministicOrder() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    serializer.serialize(new MultiPropertyBean(), sb);
    assertThat(sb.toString()).isEqualTo("{\"a\":\"1\",\"m\":\"2\",\"z\":\"3\"}");
  }

  static class BrokenGetterBean {
    public String getGood() {
      return "ok";
    }

    public String getExploding() {
      throw new IllegalStateException("Getter exploded!");
    }
  }

  @Test
  void throwsOnFailingGetter() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> serializer.serialize(new BrokenGetterBean(), sb))
        .isInstanceOf(ClientDataSerializationException.class)
        .satisfies(
            e -> {
              ClientDataSerializationException ex = (ClientDataSerializationException) e;
              assertThat(ex.code()).isEqualTo(AssetDiagnosticCode.VT_CLIENT_002);
              assertThat(ex.getMessage()).contains("Failed reading property 'exploding'");
              assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
            });
  }

  @Test
  void defaultSerializerIsSimpleJsonSerializer() {
    assertThat(ClientData.defaultSerializer()).isSameAs(SimpleJsonSerializer.INSTANCE);
  }

  @Test
  void serializesMapWithNonStringKeys() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    serializer.serialize(Map.of(42, "number", true, "bool"), sb);
    assertThat(sb.toString()).contains("\"42\":\"number\"").contains("\"true\":\"bool\"");
  }

  @Test
  void serializesSqlDateWithoutToInstantException() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    java.sql.Date sqlDate = new java.sql.Date(1700000000000L);
    serializer.serialize(sqlDate, sb);
    assertThat(sb.toString()).isEqualTo("\"2023-11-14T22:13:20Z\"");
  }

  static class EdgeCaseBean {
    public static String getStaticProperty() {
      return "static-value";
    }

    public void getVoidProperty() {}

    public String getValidProperty() {
      return "valid";
    }

    public boolean isFlag() {
      return true;
    }

    public void isVoidFlag() {}

    public String isNotBoolean() {
      return "ignored";
    }
  }

  @Test
  void filtersOutStaticVoidAndNonBooleanIsMethods() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    serializer.serialize(new EdgeCaseBean(), sb);
    assertThat(sb.toString()).isEqualTo("{\"flag\":true,\"validProperty\":\"valid\"}");
  }

  @Test
  void rejectsUnsupportedSystemTypes() {
    SimpleJsonSerializer serializer = SimpleJsonSerializer.INSTANCE;
    StringBuilder sb = new StringBuilder();

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> serializer.serialize(String.class, sb))
        .isInstanceOf(ClientDataSerializationException.class)
        .satisfies(
            e -> {
              ClientDataSerializationException ex = (ClientDataSerializationException) e;
              assertThat(ex.code()).isEqualTo(AssetDiagnosticCode.VT_CLIENT_002);
              assertThat(ex.getMessage()).contains("Unsupported system type");
            });

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> serializer.serialize(Thread.currentThread(), sb))
        .isInstanceOf(ClientDataSerializationException.class)
        .satisfies(
            e -> {
              ClientDataSerializationException ex = (ClientDataSerializationException) e;
              assertThat(ex.code()).isEqualTo(AssetDiagnosticCode.VT_CLIENT_002);
              assertThat(ex.getMessage()).contains("Unsupported system type");
            });
  }
}
