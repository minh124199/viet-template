package io.github.minh124199.viettemplate.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeScriptSchemaImporterTest {

  private final TypeScriptSchemaImporter importer = new TypeScriptSchemaImporter();

  @Test
  @DisplayName("Import distinguishes all 4 states of required/optional and nullable/non-null")
  void testFourStatesOfNullabilityAndOptionality() {
    String dts =
        """
        export interface TestModel {
          req_nonnull: string;
          req_nullable: string | null;
          opt_nonnull?: string;
          opt_nullable?: string | null;
          opt_undefined: string | undefined;
          opt_all?: string | null | undefined;
        }
        """;

    SchemaImportResult result = importer.importString(dts, "test-nullability");
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.schemas()).containsKey("test-nullability");

    CanonicalSchema schema = result.schemas().get("test-nullability");
    assertThat(schema.types()).containsKey("TestModel");
    TypeDef model = schema.types().get("TestModel");

    // 1. Required, non-null
    PropertyDef reqNonNull = model.properties().get("req_nonnull");
    assertThat(reqNonNull.required()).isTrue();
    assertThat(reqNonNull.optional()).isFalse();
    assertThat(reqNonNull.nullable()).isFalse();
    assertThat(reqNonNull.type()).isEqualTo(new PrimitiveTypeRef("string"));

    // 2. Required, nullable
    PropertyDef reqNullable = model.properties().get("req_nullable");
    assertThat(reqNullable.required()).isTrue();
    assertThat(reqNullable.optional()).isFalse();
    assertThat(reqNullable.nullable()).isTrue();
    assertThat(reqNullable.type()).isEqualTo(new PrimitiveTypeRef("string"));

    // 3. Optional, non-null
    PropertyDef optNonNull = model.properties().get("opt_nonnull");
    assertThat(optNonNull.required()).isFalse();
    assertThat(optNonNull.optional()).isTrue();
    assertThat(optNonNull.nullable()).isFalse();
    assertThat(optNonNull.type()).isEqualTo(new PrimitiveTypeRef("string"));

    // 4. Optional, nullable
    PropertyDef optNullable = model.properties().get("opt_nullable");
    assertThat(optNullable.required()).isFalse();
    assertThat(optNullable.optional()).isTrue();
    assertThat(optNullable.nullable()).isTrue();
    assertThat(optNullable.type()).isEqualTo(new PrimitiveTypeRef("string"));

    // 5. Explicit undefined in union marks optional
    PropertyDef optUndefined = model.properties().get("opt_undefined");
    assertThat(optUndefined.optional()).isTrue();
    assertThat(optUndefined.nullable()).isFalse();

    // 6. Optional + nullable + undefined
    PropertyDef optAll = model.properties().get("opt_all");
    assertThat(optAll.optional()).isTrue();
    assertThat(optAll.nullable()).isTrue();
  }

  @Test
  @DisplayName("Import handles all primitive types, arrays, and maps")
  void testPrimitivesArraysAndMaps() {
    String dts =
        """
        export interface ComplexData {
          str: string;
          num: number;
          bool: boolean;
          big: bigint;
          dynamicAny: any;
          dynamicUnknown: unknown;
          tags: string[];
          matrix: number[][];
          items: Array<boolean>;
          roItems: ReadonlyArray<string>;
          metadata: Record<string, string>;
          countMap: Record<string, number>;
          indexMap: { [key: string]: number };
        }
        """;

    SchemaImportResult result = importer.importString(dts, "complex-types");
    assertThat(result.hasErrors()).isFalse();

    TypeDef typeDef = result.schemas().get("complex-types").types().get("ComplexData");
    assertThat(typeDef.properties().get("str").type()).isEqualTo(new PrimitiveTypeRef("string"));
    assertThat(typeDef.properties().get("num").type()).isEqualTo(new PrimitiveTypeRef("number"));
    assertThat(typeDef.properties().get("bool").type()).isEqualTo(new PrimitiveTypeRef("boolean"));
    assertThat(typeDef.properties().get("big").type()).isEqualTo(new PrimitiveTypeRef("bigint"));
    assertThat(typeDef.properties().get("dynamicAny").type()).isInstanceOf(DynamicTypeRef.class);
    assertThat(typeDef.properties().get("dynamicUnknown").type())
        .isInstanceOf(DynamicTypeRef.class);

    assertThat(typeDef.properties().get("tags").type())
        .isEqualTo(new ArrayTypeRef(new PrimitiveTypeRef("string")));
    assertThat(typeDef.properties().get("matrix").type())
        .isEqualTo(new ArrayTypeRef(new ArrayTypeRef(new PrimitiveTypeRef("number"))));
    assertThat(typeDef.properties().get("items").type())
        .isEqualTo(new ArrayTypeRef(new PrimitiveTypeRef("boolean")));
    assertThat(typeDef.properties().get("roItems").type())
        .isEqualTo(new ArrayTypeRef(new PrimitiveTypeRef("string")));

    assertThat(typeDef.properties().get("metadata").type())
        .isEqualTo(new MapTypeRef(new PrimitiveTypeRef("string"), new PrimitiveTypeRef("string")));
    assertThat(typeDef.properties().get("countMap").type())
        .isEqualTo(new MapTypeRef(new PrimitiveTypeRef("string"), new PrimitiveTypeRef("number")));
    assertThat(typeDef.properties().get("indexMap").type())
        .isEqualTo(new MapTypeRef(new PrimitiveTypeRef("string"), new PrimitiveTypeRef("number")));
  }

  @Test
  @DisplayName("Import handles TypeScript enum and string literal union types")
  void testEnumsAndLiteralUnions() {
    String dts =
        """
        export enum OrderStatus {
          PENDING,
          SHIPPED,
          DELIVERED = "DELIVERED"
        }

        export type Priority = "LOW" | "MEDIUM" | "HIGH";

        export interface Order {
          status: OrderStatus;
          priority: Priority;
        }
        """;

    SchemaImportResult result = importer.importString(dts, "orders");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("orders");
    assertThat(schema.types()).containsKeys("OrderStatus", "Priority", "Order");

    TypeDef statusDef = schema.types().get("OrderStatus");
    assertThat(statusDef.kind()).isEqualTo("enum");
    assertThat(statusDef.enumConstants()).containsExactly("PENDING", "SHIPPED", "DELIVERED");

    TypeDef priorityDef = schema.types().get("Priority");
    assertThat(priorityDef.kind()).isEqualTo("enum");
    assertThat(priorityDef.enumConstants()).containsExactly("LOW", "MEDIUM", "HIGH");

    TypeDef orderDef = schema.types().get("Order");
    PropertyDef statusProp = orderDef.properties().get("status");
    assertThat(statusProp.type())
        .isEqualTo(new EnumTypeRef("OrderStatus", List.of("PENDING", "SHIPPED", "DELIVERED")));

    PropertyDef priorityProp = orderDef.properties().get("priority");
    assertThat(priorityProp.type())
        .isEqualTo(new EnumTypeRef("Priority", List.of("LOW", "MEDIUM", "HIGH")));
  }

  @Test
  @DisplayName("Import synthesizes deterministic TypeDef for nested object literals")
  void testNestedObjectLiterals() {
    String dts =
        """
        export interface UserProfile {
          id: number;
          address: {
            city: string;
            zip: number;
          };
        }
        """;

    SchemaImportResult result = importer.importString(dts, "nested-obj");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("nested-obj");
    assertThat(schema.types()).containsKey("UserProfile");

    TypeDef userProfile = schema.types().get("UserProfile");
    PropertyDef addressProp = userProfile.properties().get("address");
    assertThat(addressProp.type()).isInstanceOf(NamedTypeRef.class);

    NamedTypeRef addrRef = (NamedTypeRef) addressProp.type();
    assertThat(schema.types()).containsKey(addrRef.name());

    TypeDef synthesized = schema.types().get(addrRef.name());
    assertThat(synthesized.properties()).containsKeys("city", "zip");
    assertThat(synthesized.properties().get("city").type())
        .isEqualTo(new PrimitiveTypeRef("string"));
    assertThat(synthesized.properties().get("zip").type())
        .isEqualTo(new PrimitiveTypeRef("number"));
  }

  @Test
  @DisplayName("Import handles recursive self-referential types without infinite loop")
  void testRecursiveTypes() {
    String dts =
        """
        export interface TreeNode {
          value: string;
          left?: TreeNode | null;
          right?: TreeNode | null;
        }
        """;

    SchemaImportResult result = importer.importString(dts, "tree");
    assertThat(result.hasErrors()).isFalse();

    TypeDef treeNode = result.schemas().get("tree").types().get("TreeNode");
    PropertyDef left = treeNode.properties().get("left");
    assertThat(left.type()).isEqualTo(new NamedTypeRef("TreeNode"));
    assertThat(left.nullable()).isTrue();
    assertThat(left.optional()).isTrue();
  }

  @Test
  @DisplayName("Interface inheritance copies properties from parent interfaces")
  void testInterfaceInheritance() {
    String dts =
        """
        export interface BaseEntity {
          id: number;
          createdAt: string;
        }

        export interface Customer extends BaseEntity {
          email: string;
        }
        """;

    SchemaImportResult result = importer.importString(dts, "inheritance");
    assertThat(result.hasErrors()).isFalse();

    TypeDef customer = result.schemas().get("inheritance").types().get("Customer");
    assertThat(customer.properties()).containsKeys("id", "createdAt", "email");
    assertThat(customer.properties().get("id").type()).isEqualTo(new PrimitiveTypeRef("number"));
    assertThat(customer.properties().get("email").type()).isEqualTo(new PrimitiveTypeRef("string"));
  }

  @Test
  @DisplayName("TemplateParameters interface populates root parameters")
  void testTemplateParametersMapping() {
    String dts =
        """
        export interface SimpleRecord {
          name: string;
        }

        export interface TemplateParameters {
          user: SimpleRecord;
          active: boolean;
        }
        """;

    SchemaImportResult result = importer.importString(dts, "tmpl");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("tmpl");
    assertThat(schema.parameters()).containsKeys("user", "active");
    assertThat(schema.parameters().get("user").type()).isEqualTo(new NamedTypeRef("SimpleRecord"));
    assertThat(schema.parameters().get("active").type()).isEqualTo(new PrimitiveTypeRef("boolean"));

    // TemplateParameters itself is excluded from types
    assertThat(schema.types()).containsKey("SimpleRecord");
    assertThat(schema.types()).doesNotContainKey("TemplateParameters");
  }

  @Test
  @DisplayName(
      "When TemplateParameters is absent, top-level interfaces populate parameters with"
          + " decapitalized names")
  void testTopLevelInterfaceFallbackParameters() {
    String dts =
        """
        export interface UserAccount {
          id: number;
          username: string;
        }
        """;

    SchemaImportResult result = importer.importString(dts, "fallback");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("fallback");
    assertThat(schema.types()).containsKey("UserAccount");

    // Both decapitalized name and dollar-prefixed name are registered for seamless resolution
    assertThat(schema.parameters()).containsKey("userAccount");
    assertThat(schema.parameters()).containsKey("$userAccount");

    ParameterDef p = schema.parameters().get("userAccount");
    assertThat(p.type()).isEqualTo(new NamedTypeRef("UserAccount"));
  }

  @Test
  @DisplayName("Comments and JSDoc documentation are cleanly extracted")
  void testCommentsAndJSDocExtraction() {
    String dts =
        """
        // Single line comment
        /* Multi line block comment */

        /** Customer entity */
        export interface Customer {
          /** Customer unique identifier */
          id: number;
          name: string; // trailing comment
        }
        """;

    SchemaImportResult result = importer.importString(dts, "docs");
    assertThat(result.hasErrors()).isFalse();

    TypeDef cust = result.schemas().get("docs").types().get("Customer");
    assertThat(cust.documentation()).isEqualTo("Customer entity");
    assertThat(cust.properties().get("id").documentation()).isEqualTo("Customer unique identifier");
  }

  @Test
  @DisplayName("Diagnostics emitted for unsupported TypeScript language features")
  void testUnsupportedFeaturesDiagnostics() {
    // 1. Conditional types
    String condDts = "export type IsString<T> = T extends string ? true : false;";
    SchemaImportResult condRes = importer.importString(condDts, "t1");
    assertThat(condRes.hasErrors()).isTrue();
    assertThat(condRes.diagnostics())
        .anyMatch(d -> d.code().equals(TypeScriptSchemaImporter.CODE_UNSUPPORTED_CONDITIONAL_TYPE));

    // 2. Mapped types
    String mappedDts = "export interface Mapped { [K in 'a' | 'b']: string; }";
    SchemaImportResult mappedRes = importer.importString(mappedDts, "t2");
    assertThat(mappedRes.hasErrors()).isTrue();
    assertThat(mappedRes.diagnostics())
        .anyMatch(d -> d.code().equals(TypeScriptSchemaImporter.CODE_UNSUPPORTED_MAPPED_TYPE));

    // 3. Function types
    String fnDts = "export interface Service { execute: (cmd: string) => void; }";
    SchemaImportResult fnRes = importer.importString(fnDts, "t3");
    assertThat(fnRes.hasErrors()).isTrue();
    assertThat(fnRes.diagnostics())
        .anyMatch(d -> d.code().equals(TypeScriptSchemaImporter.CODE_UNSUPPORTED_FUNCTION_TYPE));

    // 4. Template literal types
    String tmplDts = "export type EventName = `on_${string}`;";
    SchemaImportResult tmplRes = importer.importString(tmplDts, "t4");
    assertThat(tmplRes.hasErrors()).isTrue();
    assertThat(tmplRes.diagnostics())
        .anyMatch(d -> d.code().equals(TypeScriptSchemaImporter.CODE_UNSUPPORTED_TEMPLATE_LITERAL));

    // 5. NPM import statements
    String importDts = "import { User } from 'external-npm-pkg';";
    SchemaImportResult importRes = importer.importString(importDts, "t5");
    assertThat(importRes.hasErrors()).isTrue();
    assertThat(importRes.diagnostics())
        .anyMatch(d -> d.code().equals(TypeScriptSchemaImporter.CODE_UNSUPPORTED_NPM_IMPORT));
  }

  @Test
  @DisplayName("Syntax errors report precise line and column numbers")
  void testSyntaxErrorReporting() {
    String malformed =
        """
        export interface BadSyntax {
          prop: ;
        }
        """;

    SchemaImportResult result = importer.importString(malformed, "bad");
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).isNotEmpty();

    SchemaDiagnostic diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(TypeScriptSchemaImporter.CODE_SYNTAX_ERROR);
    assertThat(diag.line()).isEqualTo(2);
    assertThat(diag.column()).isGreaterThan(0);
    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.ERROR);
  }

  @Test
  @DisplayName("Deterministic outputs guarantee sorted parameters and types")
  void testDeterministicOutput() {
    String dts =
        """
        export interface Zeta { z: string; }
        export interface Alpha { a: string; }
        export interface Beta { b: string; }

        export interface TemplateParameters {
          z: Zeta;
          a: Alpha;
          b: Beta;
        }
        """;

    SchemaImportResult res1 = importer.importString(dts, "test-det");
    SchemaImportResult res2 = importer.importString(dts, "test-det");

    CanonicalSchema s1 = res1.schemas().get("test-det");
    CanonicalSchema s2 = res2.schemas().get("test-det");

    assertThat(s1.parameters().keySet()).containsExactly("a", "b", "z");
    assertThat(s1.types().keySet()).containsExactly("Alpha", "Beta", "Zeta");
    assertThat(s1.contractFingerprint()).isEqualTo(s2.contractFingerprint());
  }

  @Test
  @DisplayName("Golden 1: simple-user.d.ts matches expected canonical parameters and fingerprint")
  void testGoldenSimpleUser() throws Exception {
    String dts = loadGolden("simple-user.d.ts");
    SchemaImportResult result = importer.importString(dts, "users/simple-user.vtl");

    assertThat(result.hasErrors()).isFalse();
    CanonicalSchema schema = result.schemas().get("users/simple-user.vtl");
    assertThat(schema.templateId()).isEqualTo("users/simple-user.vtl");
    assertThat(schema.contractFingerprint())
        .isEqualTo(
            "vt-contract:v1:7e046f289b39592313e016420c7309ad8e3b9c334cf2f75de91f4a72c6353dcd");

    assertThat(schema.parameters()).containsKeys("active", "id", "username");
    ParameterDef active = schema.parameters().get("active");
    assertThat(active.type()).isEqualTo(new PrimitiveTypeRef("boolean"));
    assertThat(active.nullable()).isFalse();
    assertThat(active.required()).isTrue();

    ParameterDef id = schema.parameters().get("id");
    assertThat(id.type()).isEqualTo(new PrimitiveTypeRef("number"));
    assertThat(id.nullable()).isFalse();

    ParameterDef username = schema.parameters().get("username");
    assertThat(username.type()).isEqualTo(new PrimitiveTypeRef("string"));
    assertThat(username.nullable()).isFalse();
  }

  @Test
  @DisplayName(
      "Golden 2: order-details.d.ts matches expected SimpleRecord type and order parameter")
  void testGoldenOrderDetails() throws Exception {
    String dts = loadGolden("order-details.d.ts");
    SchemaImportResult result = importer.importString(dts, "orders/order-details.vtl");

    assertThat(result.hasErrors()).isFalse();
    CanonicalSchema schema = result.schemas().get("orders/order-details.vtl");
    assertThat(schema.contractFingerprint())
        .isEqualTo(
            "vt-contract:v1:66125111cc9d7aba61f61caf428b25cb9c36010b6f6f4ad87ee9c47257e8b8ac");

    assertThat(schema.parameters()).containsKey("order");
    ParameterDef order = schema.parameters().get("order");
    assertThat(order.type()).isEqualTo(new NamedTypeRef("SimpleRecord"));

    assertThat(schema.types()).containsKey("SimpleRecord");
    TypeDef simpleRecord = schema.types().get("SimpleRecord");
    assertThat(simpleRecord.properties()).containsKeys("active", "age", "name");

    assertThat(simpleRecord.properties().get("name").nullable()).isTrue();
    assertThat(simpleRecord.properties().get("age").nullable()).isFalse();
    assertThat(simpleRecord.properties().get("active").nullable()).isFalse();
  }

  @Test
  @DisplayName("Golden 3: tree-node.d.ts matches RecursiveNode and root parameter")
  void testGoldenTreeNode() throws Exception {
    String dts = loadGolden("tree-node.d.ts");
    SchemaImportResult result = importer.importString(dts, "tree/tree-node.vtl");

    assertThat(result.hasErrors()).isFalse();
    CanonicalSchema schema = result.schemas().get("tree/tree-node.vtl");
    assertThat(schema.contractFingerprint())
        .isEqualTo(
            "vt-contract:v1:ed12fed18f2f66af912bc71da91f0ac1e0be276d5315ad77c27b49c1db39876f");

    assertThat(schema.parameters()).containsKey("root");
    assertThat(schema.types()).containsKey("RecursiveNode");

    TypeDef node = schema.types().get("RecursiveNode");
    assertThat(node.properties().get("next").nullable()).isTrue();
    assertThat(node.properties().get("next").type()).isEqualTo(new NamedTypeRef("RecursiveNode"));
    assertThat(node.properties().get("value").nullable()).isTrue();
    assertThat(node.properties().get("value").type()).isEqualTo(new PrimitiveTypeRef("string"));
  }

  @Test
  @DisplayName("Golden 4: generic-catalog.d.ts matches arrays and nullability")
  void testGoldenGenericCatalog() throws Exception {
    String dts = loadGolden("generic-catalog.d.ts");
    SchemaImportResult result = importer.importString(dts, "catalog/generic-catalog.vtl");

    assertThat(result.hasErrors()).isFalse();
    CanonicalSchema schema = result.schemas().get("catalog/generic-catalog.vtl");
    assertThat(schema.contractFingerprint())
        .isEqualTo(
            "vt-contract:v1:f668af6bf9bca700a3d10e7ffc208debb7f9d442aa5c3d98aed616e783fbf9fa");

    assertThat(schema.parameters()).containsKeys("filter", "items", "tags");
    ParameterDef filter = schema.parameters().get("filter");
    assertThat(filter.nullable()).isTrue();
    assertThat(filter.type()).isEqualTo(new ArrayTypeRef(new NamedTypeRef("SimpleRecord")));

    ParameterDef tags = schema.parameters().get("tags");
    assertThat(tags.nullable()).isTrue();
    assertThat(tags.type()).isEqualTo(new ArrayTypeRef(new PrimitiveTypeRef("string")));
  }

  @Test
  @DisplayName("File import from Path works end-to-end")
  void testImportPath(@TempDir Path tempDir) throws Exception {
    Path dtsFile = tempDir.resolve("sample.d.ts");
    Files.writeString(dtsFile, "export interface Sample { id: number; }", StandardCharsets.UTF_8);

    SchemaImportResult result = importer.importPath(dtsFile);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.schemas()).containsKey("sample");
    CanonicalSchema schema = result.schemas().get("sample");
    assertThat(schema.types()).containsKey("Sample");
  }

  private String loadGolden(String filename) throws Exception {
    Path p = Path.of("src/test/resources/golden-dts", filename);
    if (Files.exists(p)) {
      return Files.readString(p, StandardCharsets.UTF_8);
    }
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream("golden-dts/" + filename)) {
      if (is == null) {
        throw new IllegalStateException("Golden resource not found: " + filename);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
