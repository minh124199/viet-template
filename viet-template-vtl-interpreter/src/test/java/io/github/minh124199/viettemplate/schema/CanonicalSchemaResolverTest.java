package io.github.minh124199.viettemplate.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalSchemaResolverTest {

  @Test
  @DisplayName("Resolve sibling *.schema.json for a template file")
  void testResolveSiblingJsonSchema(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("user.vtl");
    Files.writeString(templateFile, "Hello $user.name");

    Path schemaFile = tempDir.resolve("user.schema.json");
    String jsonSchema =
        """
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "title": "UserTemplate",
          "type": "object",
          "properties": {
            "user": {
              "$ref": "#/definitions/User"
            }
          },
          "required": ["user"],
          "definitions": {
            "User": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "age": { "type": "integer" }
              },
              "required": ["name"]
            }
          }
        }
        """;
    Files.writeString(schemaFile, jsonSchema);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    Optional<CanonicalSchema> resolved =
        resolver.resolveSchema(TemplateId.of("user.vtl"), templateFile);

    assertTrue(resolved.isPresent());
    CanonicalSchema schema = resolved.get();
    assertEquals(SchemaFormat.JSON_SCHEMA, schema.format());
    assertTrue(schema.parameters().containsKey("user"));
    assertTrue(schema.types().containsKey("User"));

    Optional<TypeRef> userType = resolver.resolveReceiverType("user.vtl", "user", List.of("name"));
    assertTrue(userType.isPresent());
    assertEquals("string", userType.get().displayName());

    Map<String, PropertyDef> props =
        resolver.getAccessibleProperties(
            "user.vtl", new NamedTypeRef("User"), MemberAccessPolicy.standard());
    assertEquals(2, props.size());
    assertTrue(props.containsKey("name"));
    assertTrue(props.containsKey("age"));
  }

  @Test
  @DisplayName("Resolve sibling *.d.ts for a template file")
  void testResolveSiblingTypeScript(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("order.vtl");
    Files.writeString(templateFile, "Order $order.id for $order.customer.email");

    Path dtsFile = tempDir.resolve("order.d.ts");
    String dtsContent =
        """
        export interface Customer {
          email: string;
        }

        export interface Order {
          id: string;
          customer: Customer;
        }

        export interface TemplateParameters {
          order: Order;
        }
        """;
    Files.writeString(dtsFile, dtsContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    Optional<CanonicalSchema> resolved =
        resolver.resolveSchema(TemplateId.of("order.vtl"), templateFile);

    assertTrue(resolved.isPresent());
    CanonicalSchema schema = resolved.get();
    assertEquals(SchemaFormat.TYPESCRIPT, schema.format());
    assertTrue(schema.parameters().containsKey("order"));
    assertTrue(schema.types().containsKey("Order"));
    assertTrue(schema.types().containsKey("Customer"));

    Optional<TypeRef> emailType =
        resolver.resolveReceiverType("order.vtl", "order", List.of("customer", "email"));
    assertTrue(emailType.isPresent());
    assertEquals("string", emailType.get().displayName());
  }

  @Test
  @DisplayName("Resolve sibling *.vt-schema.json for a template file")
  void testResolveSiblingVtSchema(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("invoice.vtl");
    Files.writeString(templateFile, "Invoice $invoice.amount");

    Path vtSchemaFile = tempDir.resolve("invoice.vt-schema.json");
    String vtSchemaContent =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "invoice.vtl",
          "parameters": [
            {
              "name": "invoice",
              "type": { "kind": "named", "name": "Invoice" },
              "nullable": false
            }
          ],
          "types": {
            "Invoice": {
              "kind": "record",
              "properties": [
                {
                  "name": "amount",
                  "type": { "kind": "primitive", "primitiveKind": "double" },
                  "nullable": false
                }
              ]
            }
          }
        }
        """;
    Files.writeString(vtSchemaFile, vtSchemaContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    Optional<CanonicalSchema> resolved =
        resolver.resolveSchema(TemplateId.of("invoice.vtl"), templateFile);

    assertTrue(resolved.isPresent());
    CanonicalSchema schema = resolved.get();
    assertEquals(SchemaFormat.CONTRACT, schema.format());
    assertTrue(schema.parameters().containsKey("invoice"));
    assertTrue(schema.types().containsKey("Invoice"));
  }

  @Test
  @DisplayName("Resolve from configured schema directory")
  void testResolveFromSchemaDirectory(@TempDir Path tempDir) throws IOException {
    Path templateDir = tempDir.resolve("templates");
    Path schemaDir = tempDir.resolve("schemas");
    Files.createDirectories(templateDir);
    Files.createDirectories(schemaDir);

    Path templateFile = templateDir.resolve("report.vtl");
    Files.writeString(templateFile, "Report $report.title");

    Path dtsFile = schemaDir.resolve("report.d.ts");
    String dtsContent =
        """
        export interface Report {
          title: string;
        }
        export interface TemplateParameters {
          report: Report;
        }
        """;
    Files.writeString(dtsFile, dtsContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.setSchemaDirectory(schemaDir);

    Optional<CanonicalSchema> resolved =
        resolver.resolveSchema(TemplateId.of("report.vtl"), templateFile);
    assertTrue(resolved.isPresent());
    assertEquals(SchemaFormat.TYPESCRIPT, resolved.get().format());
    assertTrue(resolved.get().parameters().containsKey("report"));
  }
}
