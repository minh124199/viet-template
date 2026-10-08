package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.lsp.WorkspaceSymbolKey.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceSymbolKeyTest {

  @Test
  @DisplayName(
      "JvmMemberSymbolKey stores properties, normalizes null descriptor, and checks invariants")
  void testJvmMemberSymbolKeyInvariants() {
    JvmMemberSymbolKey key =
        new JvmMemberSymbolKey(
            "com.example.User", JvmMemberSymbolKey.Kind.GETTER, "getName", null, 0);

    assertEquals("com.example.User", key.declaringClassName());
    assertEquals(JvmMemberSymbolKey.Kind.GETTER, key.memberKind());
    assertEquals("getName", key.memberName());
    assertEquals("", key.descriptor());
    assertEquals(0, key.parameterCount());

    assertThrows(
        NullPointerException.class,
        () -> new JvmMemberSymbolKey(null, JvmMemberSymbolKey.Kind.GETTER, "getName", "", 0));
    assertThrows(
        NullPointerException.class,
        () -> new JvmMemberSymbolKey("com.example.User", null, "getName", "", 0));
    assertThrows(
        NullPointerException.class,
        () ->
            new JvmMemberSymbolKey(
                "com.example.User", JvmMemberSymbolKey.Kind.GETTER, null, "", 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JvmMemberSymbolKey(
                "com.example.User", JvmMemberSymbolKey.Kind.GETTER, "getName", "", -1));
  }

  @Test
  @DisplayName("JvmMemberSymbolKey equals, hashCode, and distinct keys for overrides and overloads")
  void testJvmMemberSymbolKeyEquality() {
    JvmMemberSymbolKey key1 =
        WorkspaceSymbolKey.jvmMember(
            "com.example.BaseUser", JvmMemberSymbolKey.Kind.GETTER, "getName");
    JvmMemberSymbolKey key2 =
        new JvmMemberSymbolKey(
            "com.example.BaseUser", JvmMemberSymbolKey.Kind.GETTER, "getName", "", 0);
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());

    // Distinct declaring class (BaseUser vs SubUser override)
    JvmMemberSymbolKey overrideKey =
        WorkspaceSymbolKey.jvmMember(
            "com.example.SubUser", JvmMemberSymbolKey.Kind.GETTER, "getName");
    assertNotEquals(key1, overrideKey);

    // Overloads with different descriptors / parameter counts
    JvmMemberSymbolKey method1 =
        WorkspaceSymbolKey.jvmMember(
            "com.example.Service",
            JvmMemberSymbolKey.Kind.METHOD,
            "find",
            "(Ljava/lang/String;)V",
            1);
    JvmMemberSymbolKey method2 =
        WorkspaceSymbolKey.jvmMember(
            "com.example.Service",
            JvmMemberSymbolKey.Kind.METHOD,
            "find",
            "(Ljava/lang/String;Z)V",
            2);
    JvmMemberSymbolKey method3 =
        WorkspaceSymbolKey.jvmMember(
            "com.example.Service",
            JvmMemberSymbolKey.Kind.METHOD,
            "find",
            "(Ljava/lang/Integer;)V",
            1);

    assertNotEquals(method1, method2);
    assertNotEquals(method1, method3);
    assertNotEquals(method2, method3);

    // Kind distinction: Getter vs Field vs RecordComponent
    JvmMemberSymbolKey getter =
        WorkspaceSymbolKey.jvmMember("com.example.Item", JvmMemberSymbolKey.Kind.GETTER, "name");
    JvmMemberSymbolKey field =
        WorkspaceSymbolKey.jvmMember("com.example.Item", JvmMemberSymbolKey.Kind.FIELD, "name");
    JvmMemberSymbolKey recordComp =
        WorkspaceSymbolKey.jvmMember(
            "com.example.Item", JvmMemberSymbolKey.Kind.RECORD_COMPONENT, "name");
    assertNotEquals(getter, field);
    assertNotEquals(getter, recordComp);
    assertNotEquals(field, recordComp);
  }

  @Test
  @DisplayName("SchemaMemberSymbolKey enforces invariants and isolates shapes")
  void testSchemaMemberSymbolKey() {
    SchemaMemberSymbolKey s1 =
        WorkspaceSymbolKey.schemaMember("file:///schema.json", "User", "name");
    SchemaMemberSymbolKey s2 = new SchemaMemberSymbolKey("file:///schema.json", "User", "name");
    assertEquals(s1, s2);
    assertEquals(s1.hashCode(), s2.hashCode());

    // Different type in same schema
    SchemaMemberSymbolKey sAddress =
        WorkspaceSymbolKey.schemaMember("file:///schema.json", "Address", "name");
    assertNotEquals(s1, sAddress);

    // Different schema source
    SchemaMemberSymbolKey sOtherSource =
        WorkspaceSymbolKey.schemaMember("file:///other.json", "User", "name");
    assertNotEquals(s1, sOtherSource);

    // Null schemaSource defaults to empty string
    SchemaMemberSymbolKey sNullSource = new SchemaMemberSymbolKey(null, "User", "name");
    assertEquals("", sNullSource.schemaSource());

    assertThrows(
        NullPointerException.class,
        () -> new SchemaMemberSymbolKey("file:///schema.json", null, "name"));
    assertThrows(
        NullPointerException.class,
        () -> new SchemaMemberSymbolKey("file:///schema.json", "User", null));
  }

  @Test
  @DisplayName("TemplateLocalSymbolKey isolates scopes and templates")
  void testTemplateLocalSymbolKey() {
    TemplateLocalSymbolKey local1 =
        WorkspaceSymbolKey.templateLocal("file:///template.vtl", "item", 10, 15);
    TemplateLocalSymbolKey local1Same =
        new TemplateLocalSymbolKey("file:///template.vtl", "item", 10, 15);
    assertEquals(local1, local1Same);
    assertEquals(local1.hashCode(), local1Same.hashCode());

    // Different scope in same template
    TemplateLocalSymbolKey local2 =
        WorkspaceSymbolKey.templateLocal("file:///template.vtl", "item", 50, 55);
    assertNotEquals(local1, local2);

    // Different template
    TemplateLocalSymbolKey localOtherTemplate =
        WorkspaceSymbolKey.templateLocal("file:///other.vtl", "item", 10, 15);
    assertNotEquals(local1, localOtherTemplate);

    assertThrows(NullPointerException.class, () -> new TemplateLocalSymbolKey(null, "item", 0, 5));
    assertThrows(
        NullPointerException.class, () -> new TemplateLocalSymbolKey("file:///t.vtl", null, 0, 5));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TemplateLocalSymbolKey("file:///t.vtl", "item", -1, 5));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TemplateLocalSymbolKey("file:///t.vtl", "item", 10, 5));
  }

  @Test
  @DisplayName("RootParameterSymbolKey isolates root parameters")
  void testRootParameterSymbolKey() {
    RootParameterSymbolKey p1 = WorkspaceSymbolKey.rootParameter("file:///schema.json", "user");
    RootParameterSymbolKey p1Same = new RootParameterSymbolKey("file:///schema.json", "user");
    assertEquals(p1, p1Same);
    assertEquals(p1.hashCode(), p1Same.hashCode());

    RootParameterSymbolKey p2 = WorkspaceSymbolKey.rootParameter("file:///schema.json", "product");
    assertNotEquals(p1, p2);

    RootParameterSymbolKey pNullSource = new RootParameterSymbolKey(null, "user");
    assertEquals("", pNullSource.schemaSourceOrTemplateUri());

    assertThrows(
        NullPointerException.class, () -> new RootParameterSymbolKey("file:///s.json", null));
  }
}
