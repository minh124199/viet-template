package io.github.minh124199.viettemplate.language.vtl.semantics.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelSchemaTest {

  record SampleRecord(String name, int age, List<String> tags) {}

  @TemplateModel("users/view.vm")
  interface SampleInterface {
    String getName();

    int age();

    boolean isActive();
  }

  static class SamplePojo {
    private String title;

    public String getTitle() {
      return title;
    }

    public boolean isPublished() {
      return true;
    }
  }

  @Test
  @DisplayName("Extract schema from Java record")
  void fromRecord() {
    ModelSchema schema = ModelSchema.fromRecord(SampleRecord.class);

    assertThat(schema.contains("name")).isTrue();
    assertThat(schema.contains("age")).isTrue();
    assertThat(schema.contains("tags")).isTrue();
    assertThat(schema.contains("missing")).isFalse();

    assertThat(schema.find("name").orElseThrow().type())
        .isEqualTo(VType.ClassType.of(String.class, Nullability.NULLABLE));
    assertThat(schema.find("age").orElseThrow().type()).isEqualTo(VTypes.INT);

    VType tagsType = schema.find("tags").orElseThrow().type();
    assertThat(VTypes.isIterable(tagsType)).isTrue();
    assertThat(VTypes.elementType(tagsType))
        .isEqualTo(VType.ClassType.of(String.class, Nullability.NULLABLE));
  }

  @Test
  @DisplayName("Extract schema from Java interface with getters and accessors")
  void fromInterface() {
    ModelSchema schema = ModelSchema.fromInterface(SampleInterface.class);

    assertThat(schema.contains("name")).isTrue();
    assertThat(schema.contains("age")).isTrue();
    assertThat(schema.contains("active")).isTrue();

    assertThat(schema.find("name").orElseThrow().type())
        .isEqualTo(VType.ClassType.of(String.class, Nullability.NULLABLE));
    assertThat(schema.find("age").orElseThrow().type()).isEqualTo(VTypes.INT);
    assertThat(schema.find("active").orElseThrow().type()).isEqualTo(VTypes.BOOLEAN);
  }

  @Test
  @DisplayName("Extract schema from POJO class")
  void fromPojo() {
    ModelSchema schema = ModelSchema.fromClass(SamplePojo.class);

    assertThat(schema.contains("title")).isTrue();
    assertThat(schema.contains("published")).isTrue();
    assertThat(schema.find("title").orElseThrow().type())
        .isEqualTo(VType.ClassType.of(String.class, Nullability.NULLABLE));
    assertThat(schema.find("published").orElseThrow().type()).isEqualTo(VTypes.BOOLEAN);
  }

  @Test
  @DisplayName("Build schema programmatically with Builder")
  void programmaticBuilder() {
    ModelSchema schema =
        ModelSchema.builder().add("user", VTypes.STRING).add("count", VTypes.INT).build();

    assertThat(schema.size()).isEqualTo(2);
    assertThat(schema.contains("user")).isTrue();
    assertThat(schema.contains("count")).isTrue();
    assertThat(schema.find("user").orElseThrow().type()).isEqualTo(VTypes.STRING);
    assertThat(schema.find("count").orElseThrow().type()).isEqualTo(VTypes.INT);
  }
}
