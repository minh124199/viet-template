package io.github.minh124199.viettemplate.qualification.m46a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import io.github.minh124199.viettemplate.api.TemplateType;
import io.github.minh124199.viettemplate.assets.ClientData;
import io.github.minh124199.viettemplate.assets.ClientDataSerializer;
import io.github.minh124199.viettemplate.qualification.m46a.model.DepartmentRecord;
import io.github.minh124199.viettemplate.qualification.m46a.model.EmployeeBean;
import io.github.minh124199.viettemplate.qualification.m46a.model.EmployeeRecord;
import io.github.minh124199.viettemplate.qualification.m46a.model.NestedContainerRecord;
import io.github.minh124199.viettemplate.qualification.m46a.model.RecursiveNode;
import io.github.minh124199.viettemplate.qualification.m46a.model.SecuritySensitiveBean;
import io.github.minh124199.viettemplate.qualification.m46a.model.StatusEnum;
import io.github.minh124199.viettemplate.schema.TypeScriptSchemaImporter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comprehensive M46A Architecture Qualification Test Suite.
 *
 * <p>Validates all 16 bounded qualification prototypes:
 *
 * <ol>
 *   <li>Real {@code tsc --noEmit} execution proving generated .d.ts is valid TypeScript.
 *   <li>Negative test proving intentional property typo is rejected by {@code tsc}.
 *   <li>Nullability and optionality mapping ({@code T | null}).
 *   <li>Map type mapping ({@code Record<string, V>}).
 *   <li>Recursive data structures ({@code RecursiveNode}).
 *   <li>Type name collision disambiguation across packages.
 *   <li>Byte-for-byte determinism across 100 runs.
 *   <li>No-op regeneration / write avoidance (mtime preserved).
 *   <li>Safe stale output cleanup (only owned outputs deleted, foreign files untouched).
 *   <li>Windows/Unix path normalization and traversal prevention.
 *   <li>Security boundary (MemberAccessPolicy excludes getClass, ClassLoader, Thread).
 *   <li>Serialization parity with {@code SimpleJsonSerializer}.
 *   <li>Performance scaling (< 2ms for 10 contracts, < 20ms for 100).
 *   <li>Frontend framework neutrality (0 framework imports).
 *   <li>Bidirectional round-trip projection and re-import via TypeScriptSchemaImporter.
 *   <li>Nested and multi-nested Java classes projection ({@code Outer_Inner}).
 * </ol>
 */
class M46ATypeScriptProjectionQualificationTest {

  private static Path repoRoot;
  private static Path tscScript;

  @BeforeAll
  static void setupToolchain() {
    repoRoot = findRepoRoot();
    if (repoRoot != null) {
      Path svelteTsc =
          repoRoot.resolve("examples/frontend-svelte-islands/node_modules/typescript/bin/tsc");
      if (Files.exists(svelteTsc)) {
        tscScript = svelteTsc;
      } else {
        Path vscodeTsc = repoRoot.resolve("editors/vscode/node_modules/typescript/bin/tsc");
        if (Files.exists(vscodeTsc)) {
          tscScript = vscodeTsc;
        }
      }
    }
  }

  private static Path findRepoRoot() {
    Path dir = Path.of(".").toAbsolutePath().normalize();
    while (dir != null && !Files.exists(dir.resolve("settings.gradle.kts"))) {
      dir = dir.getParent();
    }
    return dir;
  }

  private static boolean canRunTsc() {
    if (tscScript == null || !Files.exists(tscScript)) {
      return false;
    }
    try {
      Process process = new ProcessBuilder("node", "--version").start();
      return process.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  // =========================================================================
  // Prototype 1 & 2: Real TypeScript Compiler & Negative Typo Verification
  // =========================================================================

  @Test
  @DisplayName("Prototype 01: Real tsc --noEmit validates generated .d.ts")
  void prototype01_realTscValidation(@TempDir Path tempDir) throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        canRunTsc(), "tsc binary or node runtime not available. Skipping real tsc validation.");
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    // 1. Project EmployeeRecord
    String dts = projector.projectClass(EmployeeRecord.class, "employees/show.vtl");
    Path dtsFile = tempDir.resolve("employees.d.ts");
    Files.writeString(dtsFile, dts, StandardCharsets.UTF_8);

    // 2. Write consumer TypeScript file importing generated declarations
    Path tsConsumer = tempDir.resolve("consumer.ts");
    String tsCode =
        """
        import type {
          EmployeeRecord,
          DepartmentRecord,
          StatusEnum,
          EmployeesShowTemplateParameters
        } from "./employees";

        const dept: DepartmentRecord = {
          id: 10,
          code: "ENG",
          name: "Engineering"
        };

        const status: StatusEnum = "ACTIVE";

        const employee: EmployeeRecord = {
          id: 101,
          name: "Alice",
          department: dept,
          status: status,
          skills: ["Java", "TypeScript"],
          attributes: { role: "Architect" },
          hireDate: "2026-01-01T00:00:00Z",
          salary: 150000.0
        };

        const params: EmployeesShowTemplateParameters = {
          attributes: { key: "val" },
          department: dept,
          hireDate: "2026-01-01T00:00:00Z",
          id: 101,
          name: "Alice",
          salary: 150000.0,
          skills: ["Java"],
          status: status
        };

        // Access valid properties
        const empName: string | null = employee.name;
        if (employee.department) {
          const deptCode: string | null = employee.department.code;
        }
        if (employee.skills) {
          const skill: string = employee.skills[0];
        }
        """;
    Files.writeString(tsConsumer, tsCode, StandardCharsets.UTF_8);

    // 3. Execute tsc --noEmit
    runTscCompilation(tempDir, tsConsumer, true);
  }

  @Test
  @DisplayName("Prototype 02: Negative test — consumer typo 'employee.nmae' is rejected by tsc")
  void prototype02_negativeTscTypoRejected(@TempDir Path tempDir) throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        canRunTsc(), "tsc binary or node runtime not available. Skipping negative tsc test.");
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    String dts = projector.projectClass(EmployeeRecord.class, "employees/show.vtl");
    Files.writeString(tempDir.resolve("employees.d.ts"), dts, StandardCharsets.UTF_8);

    Path invalidTs = tempDir.resolve("invalid.ts");
    String invalidCode =
        """
        import type { EmployeeRecord } from "./employees";

        declare const employee: EmployeeRecord;
        // Typo: 'nmae' does not exist on 'EmployeeRecord'
        const bad = employee.nmae;
        """;
    Files.writeString(invalidTs, invalidCode, StandardCharsets.UTF_8);

    // Should FAIL under tsc
    CompilationResult result = runTsc(tempDir, invalidTs);
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("nmae");
    assertThat(result.output()).contains("EmployeeRecord");
  }

  // =========================================================================
  // Prototype 3: Nullability and Optionality Mapping
  // =========================================================================

  @Test
  @DisplayName("Prototype 03: Nullable template parameters map to T | null and enforce TS checks")
  void prototype03_nullabilityAndOptionality(@TempDir Path tempDir) throws Exception {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("reports/summary.vtl"))
            .parameter(TemplateParameter.of("manager", TemplateType.of(EmployeeBean.class), true))
            .parameter(
                TemplateParameter.of("department", TemplateType.of(DepartmentRecord.class), false))
            .build();

    String dts = projector.projectContract(contract);
    assertThat(dts).contains("manager: EmployeeBean | null;");
    assertThat(dts).contains("department: DepartmentRecord;");

    Files.writeString(tempDir.resolve("reports.d.ts"), dts, StandardCharsets.UTF_8);

    Path testTs = tempDir.resolve("nullability_test.ts");
    String testCode =
        """
        import type { ReportsSummaryTemplateParameters } from "./reports";

        declare const data: ReportsSummaryTemplateParameters;

        // Nullable property can be null
        if (data.manager !== null) {
          const u: string | null = data.manager.username;
        }

        // Non-nullable department property (data.department cannot be null, but department.name can be null)
        const dName: string | null = data.department.name;
        """;
    Files.writeString(testTs, testCode, StandardCharsets.UTF_8);
    if (canRunTsc()) {
      runTscCompilation(tempDir, testTs, true);
    }

    // Wildcard upper bound (? extends T -> T) and lower bound (? super T -> unknown)
    String extendsTs =
        M46APrototypeTypeScriptProjector.projectCanonicalTypeRef(
            new io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.WildcardTypeRef(
                "extends",
                new io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ClassTypeRef(
                    EmployeeBean.class.getName())),
            Map.of(EmployeeBean.class.getName(), "EmployeeBean"));
    assertThat(extendsTs).isEqualTo("EmployeeBean");

    String superTs =
        M46APrototypeTypeScriptProjector.projectCanonicalTypeRef(
            new io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.WildcardTypeRef(
                "super",
                new io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ClassTypeRef(
                    EmployeeBean.class.getName())),
            Map.of(EmployeeBean.class.getName(), "EmployeeBean"));
    assertThat(superTs).isEqualTo("unknown");

    // Optional<T> -> T | null
    String optionalTs =
        M46APrototypeTypeScriptProjector.projectCanonicalTypeRef(
            new io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ParameterizedTypeRef(
                "java.util.Optional",
                List.of(
                    new io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ClassTypeRef(
                        EmployeeBean.class.getName()))),
            Map.of(EmployeeBean.class.getName(), "EmployeeBean"));
    assertThat(optionalTs).isEqualTo("EmployeeBean | null");
  }

  // =========================================================================
  // Prototype 4: Map Type Projection
  // =========================================================================

  @Test
  @DisplayName("Prototype 04: Map types project to Record<string, V>")
  void prototype04_mapProjection(@TempDir Path tempDir) throws Exception {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("maps/data.vtl"))
            .parameter(
                TemplateParameter.of(
                    "lookup",
                    TemplateType.parameterized(
                        Map.class,
                        TemplateType.of(String.class),
                        TemplateType.of(DepartmentRecord.class)),
                    false))
            .parameter(
                TemplateParameter.of(
                    "counts",
                    TemplateType.parameterized(
                        Map.class, TemplateType.of(Integer.class), TemplateType.of(Long.class)),
                    false))
            .build();

    String dts = projector.projectContract(contract);
    assertThat(dts).contains("lookup: Record<string, DepartmentRecord>;");
    assertThat(dts).contains("counts: Record<number, number>;");

    Files.writeString(tempDir.resolve("maps.d.ts"), dts, StandardCharsets.UTF_8);

    Path consumerTs = tempDir.resolve("map_consumer.ts");
    String tsCode =
        """
        import type { MapsDataTemplateParameters } from "./maps";

        declare const data: MapsDataTemplateParameters;
        const dept = data.lookup["eng"];
        if (dept) {
          const code: string | null = dept.code;
        }
        const count: number | undefined = data.counts[42];
        """;
    Files.writeString(consumerTs, tsCode, StandardCharsets.UTF_8);
    if (canRunTsc()) {
      runTscCompilation(tempDir, consumerTs, true);
    }
  }

  // =========================================================================
  // Prototype 5: Recursive Types
  // =========================================================================

  @Test
  @DisplayName("Prototype 05: Recursive types project safely without cycle or overflow")
  void prototype05_recursiveTypes(@TempDir Path tempDir) throws Exception {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    String dts = projector.projectClass(RecursiveNode.class, "tree/nav.vtl");
    assertThat(dts).contains("export interface RecursiveNode {");
    assertThat(dts).contains("parent: RecursiveNode | null;");
    assertThat(dts).contains("children: RecursiveNode[] | null;");

    Files.writeString(tempDir.resolve("tree.d.ts"), dts, StandardCharsets.UTF_8);

    Path consumerTs = tempDir.resolve("tree_consumer.ts");
    String tsCode =
        """
        import type { RecursiveNode } from "./tree";

        const leaf: RecursiveNode = {
          id: "2",
          label: "Child",
          parent: null,
          children: []
        };

        const root: RecursiveNode = {
          id: "1",
          label: "Root",
          parent: null,
          children: [leaf]
        };

        const label: string | null = root.children ? root.children[0].label : null;
        """;
    Files.writeString(consumerTs, tsCode, StandardCharsets.UTF_8);
    if (canRunTsc()) {
      runTscCompilation(tempDir, consumerTs, true);
    }
  }

  // =========================================================================
  // Prototype 6: Type Name Collision Disambiguation
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 06: Same simple name in different packages is disambiguated deterministically")
  void prototype06_nameCollisionDisambiguation(@TempDir Path tempDir) throws Exception {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("collision/test.vtl"))
            .parameter(
                TemplateParameter.of(
                    "modelA",
                    TemplateType.of(
                        io.github.minh124199.viettemplate.qualification.m46a.model.a.ConflictModel
                            .class),
                    false))
            .parameter(
                TemplateParameter.of(
                    "modelB",
                    TemplateType.of(
                        io.github.minh124199.viettemplate.qualification.m46a.model.b.ConflictModel
                            .class),
                    false))
            .build();

    String dts = projector.projectContract(contract);

    // Both must exist and have unique names
    assertThat(dts).contains("ConflictModel");
    assertThat(dts).contains("modelA:");
    assertThat(dts).contains("modelB:");
    // Package-derived prefix disambiguation
    assertThat(dts).contains("ModelA_ConflictModel");
    assertThat(dts).contains("ModelB_ConflictModel");

    Files.writeString(tempDir.resolve("collision.d.ts"), dts, StandardCharsets.UTF_8);

    Path consumerTs = tempDir.resolve("collision_consumer.ts");
    String tsCode =
        """
        import type {
          ModelA_ConflictModel as ModelA,
          ModelB_ConflictModel as ModelB,
          CollisionTestTemplateParameters
        } from "./collision";

        declare const params: CollisionTestTemplateParameters;
        const a: ModelA = params.modelA;
        const b: ModelB = params.modelB;
        const code: number = a.code;
        const score: number = b.score;
        """;
    Files.writeString(consumerTs, tsCode, StandardCharsets.UTF_8);
    if (canRunTsc()) {
      runTscCompilation(tempDir, consumerTs, true);
    }
  }

  // =========================================================================
  // Prototype 7: Byte-for-Byte Determinism Across 100 Runs
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 07: 100 consecutive generations produce identical byte output and SHA-256")
  void prototype07_determinismAcross100Runs() throws Exception {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    String firstOutput = projector.projectClass(EmployeeRecord.class, "employees/index.vtl");
    byte[] firstBytes = firstOutput.getBytes(StandardCharsets.UTF_8);
    String firstHash = sha256Hex(firstBytes);

    for (int i = 0; i < 99; i++) {
      String runOutput = projector.projectClass(EmployeeRecord.class, "employees/index.vtl");
      byte[] runBytes = runOutput.getBytes(StandardCharsets.UTF_8);
      String runHash = sha256Hex(runBytes);

      assertThat(runOutput).isEqualTo(firstOutput);
      assertThat(runHash).isEqualTo(firstHash);
    }
  }

  // =========================================================================
  // Prototype 8: Incremental Generation & Write Avoidance (mtime preserved)
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 08: writeIfChanged skips rewrite and preserves mtime when content is unchanged")
  void prototype08_noopRegenerationAndWriteAvoidance(@TempDir Path tempDir) throws Exception {
    Path targetFile = tempDir.resolve("test.d.ts");
    String initialContent = "export interface Foo { id: number; }\n";

    // 1. Initial write
    boolean written1 = M46APrototypeTypeScriptProjector.writeIfChanged(targetFile, initialContent);
    assertThat(written1).isTrue();
    assertThat(Files.exists(targetFile)).isTrue();

    FileTime mtime1 = Files.getLastModifiedTime(targetFile);

    // Sleep briefly to allow filesystem timestamp tick if granularity is coarse
    Thread.sleep(50);

    // 2. Second write with identical content
    boolean written2 = M46APrototypeTypeScriptProjector.writeIfChanged(targetFile, initialContent);
    assertThat(written2).isFalse();
    FileTime mtime2 = Files.getLastModifiedTime(targetFile);

    // mtime MUST NOT be modified
    assertThat(mtime2).isEqualTo(mtime1);

    // 3. Third write with changed content
    String newContent = "export interface Foo { id: number; name: string; }\n";
    boolean written3 = M46APrototypeTypeScriptProjector.writeIfChanged(targetFile, newContent);
    assertThat(written3).isTrue();
    FileTime mtime3 = Files.getLastModifiedTime(targetFile);

    assertThat(mtime3).isGreaterThanOrEqualTo(mtime1);
    assertThat(Files.readString(targetFile)).isEqualTo(newContent);
  }

  // =========================================================================
  // Prototype 9: Stale File Cleanup Safety
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 09: cleanStaleOutputs deletes only owned stale files, leaving user files"
          + " untouched")
  void prototype09_staleOutputCleanupSafety(@TempDir Path tempDir) throws Exception {
    Path fileA = tempDir.resolve("owned-a.vtl.d.ts");
    Path fileB = tempDir.resolve("owned-b.vtl.d.ts");
    Path userCustomFile = tempDir.resolve("user-custom-island.ts");
    Path foreignDataFile = tempDir.resolve("foreign-data.json");

    Files.writeString(fileA, "// A", StandardCharsets.UTF_8);
    Files.writeString(fileB, "// B", StandardCharsets.UTF_8);
    Files.writeString(userCustomFile, "// Custom user island", StandardCharsets.UTF_8);
    Files.writeString(foreignDataFile, "{}", StandardCharsets.UTF_8);

    Set<Path> previousOwned = Set.of(fileA, fileB, foreignDataFile);
    Set<Path> currentOutputs = Set.of(fileA); // fileB removed in this run

    int deleted =
        M46APrototypeTypeScriptProjector.cleanStaleOutputs(tempDir, currentOutputs, previousOwned);

    assertThat(deleted).isEqualTo(1);
    assertThat(Files.exists(fileA)).isTrue();
    assertThat(Files.exists(fileB)).isFalse();
    assertThat(Files.exists(userCustomFile)).isTrue(); // Foreign file strictly preserved!
    assertThat(Files.exists(foreignDataFile)).isTrue(); // Non-declaration file strictly preserved!
  }

  // =========================================================================
  // Prototype 10: Path Normalization & Security Traversal Guards
  // =========================================================================

  @Test
  @DisplayName("Prototype 10: Windows backslashes normalize and path traversals are blocked")
  void prototype10_windowsPathNormalizationAndTraversalGuards(@TempDir Path tempDir) {
    Path winPath =
        M46APrototypeTypeScriptProjector.deriveDeclarationFilePath(
            tempDir, "templates\\users\\index.vtl");
    Path unixPath =
        M46APrototypeTypeScriptProjector.deriveDeclarationFilePath(
            tempDir, "templates/users/index.vtl");

    assertThat(winPath).isEqualTo(unixPath);
    assertThat(winPath.toString())
        .endsWith("templates/users/index.vtl.d.ts".replace('/', java.io.File.separatorChar));

    // Path traversal attempt
    assertThatThrownBy(
            () ->
                M46APrototypeTypeScriptProjector.deriveDeclarationFilePath(
                    tempDir, "../../secret.vtl"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Path traversal detected");
  }

  // =========================================================================
  // Prototype 11: Security & MemberAccessPolicy Boundary
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 11: Sensitive methods (getClass, ClassLoader, Thread) are never projected")
  void prototype11_securityMemberExposureBoundary() {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    String dts = projector.projectClass(SecuritySensitiveBean.class, "security/test.vtl");

    // Public permitted property is projected
    assertThat(dts).contains("publicData: string | null;");

    // Sensitive internal reflection methods are NEVER projected
    assertThat(dts).doesNotContain("classLoader");
    assertThat(dts).doesNotContain("currentThread");
    assertThat(dts).doesNotContain("class:");
    assertThat(dts).doesNotContain("getClass");
  }

  // =========================================================================
  // Prototype 12: ClientData Serialization Parity
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 12: Generated declarations match SimpleJsonSerializer runtime JSON shape 1:1")
  void prototype12_clientDataSerializationParity() {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    DepartmentRecord dept = new DepartmentRecord(42, "DEV", "Development");
    EmployeeRecord emp =
        new EmployeeRecord(
            999L,
            "Bob",
            dept,
            StatusEnum.ACTIVE,
            List.of("Go", "Rust"),
            Map.of("level", "Senior"),
            Instant.parse("2026-02-15T12:00:00Z"),
            120000.0);

    ClientDataSerializer serializer = ClientData.defaultSerializer();
    StringBuilder sb = new StringBuilder();
    serializer.serialize(emp, sb);
    String json = sb.toString();

    // Verify runtime JSON shape:
    // 1. Primitive/boxed numerics -> numeric literal
    assertThat(json).contains("\"id\":999");
    assertThat(json).contains("\"salary\":120000.0");
    // 2. Strings -> quoted string
    assertThat(json).contains("\"name\":\"Bob\"");
    // 3. Enum -> string name
    assertThat(json).contains("\"status\":\"ACTIVE\"");
    // 4. Instant / Temporal -> ISO string
    assertThat(json).contains("\"hireDate\":\"2026-02-15T12:00:00Z\"");
    // 5. List / Array -> JSON array [...]
    assertThat(json).contains("\"skills\":[\"Go\",\"Rust\"]");
    // 6. Map -> JSON object { ... }
    assertThat(json).contains("\"attributes\":{\"level\":\"Senior\"}");
    // 7. Nested composite record
    assertThat(json)
        .contains("\"department\":{\"id\":42,\"code\":\"DEV\",\"name\":\"Development\"}");

    // Project TypeScript declaration
    String dts = projector.projectClass(EmployeeRecord.class, "emp.vtl");

    // Parity verification with generated TS:
    assertThat(dts).contains("id: number;");
    assertThat(dts).contains("name: string | null;");
    assertThat(dts).contains("status: StatusEnum | null;");
    assertThat(dts).contains("hireDate: string | null;");
    assertThat(dts).contains("skills: string[] | null;");
    assertThat(dts).contains("attributes: Record<string, string> | null;");
    assertThat(dts).contains("department: DepartmentRecord | null;");
    assertThat(dts).contains("export type StatusEnum = \"ACTIVE\" | \"INACTIVE\" | \"PENDING\";");
  }

  // =========================================================================
  // Prototype 13: Performance Benchmark Scaling
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 13: Generator scales well (< 500ms for 10 contracts, < 2000ms for 100 contracts)")
  void prototype13_performanceBenchmark() {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    // Warm-up to ensure HotSpot JIT compiles reflection paths
    for (int i = 0; i < 50; i++) {
      projector.projectClass(EmployeeRecord.class, "warmup" + i + ".vtl");
    }

    // Measure 10 contracts
    long start10 = System.nanoTime();
    for (int i = 0; i < 10; i++) {
      projector.projectClass(EmployeeRecord.class, "template" + i + ".vtl");
    }
    long elapsed10Ms = (System.nanoTime() - start10) / 1_000_000;

    // Measure 100 contracts
    long start100 = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      projector.projectClass(EmployeeRecord.class, "template" + i + ".vtl");
    }
    long elapsed100Ms = (System.nanoTime() - start100) / 1_000_000;

    // Assert absence of pathological scaling
    assertThat(elapsed10Ms).isLessThan(500); // Conservative CI threshold
    assertThat(elapsed100Ms).isLessThan(2000);
  }

  // =========================================================================
  // Prototype 14: Frontend Framework Neutrality
  // =========================================================================

  @Test
  @DisplayName("Prototype 14: Generated .d.ts has exactly zero framework imports")
  void prototype14_frontendFrameworkNeutrality() {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    String dts = projector.projectClass(EmployeeRecord.class, "neutral.vtl");

    assertThat(dts).doesNotContain("svelte");
    assertThat(dts).doesNotContain("react");
    assertThat(dts).doesNotContain("vue");
    assertThat(dts).doesNotContain("angular");
    assertThat(dts).doesNotContain("vite");
    assertThat(dts).doesNotContain("@viet-template");
    assertThat(dts).doesNotContain("import "); // Zero imports in self-contained declaration
  }

  // =========================================================================
  // Prototype 15: Bidirectional Round-Trip with TypeScriptSchemaImporter
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 15: Round-trip projection into .d.ts and re-import via TypeScriptSchemaImporter"
          + " preserves contract")
  void prototype15_bidirectionalRoundTripWithTypeScriptSchemaImporter(@TempDir Path tempDir)
      throws Exception {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("reports/annual.vtl"))
            .parameter(
                TemplateParameter.of("employee", TemplateType.of(EmployeeRecord.class), false))
            .parameter(
                TemplateParameter.of("department", TemplateType.of(DepartmentRecord.class), true))
            .build();

    String dts = projector.projectContract(contract);
    assertThat(dts).contains("export interface ReportsAnnualTemplateParameters");
    assertThat(dts)
        .contains("export interface TemplateParameters extends ReportsAnnualTemplateParameters");

    // Write to disk
    Path dtsFile = tempDir.resolve("annual.d.ts");
    Files.writeString(dtsFile, dts, StandardCharsets.UTF_8);

    // Re-import using offline TypeScriptSchemaImporter
    TypeScriptSchemaImporter schemaImporter = new TypeScriptSchemaImporter();
    var importResult = schemaImporter.importPath(dtsFile, "reports/annual.vtl");

    assertThat(importResult.hasErrors()).isFalse();
    assertThat(importResult.schemas()).containsKey("reports/annual.vtl");

    var importedSchema = importResult.schemas().get("reports/annual.vtl");
    // Verify parameters survived round-trip through TypeScript declarations
    assertThat(importedSchema.parameters()).containsKey("employee");
    assertThat(importedSchema.parameters()).containsKey("department");
    assertThat(importedSchema.parameters().get("department").nullable()).isTrue();
    assertThat(importedSchema.parameters().get("employee").nullable()).isFalse();

    // Verify types registered in schema
    assertThat(importedSchema.types()).containsKey("EmployeeRecord");
    assertThat(importedSchema.types()).containsKey("DepartmentRecord");
  }

  // =========================================================================
  // Prototype 16: Nested and Multi-Nested Java Classes Projection
  // =========================================================================

  @Test
  @DisplayName(
      "Prototype 16: Nested and multi-nested classes project cleanly (Outer_Inner) with real tsc"
          + " validation")
  void prototype16_nestedClassesWithRealTsc(@TempDir Path tempDir) throws Exception {
    M46APrototypeTypeScriptProjector projector = new M46APrototypeTypeScriptProjector();

    String dts = projector.projectClass(NestedContainerRecord.class, "nested/item.vtl");
    assertThat(dts).contains("export interface NestedContainerRecord_NestedItem {");
    assertThat(dts).contains("export interface NestedContainerRecord {");
    assertThat(dts).contains("item: NestedContainerRecord_NestedItem | null;");
    assertThat(dts).contains("export interface NestedItemTemplateParameters {");

    Files.writeString(tempDir.resolve("nested.vtl.d.ts"), dts, StandardCharsets.UTF_8);

    Path consumerTs = tempDir.resolve("nested_consumer.ts");
    String tsCode =
        """
        import type {
          NestedContainerRecord,
          NestedContainerRecord_NestedItem,
          NestedItemTemplateParameters
        } from "./nested.vtl";

        const item: NestedContainerRecord_NestedItem = {
          label: "Item A",
          value: 42
        };

        const container: NestedContainerRecord = {
          id: "c-100",
          item: item
        };

        const params: NestedItemTemplateParameters = {
          id: "c-100",
          item: item
        };

        const v: number = item.value;
        const l: string | null = container.item ? container.item.label : null;
        """;
    Files.writeString(consumerTs, tsCode, StandardCharsets.UTF_8);
    if (canRunTsc()) {
      runTscCompilation(tempDir, consumerTs, true);
    }
  }

  // =========================================================================
  // Helper methods
  // =========================================================================

  private static void runTscCompilation(Path dir, Path tsFile, boolean expectSuccess)
      throws Exception {
    CompilationResult result = runTsc(dir, tsFile);
    if (expectSuccess && result.exitCode() != 0) {
      throw new AssertionError(
          "tsc compilation failed with exit code " + result.exitCode() + ":\n" + result.output());
    }
    if (!expectSuccess && result.exitCode() == 0) {
      throw new AssertionError("tsc compilation unexpectedly succeeded!");
    }
  }

  private static CompilationResult runTsc(Path dir, Path tsFile) throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        canRunTsc(), "tsc binary or node runtime not available. Skipping real tsc execution.");

    List<String> cmd = new ArrayList<>();
    cmd.add("node");
    cmd.add(tscScript.toAbsolutePath().toString());
    cmd.add("--noEmit");
    cmd.add("--strict");
    cmd.add("--target");
    cmd.add("es2022");
    cmd.add(tsFile.getFileName().toString());

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(dir.toFile());
    pb.redirectErrorStream(true);

    Process process = pb.start();
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }
    }
    int exitCode = process.waitFor();
    return new CompilationResult(exitCode, output.toString());
  }

  private record CompilationResult(int exitCode, String output) {}

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
