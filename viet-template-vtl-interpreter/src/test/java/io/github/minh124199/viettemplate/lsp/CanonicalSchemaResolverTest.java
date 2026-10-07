package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalSchemaResolverTest {

  private static final String VALID_SCHEMA =
      """
      {
        "format": "viet-template-contract-schema/1",
        "schemaVersion": 1,
        "templateId": "user-view",
        "parameters": {
          "user": {
            "name": "user",
            "type": { "kind": "named", "name": "UserDto" },
            "required": true,
            "documentation": "Active user entity"
          },
          "isAdmin": {
            "name": "isAdmin",
            "type": { "kind": "primitive", "primitiveKind": "BOOLEAN" },
            "required": false
          }
        },
        "types": {
          "UserDto": {
            "name": "UserDto",
            "properties": {
              "id": {
                "name": "id",
                "type": { "kind": "primitive", "primitiveKind": "LONG" },
                "nullable": false
              },
              "name": {
                "name": "name",
                "type": { "kind": "class", "className": "java.lang.String" },
                "nullable": false
              },
              "roles": {
                "name": "roles",
                "type": {
                  "kind": "parameterized",
                  "rawType": "java.util.List",
                  "typeArguments": [
                    { "kind": "class", "className": "java.lang.String" }
                  ]
                },
                "nullable": true
              }
            }
          }
        }
      }
      """;

  @Test
  @DisplayName("Parse valid canonical schema JSON")
  void testParseValidSchema() {
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema("user-view", VALID_SCHEMA);

    Optional<CanonicalSchema> schemaOpt = resolver.resolveSchema("user-view");
    assertTrue(schemaOpt.isPresent());

    CanonicalSchema schema = schemaOpt.get();
    assertEquals(SchemaFormat.CONTRACT, schema.format());
    assertEquals("user-view", schema.templateId());

    assertEquals(2, schema.parameters().size());
    ParameterDef userParam = schema.parameters().get("user");
    assertNotNull(userParam);
    assertEquals("user", userParam.name());
    assertTrue(userParam.required());
    assertEquals("Active user entity", userParam.documentation());
    assertEquals("UserDto", userParam.type().displayName());

    TypeDef userDto = schema.types().get("UserDto");
    assertNotNull(userDto);
    assertEquals(3, userDto.properties().size());
    PropertyDef nameProp = userDto.properties().get("name");
    assertNotNull(nameProp);
    assertEquals("name", nameProp.name());
    assertFalse(nameProp.nullable());
  }

  @Test
  @DisplayName("Reject invalid envelope format or schema version")
  void testRejectInvalidFormat() {
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();

    String badFormat =
        """
        {
          "format": "wrong-format/2",
          "schemaVersion": 1,
          "templateId": "test",
          "parameters": {},
          "types": {}
        }
        """;
    assertThrows(IllegalArgumentException.class, () -> resolver.registerSchema("bad1", badFormat));

    String badVersion =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 99,
          "templateId": "test",
          "parameters": {},
          "types": {}
        }
        """;
    assertThrows(IllegalArgumentException.class, () -> resolver.registerSchema("bad2", badVersion));
  }

  @Test
  @DisplayName("Resolve members with security filtering")
  void testResolveMembersWithSecurity() {
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema("user-view", VALID_SCHEMA);

    CanonicalSchema schema = resolver.resolveSchema("user-view").orElseThrow();
    TypeRef userType = schema.parameters().get("user").type();

    // Standard policy allows user properties: id, name, roles
    Map<String, PropertyDef> members =
        resolver.getAccessibleProperties("user-view", userType, MemberAccessPolicy.standard());
    assertEquals(3, members.size());
    assertTrue(members.containsKey("id"));
    assertTrue(members.containsKey("name"));
    assertTrue(members.containsKey("roles"));

    // Deny-all policy allows no members
    Map<String, PropertyDef> deniedMembers =
        resolver.getAccessibleProperties("user-view", userType, MemberAccessPolicy.denyAll());
    assertTrue(deniedMembers.isEmpty());
  }

  @Test
  @DisplayName("Find parameter and property line numbers for definition navigation")
  void testFindLineNumbers() {
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema("user-view", VALID_SCHEMA);
    WorkspaceSchemaIndex index = new WorkspaceSchemaIndex(resolver);

    Optional<SchemaProvenance> userProv = index.getParameterProvenance("user-view", "user");
    assertTrue(userProv.isPresent());
    assertTrue(userProv.get().location().isPresent());
    assertTrue(userProv.get().location().get().startLine() >= 5);

    Optional<SchemaProvenance> nameProv =
        index.getPropertyProvenance("user-view", "UserDto", "name");
    assertTrue(nameProv.isPresent());
    assertTrue(nameProv.get().location().isPresent());
    assertTrue(
        nameProv.get().location().get().startLine() > userProv.get().location().get().startLine());
  }

  @Test
  @DisplayName("Register schema from file and sibling schema discovery")
  void testRegisterSchemaFromFile(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("order.vt");
    Files.writeString(templateFile, "Hello $order.id");

    Path schemaFile = tempDir.resolve("order.vt-schema.json");
    String schemaContent =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "order",
          "parameters": {
            "order": {
              "name": "order",
              "type": { "kind": "named", "name": "OrderDto" },
              "required": true
            }
          },
          "types": {
            "OrderDto": {
              "name": "OrderDto",
              "properties": {
                "id": {
                  "name": "id",
                  "type": { "kind": "primitive", "primitiveKind": "LONG" }
                }
              }
            }
          }
        }
        """;
    Files.writeString(schemaFile, schemaContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    // Resolve using template file URI
    String fileUri = templateFile.toUri().toString();
    Optional<CanonicalSchema> resolved = resolver.resolveSchema(fileUri);
    assertTrue(resolved.isPresent());
    assertEquals("order", resolved.get().templateId());
    assertTrue(resolved.get().parameters().containsKey("order"));
  }
}
