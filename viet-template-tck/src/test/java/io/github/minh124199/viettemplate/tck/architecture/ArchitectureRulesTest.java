package io.github.minh124199.viettemplate.tck.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architectural tests enforcing core module boundaries and dependency rules. */
@AnalyzeClasses(
    packages = "io.github.minh124199.viettemplate",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

  @ArchTest
  public static final ArchRule api_must_not_depend_on_external_frameworks =
      noClasses()
          .that()
          .resideInAPackage("..viettemplate.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "io.quarkus..",
              "io.micronaut..",
              "jakarta.servlet..",
              "javax.servlet..",
              "org.apache.velocity..",
              "org.objectweb.asm..",
              "net.bytebuddy..",
              "org.openjdk.jmh..");

  @ArchTest
  public static final ArchRule api_must_not_depend_on_runtime_or_interpreter =
      noClasses()
          .that()
          .resideInAPackage("..viettemplate.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..viettemplate.runtime..", "..viettemplate.language.vtl..", "..viettemplate.vtl..");

  @ArchTest
  public static final ArchRule runtime_must_not_depend_on_parser_or_compiler =
      noClasses()
          .that()
          .resideInAPackage("..viettemplate.runtime..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..viettemplate.parser..", "..viettemplate.compiler..");

  @ArchTest
  public static final ArchRule language_vtl_must_not_depend_on_velocity_runtime_or_engine_runtime =
      noClasses()
          .that()
          .resideInAPackage("..viettemplate.language.vtl..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.apache.velocity..", "..viettemplate.runtime..");

  @ArchTest
  public static final ArchRule interpreter_must_not_depend_on_bytecode_generators_or_velocity =
      noClasses()
          .that()
          .resideInAPackage("..viettemplate.vtl.interpreter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.apache.velocity..", "org.objectweb.asm..", "net.bytebuddy..");

  @ArchTest
  public static final ArchRule integration_and_tooling_boundary_must_not_access_internal_packages =
      noClasses()
          .that()
          .resideInAnyPackage("..viettemplate.integration..", "..viettemplate.tooling..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..viettemplate.runtime.linker..",
              "..viettemplate.language.vtl.ast..",
              "..viettemplate.language.vtl.ir..",
              "..viettemplate.vtl.compiler..",
              "..viettemplate.vtl.interpreter..")
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule aot_public_api_must_not_depend_on_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("..viettemplate.aot..")
          .and()
          .arePublic()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..viettemplate.runtime.linker..",
              "..viettemplate.language.vtl.ast..",
              "..viettemplate.language.vtl.ir..",
              "..viettemplate.vtl.compiler..",
              "..viettemplate.vtl.interpreter..");

  @ArchTest
  public static final ArchRule core_modules_must_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAnyPackage(
              "..viettemplate.api..",
              "..viettemplate.runtime..",
              "..viettemplate.language.vtl..",
              "..viettemplate.vtl..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.servlet..", "javax.servlet..");

  @ArchTest
  public static final ArchRule packages_must_be_free_of_cycles =
      slices().matching("io.github.minh124199.viettemplate.(*)..").should().beFreeOfCycles();
}
