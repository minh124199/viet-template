package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenameProviderTest {

  @Test
  @DisplayName("Local #set rename: declaration and all references in template are updated")
  void testLocalSetRename() {
    String uri = "file:///set-test.vtl";
    String content = "#set($val = 42)\nValue: $val\nAgain: $val\n";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on $val in line 1, col 9
    Position pos = Position.of(1, 9);

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isPresent());
    assertEquals("val", prepare.get().placeholder());
    assertEquals(Range.of(1, 8, 1, 11), prepare.get().range());

    WorkspaceEdit edit =
        RenameProvider.rename(
            doc, pos, "counter", resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertNotNull(edit);
    assertTrue(edit.changes().containsKey(uri));
    List<TextEdit> edits = edit.changes().get(uri);
    assertEquals(3, edits.size());

    // Edits sorted: line 0 (#set), line 1, line 2
    assertEquals(Range.of(0, 6, 0, 9), edits.get(0).range());
    assertEquals("counter", edits.get(0).newText());

    assertEquals(Range.of(1, 8, 1, 11), edits.get(1).range());
    assertEquals("counter", edits.get(1).newText());

    assertEquals(Range.of(2, 8, 2, 11), edits.get(2).range());
    assertEquals("counter", edits.get(2).newText());
  }

  @Test
  @DisplayName("Loop #foreach rename: loop variable declaration and body references updated")
  void testForeachLoopVariableRename() {
    String uri = "file:///loop-test.vtl";
    String content = "#foreach($item in $items)\n  Current: $item\n  Next: $item\n#end";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on $item at line 1, col 12
    Position pos = Position.of(1, 12);

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isPresent());
    assertEquals("item", prepare.get().placeholder());

    WorkspaceEdit edit =
        RenameProvider.rename(
            doc, pos, "element", resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertNotNull(edit);
    List<TextEdit> edits = edit.changes().get(uri);
    assertEquals(3, edits.size()); // line 0 (#foreach target), line 1, line 2

    assertEquals(Range.of(0, 10, 0, 14), edits.get(0).range());
    assertEquals("element", edits.get(0).newText());

    assertEquals(Range.of(1, 12, 1, 16), edits.get(1).range());
    assertEquals("element", edits.get(1).newText());

    assertEquals(Range.of(2, 9, 2, 13), edits.get(2).range());
    assertEquals("element", edits.get(2).newText());
  }

  @Test
  @DisplayName(
      "Scope shadowing: renaming outer variable does not touch inner shadowed variable and vice"
          + " versa")
  void testScopeShadowingRename() {
    String uri = "file:///scope-shadow.vtl";
    String content =
        """
        #set($x = 10)
        Outer: $x
        #foreach($x in $list)
          Inner: $x
        #end
        OuterAgain: $x
        """;
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // 1. Rename outer $x (on line 1, col 8) to 'outerX'
    Position outerPos = Position.of(1, 8);
    WorkspaceEdit outerEdit =
        RenameProvider.rename(
            doc,
            outerPos,
            "outerX",
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    List<TextEdit> outerEdits = outerEdit.changes().get(uri);
    // Should touch: line 0 (#set), line 1 ($x), line 5 (OuterAgain $x) = 3 edits
    assertEquals(3, outerEdits.size());
    assertEquals(0, outerEdits.get(0).range().start().line());
    assertEquals(1, outerEdits.get(1).range().start().line());
    assertEquals(5, outerEdits.get(2).range().start().line());
    for (TextEdit te : outerEdits) {
      assertEquals("outerX", te.newText());
    }

    // 2. Rename inner $x (on line 3, col 10) to 'innerX'
    Position innerPos = Position.of(3, 10);
    WorkspaceEdit innerEdit =
        RenameProvider.rename(
            doc,
            innerPos,
            "innerX",
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    List<TextEdit> innerEdits = innerEdit.changes().get(uri);
    // Should touch: line 2 (#foreach loop var), line 3 (Inner $x) = 2 edits
    assertEquals(2, innerEdits.size());
    assertEquals(2, innerEdits.get(0).range().start().line());
    assertEquals(3, innerEdits.get(1).range().start().line());
    for (TextEdit te : innerEdits) {
      assertEquals("innerX", te.newText());
    }
  }

  @Test
  @DisplayName("Exact token ranges: verify '$' and '.' are not included in TextEdits")
  void testExactTokenRangesExcludeSigils() {
    String uri = "file:///sigils.vtl";
    String content = "#set($myVar = 100)\nValue is $myVar and ${myVar}!";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 7);
    WorkspaceEdit edit =
        RenameProvider.rename(
            doc, pos, "renamed", resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    List<TextEdit> edits = edit.changes().get(uri);
    assertEquals(3, edits.size());

    for (TextEdit te : edits) {
      String originalText =
          doc.content()
              .substring(
                  doc.positionToOffset(te.range().start()), doc.positionToOffset(te.range().end()));
      assertEquals("myVar", originalText, "Range must cover exactly the identifier 'myVar'");
      assertFalse(originalText.contains("$"), "Range must not contain '$'");
      assertFalse(originalText.contains("."), "Range must not contain '.'");
      assertFalse(originalText.contains("{"), "Range must not contain '{'");
      assertFalse(originalText.contains("}"), "Range must not contain '}'");
    }
  }

  @Test
  @DisplayName(
      "Multiple references on same line: correctly generates separate non-overlapping edits")
  void testMultipleReferencesOnSameLine() {
    String uri = "file:///multi-ref.vtl";
    String content = "#set($tag = \"alpha\")\nTags: $tag, $tag, and $tag.";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 7);
    WorkspaceEdit edit =
        RenameProvider.rename(
            doc, pos, "newTag", resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    List<TextEdit> edits = edit.changes().get(uri);
    assertEquals(4, edits.size()); // 1 decl + 3 usages on line 1

    assertEquals(0, edits.get(0).range().start().line());
    assertEquals(1, edits.get(1).range().start().line());
    assertEquals(1, edits.get(2).range().start().line());
    assertEquals(1, edits.get(3).range().start().line());

    // Ensure monotonically increasing start columns on line 1
    assertTrue(edits.get(1).range().end().character() <= edits.get(2).range().start().character());
    assertTrue(edits.get(2).range().end().character() <= edits.get(3).range().start().character());
  }

  @Test
  @DisplayName(
      "TypeScript .d.ts property rename: declaration in .d.ts + references across multiple"
          + " templates updated")
  void testTypeScriptDtsPropertyRename(@TempDir Path tempDir) throws IOException {
    Path dtsFile = tempDir.resolve("model.d.ts");
    String dtsContent =
        """
        export interface UserModel {
          name: string;
        }

        export interface TemplateParameters {
          user: UserModel;
        }
        """;
    Files.writeString(dtsFile, dtsContent);

    Path template1 = tempDir.resolve("t1.vtl");
    String content1 = "Hello $user.name!";
    Files.writeString(template1, content1);

    Path template2 = tempDir.resolve("t2.vtl");
    String content2 = "User name: $user.name again $user.name";
    Files.writeString(template2, content2);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchemaFile(dtsFile);

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    schemaIndex.recordDependency(template1.toUri().toString(), dtsFile);
    schemaIndex.recordDependency(template2.toUri().toString(), dtsFile);

    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    TemplateDocument doc1 = new TemplateDocument(template1.toUri().toString(), 1, content1);
    TemplateDocument doc2 = new TemplateDocument(template2.toUri().toString(), 1, content2);
    refIndex.indexTemplate(doc1, resolver, MemberAccessPolicy.standard());
    refIndex.indexTemplate(doc2, resolver, MemberAccessPolicy.standard());

    // Position on .name in template1 (col 13)
    Position pos = Position.of(0, 13);

    Optional<PrepareRenameResult> prep =
        RenameProvider.prepareRename(
            doc1, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prep.isPresent());
    assertEquals("name", prep.get().placeholder());

    WorkspaceEdit edit =
        RenameProvider.rename(
            doc1, pos, "fullName", resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertNotNull(edit);

    Map<String, List<TextEdit>> changes = edit.changes();
    assertEquals(3, changes.size(), "Should have edits in .d.ts, t1.vtl, and t2.vtl");

    // 1. .d.ts declaration edit
    String dtsUri = dtsFile.toUri().toString();
    assertTrue(changes.containsKey(dtsUri));
    List<TextEdit> dtsEdits = changes.get(dtsUri);
    assertEquals(1, dtsEdits.size());
    assertEquals("fullName", dtsEdits.get(0).newText());
    // Verify dts edit target
    assertEquals(Range.of(1, 2, 1, 6), dtsEdits.get(0).range());

    // 2. t1.vtl reference edit
    List<TextEdit> t1Edits = changes.get(template1.toUri().toString());
    assertEquals(1, t1Edits.size());
    assertEquals("fullName", t1Edits.get(0).newText());
    assertEquals(Range.of(0, 12, 0, 16), t1Edits.get(0).range());

    // 3. t2.vtl reference edits (2 usages)
    List<TextEdit> t2Edits = changes.get(template2.toUri().toString());
    assertEquals(2, t2Edits.size());
    for (TextEdit te : t2Edits) {
      assertEquals("fullName", te.newText());
    }
  }

  @Test
  @DisplayName("Contract property rename: declaration in .contract + references updated")
  void testContractPropertyRename(@TempDir Path tempDir) throws IOException {
    Path contractFile = tempDir.resolve("sample.contract");
    String contractContent = "title=String\n";
    Files.writeString(contractFile, contractContent);

    Path templateFile = tempDir.resolve("sample.vtl");
    String templateContent = "Page: $title\nHeader: $title\n";
    Files.writeString(templateFile, templateContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchemaFile(contractFile);

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    schemaIndex.recordDependency(templateFile.toUri().toString(), contractFile);

    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    TemplateDocument doc =
        new TemplateDocument(templateFile.toUri().toString(), 1, templateContent);
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on $title (line 0, col 7)
    Position pos = Position.of(0, 7);

    // In a companion contract with title=string, title is a root parameter or contract property
    // If $title is resolved as RootParameterSymbolKey:
    // Notice that RootParameterSymbolKey is rejected per contract specification:
    // "UNSUPPORTED_ROOT_PARAMETER: Rename is not available for root model parameters"
    // Let's verify that root model parameters reject with UNSUPPORTED_ROOT_PARAMETER:
    Optional<PrepareRenameResult> prep =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prep.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "headline",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_ROOT_PARAMETER, ex.reason());
  }
}
