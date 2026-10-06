package io.github.minh124199.viettemplate.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.aot.TypeScriptDeclarationProjector;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.explanation.TemplateExplainRequest;
import io.github.minh124199.viettemplate.explanation.TemplateExplainer;
import io.github.minh124199.viettemplate.explanation.TemplateExplanation;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.validation.TemplateValidationRequest;
import io.github.minh124199.viettemplate.validation.TemplateValidationResult;
import io.github.minh124199.viettemplate.validation.TemplateValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossFormatSemanticParityTest {

  public enum Status {
    ACTIVE,
    INACTIVE,
    PENDING
  }

  public record Address(String street, String city) {}

  public record User(
      int id,
      String name,
      String email,
      String nickname,
      Integer age,
      List<String> tags,
      Map<String, String> attributes,
      Status status,
      Address address) {}

  public record UserContract(User user) {}

  private static final String JSON_SCHEMA =
      """
      {
        "$schema": "http://json-schema.org/draft-07/schema#",
        "$id": "user.vtl",
        "definitions": {
          "Status": {
            "type": "string",
            "enum": ["ACTIVE", "INACTIVE", "PENDING"]
          },
          "Address": {
            "type": "object",
            "properties": {
              "street": { "type": "string" },
              "city": { "type": "string" }
            },
            "required": ["street", "city"]
          },
          "User": {
            "type": "object",
            "properties": {
              "id": { "type": "integer" },
              "name": { "type": "string" },
              "email": { "type": ["string", "null"] },
              "nickname": { "type": "string" },
              "age": { "type": ["integer", "null"] },
              "tags": {
                "type": "array",
                "items": { "type": "string" }
              },
              "attributes": {
                "type": "object",
                "additionalProperties": { "type": "string" }
              },
              "status": { "$ref": "#/definitions/Status" },
              "address": { "$ref": "#/definitions/Address" }
            },
            "required": ["id", "name", "email", "tags", "attributes", "status", "address"]
          }
        },
        "type": "object",
        "properties": {
          "user": { "$ref": "#/definitions/User" }
        },
        "required": ["user"]
      }
      """;

  private static final String TYPESCRIPT_DECLARATION =
      """
      export type Status = "ACTIVE" | "INACTIVE" | "PENDING";

      export interface Address {
        street: string;
        city: string;
      }

      export interface User {
        id: number;
        name: string;
        email: string | null;
        nickname?: string;
        age?: number | null;
        tags: string[];
        attributes: Record<string, string>;
        status: Status;
        address: Address;
      }

      export interface TemplateParameters {
        user: User;
      }
      """;

  @Test
  @DisplayName("Canonical normalization parity across Java, JSON Schema, and TypeScript")
  void testCanonicalNormalizationParity(@TempDir Path tempDir) throws IOException {
    JavaModelSchemaImporter javaImporter = new JavaModelSchemaImporter();
    CanonicalSchema javaSchema =
        javaImporter.importRecord(UserContract.class, "user.vtl").schemas().get("user.vtl");
    assertThat(javaSchema).isNotNull();

    JsonSchemaImporter jsonImporter = new JsonSchemaImporter();
    Path jsonFile = tempDir.resolve("user.schema.json");
    Files.writeString(jsonFile, JSON_SCHEMA);
    CanonicalSchema jsonSchema =
        jsonImporter
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(
                            jsonFile, SchemaFormat.JSON_SCHEMA, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");
    assertThat(jsonSchema).isNotNull();

    TypeScriptSchemaImporter tsImporter = new TypeScriptSchemaImporter();
    Path tsFile = tempDir.resolve("user.d.ts");
    Files.writeString(tsFile, TYPESCRIPT_DECLARATION);
    CanonicalSchema tsSchema =
        tsImporter
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(tsFile, SchemaFormat.TYPESCRIPT, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");
    assertThat(tsSchema).isNotNull();

    // Verify root parameters match
    assertThat(javaSchema.parameters()).containsKey("user");
    assertThat(jsonSchema.parameters()).containsKey("user");
    assertThat(tsSchema.parameters()).containsKey("user");

    // Verify declared types contain User, Address, Status
    assertThat(jsonSchema.types()).containsKey("User");
    assertThat(jsonSchema.types()).containsKey("Address");
    assertThat(jsonSchema.types()).containsKey("Status");

    assertThat(tsSchema.types()).containsKey("User");
    assertThat(tsSchema.types()).containsKey("Address");
    assertThat(tsSchema.types()).containsKey("Status");

    // Verify 4 distinct optionality / nullability states on User properties in TypeScript
    var tsUserProps = tsSchema.types().get("User").properties();
    // 1. required non-null: name
    assertThat(tsUserProps.get("name").optional()).isFalse();
    assertThat(tsUserProps.get("name").nullable()).isFalse();

    // 2. required nullable: email
    assertThat(tsUserProps.get("email").optional()).isFalse();
    assertThat(tsUserProps.get("email").nullable()).isTrue();

    // 3. optional non-null: nickname
    assertThat(tsUserProps.get("nickname").optional()).isTrue();
    assertThat(tsUserProps.get("nickname").nullable()).isFalse();

    // 4. optional nullable: age
    assertThat(tsUserProps.get("age").optional()).isTrue();
    assertThat(tsUserProps.get("age").nullable()).isTrue();
  }

  @Test
  @DisplayName("Validation parity: valid template succeeds across all three schema formats")
  void testValidationParityValidTemplate(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("user.vtl");
    String validTemplate =
        "Hello $user.name! Nick: $!user.nickname Street: $user.address.street Status: $user.status";
    Files.writeString(templateFile, validTemplate);

    CanonicalSchema javaSchema =
        new JavaModelSchemaImporter()
            .importRecord(UserContract.class, "user.vtl")
            .schemas()
            .get("user.vtl");

    Path jsonFile = tempDir.resolve("user.schema.json");
    Files.writeString(jsonFile, JSON_SCHEMA);
    CanonicalSchema jsonSchema =
        new JsonSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(
                            jsonFile, SchemaFormat.JSON_SCHEMA, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    Path tsFile = tempDir.resolve("user.d.ts");
    Files.writeString(tsFile, TYPESCRIPT_DECLARATION);
    CanonicalSchema tsSchema =
        new TypeScriptSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(tsFile, SchemaFormat.TYPESCRIPT, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    TemplateValidator validator = TemplateValidator.create();

    for (Map.Entry<String, CanonicalSchema> entry :
        Map.of("Java", javaSchema, "JSON Schema", jsonSchema, "TypeScript", tsSchema).entrySet()) {
      TemplateValidationRequest req =
          TemplateValidationRequest.builder()
              .sourceDirectory(tempDir)
              .typeCheckingMode(TypeCheckingMode.ERROR)
              .canonicalSchema(TemplateId.of("user.vtl"), entry.getValue())
              .build();

      TemplateValidationResult res = validator.validate(req);
      assertThat(res.success())
          .as(
              "Valid template must succeed for %s schema, but got errors: %s",
              entry.getKey(), res.diagnostics())
          .isTrue();
      assertThat(res.errorCount()).isZero();
    }
  }

  @Test
  @DisplayName("Validation parity: invalid property fails identically across all three formats")
  void testValidationParityInvalidProperty(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("user.vtl");
    String invalidTemplate = "$user.nonExistentField";
    Files.writeString(templateFile, invalidTemplate);

    CanonicalSchema javaSchema =
        new JavaModelSchemaImporter()
            .importRecord(UserContract.class, "user.vtl")
            .schemas()
            .get("user.vtl");

    Path jsonFile = tempDir.resolve("user.schema.json");
    Files.writeString(jsonFile, JSON_SCHEMA);
    CanonicalSchema jsonSchema =
        new JsonSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(
                            jsonFile, SchemaFormat.JSON_SCHEMA, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    Path tsFile = tempDir.resolve("user.d.ts");
    Files.writeString(tsFile, TYPESCRIPT_DECLARATION);
    CanonicalSchema tsSchema =
        new TypeScriptSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(tsFile, SchemaFormat.TYPESCRIPT, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    TemplateValidator validator = TemplateValidator.create();

    for (Map.Entry<String, CanonicalSchema> entry :
        Map.of("Java", javaSchema, "JSON Schema", jsonSchema, "TypeScript", tsSchema).entrySet()) {
      TemplateValidationRequest req =
          TemplateValidationRequest.builder()
              .sourceDirectory(tempDir)
              .typeCheckingMode(TypeCheckingMode.ERROR)
              .canonicalSchema(TemplateId.of("user.vtl"), entry.getValue())
              .build();

      TemplateValidationResult res = validator.validate(req);
      assertThat(res.success()).as("Must fail for %s", entry.getKey()).isFalse();
      assertThat(res.errorCount()).as("Expected 1 error for %s", entry.getKey()).isEqualTo(1);

      TemplateAotDiagnostic diag = res.diagnostics().getFirst();
      assertThat(diag.code().id()).isEqualTo("2104"); // PROPERTY_NOT_FOUND
      assertThat(diag.message()).contains("nonExistentField");
    }
  }

  @Test
  @DisplayName("Validation parity: typo suggestion produces matching suggestion across all formats")
  void testValidationParityTypoSuggestion(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("user.vtl");
    String typoTemplate = "$user.nmae";
    Files.writeString(templateFile, typoTemplate);

    CanonicalSchema javaSchema =
        new JavaModelSchemaImporter()
            .importRecord(UserContract.class, "user.vtl")
            .schemas()
            .get("user.vtl");

    Path jsonFile = tempDir.resolve("user.schema.json");
    Files.writeString(jsonFile, JSON_SCHEMA);
    CanonicalSchema jsonSchema =
        new JsonSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(
                            jsonFile, SchemaFormat.JSON_SCHEMA, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    Path tsFile = tempDir.resolve("user.d.ts");
    Files.writeString(tsFile, TYPESCRIPT_DECLARATION);
    CanonicalSchema tsSchema =
        new TypeScriptSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(tsFile, SchemaFormat.TYPESCRIPT, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    TemplateValidator validator = TemplateValidator.create();

    for (Map.Entry<String, CanonicalSchema> entry :
        Map.of("Java", javaSchema, "JSON Schema", jsonSchema, "TypeScript", tsSchema).entrySet()) {
      TemplateValidationRequest req =
          TemplateValidationRequest.builder()
              .sourceDirectory(tempDir)
              .typeCheckingMode(TypeCheckingMode.ERROR)
              .canonicalSchema(TemplateId.of("user.vtl"), entry.getValue())
              .build();

      TemplateValidationResult res = validator.validate(req);
      assertThat(res.success()).isFalse();
      TemplateAotDiagnostic diag = res.diagnostics().getFirst();
      assertThat(diag.code().id()).isEqualTo("2104");
      assertThat(diag.message().toLowerCase()).contains("did you mean 'name'");
    }
  }

  @Test
  @DisplayName("Explanation parity: inferred type is String for $user.name across all formats")
  void testExplanationParity(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("user.vtl");
    Files.writeString(templateFile, "$user.name");

    CanonicalSchema javaSchema =
        new JavaModelSchemaImporter()
            .importRecord(UserContract.class, "user.vtl")
            .schemas()
            .get("user.vtl");

    Path jsonFile = tempDir.resolve("user.schema.json");
    Files.writeString(jsonFile, JSON_SCHEMA);
    CanonicalSchema jsonSchema =
        new JsonSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(
                            jsonFile, SchemaFormat.JSON_SCHEMA, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    Path tsFile = tempDir.resolve("user.d.ts");
    Files.writeString(tsFile, TYPESCRIPT_DECLARATION);
    CanonicalSchema tsSchema =
        new TypeScriptSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(tsFile, SchemaFormat.TYPESCRIPT, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    TemplateExplainer explainer = TemplateExplainer.create();

    for (Map.Entry<String, CanonicalSchema> entry :
        Map.of("Java", javaSchema, "JSON Schema", jsonSchema, "TypeScript", tsSchema).entrySet()) {
      TemplateExplainRequest req =
          TemplateExplainRequest.builder()
              .sourceDirectory(tempDir)
              .canonicalSchema(TemplateId.of("user.vtl"), entry.getValue())
              .build();

      TemplateExplanation exp = explainer.explain(req);
      assertThat(exp.success()).isTrue();
      assertThat(exp.templates()).hasSize(1);
      var single = exp.templates().getFirst();
      assertThat(single.expressions()).isNotEmpty();
      var expr = single.expressions().getFirst();
      assertThat(expr.inferredType()).contains("String");
    }
  }

  @Test
  @DisplayName("TypeScript projection round-trip from CanonicalSchema preserves contracts")
  void testTypeScriptProjectionFromCanonicalSchema(@TempDir Path tempDir) throws IOException {
    Path tsFile = tempDir.resolve("user.d.ts");
    Files.writeString(tsFile, TYPESCRIPT_DECLARATION);
    CanonicalSchema tsSchema =
        new TypeScriptSchemaImporter()
            .importSchemas(
                new SchemaImportRequest(
                    List.of(
                        new SchemaSource(tsFile, SchemaFormat.TYPESCRIPT, Optional.of("user.vtl"))),
                    false,
                    false))
            .schemas()
            .get("user.vtl");

    String projectedDts = TypeScriptDeclarationProjector.project(tsSchema);
    assertThat(projectedDts).contains("// Generated by Viet Template M28.");
    assertThat(projectedDts)
        .contains("export type Status = \"ACTIVE\" | \"INACTIVE\" | \"PENDING\";");
    assertThat(projectedDts).contains("export interface Address {");
    assertThat(projectedDts).contains("  city: string;");
    assertThat(projectedDts).contains("  street: string;");
    assertThat(projectedDts).contains("export interface User {");
    assertThat(projectedDts).contains("  address: Address;");
    assertThat(projectedDts).contains("  age?: number | null;");
    assertThat(projectedDts).contains("  attributes: Record<string, string>;");
    assertThat(projectedDts).contains("  email: string | null;");
    assertThat(projectedDts).contains("  id: number;");
    assertThat(projectedDts).contains("  name: string;");
    assertThat(projectedDts).contains("  nickname?: string;");
    assertThat(projectedDts).contains("  status: Status;");
    assertThat(projectedDts).contains("  tags: string[];");
    assertThat(projectedDts).contains("export interface TemplateParameters {");
    assertThat(projectedDts).contains("  user: User;");
  }
}
