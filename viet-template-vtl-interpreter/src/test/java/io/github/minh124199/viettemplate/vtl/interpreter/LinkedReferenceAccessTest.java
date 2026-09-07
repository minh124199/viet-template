package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.linker.CallSiteRegistry;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkedReferenceAccessTest {

  public static class PersonBean {
    private final String name;

    public PersonBean(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public String zeroArgMethod() {
      return "zeroArg:" + name;
    }

    public String greet(String target) {
      return "Hello " + target + " from " + name;
    }
  }

  private LinkedReferenceAccess access;
  private CallSiteRegistry registry;
  private final TemplateId templateId = TemplateId.of("test.vtl");
  private final SourceSpan span = SourceSpan.UNKNOWN;

  @BeforeEach
  void setUp() {
    registry = new CallSiteRegistry(100, new DynamicLinker());
    access = new LinkedReferenceAccess(VtlSecurityPolicy.standard(), registry);
  }

  @Test
  void testGetPropertyWithInlineCacheHits() {
    PersonBean person1 = new PersonBean("Alice");
    PersonBean person2 = new PersonBean("Bob");

    // First call: cache miss, links
    EvaluationValue val1 = access.getProperty(person1, "name", span, templateId);
    assertThat(val1.value()).isEqualTo("Alice");

    // Second call: inline cache hit
    EvaluationValue val2 = access.getProperty(person2, "name", span, templateId);
    assertThat(val2.value()).isEqualTo("Bob");

    assertThat(registry.statistics().picHits()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void testZeroArgMethodFastPath() {
    PersonBean person = new PersonBean("Charlie");

    EvaluationValue res = access.invokeMethod(person, "zeroArgMethod", List.of(), span, templateId);
    assertThat(res.value()).isEqualTo("zeroArg:Charlie");
  }

  @Test
  void testMethodWithArgumentsFallback() {
    PersonBean person = new PersonBean("David");

    EvaluationValue arg = EvaluationValue.of("World");
    EvaluationValue res = access.invokeMethod(person, "greet", List.of(arg), span, templateId);
    assertThat(res.value()).isEqualTo("Hello World from David");
  }

  @Test
  void testSecurityEnforcement() {
    PersonBean person = new PersonBean("Eve");

    assertThatThrownBy(() -> access.invokeMethod(person, "getClass", List.of(), span, templateId))
        .isInstanceOf(TemplateSecurityException.class);
  }

  @Test
  void testMapPropertyAccess() {
    Map<String, Object> map = Map.of("key1", "value1");

    EvaluationValue res = access.getProperty(map, "key1", span, templateId);
    assertThat(res.value()).isEqualTo("value1");

    // Missing key in map -> undefined
    EvaluationValue missing = access.getProperty(map, "missingKey", span, templateId);
    assertThat(missing.isUndefined()).isTrue();
  }
}
