package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.api.TemplateType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateContractSchemaGeneratorTest {

  // --- Test Models ---

  public record SimpleRecord(String name, int age, boolean active) {}

  public record RecordWithNested(String id, SimpleRecord details) {}

  public record RecursiveNode(String value, RecursiveNode next) {}

  public static class RecursiveParent {
    private RecursiveChild child;

    public RecursiveChild getChild() {
      return child;
    }

    public void setChild(RecursiveChild child) {
      this.child = child;
    }
  }

  public static class RecursiveChild {
    private RecursiveParent parent;

    public RecursiveParent getParent() {
      return parent;
    }

    public void setParent(RecursiveParent parent) {
      this.parent = parent;
    }
  }

  public static class ShuffledPropertiesBean {
    public String getZeta() {
      return "z";
    }

    public String getAlpha() {
      return "a";
    }

    public String getMiddle() {
      return "m";
    }

    public String getBeta() {
      return "b";
    }
  }

  public static class SecurityTestModel {
    public String getClassLikeBusinessProperty() {
      return "allowed";
    }

    public ClassLoader getClassLoader() {
      return getClass().getClassLoader();
    }

    public Runtime getRuntime() {
      return Runtime.getRuntime();
    }

    public Process getProcess() {
      return null;
    }

    public String getName() {
      return "safe";
    }
  }

  public record SecurityTestRecord(
      String classLikeBusinessProperty,
      ClassLoader classLoader,
      Runtime runtime,
      Process process,
      String name) {}

  public static class OuterClass {
    public static class InnerClass {
      public String getInnerValue() {
        return "inner";
      }
    }

    public InnerClass getInner() {
      return new InnerClass();
    }
  }

  public static class GenericContainer<T> {
    private T item;

    public T getItem() {
      return item;
    }
  }

  // --- Tests ---

  @Nested
  @DisplayName("Type Mapping & Fidelity")
  class TypeMappingTests {

    @Test
    @DisplayName("Maps all Java primitive types without loss")
    void testPrimitiveTypes() {
      Class<?>[] primitives = {
        boolean.class,
        byte.class,
        short.class,
        char.class,
        int.class,
        long.class,
        float.class,
        double.class
      };

      for (Class<?> prim : primitives) {
        TemplateParameter param = TemplateParameter.of("val_" + prim.getName(), prim);
        TemplateContract contract = TemplateContract.of(TemplateId.of("primitives.vtl"), param);
        String json = TemplateContractSchemaGenerator.generateJson(contract);

        assertThat(json).contains("\"kind\": \"primitive\"");
        assertThat(json).contains("\"name\": \"" + prim.getName() + "\"");
      }
    }

    @Test
    @DisplayName("Maps reference classes and records")
    void testReferenceAndRecord() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("users/record.vtl"), TemplateParameter.of("user", SimpleRecord.class));
      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).contains("\"templateId\": \"users/record.vtl\"");
      assertThat(json).contains("\"name\": \"user\"");
      assertThat(json).contains("\"name\": \"name\"");
      assertThat(json).contains("\"name\": \"age\"");
      assertThat(json).contains("\"name\": \"active\"");
      assertThat(json).contains("\"kind\": \"record\"");
      assertThat(json).contains("\"name\": \"java.lang.String\"");
    }

    @Test
    @DisplayName("Maps parameterized generic types and nested generics")
    void testGenericTypes() {
      TemplateType listString =
          TemplateType.parameterized(List.class, TemplateType.of(String.class));
      TemplateType mapStringUser =
          TemplateType.parameterized(
              Map.class, TemplateType.of(String.class), TemplateType.of(SimpleRecord.class));
      TemplateType nestedGeneric = TemplateType.parameterized(List.class, mapStringUser);

      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("generics.vtl"),
              TemplateParameter.of("list", listString),
              TemplateParameter.of("map", mapStringUser),
              TemplateParameter.of("nested", nestedGeneric));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).contains("\"kind\": \"parameterized\"");
      assertThat(json).contains("\"rawType\": \"java.util.List\"");
      assertThat(json).contains("\"rawType\": \"java.util.Map\"");
      assertThat(json).contains(SimpleRecord.class.getName());
    }

    @Test
    @DisplayName("Maps single- and multi-dimensional arrays")
    void testArrays() {
      TemplateType intArray = TemplateType.of(int[].class);
      TemplateType stringArray = TemplateType.of(String[].class);
      TemplateType multiArray = TemplateType.of(SimpleRecord[][].class);

      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("arrays.vtl"),
              TemplateParameter.of("ints", intArray),
              TemplateParameter.of("strings", stringArray),
              TemplateParameter.of("matrix", multiArray));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).contains("\"kind\": \"array\"");
      assertThat(json).contains("\"name\": \"int\"");
      assertThat(json).contains("\"name\": \"java.lang.String\"");
      assertThat(json).contains(SimpleRecord.class.getName());
    }

    @Test
    @DisplayName("Maps wildcard bounds: ?, ? extends T, ? super T")
    void testWildcards() {
      TemplateType wildcardUnbounded = TemplateType.wildcard();
      TemplateType wildcardExtends =
          TemplateType.wildcardExtends(TemplateType.of(SimpleRecord.class));
      TemplateType wildcardSuper = TemplateType.wildcardSuper(TemplateType.of(SimpleRecord.class));

      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("wildcards.vtl"),
              TemplateParameter.of(
                  "any", TemplateType.parameterized(List.class, wildcardUnbounded)),
              TemplateParameter.of(
                  "extendsVal", TemplateType.parameterized(List.class, wildcardExtends)),
              TemplateParameter.of(
                  "superVal", TemplateType.parameterized(List.class, wildcardSuper)));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).contains("\"kind\": \"wildcard\"");
      assertThat(json).contains("\"boundKind\": \"unbounded\"");
      assertThat(json).contains("\"boundKind\": \"extends\"");
      assertThat(json).contains("\"boundKind\": \"super\"");
      assertThat(json).contains(SimpleRecord.class.getName());
    }

    @Test
    @DisplayName("Maps named / symbolic type variables")
    void testNamedTypes() {
      TemplateType typeVarT = TemplateType.named("T");
      TemplateType typeVarWithArgs =
          TemplateType.named("Custom", List.of(TemplateType.of(String.class)));

      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("named.vtl"),
              TemplateParameter.of("t", typeVarT),
              TemplateParameter.of("custom", typeVarWithArgs));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).contains("\"kind\": \"named\"");
      assertThat(json).contains("\"name\": \"T\"");
      assertThat(json).contains("\"name\": \"Custom\"");
    }
  }

  @Nested
  @DisplayName("Nullability and Optionality")
  class NullabilityTests {

    @Test
    @DisplayName("Preserves nullable independently from optional")
    void testNullabilityAndOptionality() {
      TemplateParameter nullableReq = TemplateParameter.of("nickname", String.class, true);
      TemplateParameter nonNullReq = TemplateParameter.of("id", String.class, false);

      TemplateContract contract =
          TemplateContract.of(TemplateId.of("nullability.vtl"), nullableReq, nonNullReq);

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).contains("\"name\": \"nickname\"");
      assertThat(json).contains("\"name\": \"id\"");
      assertThat(json).contains("\"nullable\": true");
      assertThat(json).contains("\"nullable\": false");
      assertThat(json).contains("\"optional\": false");
    }
  }

  @Nested
  @DisplayName("Graph Expansion & Recursion")
  class GraphExpansionTests {

    @Test
    @DisplayName(
        "Self-referential recursive record (Node -> Node) terminates and outputs single type"
            + " definition")
    void testSelfReferentialRecursion() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("tree.vtl"), TemplateParameter.of("root", RecursiveNode.class));

      assertThatCode(
              () -> {
                String json = TemplateContractSchemaGenerator.generateJson(contract);
                assertThat(json).contains(RecursiveNode.class.getName());
                assertThat(json).contains("\"name\": \"next\"");
                assertThat(json).contains("\"name\": \"value\"");
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mutual recursion (Parent -> Child -> Parent) terminates and discovers both types")
    void testMutualRecursion() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("family.vtl"), TemplateParameter.of("parent", RecursiveParent.class));

      assertThatCode(
              () -> {
                String json = TemplateContractSchemaGenerator.generateJson(contract);
                assertThat(json).contains(RecursiveParent.class.getName());
                assertThat(json).contains(RecursiveChild.class.getName());
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Standard library types are not expanded into hundreds of methods")
    void testStandardLibraryNotExpanded() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("jdk.vtl"),
              TemplateParameter.of("string", String.class),
              TemplateParameter.of(
                  "list", TemplateType.parameterized(List.class, TemplateType.of(String.class))),
              TemplateParameter.of("time", java.time.Instant.class));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).contains("\"types\": {}");
      assertThat(json).doesNotContain("java.lang.String\":");
      assertThat(json).doesNotContain("java.util.List\":");
      assertThat(json).doesNotContain("java.time.Instant\":");
    }

    @Test
    @DisplayName("Nested classes (Outer.Inner) are expanded correctly")
    void testNestedClasses() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("nested.vtl"), TemplateParameter.of("outer", OuterClass.class));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).contains(OuterClass.class.getName());
      assertThat(json).contains(OuterClass.InnerClass.class.getName());
      assertThat(json).contains("\"name\": \"innerValue\"");
    }
  }

  @Nested
  @DisplayName("Security & Denied Members")
  class SecurityTests {

    @Test
    @DisplayName("Filters denied methods and classes on JavaBeans at discovery time")
    void testSecurityFilteringOnBean() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("security.vtl"), TemplateParameter.of("sec", SecurityTestModel.class));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      // Allowed business properties
      assertThat(json).contains("\"name\": \"name\"");
      assertThat(json).contains("\"name\": \"classLikeBusinessProperty\"");

      // Denied members
      assertThat(json).doesNotContain("\"name\": \"classLoader\"");
      assertThat(json).doesNotContain("\"name\": \"runtime\"");
      assertThat(json).doesNotContain("\"name\": \"process\"");
      assertThat(json).doesNotContain("\"name\": \"class\"");
      assertThat(json).doesNotContain("java.lang.ClassLoader");
      assertThat(json).doesNotContain("java.lang.Runtime");
      assertThat(json).doesNotContain("java.lang.Process");
    }

    @Test
    @DisplayName("Filters denied components on Records at discovery time")
    void testSecurityFilteringOnRecord() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("security_rec.vtl"),
              TemplateParameter.of("rec", SecurityTestRecord.class));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      // Allowed business properties
      assertThat(json).contains("\"name\": \"name\"");
      assertThat(json).contains("\"name\": \"classLikeBusinessProperty\"");

      // Denied members must never appear
      assertThat(json).doesNotContain("\"name\": \"classLoader\"");
      assertThat(json).doesNotContain("\"name\": \"runtime\"");
      assertThat(json).doesNotContain("\"name\": \"process\"");
      assertThat(json).doesNotContain("java.lang.ClassLoader");
      assertThat(json).doesNotContain("java.lang.Runtime");
      assertThat(json).doesNotContain("java.lang.Process");
    }

    @Test
    @DisplayName("Security filtering occurs before serialization")
    void testSecurityFilteringAtDiscovery() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("sec_env.vtl"), TemplateParameter.of("model", SecurityTestModel.class));

      TemplateContractSchemaGenerator.SchemaEnvelope envelope =
          TemplateContractSchemaGenerator.extractSchema(contract);

      TemplateContractSchemaGenerator.TypeDef def =
          envelope.types().get(SecurityTestModel.class.getName());
      assertThat(def).isNotNull();

      List<String> propNames =
          def.properties().stream().map(TemplateContractSchemaGenerator.PropertyDef::name).toList();
      assertThat(propNames).containsExactly("classLikeBusinessProperty", "name");
    }
  }

  @Nested
  @DisplayName("Deterministic Ordering & Byte Equivalence")
  class DeterminismTests {

    @Test
    @DisplayName(
        "Shuffled JavaBean accessors are sorted deterministically: alpha, beta, middle, zeta")
    void testPropertySorting() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("shuffled.vtl"),
              TemplateParameter.of("item", ShuffledPropertiesBean.class));

      TemplateContractSchemaGenerator.SchemaEnvelope env =
          TemplateContractSchemaGenerator.extractSchema(contract);

      TemplateContractSchemaGenerator.TypeDef def =
          env.types().get(ShuffledPropertiesBean.class.getName());
      assertThat(def).isNotNull();

      List<String> propNames =
          def.properties().stream().map(TemplateContractSchemaGenerator.PropertyDef::name).toList();
      assertThat(propNames).containsExactly("alpha", "beta", "middle", "zeta");
    }

    @Test
    @DisplayName("1000 generations produce identical bytes and identical SHA-256 hash")
    void test1000GenerationsDeterminism() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("reports/summary.vtl"),
              TemplateParameter.of("zeta", String.class),
              TemplateParameter.of("alpha", Integer.class),
              TemplateParameter.of("record", SimpleRecord.class),
              TemplateParameter.of("tree", RecursiveNode.class),
              TemplateParameter.of("parent", RecursiveParent.class));

      String firstJson = TemplateContractSchemaGenerator.generateJson(contract);
      byte[] firstBytes = firstJson.getBytes(StandardCharsets.UTF_8);

      MessageDigest md;
      try {
        md = MessageDigest.getInstance("SHA-256");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      String firstHash = HexFormat.of().formatHex(md.digest(firstBytes));

      for (int i = 0; i < 1000; i++) {
        String currentJson = TemplateContractSchemaGenerator.generateJson(contract);
        byte[] currentBytes = currentJson.getBytes(StandardCharsets.UTF_8);

        assertThat(currentBytes)
            .as("Iteration %d must produce identical byte array", i)
            .isEqualTo(firstBytes);

        md.reset();
        String currentHash = HexFormat.of().formatHex(md.digest(currentBytes));
        assertThat(currentHash)
            .as("Iteration %d must produce identical SHA-256 hash", i)
            .isEqualTo(firstHash);
      }
    }

    @Test
    @DisplayName("Output uses UTF-8 and LF without CRLF")
    void testLineEndingAndCharset() {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("formatting.vtl"), TemplateParameter.of("title", String.class));

      String json = TemplateContractSchemaGenerator.generateJson(contract);

      assertThat(json).doesNotContain("\r");
      assertThat(json).endsWith("\n");
    }

    @Test
    @DisplayName("File writer creates correct directory structure and .vt-schema.json extension")
    void testFileWriter(@TempDir Path tempDir) throws Exception {
      TemplateContract contract =
          TemplateContract.of(
              TemplateId.of("admin/users/details.vtl"),
              TemplateParameter.of("user", SimpleRecord.class));

      Path written = TemplateContractSchemaGenerator.generateSchemaFile(contract, tempDir);

      assertThat(written).isRegularFile();
      assertThat(written.getFileName().toString()).isEqualTo("details.vt-schema.json");
      assertThat(written.getParent().getFileName().toString()).isEqualTo("users");
      assertThat(written.getParent().getParent().getFileName().toString()).isEqualTo("admin");

      String content = Files.readString(written, StandardCharsets.UTF_8);
      assertThat(content).contains("\"templateId\": \"admin/users/details.vtl\"");
      assertThat(content).contains("\"format\": \"viet-template-contract-schema/1\"");
      assertThat(content).contains("\"schemaVersion\": 1");
    }
  }
}
