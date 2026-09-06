package io.github.minh124199.viettemplate.language.vtl.semantics.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MethodResolverTest {

  public static class Calculator {
    public int add(int a, int b) {
      return a + b;
    }

    public double add(double a, double b) {
      return a + b;
    }

    public String compute(String prefix, int count) {
      return prefix + count;
    }
  }

  @Test
  @DisplayName("Overload resolution scores exact and widened arguments")
  void overloadScoring() {
    VType calcType = VType.ClassType.of(Calculator.class, Nullability.NON_NULL);

    MethodResolution intRes =
        MethodResolver.resolveMethod(calcType, "add", List.of(VTypes.INT, VTypes.INT));
    assertThat(intRes.isResolved()).isTrue();
    assertThat(intRes.returnType()).isEqualTo(VTypes.INT);

    MethodResolution doubleRes =
        MethodResolver.resolveMethod(calcType, "add", List.of(VTypes.DOUBLE, VTypes.DOUBLE));
    assertThat(doubleRes.isResolved()).isTrue();
    assertThat(doubleRes.returnType()).isEqualTo(VTypes.DOUBLE);
  }

  @Test
  @DisplayName("Security policy denies dangerous methods such as getClass")
  void securityDenials() {
    VType calcType = VType.ClassType.of(Calculator.class, Nullability.NON_NULL);

    MethodResolution getClassRes = MethodResolver.resolveMethod(calcType, "getClass", List.of());
    assertThat(getClassRes.kind()).isEqualTo(MethodResolution.Kind.DENIED_BY_POLICY);
    assertThat(getClassRes.diagnosticMessage().orElseThrow()).contains("getClass");

    MethodResolution waitRes = MethodResolver.resolveMethod(calcType, "wait", List.of());
    assertThat(waitRes.kind()).isEqualTo(MethodResolution.Kind.DENIED_BY_POLICY);
  }

  @Test
  @DisplayName("Missing method produces typo suggestions")
  void missingMethodTypo() {
    VType calcType = VType.ClassType.of(Calculator.class, Nullability.NON_NULL);

    MethodResolution typoRes =
        MethodResolver.resolveMethod(calcType, "comput", List.of(VTypes.STRING, VTypes.INT));
    assertThat(typoRes.isResolved()).isFalse();
    assertThat(typoRes.kind()).isEqualTo(MethodResolution.Kind.METHOD_NOT_FOUND);
    assertThat(typoRes.typoSuggestion()).contains("compute");
  }
}
