package io.github.minh124199.viettemplate.spring.security.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.github.minh124199.viettemplate.spring.security",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class SpringSecurityArchitectureRulesTest {

  @ArchTest
  public static final ArchRule spring_security_integration_must_not_access_internal_packages =
      noClasses()
          .that()
          .resideInAnyPackage("..viettemplate.spring.security..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..viettemplate.runtime.linker..",
              "..viettemplate.language.vtl.ast..",
              "..viettemplate.language.vtl.ir..",
              "..viettemplate.vtl.compiler..",
              "..viettemplate.vtl.interpreter..");
}
