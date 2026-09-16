package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.fail;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AstIrAotDifferentialFuzzTest {

  private static final long DEFAULT_SEED = 0xD1FF3871EL;

  private static boolean isDeepMode() {
    return "deep".equalsIgnoreCase(System.getProperty("vietTemplate.fuzz.mode"))
        || "deep".equalsIgnoreCase(System.getenv("VIET_FUZZ_MODE"));
  }

  static long parseSeed(String text, long defaultSeed) {
    if (text == null || text.isBlank()) {
      return defaultSeed;
    }
    String prop = text.trim();
    if (prop.startsWith("0x") || prop.startsWith("0X")) {
      return Long.parseUnsignedLong(prop.substring(2), 16);
    }
    return Long.parseLong(prop);
  }

  static long resolveSeed(String prop, String env, long defaultSeed) {
    if (prop != null && !prop.isBlank()) {
      return parseSeed(prop, defaultSeed);
    }
    if (env != null && !env.isBlank()) {
      return parseSeed(env, defaultSeed);
    }
    return defaultSeed;
  }

  private static long resolveSeed() {
    return resolveSeed(
        System.getProperty("vietTemplate.fuzz.seed"),
        System.getenv("VIET_FUZZ_SEED"),
        DEFAULT_SEED);
  }

  @Test
  @DisplayName(
      "P2 & P11: Generated valid templates maintain AST == IR == AOT parity without bytecode"
          + " verifier errors")
  void testCrossTierParityOnGeneratedTemplates() {
    long seed = resolveSeed();
    SplittableRandom rng = new SplittableRandom(seed);
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
                    + "Mode: %s%n"
                    + "Reproduction command:%n"
                    + "  ./mvnw test -pl viet-template-tck -Dtest=AstIrAotDifferentialFuzzTest"
                    + " -DvietTemplate.fuzz.mode=%s -DvietTemplate.fuzz.seed=0x%X -B%n"
                    + "Error: %s%n"
                    + "Original Template:%n%s%n"
                    + "Minimized Template:%n%s",
                i,
                seed,
                isDeepMode() ? "deep" : "standard",
                isDeepMode() ? "deep" : "standard",
                seed,
                verifierError.toString(),
                generated.source(),
                minimized.source()),
            verifierError);
      } catch (Throwable t) {
        boolean isVerifierError =
            t instanceof VerifyError
                || t instanceof ClassFormatError
                || t instanceof BootstrapMethodError
                || (t.getMessage() != null
                    && (t.getMessage().contains("VerifyError")
                        || t.getMessage().contains("stackmap")
                        || t.getMessage().contains("Inconsistent stackmap")));

        if (isVerifierError) {
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
                      return other.getMessage() != null
                          && (other.getMessage().contains("VerifyError")
                              || other.getMessage().contains("stackmap"));
                    }
                  });

          fail(
              String.format(
                  "JVM Bytecode Verifier Error detected (P11 violation)!%n"
                      + "Iteration: %d, Seed: 0x%X%n"
                      + "Mode: %s%n"
                      + "Reproduction command:%n"
                      + "  ./mvnw test -pl viet-template-tck -Dtest=AstIrAotDifferentialFuzzTest"
                      + " -DvietTemplate.fuzz.mode=%s -DvietTemplate.fuzz.seed=0x%X -B%n"
                      + "Error: %s%n"
                      + "Original Template:%n%s%n"
                      + "Minimized Template:%n%s",
                  i,
                  seed,
                  isDeepMode() ? "deep" : "standard",
                  isDeepMode() ? "deep" : "standard",
                  seed,
                  t.toString(),
                  generated.source(),
                  minimized.source()),
              t);
        } else {
          fail(
              String.format(
                  "Cross-tier parity assertion failed (P2 violation)!%n"
                      + "Iteration: %d, Seed: 0x%X%n"
                      + "Mode: %s%n"
                      + "Reproduction command:%n"
                      + "  ./mvnw test -pl viet-template-tck -Dtest=AstIrAotDifferentialFuzzTest"
                      + " -DvietTemplate.fuzz.mode=%s -DvietTemplate.fuzz.seed=0x%X -B%n"
                      + "Template:%n%s%n"
                      + "Context: %s%n"
                      + "Error: %s",
                  i,
                  seed,
                  isDeepMode() ? "deep" : "standard",
                  isDeepMode() ? "deep" : "standard",
                  seed,
                  generated.source(),
                  generated.context(),
                  t.getMessage()),
              t);
        }
      }
    }
  }
}
