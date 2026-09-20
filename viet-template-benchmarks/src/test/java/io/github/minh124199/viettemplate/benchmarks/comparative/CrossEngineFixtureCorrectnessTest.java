package io.github.minh124199.viettemplate.benchmarks.comparative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

@DisplayName("Cross-Engine Fixture Output Equivalence Test")
public class CrossEngineFixtureCorrectnessTest {

  private static final List<BenchmarkEngineAdapter> ADAPTERS = new ArrayList<>();

  @BeforeAll
  static void setUpAll() {
    ADAPTERS.add(new VietIrAdapter());
    ADAPTERS.add(new VietAotAdapter());
    ADAPTERS.add(new VelocityAdapter());
    ADAPTERS.add(new QuteAdapter());
    ADAPTERS.add(new JteAdapter());
    ADAPTERS.add(new ThymeleafAdapter());

    for (BenchmarkEngineAdapter adapter : ADAPTERS) {
      adapter.setup();
    }
  }

  @AfterAll
  static void tearDownAll() {
    for (BenchmarkEngineAdapter adapter : ADAPTERS) {
      try {
        adapter.close();
      } catch (Exception ignored) {
      }
    }
    ADAPTERS.clear();
  }

  @TestFactory
  @DisplayName("Verify byte-for-byte output equivalence across all engines for C01-C08")
  List<DynamicTest> testAllWorkloadsAcrossAllEngines() {
    List<DynamicTest> tests = new ArrayList<>();

    for (String workload : ComparativeWorkloads.ALL_WORKLOADS) {
      Object model = ComparativeWorkloads.getModel(workload);
      String expected = ComparativeWorkloads.getExpectedOutput(workload);

      for (BenchmarkEngineAdapter adapter : ADAPTERS) {
        tests.add(
            DynamicTest.dynamicTest(
                "Workload [" + workload + "] on Engine [" + adapter.name() + "]",
                () -> {
                  String actual = adapter.render(workload, model);
                  assertThat(actual)
                      .as(
                          "Engine %s failed output equivalence for workload %s",
                          adapter.name(), workload)
                      .isNotNull()
                      .isNotEmpty()
                      .isEqualTo(expected);
                }));
      }
    }

    return tests;
  }
}
