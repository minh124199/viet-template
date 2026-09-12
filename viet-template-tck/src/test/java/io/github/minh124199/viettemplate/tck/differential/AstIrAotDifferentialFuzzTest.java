package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.fail;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AstIrAotDifferentialFuzzTest {

  private static final long SEED = 0xD1FF3871EL;

  private static boolean isDeepMode() {
    return "deep".equalsIgnoreCase(System.getProperty("vietTemplate.fuzz.mode"))
        || "deep".equalsIgnoreCase(System.getenv("VIET_FUZZ_MODE"));
  }

  @Test
  @DisplayName(
      "P2 & P11: Generated valid templates maintain AST == IR == AOT parity without bytecode"
          + " verifier errors")
  void testCrossTierParityOnGeneratedTemplates() {
    SplittableRandom rng = new SplittableRandom(SEED);
    BoundedVtlGenerator.GeneratorBudget budget =
        isDeepMode()
            ? BoundedVtlGenerator.GeneratorBudget.deep()
            : BoundedVtlGenerator.GeneratorBudget.standard();

    int iterations = isDeepMode() ? 800 : 120;

    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

    for (int i = 0; i < iterations; i++) {
      BoundedVtlGenerator generator = new BoundedVtlGenerator(rng, budget);
      BoundedVtlGenerator.GeneratedCase generated = generator.generate();

      try {
        TierDifferentialHarness.DifferentialResult result =
            TierDifferentialHarness.runAcrossTiers(
                generated.source(), generated.context(), options);

        TierDifferentialHarness.assertTierParity(result);
      } catch (VerifyError | ClassFormatError | BootstrapMethodError verifierError) {
        // P11 violation: Bytecode compiler generated invalid bytecode
        TemplateMinimizer.MinimizedCase minimized =
            TemplateMinimizer.minimize(
                generated.source(),
                generated.context(),
                src -> {
                  try {
                    TierDifferentialHarness.runAcrossTiers(src, generated.context(), options);
                    return false;
                  } catch (VerifyError | ClassFormatError | BootstrapMethodError e) {
                    return true;
                  } catch (Throwable other) {
                    return false;
                  }
                });

        fail(
            String.format(
                "JVM Bytecode Verifier Error detected (P11 violation)!%n"
                    + "Iteration: %d, Seed: 0x%X%n"
                    + "Error: %s%n"
                    + "Original Template:%n%s%n"
                    + "Minimized Template:%n%s",
                i, SEED, verifierError.toString(), generated.source(), minimized.source()),
            verifierError);
      } catch (Throwable t) {
        fail(
            String.format(
                "Cross-tier parity assertion failed (P2 violation)!%n"
                    + "Iteration: %d, Seed: 0x%X%n"
                    + "Template:%n%s%n"
                    + "Context: %s%n"
                    + "Error: %s",
                i, SEED, generated.source(), generated.context(), t.getMessage()),
            t);
      }
    }
  }
}
