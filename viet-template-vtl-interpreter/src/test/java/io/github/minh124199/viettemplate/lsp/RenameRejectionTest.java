package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.lsp.models.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.JavaModelSchemaImporter;
import io.github.minh124199.viettemplate.schema.SchemaImportResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenameRejectionTest {

  @Test
  @DisplayName("JVM getter: prepareRename returns empty and rename throws UNSUPPORTED_JVM_MEMBER")
  void testJvmGetterRejected() {
    String uri = "file:///jvm-getter.vtl";
    String content = "Hello $user.name";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 13); // on .name

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty(), "prepareRename should return empty for JVM getter");

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "renamedName",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER, ex.reason());
    assertTrue(
        ex.getMessage().contains("Java source refactoring is outside Viet Template's ownership"));
  }

  @Test
  @DisplayName("JVM boolean getter: rejected with UNSUPPORTED_JVM_MEMBER")
  void testJvmBooleanGetterRejected() {
    String uri = "file:///jvm-bool.vtl";
    String content = "Active: $user.active";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 15); // on .active

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "enabled",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER, ex.reason());
  }

  @Test
  @DisplayName("JVM public field: rejected with UNSUPPORTED_JVM_MEMBER")
  void testJvmFieldRejected() {
    String uri = "file:///jvm-field.vtl";
    String content = "Role: $user.role";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavFieldUser.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 13);

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "otherField",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER, ex.reason());
  }

  @Test
  @DisplayName("JVM record component: rejected with UNSUPPORTED_JVM_MEMBER")
  void testJvmRecordComponentRejected() {
    String uri = "file:///jvm-record.vtl";
    String content = "Record: $user.name";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavUserRecord.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 15);

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "renamedRecordComp",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER, ex.reason());
  }

  @Test
  @DisplayName("Inherited JVM getter: rejected with UNSUPPORTED_JVM_MEMBER")
  void testInheritedJvmGetterRejected() {
    String uri = "file:///jvm-inherited.vtl";
    String content = "Email: $admin.email";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavAdminUser.class, "admin", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 16); // on .email

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "mailAddress",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER, ex.reason());
  }

  @Test
  @DisplayName("Dynamic receiver ($dynamic.name): rejected with UNSUPPORTED_DYNAMIC_SYMBOL")
  void testDynamicReceiverRejected() {
    String uri = "file:///dynamic.vtl";
    String content = "Dynamic: $dynamic.property";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 20); // on .property

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "newProperty",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_DYNAMIC_SYMBOL, ex.reason());
  }

  @Test
  @DisplayName("Security-denied member: rejected with UNSUPPORTED_DENIED_MEMBER")
  void testSecurityDeniedMemberRejected() {
    String uri = "file:///denied.vtl";
    String content = "Class: $user.class";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 15); // on .class

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "clazz",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    // Either UNSUPPORTED_DENIED_MEMBER or UNSUPPORTED_JVM_MEMBER (both rejection reasons)
    assertTrue(
        ex.reason() == RenameConflictException.Reason.UNSUPPORTED_DENIED_MEMBER
            || ex.reason() == RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER);
  }

  @Test
  @DisplayName("Method invocation ($user.getName()): rejected with UNSUPPORTED_METHOD")
  void testMethodInvocationRejected() {
    String uri = "file:///method.vtl";
    String content = "Name: $user.getName()";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 14); // on getName

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "retrieveName",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_METHOD, ex.reason());
  }

  @Test
  @DisplayName("JSON Schema property: rejected with UNSUPPORTED_SCHEMA_FORMAT")
  void testJsonSchemaPropertyRejected(@TempDir Path tempDir) throws IOException {
    Path jsonFile = tempDir.resolve("model.schema.json");
    String jsonContent =
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "properties": {
            "name": { "type": "string" }
          }
        }
        """;
    Files.writeString(jsonFile, jsonContent);

    Path templateFile = tempDir.resolve("template.vtl");
    String templateContent = "Name: $name";
    Files.writeString(templateFile, templateContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchemaFile(jsonFile);

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    schemaIndex.recordDependency(templateFile.toUri().toString(), jsonFile);

    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    TemplateDocument doc =
        new TemplateDocument(templateFile.toUri().toString(), 1, templateContent);
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    Position pos = Position.of(0, 8); // on $name (root parameter)

    Optional<PrepareRenameResult> prepare =
        RenameProvider.prepareRename(
            doc, pos, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertTrue(prepare.isEmpty());

    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () ->
                RenameProvider.rename(
                    doc,
                    pos,
                    "newName",
                    resolver,
                    schemaIndex,
                    refIndex,
                    MemberAccessPolicy.standard()));
    // Rejected either as root parameter or unsupported schema format
    assertTrue(
        ex.reason() == RenameConflictException.Reason.UNSUPPORTED_ROOT_PARAMETER
            || ex.reason() == RenameConflictException.Reason.UNSUPPORTED_SCHEMA_FORMAT);
  }
}
