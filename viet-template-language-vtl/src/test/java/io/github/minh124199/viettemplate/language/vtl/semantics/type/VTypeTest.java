package io.github.minh124199.viettemplate.language.vtl.semantics.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VTypeTest {

  @Test
  @DisplayName("Primitive numeric types widen correctly")
  void primitiveNumericWidening() {
    assertThat(VTypes.INT.isAssignableTo(VTypes.LONG)).isTrue();
    assertThat(VTypes.INT.isAssignableTo(VTypes.DOUBLE)).isTrue();
    assertThat(VTypes.BYTE.isAssignableTo(VTypes.INT)).isTrue();
    assertThat(VTypes.SHORT.isAssignableTo(VTypes.INT)).isTrue();
    assertThat(VTypes.FLOAT.isAssignableTo(VTypes.DOUBLE)).isTrue();

    assertThat(VTypes.LONG.isAssignableTo(VTypes.INT)).isFalse();
    assertThat(VTypes.DOUBLE.isAssignableTo(VTypes.FLOAT)).isFalse();
    assertThat(VTypes.BOOLEAN.isAssignableTo(VTypes.INT)).isFalse();
  }

  @Test
  @DisplayName("Primitive types box into corresponding wrapper or Object/Number")
  void primitiveBoxing() {
    VType integerWrapper = VType.ClassType.of(Integer.class);
    VType numberClass = VType.ClassType.of(Number.class);

    assertThat(VTypes.INT.isAssignableTo(integerWrapper)).isTrue();
    assertThat(VTypes.INT.isAssignableTo(numberClass)).isTrue();
    assertThat(VTypes.INT.isAssignableTo(VTypes.OBJECT)).isTrue();
    assertThat(VTypes.LONG.isAssignableTo(integerWrapper)).isFalse();
  }

  @Test
  @DisplayName("Class types support unboxing and subtype assignability")
  void classAssignability() {
    VType stringType = VTypes.STRING;
    VType objectType = VTypes.OBJECT;
    VType charSeqType = VType.ClassType.of(CharSequence.class);

    assertThat(stringType.isAssignableTo(objectType)).isTrue();
    assertThat(stringType.isAssignableTo(charSeqType)).isTrue();
    assertThat(objectType.isAssignableTo(stringType)).isFalse();

    VType integerWrapper = VType.ClassType.of(Integer.class);
    assertThat(integerWrapper.isAssignableTo(VTypes.INT)).isTrue();
    assertThat(integerWrapper.isAssignableTo(VTypes.LONG)).isFalse();
  }

  @Test
  @DisplayName("Array types assignability")
  void arrayAssignability() {
    VType stringArray = new VType.ArrayType(VTypes.STRING, Nullability.NON_NULL);
    VType objArray = new VType.ArrayType(VTypes.OBJECT, Nullability.NULLABLE);

    assertThat(stringArray.isAssignableTo(objArray)).isTrue();
    assertThat(stringArray.isAssignableTo(VTypes.OBJECT)).isTrue();
    assertThat(objArray.isAssignableTo(stringArray)).isFalse();
  }

  @Test
  @DisplayName("Dynamic type is universally assignable")
  void dynamicAssignability() {
    assertThat(VTypes.DYNAMIC.isAssignableTo(VTypes.INT)).isTrue();
    assertThat(VTypes.DYNAMIC.isAssignableTo(VTypes.STRING)).isTrue();
    assertThat(VTypes.STRING.isAssignableTo(VTypes.DYNAMIC)).isTrue();
  }

  @Test
  @DisplayName("Null type is assignable to reference types but not primitives")
  void nullAssignability() {
    assertThat(VTypes.NULL.isAssignableTo(VTypes.STRING)).isTrue();
    assertThat(VTypes.NULL.isAssignableTo(VTypes.OBJECT)).isTrue();
    assertThat(VTypes.NULL.isAssignableTo(VTypes.INT)).isFalse();
    assertThat(VTypes.NULL.isAssignableTo(VTypes.BOOLEAN)).isFalse();
  }

  @Test
  @DisplayName("VTypes classification and element type inspection")
  void vtypesInspection() {
    assertThat(VTypes.isNumeric(VTypes.INT)).isTrue();
    assertThat(VTypes.isNumeric(VTypes.DOUBLE)).isTrue();
    assertThat(VTypes.isNumeric(VTypes.STRING)).isFalse();

    assertThat(VTypes.isBoolean(VTypes.BOOLEAN)).isTrue();
    assertThat(VTypes.isBoolean(VType.ClassType.of(Boolean.class))).isTrue();
    assertThat(VTypes.isBoolean(VTypes.INT)).isFalse();

    VType stringList = VType.ClassType.of(List.class, List.of(VTypes.STRING), Nullability.NON_NULL);
    assertThat(VTypes.isIterable(stringList)).isTrue();
    assertThat(VTypes.isCollection(stringList)).isTrue();
    assertThat(VTypes.elementType(stringList)).isEqualTo(VTypes.STRING);

    VType stringArray = new VType.ArrayType(VTypes.STRING, Nullability.NON_NULL);
    assertThat(VTypes.isIterable(stringArray)).isTrue();
    assertThat(VTypes.elementType(stringArray)).isEqualTo(VTypes.STRING);

    VType mapType =
        VType.ClassType.of(Map.class, List.of(VTypes.STRING, VTypes.INT), Nullability.NON_NULL);
    assertThat(VTypes.isMap(mapType)).isTrue();
    assertThat(VTypes.elementType(mapType)).isEqualTo(VTypes.INT);
  }
}
