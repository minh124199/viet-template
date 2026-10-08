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

class RenameConflictTest {

  @Test
  @DisplayName(
      "Local collision: renaming #set($a = 1) to 'b' when #set($b = 2) exists rejects with"
          + " NAME_COLLISION")
  void testLocalNameCollision() {
    String uri = "file:///local-collision.vtl";
    String content = "#set($a = 1)\n#set($b = 2)\nResult: $a and $b";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 6); // on $a

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc, pos, "b", resolver, schemaIndex, refIndex, MemberAccessPolicy.standard()));

    assertEquals(RenameConflictException.Reason.NAME_COLLISION, ex.reason());
    assertTrue(ex.getMessage().contains("already defined in this scope"));
  }

  @Test
  @DisplayName(
      "Loop collision: renaming loop variable to existing outer variable rejects with"
          + " NAME_COLLISION")
  void testLoopVariableCollisionWithOuterVariable() {
    String uri = "file:///loop-collision.vtl";
    String content = "#set($outer = 10)\n#foreach($item in $list)\n  Value: $item\n#end";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(1, 10); // on $item

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "outer",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));

    assertEquals(RenameConflictException.Reason.NAME_COLLISION, ex.reason());
    assertTrue(ex.getMessage().contains("already defined in this scope"));
  }

  @Test
  @DisplayName(
      "Invalid identifier: renaming to numbers, dashes, spaces, empty string rejects with"
          + " INVALID_IDENTIFIER")
  void testInvalidIdentifiers() {
    String uri = "file:///invalid-id.vtl";
    String content = "#set($val = 1)\n$val";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 6);

    for (String badName :
        List.of("123bad", "foo-bar", "foo bar", "", "   ", "a.b", "x$y", "@var")) {
      RenameConflictException ex =
          assertThrows(
              RenameConflictException.class,
              () ->
                  RenameProvider.rename(
                      doc,
                      pos,
                      badName,
                      resolver,
                      schemaIndex,
                      refIndex,
                      MemberAccessPolicy.standard()),
              "Expected invalid identifier for: '" + badName + "'");
      assertEquals(RenameConflictException.Reason.INVALID_IDENTIFIER, ex.reason());
    }
  }

  @Test
  @DisplayName(
      "Reserved keywords: renaming to VTL keywords or boolean/null literals rejects with"
          + " INVALID_IDENTIFIER")
  void testReservedKeywords() {
    String uri = "file:///keywords.vtl";
    String content = "#set($val = 1)\n$val";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 6);

    for (String keyword :
        List.of("if", "foreach", "end", "else", "elseif", "set", "true", "false", "null")) {
      RenameConflictException ex =
          assertThrows(
              RenameConflictException.class,
              () ->
                  RenameProvider.rename(
                      doc,
                      pos,
                      keyword,
                      resolver,
                      schemaIndex,
                      refIndex,
                      MemberAccessPolicy.standard()),
              "Expected reserved keyword rejection for: '" + keyword + "'");
      assertEquals(RenameConflictException.Reason.INVALID_IDENTIFIER, ex.reason());
    }
  }

  @Test
  @DisplayName(
      "Schema collision: renaming property to existing property on same type in .d.ts rejects with"
          + " NAME_COLLISION")
  void testSchemaPropertyCollision(@TempDir Path tempDir) throws IOException {
    Path dtsFile = tempDir.resolve("user.d.ts");
    String dtsContent =
        """
        export interface UserRecord {
          name: string;
          age: number;
        }

        export interface TemplateParameters {
          user: UserRecord;
        }
        """;
    Files.writeString(dtsFile, dtsContent);

    Path templateFile = tempDir.resolve("user.vtl");
    String templateContent = "$user.name ($user.age)";
    Files.writeString(templateFile, templateContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchemaFile(dtsFile);

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    schemaIndex.recordDependency(templateFile.toUri().toString(), dtsFile);

    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    TemplateDocument doc =
        new TemplateDocument(templateFile.toUri().toString(), 1, templateContent);
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on .name (col 7)
    Position pos = Position.of(0, 7);

    // Try renaming 'name' to 'age' (which already exists on UserRecord)
    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "age",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));

    assertEquals(RenameConflictException.Reason.NAME_COLLISION, ex.reason());
    assertTrue(ex.getMessage().contains("already exists on type"));
  }

  @Test
  @DisplayName(
      "Root parameter collision: renaming local variable to root parameter in schema rejects with"
          + " NAME_COLLISION")
  void testLocalRenameShadowingRootParameter(@TempDir Path tempDir) throws IOException {
    Path dtsFile = tempDir.resolve("params.d.ts");
    String dtsContent =
        """
        export interface TemplateParameters {
          account: string;
        }
        """;
    Files.writeString(dtsFile, dtsContent);

    Path templateFile = tempDir.resolve("shadow.vtl");
    String templateContent = "#set($local = 1)\nValue: $local and $account";
    Files.writeString(templateFile, templateContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchemaFile(dtsFile);

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    schemaIndex.recordDependency(templateFile.toUri().toString(), dtsFile);

    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    TemplateDocument doc =
        new TemplateDocument(templateFile.toUri().toString(), 1, templateContent);
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on $local
    Position pos = Position.of(0, 6);

    // Rename 'local' to 'account' -> shadows root parameter
    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "account",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));

    assertEquals(RenameConflictException.Reason.NAME_COLLISION, ex.reason());
    assertTrue(ex.getMessage().contains("would shadow root parameter"));
  }
}
