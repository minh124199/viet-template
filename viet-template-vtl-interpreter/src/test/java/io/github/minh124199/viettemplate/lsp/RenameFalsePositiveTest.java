package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenameFalsePositiveTest {

  @Test
  @DisplayName(
      "Local variable with same name in two different templates: renaming in A never touches B")
  void testLocalVariableIsolationBetweenTemplates() {
    String uriA = "file:///template-a.vtl";
    String contentA = "#set($count = 10)\nCount in A: $count";

    String uriB = "file:///template-b.vtl";
    String contentB = "#set($count = 20)\nCount in B: $count";

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();

    TemplateDocument docA = new TemplateDocument(uriA, 1, contentA);
    TemplateDocument docB = new TemplateDocument(uriB, 1, contentB);

    refIndex.indexTemplate(docA, resolver, MemberAccessPolicy.standard());
    refIndex.indexTemplate(docB, resolver, MemberAccessPolicy.standard());

    // Position on $count in template A (line 0, col 7)
    Position posA = Position.of(0, 7);

    WorkspaceEdit edit =
        RenameProvider.rename(
            docA,
            posA,
            "itemCount",
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());

    assertNotNull(edit);
    assertTrue(edit.changes().containsKey(uriA), "Must contain edits for template A");
    assertFalse(edit.changes().containsKey(uriB), "Must NOT contain any edits for template B");

    List<TextEdit> editsA = edit.changes().get(uriA);
    assertEquals(2, editsA.size());
    for (TextEdit te : editsA) {
      assertEquals("itemCount", te.newText());
    }
  }

  @Test
  @DisplayName(
      "Unrelated TypeScript types with same property name: renaming User.name never touches"
          + " Address.name")
  void testUnrelatedTypesWithSamePropertyName(@TempDir Path tempDir) throws IOException {
    Path dtsFile = tempDir.resolve("domain.d.ts");
    String dtsContent =
        """
        export interface User {
          name: string;
        }

        export interface Address {
          name: string;
        }

        export interface TemplateParameters {
          user: User;
          address: Address;
        }
        """;
    Files.writeString(dtsFile, dtsContent);

    Path templateFile = tempDir.resolve("profile.vtl");
    String templateContent = "User: $user.name, Location: $address.name";
    Files.writeString(templateFile, templateContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchemaFile(dtsFile);

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    schemaIndex.recordDependency(templateFile.toUri().toString(), dtsFile);

    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    TemplateDocument doc =
        new TemplateDocument(templateFile.toUri().toString(), 1, templateContent);
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on $user.name (col 13)
    Position posUser = Position.of(0, 13);

    WorkspaceEdit edit =
        RenameProvider.rename(
            doc,
            posUser,
            "username",
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());

    assertNotNull(edit);

    // 1. Verify template edits: only $user.name is edited, NOT $address.name
    List<TextEdit> templateEdits = edit.changes().get(templateFile.toUri().toString());
    assertEquals(1, templateEdits.size());
    TextEdit te = templateEdits.get(0);
    assertEquals(Range.of(0, 12, 0, 16), te.range());
    assertEquals("username", te.newText());

    // 2. Verify .d.ts edits: only User.name is edited (line 1), NOT Address.name (line 5)
    List<TextEdit> dtsEdits = edit.changes().get(dtsFile.toUri().toString());
    assertEquals(1, dtsEdits.size());
    assertEquals(1, dtsEdits.get(0).range().start().line());
    assertEquals("username", dtsEdits.get(0).newText());
  }

  @Test
  @DisplayName(
      "Customer#getName() vs Product#getName(): text collision isolation via semantic rejection")
  void testCustomerVsProductTextCollisionIsolation() {
    String uri = "file:///models.vtl";
    String content = "Customer: $cust.name, Product: $prod.name";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Dynamic receivers: both rejected safely without cross-type interference
    Position posCust = Position.of(0, 17);
    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    posCust,
                    "customerName",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_DYNAMIC_SYMBOL, ex.reason());
  }
}
