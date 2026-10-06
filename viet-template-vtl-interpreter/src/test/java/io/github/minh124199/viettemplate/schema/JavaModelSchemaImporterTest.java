package io.github.minh124199.viettemplate.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaModelSchemaImporterTest {

  private final JavaModelSchemaImporter importer = new JavaModelSchemaImporter();

  // Test models
  public record SimpleRecord(String name, int age, boolean active) {}

  public record NestedRecord(String id, SimpleRecord item) {}

  public enum Status {
    OPEN,
    IN_PROGRESS,
    RESOLVED
  }

  public record ContainerRecord(
      List<String> tags, Set<Integer> ids, Map<String, SimpleRecord> mapping) {}

  public record RecursiveNode(String value, RecursiveNode next) {}

  public static class SampleBean {
    private String code;
    private double balance;
    private boolean verified;

    public String getCode() {
      return code;
    }

    public double getBalance() {
      return balance;
    }

    public boolean isVerified() {
      return verified;
    }
  }

  @Test
  @DisplayName("Import record model maps components to parameters and expands record into types")
  void testRecordModelImport() {
    SchemaImportResult result = importer.importRecord(SimpleRecord.class, "users/simple.vtl");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("users/simple.vtl");
    assertThat(schema.templateId()).isEqualTo("users/simple.vtl");
    assertThat(schema.format()).isEqualTo(SchemaFormat.JAVA);
    assertThat(schema.contractFingerprint()).startsWith("java-model:v1:");

    assertThat(schema.parameters()).containsKeys("name", "age", "active");

    ParameterDef nameParam = schema.parameters().get("name");
    assertThat(nameParam.type()).isEqualTo(new ClassTypeRef("java.lang.String"));
    assertThat(nameParam.nullable()).isTrue();

    ParameterDef ageParam = schema.parameters().get("age");
    assertThat(ageParam.type()).isEqualTo(new PrimitiveTypeRef("int"));
    assertThat(ageParam.nullable()).isFalse();

    ParameterDef activeParam = schema.parameters().get("active");
    assertThat(activeParam.type()).isEqualTo(new PrimitiveTypeRef("boolean"));
    assertThat(activeParam.nullable()).isFalse();

    assertThat(schema.types()).containsKey(SimpleRecord.class.getName());
    TypeDef recordDef = schema.types().get(SimpleRecord.class.getName());
    assertThat(recordDef.kind()).isEqualTo("record");
    assertThat(recordDef.properties()).containsKeys("active", "age", "name");
  }

  @Test
  @DisplayName("Import JavaBean model extracts getter properties")
  void testJavaBeanModelImport() {
    SchemaImportResult result = importer.importBean(SampleBean.class, "account.vtl");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("account.vtl");
    assertThat(schema.parameters()).containsKeys("code", "balance", "verified");

    ParameterDef code = schema.parameters().get("code");
    assertThat(code.type()).isEqualTo(new ClassTypeRef("java.lang.String"));

    ParameterDef balance = schema.parameters().get("balance");
    assertThat(balance.type()).isEqualTo(new PrimitiveTypeRef("double"));
    assertThat(balance.nullable()).isFalse();

    ParameterDef verified = schema.parameters().get("verified");
    assertThat(verified.type()).isEqualTo(new PrimitiveTypeRef("boolean"));
    assertThat(verified.nullable()).isFalse();

    assertThat(schema.types()).containsKey(SampleBean.class.getName());
    TypeDef beanDef = schema.types().get(SampleBean.class.getName());
    assertThat(beanDef.kind()).isEqualTo("bean");
  }

  @Test
  @DisplayName("Import Enum registers symbols and enum TypeDef")
  void testEnumImport() {
    SchemaImportResult result = importer.importEnum(Status.class, "status.vtl");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("status.vtl");
    assertThat(schema.parameters()).containsKey("status");

    ParameterDef statusParam = schema.parameters().get("status");
    assertThat(statusParam.type()).isInstanceOf(EnumTypeRef.class);
    EnumTypeRef enumType = (EnumTypeRef) statusParam.type();
    assertThat(enumType.symbols()).containsExactly("OPEN", "IN_PROGRESS", "RESOLVED");

    assertThat(schema.types()).containsKey(Status.class.getName());
    TypeDef enumDef = schema.types().get(Status.class.getName());
    assertThat(enumDef.kind()).isEqualTo("enum");
    assertThat(enumDef.enumConstants()).containsExactly("OPEN", "IN_PROGRESS", "RESOLVED");
  }

  @Test
  @DisplayName("Import collections and maps maps to ArrayTypeRef and MapTypeRef")
  void testCollectionsAndMapsImport() {
    SchemaImportResult result = importer.importRecord(ContainerRecord.class, "container.vtl");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("container.vtl");
    assertThat(schema.parameters()).containsKeys("tags", "ids", "mapping");

    ParameterDef tags = schema.parameters().get("tags");
    assertThat(tags.type()).isEqualTo(new ArrayTypeRef(new ClassTypeRef("java.lang.String")));

    ParameterDef ids = schema.parameters().get("ids");
    assertThat(ids.type()).isEqualTo(new ArrayTypeRef(new ClassTypeRef("java.lang.Integer")));

    ParameterDef mapping = schema.parameters().get("mapping");
    assertThat(mapping.type())
        .isEqualTo(
            new MapTypeRef(
                new ClassTypeRef("java.lang.String"),
                new ClassTypeRef(SimpleRecord.class.getName())));
  }

  @Test
  @DisplayName("Import recursive model halts without infinite loop")
  void testRecursiveModelImport() {
    SchemaImportResult result = importer.importRecord(RecursiveNode.class, "tree.vtl");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("tree.vtl");
    assertThat(schema.types()).containsKey(RecursiveNode.class.getName());

    TypeDef nodeDef = schema.types().get(RecursiveNode.class.getName());
    assertThat(nodeDef.properties().get("next").type())
        .isEqualTo(new ClassTypeRef(RecursiveNode.class.getName()));
  }

  @Test
  @DisplayName("Import TemplateContract faithfully preserves parameters, fingerprint, and types")
  void testTemplateContractImport() {
    TemplateContract contract =
        TemplateContract.of(
            TemplateId.of("orders/details.vtl"),
            TemplateParameter.of("orderId", String.class, false),
            TemplateParameter.of("record", SimpleRecord.class, false));

    SchemaImportResult result = importer.importContract(contract);
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("orders/details.vtl");
    assertThat(schema.templateId()).isEqualTo("orders/details.vtl");
    assertThat(schema.format()).isEqualTo(SchemaFormat.CONTRACT);
    assertThat(schema.contractFingerprint()).isEqualTo(contract.fingerprint());

    assertThat(schema.parameters()).containsKeys("orderId", "record");
    assertThat(schema.parameters().get("orderId").nullable()).isFalse();
    assertThat(schema.parameters().get("record").nullable()).isFalse();

    assertThat(schema.types()).containsKey(SimpleRecord.class.getName());
  }

  @Test
  @DisplayName("Single model import via importModel creates named parameter and expands type")
  void testImportModel() {
    SchemaImportResult result = importer.importModel(SimpleRecord.class, "user", "user-tmpl.vtl");
    assertThat(result.hasErrors()).isFalse();

    CanonicalSchema schema = result.schemas().get("user-tmpl.vtl");
    assertThat(schema.parameters()).containsKey("user");
    assertThat(schema.parameters().get("user").type())
        .isEqualTo(new ClassTypeRef(SimpleRecord.class.getName()));
    assertThat(schema.types()).containsKey(SimpleRecord.class.getName());
  }

  @Test
  @DisplayName("CompositeSchemaImporter dispatches correctly based on SchemaFormat")
  void testCompositeSchemaImporter() {
    SchemaImporter composite = SchemaImporter.create();

    // 1. JSON Schema source
    SchemaImportResult jsonRes =
        composite.importSchemas(
            new SchemaImportRequest(
                new SchemaSource(
                    java.nio.file.Path.of(
                        "src/test/resources/golden-schemas/simple-user.vt-schema.json"),
                    SchemaFormat.JSON_SCHEMA)));
    assertThat(jsonRes.hasErrors()).isFalse();

    // 2. TypeScript source
    SchemaImportResult tsRes =
        composite.importSchemas(
            new SchemaImportRequest(
                new SchemaSource(
                    java.nio.file.Path.of("src/test/resources/golden-dts/simple-user.d.ts"),
                    SchemaFormat.TYPESCRIPT)));
    assertThat(tsRes.hasErrors()).isFalse();
  }

  @Test
  @DisplayName("Deterministic outputs guarantee sorted parameters and types")
  void testDeterminism() {
    SchemaImportResult res1 = importer.importRecord(SimpleRecord.class, "tmpl");
    SchemaImportResult res2 = importer.importRecord(SimpleRecord.class, "tmpl");

    CanonicalSchema s1 = res1.schemas().get("tmpl");
    CanonicalSchema s2 = res2.schemas().get("tmpl");

    assertThat(s1.parameters().keySet()).containsExactly("active", "age", "name");
    assertThat(s1.contractFingerprint()).isEqualTo(s2.contractFingerprint());
  }
}
