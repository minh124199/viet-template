package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeScriptDeclarationProjectorTest {

  private String readClasspath(String path) {
    try (var is = getClass().getResourceAsStream(path)) {
      Objects.requireNonNull(is, "Resource not found: " + path);
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // =========================================================================
  // Golden Fixtures Tests
  // =========================================================================

  @Nested
  @DisplayName("Golden Schema Projections")
  class GoldenFixtureTests {

    @Test
    @DisplayName("Golden fixture: simple-user.vt-schema.json -> simple-user.d.ts")
    void projectSimpleUserGolden() throws IOException {
      String schemaJson = readClasspath("/golden-schemas/simple-user.vt-schema.json");
      String projected = TypeScriptDeclarationProjector.project(schemaJson);
      String expected = readClasspath("/golden-dts/simple-user.d.ts");

      assertThat(projected).isEqualTo(expected);
      assertThat(projected).contains("\n").doesNotContain("\r\n");
    }

    @Test
    @DisplayName("Golden fixture: order-details.vt-schema.json -> order-details.d.ts")
    void projectOrderDetailsGolden() throws IOException {
      String schemaJson = readClasspath("/golden-schemas/order-details.vt-schema.json");
      String projected = TypeScriptDeclarationProjector.project(schemaJson);
      String expected = readClasspath("/golden-dts/order-details.d.ts");

      assertThat(projected).isEqualTo(expected);
      assertThat(projected).contains("\n").doesNotContain("\r\n");
    }

    @Test
    @DisplayName("Golden fixture: generic-catalog.vt-schema.json -> generic-catalog.d.ts")
    void projectGenericCatalogGolden() throws IOException {
      String schemaJson = readClasspath("/golden-schemas/generic-catalog.vt-schema.json");
      String projected = TypeScriptDeclarationProjector.project(schemaJson);
      String expected = readClasspath("/golden-dts/generic-catalog.d.ts");

      assertThat(projected).isEqualTo(expected);
      assertThat(projected).contains("\n").doesNotContain("\r\n");
    }

    @Test
    @DisplayName("Golden fixture: tree-node.vt-schema.json -> tree-node.d.ts")
    void projectTreeNodeGolden() throws IOException {
      String schemaJson = readClasspath("/golden-schemas/tree-node.vt-schema.json");
      String projected = TypeScriptDeclarationProjector.project(schemaJson);
      String expected = readClasspath("/golden-dts/tree-node.d.ts");

      assertThat(projected).isEqualTo(expected);
      assertThat(projected).contains("\n").doesNotContain("\r\n");
    }
  }

  // =========================================================================
  // Type Mapping Tests
  // =========================================================================

  @Nested
  @DisplayName("Type Mapping Rules")
  class TypeMappingTests {

    @Test
    @DisplayName("All Java primitive types map correctly")
    void testPrimitives() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/primitives.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "z", "type": {"kind": "primitive", "name": "boolean"}, "nullable": false, "optional": false},
              {"name": "b", "type": {"kind": "primitive", "name": "byte"}, "nullable": false, "optional": false},
              {"name": "s", "type": {"kind": "primitive", "name": "short"}, "nullable": false, "optional": false},
              {"name": "i", "type": {"kind": "primitive", "name": "int"}, "nullable": false, "optional": false},
              {"name": "j", "type": {"kind": "primitive", "name": "long"}, "nullable": false, "optional": false},
              {"name": "f", "type": {"kind": "primitive", "name": "float"}, "nullable": false, "optional": false},
              {"name": "d", "type": {"kind": "primitive", "name": "double"}, "nullable": false, "optional": false},
              {"name": "c", "type": {"kind": "primitive", "name": "char"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("b: number;");
      assertThat(dts).contains("c: string;");
      assertThat(dts).contains("d: number;");
      assertThat(dts).contains("f: number;");
      assertThat(dts).contains("i: number;");
      assertThat(dts).contains("j: number;");
      assertThat(dts).contains("s: number;");
      assertThat(dts).contains("z: boolean;");
    }

    @Test
    @DisplayName("All Boxed primitive types and String map correctly")
    void testBoxedPrimitivesAndString() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/boxed.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "b", "type": {"kind": "class", "name": "java.lang.Boolean"}, "nullable": false, "optional": false},
              {"name": "by", "type": {"kind": "class", "name": "java.lang.Byte"}, "nullable": false, "optional": false},
              {"name": "sh", "type": {"kind": "class", "name": "java.lang.Short"}, "nullable": false, "optional": false},
              {"name": "in", "type": {"kind": "class", "name": "java.lang.Integer"}, "nullable": false, "optional": false},
              {"name": "lo", "type": {"kind": "class", "name": "java.lang.Long"}, "nullable": false, "optional": false},
              {"name": "fl", "type": {"kind": "class", "name": "java.lang.Float"}, "nullable": false, "optional": false},
              {"name": "db", "type": {"kind": "class", "name": "java.lang.Double"}, "nullable": false, "optional": false},
              {"name": "ch", "type": {"kind": "class", "name": "java.lang.Character"}, "nullable": false, "optional": false},
              {"name": "str", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false, "optional": false},
              {"name": "cs", "type": {"kind": "class", "name": "java.lang.CharSequence"}, "nullable": false, "optional": false},
              {"name": "obj", "type": {"kind": "class", "name": "java.lang.Object"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("b: boolean;");
      assertThat(dts).contains("by: number;");
      assertThat(dts).contains("sh: number;");
      assertThat(dts).contains("\"in\": number;");
      assertThat(dts).contains("lo: number;");
      assertThat(dts).contains("fl: number;");
      assertThat(dts).contains("db: number;");
      assertThat(dts).contains("ch: string;");
      assertThat(dts).contains("str: string;");
      assertThat(dts).contains("cs: string;");
      assertThat(dts).contains("obj: unknown;");
    }

    @Test
    @DisplayName("Single, nested, and multidimensional arrays")
    void testArrays() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/arrays.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {
                "name": "intArray",
                "type": {"kind": "array", "componentType": {"kind": "primitive", "name": "int"}},
                "nullable": false,
                "optional": false
              },
              {
                "name": "stringArray",
                "type": {"kind": "array", "componentType": {"kind": "class", "name": "java.lang.String"}},
                "nullable": false,
                "optional": false
              },
              {
                "name": "matrix",
                "type": {
                  "kind": "array",
                  "componentType": {
                    "kind": "array",
                    "componentType": {"kind": "primitive", "name": "double"}
                  }
                },
                "nullable": false,
                "optional": false
              }
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("intArray: number[];");
      assertThat(dts).contains("stringArray: string[];");
      assertThat(dts).contains("matrix: number[][];");
    }

    @Test
    @DisplayName("Collections and Maps: List, Set, Map, Collection, Iterable")
    void testCollectionsAndMaps() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/collections.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {
                "name": "list",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.List",
                  "arguments": [{"kind": "class", "name": "java.lang.String"}]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "set",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.Set",
                  "arguments": [{"kind": "primitive", "name": "int"}]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "stringMap",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.Map",
                  "arguments": [
                    {"kind": "class", "name": "java.lang.String"},
                    {"kind": "primitive", "name": "int"}
                  ]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "numberMap",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.Map",
                  "arguments": [
                    {"kind": "class", "name": "java.lang.Integer"},
                    {"kind": "class", "name": "java.lang.String"}
                  ]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "collection",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.Collection",
                  "arguments": [{"kind": "primitive", "name": "long"}]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "iterable",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.lang.Iterable",
                  "arguments": [{"kind": "primitive", "name": "boolean"}]
                },
                "nullable": false,
                "optional": false
              }
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("list: string[];");
      assertThat(dts).contains("\"set\": Set<number>;");
      assertThat(dts).contains("stringMap: Record<string, number>;");
      assertThat(dts).contains("numberMap: Record<number, string>;");
      assertThat(dts).contains("collection: number[];");
      assertThat(dts).contains("iterable: boolean[];");
    }

    @Test
    @DisplayName("Nested generics: List<Map<String, User>>")
    void testNestedGenerics() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/nested.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {
                "name": "data",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.List",
                  "arguments": [
                    {
                      "kind": "parameterized",
                      "rawType": "java.util.Map",
                      "arguments": [
                        {"kind": "class", "name": "java.lang.String"},
                        {"kind": "class", "name": "com.example.User"}
                      ]
                    }
                  ]
                },
                "nullable": false,
                "optional": false
              }
            ],
            "types": {
              "com.example.User": {
                "kind": "record",
                "properties": [
                  {"name": "id", "type": {"kind": "primitive", "name": "long"}, "nullable": false}
                ]
              }
            }
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("data: Record<string, User>[];");
    }

    @Test
    @DisplayName("Wildcard bounds: extends, super, unbounded")
    void testWildcards() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/wildcards.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {
                "name": "extendsList",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.List",
                  "arguments": [
                    {
                      "kind": "wildcard",
                      "boundKind": "extends",
                      "bound": {"kind": "class", "name": "java.lang.String"}
                    }
                  ]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "superList",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.List",
                  "arguments": [
                    {
                      "kind": "wildcard",
                      "boundKind": "super",
                      "bound": {"kind": "class", "name": "java.lang.String"}
                    }
                  ]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "unboundedList",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.List",
                  "arguments": [
                    {
                      "kind": "wildcard",
                      "boundKind": "unbounded"
                    }
                  ]
                },
                "nullable": false,
                "optional": false
              }
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("extendsList: string[];");
      assertThat(dts).contains("superList: unknown[];");
      assertThat(dts).contains("unboundedList: unknown[];");
    }

    @Test
    @DisplayName("Dynamic type projects to unknown")
    void testDynamicType() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/dynamic.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "dyn", "type": {"kind": "dynamic"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("dyn: unknown;");
    }

    @Test
    @DisplayName("Named type variables on root parameters")
    void testNamedTypeVariables() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/named.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "item", "type": {"kind": "named", "name": "T"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("export interface TemplateParameters<T = unknown> {");
      assertThat(dts).contains("item: T;");
    }
  }

  // =========================================================================
  // Nullability & Optionality Truth Table Tests
  // =========================================================================

  @Nested
  @DisplayName("Nullability and Optionality Truth Table")
  class NullabilityTests {

    @Test
    @DisplayName("All four nullable and optional combinations preserve exact semantics")
    void testAllFourCombinations() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/nullability.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "reqNonNull", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false, "optional": false},
              {"name": "reqNullable", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": true, "optional": false},
              {"name": "optNonNull", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false, "optional": true},
              {"name": "optNullable", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": true, "optional": true}
            ],
            "types": {
              "com.example.Item": {
                "kind": "record",
                "properties": [
                  {"name": "pReqNonNull", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false, "optional": false},
                  {"name": "pReqNullable", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": true, "optional": false},
                  {"name": "pOptNonNull", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false, "optional": true},
                  {"name": "pOptNullable", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": true, "optional": true}
                ]
              }
            }
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("reqNonNull: string;");
      assertThat(dts).contains("reqNullable: string | null;");
      assertThat(dts).contains("optNonNull?: string;");
      assertThat(dts).contains("optNullable?: string | null;");

      assertThat(dts).contains("pReqNonNull: string;");
      assertThat(dts).contains("pReqNullable: string | null;");
      assertThat(dts).contains("pOptNonNull?: string;");
      assertThat(dts).contains("pOptNullable?: string | null;");
    }
  }

  // =========================================================================
  // Identifier Sanitization, Reserved Words & Collision Tests
  // =========================================================================

  @Nested
  @DisplayName("Identifiers, Reserved Words and Collisions")
  class IdentifierTests {

    @Test
    @DisplayName(
        "FQCN collision between com.a.User and com.b.User is disambiguated deterministically")
    void testFqcnCollision() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/collision.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "userA", "type": {"kind": "class", "name": "com.a.User"}, "nullable": false, "optional": false},
              {"name": "userB", "type": {"kind": "class", "name": "com.b.User"}, "nullable": false, "optional": false}
            ],
            "types": {
              "com.a.User": {
                "kind": "record",
                "properties": [{"name": "id", "type": {"kind": "primitive", "name": "long"}, "nullable": false}]
              },
              "com.b.User": {
                "kind": "record",
                "properties": [{"name": "id", "type": {"kind": "primitive", "name": "long"}, "nullable": false}]
              }
            }
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("export interface com_a_User {");
      assertThat(dts).contains("export interface com_b_User {");
      assertThat(dts).contains("userA: com_a_User;");
      assertThat(dts).contains("userB: com_b_User;");
    }

    @Test
    @DisplayName("Reserved keywords and special characters in property names are safely quoted")
    void testReservedWordsInProperties() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/reserved.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "class", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false, "optional": false},
              {"name": "default", "type": {"kind": "primitive", "name": "boolean"}, "nullable": false, "optional": true},
              {"name": "foo-bar", "type": {"kind": "primitive", "name": "int"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("\"class\": string;");
      assertThat(dts).contains("\"default\"?: boolean;");
      assertThat(dts).contains("\"foo-bar\": number;");
    }

    @Test
    @DisplayName("Reserved keywords in type names are prefixed with underscore")
    void testReservedWordsInTypeNames() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/reserved-type.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "model", "type": {"kind": "class", "name": "com.example.Class"}, "nullable": false, "optional": false}
            ],
            "types": {
              "com.example.Class": {
                "kind": "record",
                "properties": [{"name": "name", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false}]
              }
            }
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("export interface _Class {");
      assertThat(dts).contains("model: _Class;");
    }

    @Test
    @DisplayName("Domain model named TemplateParameters does not collide with root interface")
    void testTemplateParametersNameCollision() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/tp-collision.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "params", "type": {"kind": "class", "name": "com.example.TemplateParameters"}, "nullable": false, "optional": false}
            ],
            "types": {
              "com.example.TemplateParameters": {
                "kind": "record",
                "properties": [{"name": "id", "type": {"kind": "primitive", "name": "long"}, "nullable": false}]
              }
            }
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("export interface com_example_TemplateParameters {");
      assertThat(dts).contains("export interface TemplateParameters {");
      assertThat(dts).contains("params: com_example_TemplateParameters;");
    }

    @Test
    @DisplayName("Reserved keywords used as type variables are safely sanitized")
    void testReservedWordsInTypeParameters() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/reserved-typevar.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "item", "type": {"kind": "named", "name": "class"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("export interface TemplateParameters<_class = unknown> {");
      assertThat(dts).contains("item: _class;");
    }

    @Test
    @DisplayName("Domain models matching TypeScript built-ins like Record are prefixed")
    void testTsBuiltinNameDisambiguation() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/builtin-collision.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "rec", "type": {"kind": "class", "name": "com.example.Record"}, "nullable": false, "optional": false}
            ],
            "types": {
              "com.example.Record": {
                "kind": "record",
                "properties": [{"name": "title", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false}]
              }
            }
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("export interface _Record {");
      assertThat(dts).contains("rec: _Record;");
    }

    @Test
    @DisplayName("Multi-character type variables and generic interfaces project correctly")
    void testMultiCharTypeVariablesAndGenericInterfaces() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/generic-box.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {
                "name": "stringBox",
                "type": {
                  "kind": "parameterized",
                  "rawType": "com.example.Box",
                  "arguments": [{"kind": "class", "name": "java.lang.String"}]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "rawBox",
                "type": {
                  "kind": "class",
                  "name": "com.example.Box"
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "payload",
                "type": {"kind": "named", "name": "Payload"},
                "nullable": false,
                "optional": false
              }
            ],
            "types": {
              "com.example.Box": {
                "kind": "record",
                "properties": [
                  {"name": "item", "type": {"kind": "named", "name": "Item"}, "nullable": false}
                ]
              }
            }
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("export interface Box<Item = unknown> {");
      assertThat(dts).contains("  item: Item;");
      assertThat(dts).contains("export interface TemplateParameters<Payload = unknown> {");
      assertThat(dts).contains("  payload: Payload;");
      assertThat(dts).contains("  rawBox: Box;");
      assertThat(dts).contains("  stringBox: Box<string>;");
    }
  }

  // =========================================================================
  // Determinism & Cross-Platform Tests
  // =========================================================================

  @Nested
  @DisplayName("Determinism & Platform Parity")
  class DeterminismTests {

    @Test
    @DisplayName("Repeated projection produces byte-for-byte identical output over 100 iterations")
    void testRepeatedProjectionDeterminism() {
      String schemaJson = readClasspath("/golden-schemas/order-details.vt-schema.json");
      String initial = TypeScriptDeclarationProjector.project(schemaJson);

      for (int i = 0; i < 100; i++) {
        String repeated = TypeScriptDeclarationProjector.project(schemaJson);
        assertThat(repeated).isEqualTo(initial);
      }
    }

    @Test
    @DisplayName("File projection writes with LF line endings and UTF-8 charset")
    void testFileProjectionWriting(@TempDir Path tempDir) throws IOException {
      Path schemaFile = tempDir.resolve("order-details.vt-schema.json");
      String schemaJson = readClasspath("/golden-schemas/order-details.vt-schema.json");
      Files.writeString(schemaFile, schemaJson, StandardCharsets.UTF_8);

      Path generated = TypeScriptDeclarationProjector.projectToFile(schemaFile, tempDir);
      assertThat(generated).isRegularFile();
      assertThat(generated.getFileName().toString()).isEqualTo("order-details.d.ts");

      byte[] bytes = Files.readAllBytes(generated);
      String content = new String(bytes, StandardCharsets.UTF_8);
      assertThat(content).doesNotContain("\r\n");
      assertThat(content).contains("\n");
    }
  }

  // =========================================================================
  // Negative Tests
  // =========================================================================

  @Nested
  @DisplayName("Negative Tests & Strict Validation")
  class NegativeTests {

    @Test
    @DisplayName("Rejects unsupported schema version")
    void testUnsupportedSchemaVersion() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 2,
            "templateId": "test/v2.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [],
            "types": {}
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(schema))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported schema version: 2");
    }

    @Test
    @DisplayName("Rejects unsupported schema format")
    void testUnsupportedSchemaFormat() {
      String schema =
          """
          {
            "format": "unsupported-schema-format/9",
            "schemaVersion": 1,
            "templateId": "test/invalid.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [],
            "types": {}
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(schema))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported schema format");
    }

    @Test
    @DisplayName("Rejects malformed JSON with line and column tracking")
    void testMalformedJson() {
      String malformed =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "unclosed string
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(malformed))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Malformed JSON at line");
    }

    @Test
    @DisplayName("Rejects missing required envelope fields")
    void testMissingEnvelopeFields() {
      String missing =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(missing))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Missing");
    }

    @Test
    @DisplayName("Rejects duplicate parameter names")
    void testDuplicateParameterNames() {
      String dup =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/dup.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "user", "type": {"kind": "primitive", "name": "int"}, "nullable": false, "optional": false},
              {"name": "user", "type": {"kind": "primitive", "name": "long"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(dup))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Duplicate parameter name in schema: 'user'");
    }

    @Test
    @DisplayName("Rejects unsupported type kind")
    void testUnsupportedTypeKind() {
      String invalidKind =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/kind.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "u", "type": {"kind": "alien-kind"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(invalidKind))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported schema type kind: 'alien-kind'");
    }

    @Test
    @DisplayName("Rejects trailing characters after JSON root")
    void testTrailingGarbage() {
      String garbage =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/t.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [],
            "types": {}
          }
          extra
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(garbage))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unexpected trailing character after JSON root");
    }

    @Test
    @DisplayName("Rejects impossible nullability state on primitive parameter")
    void testImpossibleNullabilityPrimitiveParameter() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/impossible-null.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "count", "type": {"kind": "primitive", "name": "int"}, "nullable": true, "optional": false}
            ],
            "types": {}
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(schema))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Impossible nullability state: primitive type 'int' cannot be nullable for parameter"
                  + " 'count'");
    }

    @Test
    @DisplayName("Rejects impossible nullability state on primitive property in types")
    void testImpossibleNullabilityPrimitiveProperty() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/impossible-null-prop.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "user", "type": {"kind": "class", "name": "com.example.User"}, "nullable": false, "optional": false}
            ],
            "types": {
              "com.example.User": {
                "kind": "record",
                "properties": [
                  {"name": "id", "type": {"kind": "primitive", "name": "long"}, "nullable": true}
                ]
              }
            }
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(schema))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Impossible nullability state: primitive type 'long' cannot be nullable for property"
                  + " 'id'");
    }

    @Test
    @DisplayName("Rejects invalid primitive type name")
    void testInvalidPrimitiveName() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/bad-primitive.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "foo", "type": {"kind": "primitive", "name": "not_a_primitive"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(schema))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid primitive type name: 'not_a_primitive'");
    }

    @Test
    @DisplayName("Rejects blank or invalid parameter name")
    void testInvalidParameterName() {
      String schemaBlank =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/bad-param.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "   ", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(schemaBlank))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Parameter name must not be blank");

      String schemaNewline =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/bad-param2.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "bad\\nname", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false, "optional": false}
            ],
            "types": {}
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(schemaNewline))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid parameter name");
    }

    @Test
    @DisplayName("Rejects blank property name in type definition")
    void testBlankPropertyName() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/blank-prop.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "m", "type": {"kind": "class", "name": "com.example.Model"}, "nullable": false, "optional": false}
            ],
            "types": {
              "com.example.Model": {
                "kind": "record",
                "properties": [
                  {"name": "   ", "type": {"kind": "class", "name": "java.lang.String"}, "nullable": false}
                ]
              }
            }
          }
          """;

      assertThatThrownBy(() -> TypeScriptDeclarationProjector.project(schema))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Property name must not be blank in type 'com.example.Model'");
    }

    @Test
    @DisplayName("Rejects path traversal in deriveDeclarationFilePath")
    void testPathTraversalInTemplateId(@TempDir Path tempDir) {
      assertThatThrownBy(
              () ->
                  TypeScriptDeclarationProjector.deriveDeclarationFilePath(
                      tempDir, "../../secret.vtl"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Path traversal detected in templateId");
    }

    @Test
    @DisplayName("Maps raw and specialized collection and numeric Java classes")
    void testRawAndSpecializedCollections() {
      String schema =
          """
          {
            "format": "viet-template-contract-schema/1",
            "schemaVersion": 1,
            "templateId": "test/collections.vtl",
            "contractFingerprint": "fp:test",
            "parameters": [
              {"name": "rawList", "type": {"kind": "class", "name": "java.util.List"}, "nullable": false, "optional": false},
              {"name": "rawMap", "type": {"kind": "class", "name": "java.util.Map"}, "nullable": false, "optional": false},
              {"name": "rawSet", "type": {"kind": "class", "name": "java.util.Set"}, "nullable": false, "optional": false},
              {
                "name": "arrayList",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.ArrayList",
                  "arguments": [{"kind": "class", "name": "java.lang.String"}]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "hashMap",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.HashMap",
                  "arguments": [
                    {"kind": "class", "name": "java.lang.String"},
                    {"kind": "class", "name": "java.lang.Number"}
                  ]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "hashSet",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.HashSet",
                  "arguments": [{"kind": "class", "name": "java.lang.String"}]
                },
                "nullable": false,
                "optional": false
              },
              {
                "name": "bigDecimal",
                "type": {"kind": "class", "name": "java.math.BigDecimal"},
                "nullable": false,
                "optional": false
              },
              {
                "name": "opt",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.Optional",
                  "arguments": [{"kind": "class", "name": "java.lang.String"}]
                },
                "nullable": false,
                "optional": false
              }
            ],
            "types": {}
          }
          """;

      String dts = TypeScriptDeclarationProjector.project(schema);
      assertThat(dts).contains("rawList: unknown[];");
      assertThat(dts).contains("rawMap: Record<string, unknown>;");
      assertThat(dts).contains("rawSet: Set<unknown>;");
      assertThat(dts).contains("arrayList: string[];");
      assertThat(dts).contains("hashMap: Record<string, number>;");
      assertThat(dts).contains("hashSet: Set<string>;");
      assertThat(dts).contains("bigDecimal: number;");
      assertThat(dts).contains("opt: string | null;");
    }
  }
}
