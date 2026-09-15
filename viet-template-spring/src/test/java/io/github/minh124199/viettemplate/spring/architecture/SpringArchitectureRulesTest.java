package io.github.minh124199.viettemplate.spring.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architectural tests enforcing Spring integration module boundaries and dependency rules. */
@AnalyzeClasses(
    packages = "io.github.minh124199.viettemplate",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class SpringArchitectureRulesTest {

  @ArchTest
  public static final ArchRule spring_integration_must_not_access_internal_packages =
      noClasses()
          .that()
          .resideInAnyPackage("..viettemplate.spring..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..viettemplate.runtime.linker..",
              "..viettemplate.language.vtl.ast..",
              "..viettemplate.language.vtl.ir..",
              "..viettemplate.vtl.compiler..",
              "..viettemplate.vtl.interpreter..");
}
