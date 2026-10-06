package io.github.minh124199.viettemplate.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CanonicalSchemaModelTest {

  @Test
  @DisplayName("All TypeRef implementations report correct display names")
  void testAllTypeRefsAndDisplayNames() {
    TypeRef primitive = new PrimitiveTypeRef("string");
    assertThat(primitive.displayName()).isEqualTo("string");

    TypeRef classType = new ClassTypeRef("java.lang.String");
    assertThat(classType.displayName()).isEqualTo("String");

    TypeRef namedNoArgs = new NamedTypeRef("User");
    assertThat(namedNoArgs.displayName()).isEqualTo("User");

    TypeRef namedWithArgs =
        new NamedTypeRef(
            "com.example.Container",
            List.of(new PrimitiveTypeRef("string"), new PrimitiveTypeRef("integer")));
    assertThat(namedWithArgs.displayName()).isEqualTo("Container<string, integer>");

    TypeRef array = new ArrayTypeRef(new PrimitiveTypeRef("string"));
    assertThat(array.displayName()).isEqualTo("string[]");

    TypeRef map = new MapTypeRef(new PrimitiveTypeRef("string"), new PrimitiveTypeRef("integer"));
    assertThat(map.displayName()).isEqualTo("Map<string, integer>");

    TypeRef enumType = new EnumTypeRef("Status", List.of("ACTIVE", "INACTIVE"));
    assertThat(enumType.displayName()).isEqualTo("Status");

    TypeRef union =
        new UnionTypeRef(List.of(new PrimitiveTypeRef("string"), new PrimitiveTypeRef("integer")));
    assertThat(union.displayName()).isEqualTo("string | integer");

    TypeRef wildcardUnbounded = new WildcardTypeRef();
    assertThat(wildcardUnbounded.displayName()).isEqualTo("?");

    TypeRef wildcardExtends = new WildcardTypeRef("extends", new PrimitiveTypeRef("Number"));
    assertThat(wildcardExtends.displayName()).isEqualTo("? extends Number");

    TypeRef wildcardSuper = new WildcardTypeRef("super", new PrimitiveTypeRef("Integer"));
    assertThat(wildcardSuper.displayName()).isEqualTo("? super Integer");

    TypeRef parameterized =
        new ParameterizedTypeRef("java.util.List", List.of(new PrimitiveTypeRef("string")));
    assertThat(parameterized.displayName()).isEqualTo("List<string>");

    TypeRef dynamicType = new DynamicTypeRef();
    assertThat(dynamicType.displayName()).isEqualTo("dynamic");
  }

  @Test
  @DisplayName("PropertyDef supports all 4 combinations of nullability and optionality")
  void testPropertyDefAllFourNullabilityOptionalityCombinations() {
    TypeRef type = new PrimitiveTypeRef("string");

    // 1. Required, Non-null
    PropertyDef reqNonNull = new PropertyDef("p1", type, false, false, "doc1");
    assertThat(reqNonNull.nullable()).isFalse();
    assertThat(reqNonNull.optional()).isFalse();
    assertThat(reqNonNull.required()).isTrue();
    assertThat(reqNonNull.documentation()).isEqualTo("doc1");

    // 2. Required, Nullable
    PropertyDef reqNullable = new PropertyDef("p2", type, true, false, "doc2");
    assertThat(reqNullable.nullable()).isTrue();
    assertThat(reqNullable.optional()).isFalse();
    assertThat(reqNullable.required()).isTrue();

    // 3. Optional, Non-null
    PropertyDef optNonNull = new PropertyDef("p3", type, false, true, "doc3");
    assertThat(optNonNull.nullable()).isFalse();
    assertThat(optNonNull.optional()).isTrue();
    assertThat(optNonNull.required()).isFalse();

    // 4. Optional, Nullable
    PropertyDef optNullable = new PropertyDef("p4", type, true, true, "doc4");
    assertThat(optNullable.nullable()).isTrue();
    assertThat(optNullable.optional()).isTrue();
    assertThat(optNullable.required()).isFalse();

    // Overloaded constructor defaults: optional=false, documentation=""
    PropertyDef defaultProp = new PropertyDef("default", type, false);
    assertThat(defaultProp.optional()).isFalse();
    assertThat(defaultProp.required()).isTrue();
    assertThat(defaultProp.documentation()).isEmpty();
  }

  @Test
  @DisplayName("ParameterDef supports all 4 combinations of nullability and optionality")
  void testParameterDefAllFourNullabilityOptionalityCombinations() {
    TypeRef type = new PrimitiveTypeRef("integer");

    // 1. Required, Non-null
    ParameterDef reqNonNull = new ParameterDef("param1", type, false, false, "doc1");
    assertThat(reqNonNull.nullable()).isFalse();
    assertThat(reqNonNull.optional()).isFalse();
    assertThat(reqNonNull.required()).isTrue();

    // 2. Required, Nullable
    ParameterDef reqNullable = new ParameterDef("param2", type, true, false, "doc2");
    assertThat(reqNullable.nullable()).isTrue();
    assertThat(reqNullable.optional()).isFalse();
    assertThat(reqNullable.required()).isTrue();

    // 3. Optional, Non-null
    ParameterDef optNonNull = new ParameterDef("param3", type, false, true, "doc3");
    assertThat(optNonNull.nullable()).isFalse();
    assertThat(optNonNull.optional()).isTrue();
    assertThat(optNonNull.required()).isFalse();

    // 4. Optional, Nullable
    ParameterDef optNullable = new ParameterDef("param4", type, true, true, "doc4");
    assertThat(optNullable.nullable()).isTrue();
    assertThat(optNullable.optional()).isTrue();
    assertThat(optNullable.required()).isFalse();

    // Overloaded constructors
    ParameterDef def1 = new ParameterDef("paramDef1", type, false);
    assertThat(def1.optional()).isFalse();
    assertThat(def1.required()).isTrue();
    assertThat(def1.documentation()).isEmpty();

    ParameterDef def2 = new ParameterDef("paramDef2", type, true, true);
    assertThat(def2.nullable()).isTrue();
    assertThat(def2.optional()).isTrue();
    assertThat(def2.documentation()).isEmpty();
  }

  @Test
  @DisplayName("Defensive copying ensures immutability of models")
  void testDefensiveCopyingAndImmutability() {
    List<TypeRef> mutableArgs = new ArrayList<>();
    mutableArgs.add(new PrimitiveTypeRef("string"));
    NamedTypeRef named = new NamedTypeRef("Container", mutableArgs);
    mutableArgs.add(new PrimitiveTypeRef("integer"));
    assertThat(named.arguments()).hasSize(1);
    assertThatThrownBy(() -> named.arguments().add(new PrimitiveTypeRef("boolean")))
        .isInstanceOf(UnsupportedOperationException.class);

    List<String> mutableSymbols = new ArrayList<>();
    mutableSymbols.add("A");
    EnumTypeRef enumType = new EnumTypeRef("EnumA", mutableSymbols);
    mutableSymbols.add("B");
    assertThat(enumType.symbols()).containsExactly("A");
    assertThatThrownBy(() -> enumType.symbols().add("C"))
        .isInstanceOf(UnsupportedOperationException.class);

    Map<String, PropertyDef> mutableProps = new HashMap<>();
    mutableProps.put("z", new PropertyDef("z", new PrimitiveTypeRef("string"), false));
    TypeDef typeDef = new TypeDef("MyType", "object", mutableProps);
    mutableProps.put("a", new PropertyDef("a", new PrimitiveTypeRef("integer"), false));
    assertThat(typeDef.properties()).hasSize(1);
    assertThatThrownBy(
            () ->
                typeDef
                    .properties()
                    .put("c", new PropertyDef("c", new PrimitiveTypeRef("boolean"), false)))
        .isInstanceOf(UnsupportedOperationException.class);

    Map<String, ParameterDef> mutableParams = new HashMap<>();
    mutableParams.put("p", new ParameterDef("p", new PrimitiveTypeRef("string"), false));
    CanonicalSchema schema =
        new CanonicalSchema(
            "test-template",
            SchemaFormat.CONTRACT,
            "fingerprint-123",
            mutableParams,
            Map.of("MyType", typeDef),
            "{}");
    mutableParams.put("p2", new ParameterDef("p2", new PrimitiveTypeRef("integer"), false));
    assertThat(schema.parameters()).hasSize(1);
    assertThatThrownBy(
            () ->
                schema
                    .parameters()
                    .put("p3", new ParameterDef("p3", new PrimitiveTypeRef("boolean"), false)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Determinism is guaranteed by sorted TreeMaps in TypeDef and CanonicalSchema")
  void testDeterministicOrdering() {
    Map<String, PropertyDef> unsortedProps = new HashMap<>();
    unsortedProps.put("zebra", new PropertyDef("zebra", new PrimitiveTypeRef("string"), false));
    unsortedProps.put("apple", new PropertyDef("apple", new PrimitiveTypeRef("string"), false));
    unsortedProps.put("monkey", new PropertyDef("monkey", new PrimitiveTypeRef("string"), false));

    TypeDef typeDef = new TypeDef("Animal", "object", unsortedProps);
    assertThat(typeDef.properties().keySet()).containsExactly("apple", "monkey", "zebra");

    Map<String, ParameterDef> unsortedParams = new HashMap<>();
    unsortedParams.put("z", new ParameterDef("z", new PrimitiveTypeRef("string"), false));
    unsortedParams.put("a", new ParameterDef("a", new PrimitiveTypeRef("string"), false));
    unsortedParams.put("m", new ParameterDef("m", new PrimitiveTypeRef("string"), false));

    Map<String, TypeDef> unsortedTypes = new HashMap<>();
    unsortedTypes.put("ZType", new TypeDef("ZType", "object", Map.of()));
    unsortedTypes.put("AType", new TypeDef("AType", "object", Map.of()));

    CanonicalSchema schema =
        new CanonicalSchema("tmpl", SchemaFormat.CONTRACT, "fp", unsortedParams, unsortedTypes, "");

    assertThat(schema.parameters().keySet()).containsExactly("a", "m", "z");
    assertThat(schema.types().keySet()).containsExactly("AType", "ZType");
  }

  @Test
  @DisplayName("CanonicalSchemaModel.simpleName extracts short class names correctly")
  void testSimpleNameHelper() {
    assertThat(CanonicalSchemaModel.simpleName("java.util.List")).isEqualTo("List");
    assertThat(CanonicalSchemaModel.simpleName("com.example.Outer$Inner")).isEqualTo("Inner");
    assertThat(CanonicalSchemaModel.simpleName("SimpleClass")).isEqualTo("SimpleClass");
    assertThat(CanonicalSchemaModel.simpleName("")).isEqualTo("");
    assertThat(CanonicalSchemaModel.simpleName(null)).isEqualTo("");
  }
}
