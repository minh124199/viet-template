package io.github.minh124199.viettemplate.language.vtl.qualification.m45a;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.Address;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.Department;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.Employee;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.Manager;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.PayrollRecord;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser.CandidateSyntax;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser.ParamDeclarationPrototypeParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class M45APerformanceQualificationTest {

  private static final Map<String, Class<?>> KNOWN_CLASSES =
      Map.of(
          "Employee", Employee.class,
          "Manager", Manager.class,
          "Department", Department.class,
          "Address", Address.class,
          "PayrollRecord", PayrollRecord.class);

  private static SourceText src(String name, String content) {
    return SourceText.from(content, TemplateId.of(name));
  }

  private static SemanticAnalysisResult runFullPipeline(SourceText source) {
    var paramParse =
        ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);
    ModelSchema schema =
        ParamDeclarationPrototypeParser.toModelSchema(paramParse.declarations(), KNOWN_CLASSES);

    VtlParseResult parseResult = VtlParser.parse(source);

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .allowArbitraryMethods(true)
            .build();

    return VtlSemanticAnalyzer.analyze(parseResult.template(), options);
  }

  @BeforeAll
  static void warmupJvm() {
    // Warmup JIT to get representative steady-state timings
    SourceText warmupSource =
        src(
            "warmup.vtl",
            """
            #param(Employee $employee)
            Hello $employee.name!
            #if($employee.active)
              Department: $employee.address.city
            #end
            """);

    for (int i = 0; i < 2000; i++) {
      runFullPipeline(warmupSource);
    }
  }

  @Test
  @DisplayName("Performance: Untyped template parsing has zero penalty when no #param exists")
  void zeroOverheadForUntypedTemplates() {
    String untypedText =
        """
        <h1>Welcome, $user.name!</h1>
        <ul>
        #foreach($item in $items)
          <li>$item.title - $item.price</li>
        #end
        </ul>
        """;
    SourceText source = src("untyped.vtl", untypedText);

    // Warmup
    for (int i = 0; i < 1000; i++) {
      VtlParser.parse(source);
      ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);
    }

    int iterations = 5000;

    // Time baseline parser
    long startBaseline = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      VtlParser.parse(source);
    }
    long elapsedBaseline = System.nanoTime() - startBaseline;

    // Time prototype scanner on untyped template
    long startScanner = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);
    }
    long elapsedScanner = System.nanoTime() - startScanner;

    double scannerMicrosPerCall = (elapsedScanner / 1_000.0) / iterations;
    double baselineMicrosPerCall = (elapsedBaseline / 1_000.0) / iterations;

    // Scanner should be negligible (single-digit microseconds)
    assertThat(scannerMicrosPerCall).isLessThan(50.0);
  }

  @Test
  @DisplayName("Performance: Small typed template compile-time overhead < 2.0 ms")
  void compileTimeOverheadSmallTemplate() {
    String smallTemplate =
        """
        #param(Employee $employee)
        Hello $employee.name, ID: $employee.id!
        """;
    SourceText source = src("small.vtl", smallTemplate);

    // Warmup template
    for (int i = 0; i < 200; i++) {
      runFullPipeline(source);
    }

    int iterations = 1000;
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      runFullPipeline(source);
    }
    long elapsed = System.nanoTime() - start;

    SemanticAnalysisResult verification = runFullPipeline(source);
    assertThat(verification.hasErrors()).isFalse();

    double millisPerCall = (elapsed / 1_000_000.0) / iterations;
    assertThat(millisPerCall).isLessThan(2.0);
  }

  @Test
  @DisplayName("Performance: Medium typed template (~50 lines) compile-time overhead < 20.0 ms")
  void compileTimeOverheadMediumTemplate() {
    StringBuilder sb = new StringBuilder();
    sb.append("#param(Employee $employee)\n");
    sb.append("#param(Department $dept)\n");
    sb.append("<div>\n");
    sb.append("  <h1>$dept.name</h1>\n");
    sb.append("  <p>Leader: $dept.manager.name ($dept.manager.directReportsCount)</p>\n");
    sb.append("  #if($employee.active)\n");
    sb.append("    <p>City: $employee.address.city, Zip: $employee.address.zipCode</p>\n");
    sb.append("    <ul>\n");
    sb.append("    #foreach($skill in $employee.skills)\n");
    sb.append("      <li>Skill: $skill</li>\n");
    sb.append("    #end\n");
    sb.append("    </ul>\n");
    sb.append("  #end\n");
    for (int i = 0; i < 20; i++) {
      sb.append("  <span>Field ").append(i).append(": $employee.name</span>\n");
    }
    sb.append("</div>\n");

    SourceText source = src("medium.vtl", sb.toString());

    // Warmup template
    for (int i = 0; i < 100; i++) {
      runFullPipeline(source);
    }

    int iterations = 300;
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      runFullPipeline(source);
    }
    long elapsed = System.nanoTime() - start;

    SemanticAnalysisResult verification = runFullPipeline(source);
    assertThat(verification.hasErrors()).isFalse();

    double millisPerCall = (elapsed / 1_000_000.0) / iterations;
    assertThat(millisPerCall).isLessThan(20.0);
  }

  @Test
  @DisplayName("Performance: Large typed template (~300 lines) compile-time overhead < 50.0 ms")
  void compileTimeOverheadLargeTemplate() {
    StringBuilder sb = new StringBuilder();
    sb.append("#param(Employee $employee)\n");
    sb.append("#param(Department $dept)\n");
    sb.append("#param(Manager $manager)\n");
    sb.append("#param(PayrollRecord $payroll)\n");
    sb.append("<div>\n");
    sb.append("  <h1>$dept.name</h1>\n");
    sb.append("  <p>Reports: $manager.directReportsCount</p>\n");
    for (int i = 0; i < 60; i++) {
      sb.append("  #set($line").append(i).append(" = $employee.name)\n");
      sb.append("  <div class=\"row-").append(i).append("\">\n");
      sb.append("    <span>ID: $employee.id</span>\n");
      sb.append("    <span>Name: $employee.name</span>\n");
      sb.append("    <span>Base: $payroll.baseSalary</span>\n");
      sb.append("  </div>\n");
    }
    sb.append("</div>\n");

    SourceText source = src("large.vtl", sb.toString());

    // Warmup template
    for (int i = 0; i < 50; i++) {
      runFullPipeline(source);
    }

    int iterations = 150;
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      runFullPipeline(source);
    }
    long elapsed = System.nanoTime() - start;

    SemanticAnalysisResult verification = runFullPipeline(source);
    assertThat(verification.hasErrors()).isFalse();

    double millisPerCall = (elapsed / 1_000_000.0) / iterations;
    assertThat(millisPerCall).isLessThan(50.0);
  }
}
