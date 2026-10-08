package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.lsp.models.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReferenceProviderFalsePositiveTest {

  public static record Customer(String name) {}

  public static record Product(String name) {}

  public static class OverridingUser extends NavBaseUser {
    public OverridingUser(String email) {
      super(email);
    }

    @Override
    public String getEmail() {
      return super.getEmail();
    }
  }

  public static class InheritingUser extends NavBaseUser {
    public InheritingUser(String email) {
      super(email);
    }
  }

  public static class FieldUser {
    public String name;

    public FieldUser(String name) {
      this.name = name;
    }
  }

  public static class OverloadedService {
    public String find(String id) {
      return id;
    }

    public String find(String id, boolean active) {
      return id;
    }
  }

  public static class AddressHolder {
    private final Address address = new Address();

    public Address getAddress() {
      return address;
    }
  }

  public static class Address {
    public String getStreet() {
      return "Main";
    }
  }

  private static CanonicalSchema javaSchema(String uri, Map<String, Class<?>> params) {
    Map<String, ParameterDef> parameters = new TreeMap<>();
    for (Map.Entry<String, Class<?>> e : params.entrySet()) {
      parameters.put(
          e.getKey(),
          new ParameterDef(e.getKey(), new ClassTypeRef(e.getValue().getName()), false));
    }
    return new CanonicalSchema(uri, SchemaFormat.JAVA, "", parameters, Map.of(), "");
  }

  private static CanonicalSchema schemaWithShapes(
      String uri, Map<String, String> params, Map<String, Map<String, String>> typeDefinitions) {
    Map<String, ParameterDef> parameters = new TreeMap<>();
    for (Map.Entry<String, String> e : params.entrySet()) {
      parameters.put(
          e.getKey(), new ParameterDef(e.getKey(), new NamedTypeRef(e.getValue()), false));
    }
    Map<String, TypeDef> types = new TreeMap<>();
    for (Map.Entry<String, Map<String, String>> te : typeDefinitions.entrySet()) {
      Map<String, PropertyDef> props = new TreeMap<>();
      for (Map.Entry<String, String> pe : te.getValue().entrySet()) {
        props.put(
            pe.getKey(), new PropertyDef(pe.getKey(), new PrimitiveTypeRef(pe.getValue()), false));
      }
      types.put(te.getKey(), new TypeDef(te.getKey(), "record", props));
    }
    return new CanonicalSchema(uri, SchemaFormat.JSON_SCHEMA, "", parameters, types, "");
  }

  @Test
  @DisplayName("1. Customer.name vs Product.name: separate declaring classes do not conflate")
  void testCustomerVsProductName() {
    String uri = "file:///shop.vtl";
    String content = "Customer: $customer.name and Product: $product.name";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(
        uri,
        javaSchema(
            uri,
            Map.of(
                "customer", Customer.class,
                "product", Product.class)));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on customer.name (col 21)
    List<LocationInfo> customerRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 21),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());

    assertEquals(1, customerRefs.size());
    assertEquals(Range.of(0, 20, 0, 24), customerRefs.get(0).range());

    // Cursor on product.name (col 47)
    List<LocationInfo> productRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 47),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());

    assertEquals(1, productRefs.size());
    assertEquals(Range.of(0, 47, 0, 51), productRefs.get(0).range());
  }

  @Test
  @DisplayName("2. BaseUser vs override: inherited shares key while override stays distinct")
  void testBaseUserVsOverride() {
    String uri = "file:///users.vtl";
    String content = "$base.email and $sub.email and $override.email";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(
        uri,
        javaSchema(
            uri,
            Map.of(
                "base", NavBaseUser.class,
                "sub", InheritingUser.class,
                "override", OverridingUser.class)));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on base.email (col 8)
    List<LocationInfo> baseRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 8),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());

    // Should match both base.email and sub.email (inherited from NavBaseUser), but NOT
    // override.email!
    assertEquals(2, baseRefs.size());
    assertEquals(Range.of(0, 6, 0, 11), baseRefs.get(0).range());
    assertEquals(Range.of(0, 21, 0, 26), baseRefs.get(1).range());

    // Cursor on override.email (col 42)
    List<LocationInfo> overrideRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 42),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, overrideRefs.size());
    assertEquals(Range.of(0, 41, 0, 46), overrideRefs.get(0).range());
  }

  @Test
  @DisplayName("3. getter vs field: JavaBean getter and public field do not conflate")
  void testGetterVsField() {
    String uri = "file:///memberkind.vtl";
    String content = "Bean: $bean.name and Field: $field.name";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(
        uri,
        javaSchema(
            uri,
            Map.of(
                "bean", NavBeanUser.class,
                "field", FieldUser.class)));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on bean.name (col 13)
    List<LocationInfo> beanRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 13),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, beanRefs.size());
    assertEquals(Range.of(0, 12, 0, 16), beanRefs.get(0).range());

    // Cursor on field.name (col 36)
    List<LocationInfo> fieldRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 36),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, fieldRefs.size());
    assertEquals(Range.of(0, 35, 0, 39), fieldRefs.get(0).range());
  }

  @Test
  @DisplayName("4. record component vs getter: Record component and getter do not conflate")
  void testRecordComponentVsGetter() {
    String uri = "file:///rec-vs-getter.vtl";
    String content = "Record: $rec.name and Getter: $bean.name";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(
        uri,
        javaSchema(
            uri,
            Map.of(
                "rec", NavUserRecord.class,
                "bean", NavBeanUser.class)));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on rec.name (col 14)
    List<LocationInfo> recRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 14),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, recRefs.size());
    assertEquals(Range.of(0, 13, 0, 17), recRefs.get(0).range());

    // Cursor on bean.name (col 36)
    List<LocationInfo> beanRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 36),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, beanRefs.size());
    assertEquals(Range.of(0, 36, 0, 40), beanRefs.get(0).range());
  }

  @Test
  @DisplayName("5. local shadowing root: local variable shadows root parameter cleanly")
  void testLocalShadowingRoot() {
    String uri = "file:///shadow.vtl";
    String content = "Root: $user.name\n#set($user = 'local')\nLocal: $user";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(uri, javaSchema(uri, Map.of("user", NavUserRecord.class)));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on local $user on line 2, col 9
    List<LocationInfo> localRefs =
        ReferenceProvider.references(
            doc,
            Position.of(2, 9),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());

    assertEquals(1, localRefs.size());
    assertEquals(Range.of(2, 7, 2, 12), localRefs.get(0).range());

    // Cursor on root $user on line 0, col 7
    List<LocationInfo> rootRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 7),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());

    assertEquals(1, rootRefs.size());
    assertEquals(Range.of(0, 6, 0, 11), rootRefs.get(0).range());
  }

  @Test
  @DisplayName("6. different local scopes: separate loops with same variable name do not conflate")
  void testDifferentLocalScopes() {
    String uri = "file:///scopes.vtl";
    String content =
        """
        #foreach($item in $list1)
          Loop1: $item
        #end
        #foreach($item in $list2)
          Loop2: $item
        #end
        """;
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on $item in loop 1 (line 1, col 10)
    List<LocationInfo> loop1Refs =
        ReferenceProvider.references(
            doc,
            Position.of(1, 10),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, loop1Refs.size());
    assertEquals(1, loop1Refs.get(0).range().start().line());

    // Cursor on $item in loop 2 (line 4, col 10)
    List<LocationInfo> loop2Refs =
        ReferenceProvider.references(
            doc,
            Position.of(4, 10),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, loop2Refs.size());
    assertEquals(4, loop2Refs.get(0).range().start().line());
  }

  @Test
  @DisplayName("7. different templates: locals in different templates do not conflate")
  void testDifferentTemplates() {
    String uri1 = "file:///t1.vtl";
    String uri2 = "file:///t2.vtl";
    String c1 = "#set($temp = 1)\n$temp";
    String c2 = "#set($temp = 2)\n$temp";

    TemplateDocument doc1 = new TemplateDocument(uri1, 1, c1);
    TemplateDocument doc2 = new TemplateDocument(uri2, 1, c2);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc1, resolver, MemberAccessPolicy.standard());
    refIndex.indexTemplate(doc2, resolver, MemberAccessPolicy.standard());

    List<LocationInfo> t1Refs =
        ReferenceProvider.references(
            doc1,
            Position.of(1, 2),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());

    assertEquals(1, t1Refs.size());
    assertEquals(uri1, t1Refs.get(0).uri());
  }

  @Test
  @DisplayName("8. schema User.name vs Address.name: schema shapes isolate properties by typeName")
  void testSchemaUserNameVsAddressName() {
    String uri = "file:///shapes.vtl";
    String content = "User: $user.name and Address: $addr.name";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(
        uri,
        schemaWithShapes(
            uri,
            Map.of("user", "User", "addr", "Address"),
            Map.of(
                "User", Map.of("name", "string"),
                "Address", Map.of("name", "string"))));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on user.name (col 13)
    List<LocationInfo> userRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 13),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, userRefs.size());
    assertEquals(Range.of(0, 12, 0, 16), userRefs.get(0).range());

    // Cursor on addr.name (col 37)
    List<LocationInfo> addrRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 37),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, addrRefs.size());
    assertEquals(Range.of(0, 36, 0, 40), addrRefs.get(0).range());
  }

  @Test
  @DisplayName("9. method overloads: methods with different arity do not conflate")
  void testMethodOverloads() {
    String uri = "file:///overload.vtl";
    String content = "Single: $svc.find('a') and Double: $svc.find('a', true)";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(uri, javaSchema(uri, Map.of("svc", OverloadedService.class)));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on find 1-arg (col 14)
    List<LocationInfo> find1Refs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 14),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, find1Refs.size());
    assertEquals(Range.of(0, 13, 0, 17), find1Refs.get(0).range());

    // Cursor on find 2-arg (col 41)
    List<LocationInfo> find2Refs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 41),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, find2Refs.size());
    assertEquals(Range.of(0, 40, 0, 44), find2Refs.get(0).range());
  }

  @Test
  @DisplayName(
      "10. dynamic receiver: unresolved/dynamic references yield empty references without false"
          + " positives")
  void testDynamicReceiver() {
    String uri = "file:///dynamic.vtl";
    String content = "Unknown: $unknown.something";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on something (col 20)
    List<LocationInfo> refs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 20),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertTrue(refs.isEmpty());
  }

  @Test
  @DisplayName("11. denied member: policy-denied member yields empty references")
  void testDeniedMember() {
    String uri = "file:///denied.vtl";
    String content = "Class: $user.class";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(uri, javaSchema(uri, Map.of("user", NavUserRecord.class)));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Cursor on class (col 15)
    List<LocationInfo> refs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 15),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertTrue(refs.isEmpty());
  }

  @Test
  @DisplayName("12. chained property access ranges: each step has exact disjoint range")
  void testChainedPropertyAccessRanges() {
    String uri = "file:///chained.vtl";
    String content = "$holder.address.street";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema(uri, javaSchema(uri, Map.of("holder", AddressHolder.class)));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // 1. Cursor on $holder (col 3)
    List<LocationInfo> holderRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 3),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, holderRefs.size());
    assertEquals(Range.of(0, 0, 0, 7), holderRefs.get(0).range());

    // 2. Cursor on address (col 11)
    List<LocationInfo> addressRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 11),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, addressRefs.size());
    assertEquals(Range.of(0, 8, 0, 15), addressRefs.get(0).range());

    // 3. Cursor on street (col 19)
    List<LocationInfo> streetRefs =
        ReferenceProvider.references(
            doc,
            Position.of(0, 19),
            false,
            resolver,
            schemaIndex,
            refIndex,
            MemberAccessPolicy.standard());
    assertEquals(1, streetRefs.size());
    assertEquals(Range.of(0, 16, 0, 22), streetRefs.get(0).range());
  }
}
