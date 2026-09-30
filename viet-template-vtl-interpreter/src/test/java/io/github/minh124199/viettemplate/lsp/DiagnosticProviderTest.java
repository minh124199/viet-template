package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiagnosticProviderTest {

  private CanonicalSchemaResolver schemaResolver;
  private static final String SCHEMA =
      """
      {
        "format": "viet-template-contract-schema/1",
        "schemaVersion": 1,
        "templateId": "test-diag.vt",
        "parameters": {
          "user": {
            "name": "user",
            "type": { "kind": "named", "name": "User" },
            "required": true
          }
        },
        "types": {
          "User": {
            "name": "User",
            "properties": {
              "name": {
                "name": "name",
                "type": { "kind": "class", "className": "java.lang.String" },
                "nullable": false
              },
              "profile": {
                "name": "profile",
                "type": { "kind": "named", "name": "Profile" },
                "nullable": true
              }
            }
          },
          "Profile": {
            "name": "Profile",
            "properties": {
              "bio": {
                "name": "bio",
                "type": { "kind": "class", "className": "java.lang.String" },
                "nullable": true
              }
            }
          }
        }
      }
      """;

  @BeforeEach
  void setUp() {
    schemaResolver = new CanonicalSchemaResolver();
    schemaResolver.registerSchema("test-diag.vt", SCHEMA);
  }

  @Test
  @DisplayName("Clean template produces zero diagnostics")
  void testCleanTemplate() {
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, "Hello $user.name");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertTrue(diags.isEmpty());
  }

  @Test
  @DisplayName("Syntax error produces SYNTAX:PARSE_ERROR diagnostic")
  void testSyntaxErrorDiagnostic() {
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, "Hello #if($unclosed");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    assertTrue(diags.stream().anyMatch(d -> d.code().qualifiedCode().equals("SYNTAX:PARSE_ERROR")));
    assertEquals(DiagnosticSeverity.ERROR, diags.get(0).severity());
  }

  @Test
  @DisplayName("Unresolved root parameter produces VTLS:2101 error")
  void testUnresolvedRootDiagnostic() {
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, "Hello $unknownVar");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    Diagnostic d = diags.get(0);
    assertEquals("VTLS:2101", d.code().qualifiedCode());
    assertEquals(DiagnosticSeverity.ERROR, d.severity());
    assertTrue(d.message().contains("Unresolved root variable: $unknownVar"));
  }

  @Test
  @DisplayName("Unresolved property on schema model produces VTLS:2104 error")
  void testUnresolvedPropertyDiagnostic() {
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, "Hello $user.nonExistentProp");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    Diagnostic d = diags.get(0);
    assertEquals("VTLS:2104", d.code().qualifiedCode());
    assertEquals(DiagnosticSeverity.ERROR, d.severity());
    assertTrue(d.message().contains("Property 'nonExistentProp' not found"));
  }

  @Test
  @DisplayName("Nullable dereference produces VTLS:2107 warning when unguarded")
  void testNullableDereferenceWarning() {
    // $user.profile is nullable. Dereferencing $user.profile.bio unguarded produces VTLS:2107
    // warning
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, "Bio: $user.profile.bio");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    assertTrue(
        diags.stream()
            .anyMatch(
                d ->
                    d.code().qualifiedCode().equals("VTLS:2107")
                        && d.severity() == DiagnosticSeverity.WARNING));
  }

  @Test
  @DisplayName("Guarded nullable dereference produces zero warnings")
  void testGuardedNullableDereference() {
    // When guarded by #if($user.profile), no warning is emitted
    String text = "#if($user.profile)\nBio: $user.profile.bio\n#end";
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, text);
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertTrue(diags.isEmpty());
  }

  @Test
  @DisplayName("Security violation produces VTLSEC:2401 error")
  void testSecurityViolationDiagnostic() {
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, "Class: $user.class");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    assertTrue(
        diags.stream()
            .anyMatch(
                d ->
                    d.code().qualifiedCode().equals("VTLSEC:2401")
                        && d.severity() == DiagnosticSeverity.ERROR));
  }

  @Test
  @DisplayName("Security violation on method call produces VTLSEC:2401 error")
  void testMethodCallSecurityViolationDiagnostic() {
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, "Class: $user.getClass()");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    assertTrue(
        diags.stream()
            .anyMatch(
                d ->
                    d.code().qualifiedCode().equals("VTLSEC:2401")
                        && d.severity() == DiagnosticSeverity.ERROR));
  }

  @Test
  @DisplayName("Unresolved property in #if condition produces VTLS:2104 error")
  void testUnresolvedPropertyInIfCondition() {
    TemplateDocument doc =
        new TemplateDocument("test-diag.vt", 1, "#if($user.nonExistentProp)\nHi\n#end");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    assertTrue(
        diags.stream()
            .anyMatch(
                d ->
                    d.code().qualifiedCode().equals("VTLS:2104")
                        && d.severity() == DiagnosticSeverity.ERROR));
  }

  @Test
  @DisplayName("Unresolved property in #foreach iterable produces VTLS:2104 error")
  void testUnresolvedPropertyInForeachIterable() {
    TemplateDocument doc =
        new TemplateDocument(
            "test-diag.vt", 1, "#foreach($item in $user.nonExistentList)\n$item\n#end");
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    assertTrue(
        diags.stream()
            .anyMatch(
                d ->
                    d.code().qualifiedCode().equals("VTLS:2104")
                        && d.severity() == DiagnosticSeverity.ERROR));
  }

  @Test
  @DisplayName("Foreach loop variable referenced outside loop produces VTLS:2101 error")
  void testForeachVariableReferencedOutsideLoopProducesDiagnostic() {
    String text = "#foreach($item in $user.profile)\n  $item\n#end\n$item";
    TemplateDocument doc = new TemplateDocument("test-diag.vt", 1, text);
    List<Diagnostic> diags =
        DiagnosticProvider.diagnostics(doc, schemaResolver, MemberAccessPolicy.standard());

    assertFalse(diags.isEmpty());
    assertTrue(
        diags.stream()
            .anyMatch(
                d ->
                    d.code().qualifiedCode().equals("VTLS:2101")
                        && d.severity() == DiagnosticSeverity.ERROR
                        && d.message().contains("Unresolved root variable: $item")));
  }
}
