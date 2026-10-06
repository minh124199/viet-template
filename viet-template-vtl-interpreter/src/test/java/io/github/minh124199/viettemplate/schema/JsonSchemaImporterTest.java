package io.github.minh124199.viettemplate.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonSchemaImporterTest {

  private final JsonSchemaImporter importer = new JsonSchemaImporter();

  @Test
  @DisplayName("Import flat object with required and optional properties")
  void testFlatObject() {
    String json =
        """
        {
          "$id": "user-schema",
          "title": "User",
          "type": "object",
          "properties": {
            "id": {
              "type": "integer",
              "description": "Unique identifier"
            },
            "username": {
              "type": "string"
            }
          },
          "required": ["id"]
        }
        """;

    SchemaImportResult result = importer.importString(json, "users/user.vtl");
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.schemas()).containsKey("users/user.vtl");

    CanonicalSchema schema = result.schemas().get("users/user.vtl");
    assertThat(schema.templateId()).isEqualTo("users/user.vtl");
    assertThat(schema.format()).isEqualTo(SchemaFormat.JSON_SCHEMA);
    assertThat(schema.contractFingerprint()).startsWith("jsonschema:v1:");

    assertThat(schema.parameters()).containsKeys("id", "username");

    ParameterDef idParam = schema.parameters().get("id");
    assertThat(idParam.name()).isEqualTo("id");
    assertThat(idParam.type()).isEqualTo(new PrimitiveTypeRef("integer"));
    assertThat(idParam.optional()).isFalse();
    assertThat(idParam.required()).isTrue();
    assertThat(idParam.nullable()).isFalse();
    assertThat(idParam.documentation()).isEqualTo("Unique identifier");

    ParameterDef usernameParam = schema.parameters().get("username");
    assertThat(usernameParam.name()).isEqualTo("username");
    assertThat(usernameParam.type()).isEqualTo(new PrimitiveTypeRef("string"));
    assertThat(usernameParam.optional()).isTrue();
    assertThat(usernameParam.required()).isFalse();
    assertThat(usernameParam.nullable()).isFalse();
  }

  @Test
  @DisplayName("Import distinguishes all 4 states of required/optional and nullable/non-null")
  void testFourStatesOfNullabilityAndOptionality() {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "req_nonnull": { "type": "string" },
            "req_nullable_flag": { "type": "string", "nullable": true },
            "req_nullable_type": { "type": ["string", "null"] },
            "opt_nonnull": { "type": "integer" },
            "opt_nullable_flag": { "type": "integer", "nullable": true },
            "opt_nullable_type": { "type": ["integer", "null"] }
          },
          "required": ["req_nonnull", "req_nullable_flag", "req_nullable_type"]
        }
        """;

    SchemaImportResult result = importer.importString(json, "test-nullability");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("test-nullability");

    // 1. Required Non-null
    ParameterDef reqNonNull = schema.parameters().get("req_nonnull");
    assertThat(reqNonNull.required()).isTrue();
    assertThat(reqNonNull.optional()).isFalse();
    assertThat(reqNonNull.nullable()).isFalse();
    assertThat(reqNonNull.type()).isEqualTo(new PrimitiveTypeRef("string"));

    // 2. Required Nullable (via nullable: true)
    ParameterDef reqNullableFlag = schema.parameters().get("req_nullable_flag");
    assertThat(reqNullableFlag.required()).isTrue();
    assertThat(reqNullableFlag.optional()).isFalse();
    assertThat(reqNullableFlag.nullable()).isTrue();
    assertThat(reqNullableFlag.type()).isEqualTo(new PrimitiveTypeRef("string"));

    // 2b. Required Nullable (via type: ["string", "null"])
    ParameterDef reqNullableType = schema.parameters().get("req_nullable_type");
    assertThat(reqNullableType.required()).isTrue();
    assertThat(reqNullableType.optional()).isFalse();
    assertThat(reqNullableType.nullable()).isTrue();
    assertThat(reqNullableType.type()).isEqualTo(new PrimitiveTypeRef("string"));

    // 3. Optional Non-null
    ParameterDef optNonNull = schema.parameters().get("opt_nonnull");
    assertThat(optNonNull.required()).isFalse();
    assertThat(optNonNull.optional()).isTrue();
    assertThat(optNonNull.nullable()).isFalse();
    assertThat(optNonNull.type()).isEqualTo(new PrimitiveTypeRef("integer"));

    // 4. Optional Nullable (via nullable: true)
    ParameterDef optNullableFlag = schema.parameters().get("opt_nullable_flag");
    assertThat(optNullableFlag.required()).isFalse();
    assertThat(optNullableFlag.optional()).isTrue();
    assertThat(optNullableFlag.nullable()).isTrue();
    assertThat(optNullableFlag.type()).isEqualTo(new PrimitiveTypeRef("integer"));

    // 4b. Optional Nullable (via type: ["integer", "null"])
    ParameterDef optNullableType = schema.parameters().get("opt_nullable_type");
    assertThat(optNullableType.required()).isFalse();
    assertThat(optNullableType.optional()).isTrue();
    assertThat(optNullableType.nullable()).isTrue();
    assertThat(optNullableType.type()).isEqualTo(new PrimitiveTypeRef("integer"));
  }

  @Test
  @DisplayName("Import array items into ArrayTypeRef")
  void testArrayItems() {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "tags": {
              "type": "array",
              "items": { "type": "string" }
            }
          }
        }
        """;

    SchemaImportResult result = importer.importString(json, "array-schema");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("array-schema");
    ParameterDef tags = schema.parameters().get("tags");
    assertThat(tags.type()).isEqualTo(new ArrayTypeRef(new PrimitiveTypeRef("string")));
    assertThat(tags.type().displayName()).isEqualTo("string[]");
  }

  @Test
  @DisplayName("Import inline nested objects and register in types map")
  void testNestedObjects() {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "address": {
              "type": "object",
              "title": "Address",
              "properties": {
                "street": { "type": "string" },
                "city": { "type": "string" }
              },
              "required": ["street"]
            }
          }
        }
        """;

    SchemaImportResult result = importer.importString(json, "nested-schema");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("nested-schema");
    ParameterDef addressParam = schema.parameters().get("address");
    assertThat(addressParam.type()).isEqualTo(new NamedTypeRef("Address", List.of()));

    assertThat(schema.types()).containsKey("Address");
    TypeDef addressType = schema.types().get("Address");
    assertThat(addressType.name()).isEqualTo("Address");
    assertThat(addressType.kind()).isEqualTo("object");
    assertThat(addressType.properties()).containsKeys("city", "street");

    PropertyDef street = addressType.properties().get("street");
    assertThat(street.required()).isTrue();
    assertThat(street.optional()).isFalse();

    PropertyDef city = addressType.properties().get("city");
    assertThat(city.required()).isFalse();
    assertThat(city.optional()).isTrue();
  }

  @Test
  @DisplayName("Import enum into EnumTypeRef and register in types map")
  void testEnum() {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "status": {
              "title": "Status",
              "enum": ["PENDING", "ACTIVE", "ARCHIVED"]
            }
          }
        }
        """;

    SchemaImportResult result = importer.importString(json, "enum-schema");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("enum-schema");
    ParameterDef statusParam = schema.parameters().get("status");
    assertThat(statusParam.type())
        .isEqualTo(new EnumTypeRef("Status", List.of("PENDING", "ACTIVE", "ARCHIVED")));
    assertThat(statusParam.type().displayName()).isEqualTo("Status");

    assertThat(schema.types()).containsKey("Status");
    TypeDef enumDef = schema.types().get("Status");
    assertThat(enumDef.kind()).isEqualTo("enum");
    assertThat(enumDef.enumConstants()).containsExactly("PENDING", "ACTIVE", "ARCHIVED");
  }

  @Test
  @DisplayName("Import map via additionalProperties")
  void testMapViaAdditionalProperties() {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "metadata": {
              "type": "object",
              "additionalProperties": { "type": "string" }
            }
          }
        }
        """;

    SchemaImportResult result = importer.importString(json, "map-schema");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("map-schema");
    ParameterDef metadataParam = schema.parameters().get("metadata");
    assertThat(metadataParam.type())
        .isEqualTo(new MapTypeRef(new PrimitiveTypeRef("string"), new PrimitiveTypeRef("string")));
    assertThat(metadataParam.type().displayName()).isEqualTo("Map<string, string>");
  }

  @Test
  @DisplayName("Import local $defs and definitions references")
  void testLocalDefsAndDefinitions() {
    String jsonDefs =
        """
        {
          "$defs": {
            "Customer": {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
          },
          "type": "object",
          "properties": {
            "customer": { "$ref": "#/$defs/Customer" }
          }
        }
        """;

    SchemaImportResult resDefs = importer.importString(jsonDefs, "defs-schema");
    assertThat(resDefs.hasErrors()).isFalse();
    CanonicalSchema schemaDefs = resDefs.schemas().get("defs-schema");
    assertThat(schemaDefs.parameters().get("customer").type())
        .isEqualTo(new NamedTypeRef("Customer", List.of()));
    assertThat(schemaDefs.types()).containsKey("Customer");

    String jsonDefinitions =
        """
        {
          "definitions": {
            "Account": {
              "type": "object",
              "properties": {
                "balance": { "type": "number" }
              }
            }
          },
          "type": "object",
          "properties": {
            "account": { "$ref": "#/definitions/Account" }
          }
        }
        """;

    SchemaImportResult resOldDefs = importer.importString(jsonDefinitions, "definitions-schema");
    assertThat(resOldDefs.hasErrors()).isFalse();
    CanonicalSchema schemaOldDefs = resOldDefs.schemas().get("definitions-schema");
    assertThat(schemaOldDefs.parameters().get("account").type())
        .isEqualTo(new NamedTypeRef("Account", List.of()));
    assertThat(schemaOldDefs.types()).containsKey("Account");
  }

  @Test
  @DisplayName("Recursive and self-referential schemas terminate safely")
  void testRecursiveAndSelfReferentialSchema() {
    String jsonRecursiveDefs =
        """
        {
          "$defs": {
            "Node": {
              "type": "object",
              "properties": {
                "value": { "type": "string" },
                "next": { "$ref": "#/$defs/Node" }
              }
            }
          },
          "type": "object",
          "properties": {
            "head": { "$ref": "#/$defs/Node" }
          }
        }
        """;

    SchemaImportResult result = importer.importString(jsonRecursiveDefs, "recursive-node");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("recursive-node");
    assertThat(schema.parameters().get("head").type())
        .isEqualTo(new NamedTypeRef("Node", List.of()));
    assertThat(schema.types()).containsKey("Node");

    TypeDef nodeType = schema.types().get("Node");
    assertThat(nodeType.properties().get("next").type())
        .isEqualTo(new NamedTypeRef("Node", List.of()));

    // Self-referential root schema
    String jsonRootRecursive =
        """
        {
          "title": "TreeNode",
          "type": "object",
          "properties": {
            "value": { "type": "string" },
            "left": { "$ref": "#" },
            "right": { "$ref": "#" }
          }
        }
        """;

    SchemaImportResult rootResult = importer.importString(jsonRootRecursive, "tree-node");
    assertThat(rootResult.hasErrors()).isFalse();
    CanonicalSchema rootSchema = rootResult.schemas().get("tree-node");
    assertThat(rootSchema.parameters().get("left").type())
        .isEqualTo(new NamedTypeRef("TreeNode", List.of()));
    assertThat(rootSchema.parameters().get("right").type())
        .isEqualTo(new NamedTypeRef("TreeNode", List.of()));
    assertThat(rootSchema.types()).containsKey("TreeNode");
  }

  @Test
  @DisplayName("Unresolved local reference emits SCHEMA_UNRESOLVED_REFERENCE error")
  void testUnresolvedReferenceDiagnostic() {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "user": { "$ref": "#/$defs/NonExistentType" }
          }
        }
        """;

    SchemaImportResult result = importer.importString(json, "unresolved-test");
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).isNotEmpty();

    SchemaDiagnostic diag =
        result.diagnostics().stream()
            .filter(d -> d.code().equals(JsonSchemaImporter.CODE_UNRESOLVED_REFERENCE))
            .findFirst()
            .orElseThrow();

    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.ERROR);
    assertThat(diag.message()).contains("NonExistentType");
    assertThat(diag.line()).isGreaterThan(0);
    assertThat(diag.column()).isGreaterThan(0);
  }

  @Test
  @DisplayName("Remote HTTP reference is rejected offline without network calls")
  void testRemoteHttpRefRejection() {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "remoteUser": { "$ref": "https://example.com/schemas/remote-user.json" }
          }
        }
        """;

    SchemaImportResult result = importer.importString(json, "remote-ref-test");
    assertThat(result.hasErrors()).isTrue();

    SchemaDiagnostic diag =
        result.diagnostics().stream()
            .filter(d -> d.code().equals(JsonSchemaImporter.CODE_UNSUPPORTED_REMOTE_REF))
            .findFirst()
            .orElseThrow();

    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.ERROR);
    assertThat(diag.message()).contains("https://example.com/schemas/remote-user.json");
    assertThat(diag.suggestedAction()).contains("local");
  }

  @Test
  @DisplayName("Malformed JSON syntax emits JSON_SCHEMA_SYNTAX_ERROR with position")
  void testMalformedJsonSyntaxError() {
    String malformed =
        """
        {
          "type": "object",
          "properties": {
            "id": { "type": "integer"
        """;

    SchemaImportResult result = importer.importString(malformed, "malformed-test");
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.schemas()).isEmpty();

    SchemaDiagnostic diag =
        result.diagnostics().stream()
            .filter(d -> d.code().equals(JsonSchemaImporter.CODE_SYNTAX_ERROR))
            .findFirst()
            .orElseThrow();

    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.ERROR);
    assertThat(diag.line()).isGreaterThan(0);
    assertThat(diag.column()).isGreaterThan(0);
  }

  @Test
  @DisplayName("Parameters and types are deterministically sorted regardless of JSON ordering")
  void testDeterministicOrdering() {
    String json =
        """
        {
          "$defs": {
            "ZetaType": { "type": "object", "properties": { "x": { "type": "string" } } },
            "AlphaType": { "type": "object", "properties": { "y": { "type": "string" } } }
          },
          "type": "object",
          "properties": {
            "zebra": { "type": "string" },
            "apple": { "type": "string" },
            "mango": { "type": "string" }
          }
        }
        """;

    SchemaImportResult result = importer.importString(json, "deterministic-test");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("deterministic-test");
    assertThat(schema.parameters().keySet()).containsExactly("apple", "mango", "zebra");
    assertThat(schema.types().keySet()).containsExactly("AlphaType", "ZetaType");
  }

  @Test
  @DisplayName("File-based schema import through SchemaSource and SchemaImportRequest")
  void testFileImportThroughSchemaRequest(@TempDir Path tempDir) throws IOException {
    Path schemaFile = tempDir.resolve("order.schema.json");
    String json =
        """
        {
          "title": "Order",
          "type": "object",
          "properties": {
            "orderId": { "type": "string" },
            "amount": { "type": "number" }
          },
          "required": ["orderId"]
        }
        """;
    Files.writeString(schemaFile, json, StandardCharsets.UTF_8);

    SchemaSource source =
        new SchemaSource(schemaFile, SchemaFormat.JSON_SCHEMA, "templates/order.vtl");
    SchemaImportRequest request = new SchemaImportRequest(List.of(source));

    SchemaImportResult result = importer.importSchemas(request);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.schemas()).containsKey("templates/order.vtl");

    CanonicalSchema schema = result.schemas().get("templates/order.vtl");
    assertThat(schema.parameters()).containsKeys("orderId", "amount");
    assertThat(schema.parameters().get("orderId").required()).isTrue();
    assertThat(schema.parameters().get("amount").required()).isFalse();
  }
}
