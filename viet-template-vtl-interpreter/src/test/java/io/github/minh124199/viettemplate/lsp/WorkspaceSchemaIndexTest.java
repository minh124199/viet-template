package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSchemaIndexTest {

  @Test
  @DisplayName("Record and retrieve template-to-schema dependencies")
  void testRecordDependency(@TempDir Path tempDir) {
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex index = new WorkspaceSchemaIndex(resolver);

    Path schemaPath = tempDir.resolve("user.schema.json");
    String templateUri = "file:///workspace/user-view.vtl";

    index.recordDependency(templateUri, schemaPath);
    Optional<Path> associated = index.getAssociatedSchemaPath(templateUri);

    assertTrue(associated.isPresent());
    assertEquals(schemaPath.toAbsolutePath().normalize(), associated.get());
  }

  @Test
  @DisplayName("Incremental invalidation and affected templates notification on schema change")
  void testOnSchemaChanged(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("product.vtl");
    Files.writeString(templateFile, "Product: $item.name");

    Path schemaFile = tempDir.resolve("product.schema.json");
    String jsonSchemaV1 =
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "title": "product",
          "type": "object",
          "properties": {
            "item": {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
          }
        }
        """;
    Files.writeString(schemaFile, jsonSchemaV1);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchemaFile(schemaFile);
    WorkspaceSchemaIndex index = new WorkspaceSchemaIndex(resolver);

    String templateUri = templateFile.toUri().toString();
    index.recordDependency(templateUri, schemaFile);

    // Initial provenance cached
    Optional<SchemaProvenance> provV1 = index.getParameterProvenance(templateUri, "item");
    assertTrue(provV1.isPresent());

    // Update schema file on disk
    String jsonSchemaV2 =
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "title": "product",
          "type": "object",
          "properties": {
            "item": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "price": { "type": "number" }
              }
            }
          }
        }
        """;
    Files.writeString(schemaFile, jsonSchemaV2);

    List<String> affected = index.onSchemaChanged(schemaFile);
    assertTrue(affected.contains(TemplateDocumentStore.normalizeUri(templateUri)));

    // Re-query: updated property is now accessible
    Optional<CanonicalSchema> updatedSchema = resolver.resolveSchema(schemaFile.toUri().toString());
    assertTrue(updatedSchema.isPresent());
    assertTrue(
        updatedSchema.get().types().containsKey("Item")
            || updatedSchema.get().types().containsKey("item"));
  }

  @Test
  @DisplayName("Clean dependency cleanup and notification on schema deletion")
  void testOnSchemaDeleted(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("order.vtl");
    Files.writeString(templateFile, "Order: $order.id");

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
              "type": { "kind": "primitive", "primitiveKind": "LONG" }
            }
          }
        }
        """;
    Files.writeString(schemaFile, schemaContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchemaFile(schemaFile);
    WorkspaceSchemaIndex index = new WorkspaceSchemaIndex(resolver);

    String templateUri = templateFile.toUri().toString();
    index.recordDependency(templateUri, schemaFile);

    // Delete schema
    Files.delete(schemaFile);
    List<String> affected = index.onSchemaDeleted(schemaFile);

    assertTrue(affected.contains(TemplateDocumentStore.normalizeUri(templateUri)));
    assertTrue(resolver.resolveSchema(schemaFile.toUri().toString()).isEmpty());
    assertTrue(index.getAssociatedSchemaPath(templateUri).isEmpty());
  }
}
