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
}
