package io.github.minh124199.viettemplate.language.vtl.semantics.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MethodResolution;
import io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve.MethodResolver;
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

  public interface Formatter {
    String format(String a, String b, String c, String d);

    String process(int a1, long a2, double a3, boolean a4, String a5, Object a6, int a7, int a8);
  }

  public static class AmbiguousOverload {
    public void execute(CharSequence a, String b) {}

    public void execute(String a, CharSequence b) {}
  }

  public static class VarargHolder {
    public void print(String prefix, String... items) {}
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
  @DisplayName("Ambiguous tie in overload scores falls back to dynamic resolution")
  void ambiguousOverloadsFallbackToDynamic() {
    VType ambigType = VType.ClassType.of(AmbiguousOverload.class, Nullability.NON_NULL);
    MethodResolution res =
        MethodResolver.resolveMethod(ambigType, "execute", List.of(VTypes.STRING, VTypes.STRING));
    assertThat(res.kind()).isEqualTo(MethodResolution.Kind.DYNAMIC);
  }

  @Test
  @DisplayName("Multiple overloads with dynamic argument falls back to dynamic resolution")
  void dynamicArgWithMultipleOverloadsFallbackToDynamic() {
    VType calcType = VType.ClassType.of(Calculator.class, Nullability.NON_NULL);
    MethodResolution res =
        MethodResolver.resolveMethod(calcType, "add", List.of(VTypes.DYNAMIC, VTypes.INT));
    assertThat(res.kind()).isEqualTo(MethodResolution.Kind.DYNAMIC);
  }

  @Test
  @DisplayName("Vararg methods are excluded from static fixed-arity direct resolution")
  void varargExcludedFromStaticResolution() {
    VType varargType = VType.ClassType.of(VarargHolder.class, Nullability.NON_NULL);
    MethodResolution res =
        MethodResolver.resolveMethod(varargType, "print", List.of(VTypes.STRING, VTypes.STRING));
    assertThat(res.isResolved()).isFalse();
  }

  @Test
  @DisplayName("Resolves 4-arg and 8-arg methods on interfaces")
  void multiArgInterfaceResolution() {
    VType fmtType = VType.ClassType.of(Formatter.class, Nullability.NON_NULL);

    MethodResolution formatRes =
        MethodResolver.resolveMethod(
            fmtType, "format", List.of(VTypes.STRING, VTypes.STRING, VTypes.STRING, VTypes.STRING));
    assertThat(formatRes.isResolved()).isTrue();
    assertThat(formatRes.returnType().typeName()).isEqualTo("java.lang.String");

    MethodResolution processRes =
        MethodResolver.resolveMethod(
            fmtType,
            "process",
            List.of(
                VTypes.INT,
                VTypes.LONG,
                VTypes.DOUBLE,
                VTypes.BOOLEAN,
                VTypes.STRING,
                VTypes.OBJECT,
                VTypes.INT,
                VTypes.INT));
    assertThat(processRes.isResolved()).isTrue();
    assertThat(processRes.returnType().typeName()).isEqualTo("java.lang.String");
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
