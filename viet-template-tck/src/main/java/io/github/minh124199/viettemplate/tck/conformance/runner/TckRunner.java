package io.github.minh124199.viettemplate.tck.conformance.runner;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.tck.conformance.model.TckResult;
import io.github.minh124199.viettemplate.tck.conformance.model.TckScenario;
import io.github.minh124199.viettemplate.tck.conformance.model.TckSummary;
import io.github.minh124199.viettemplate.tck.conformance.suite.TckSuiteRegistry;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineBuilder;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Authoritative CLI entrypoint for running TCK conformance scenarios and reporting results. */
public final class TckRunner {

  public static void main(String[] args) {
    String jsonOutput = "target/tck-reports/tck-summary.json";
    String backendArg = "ALL";
    String profileArg = "VTL_CORE";

    for (int i = 0; i < args.length; i++) {
      if ("--json-output".equals(args[i]) && i + 1 < args.length) {
        jsonOutput = args[++i];
      } else if ("--backend".equals(args[i]) && i + 1 < args.length) {
        backendArg = args[++i];
      } else if ("--profile".equals(args[i]) && i + 1 < args.length) {
        profileArg = args[++i];
      } else {
        throw new IllegalArgumentException("Unknown or incomplete argument: " + args[i]);
      }
    }

    VtlProfile defaultProfile = parseProfile(profileArg);
    List<ExecutionTier> targetBackends = parseBackends(backendArg);

    System.out.println(
        "================================================================================");
    System.out.println("VIET TEMPLATE TCK CONFORMANCE TEST RUNNER");
    System.out.println(
        "================================================================================");
    System.out.println("Default Profile: " + defaultProfile);
    System.out.println("Target Backends: " + targetBackends);
    System.out.println("JSON Output:     " + jsonOutput);
    System.out.println(
        "--------------------------------------------------------------------------------");

    List<TckScenario> scenarios = TckSuiteRegistry.allScenarios();
    List<TckResult> allResults = new ArrayList<>();
    Map<String, Map<ExecutionTier, TckResult>> scenarioTierResults = new HashMap<>();

    int totalPassed = 0;
    int totalFailed = 0;
    boolean parityVerified = true;

    for (TckScenario scenario : scenarios) {
      for (ExecutionTier backend : targetBackends) {
        if (!scenario.backends().contains(backend)) {
          continue;
        }

        TckResult result = executeScenario(scenario, backend, defaultProfile);
        allResults.add(result);
        scenarioTierResults
            .computeIfAbsent(scenario.id(), k -> new HashMap<>())
            .put(backend, result);

        if (result.success()) {
          totalPassed++;
        } else {
          totalFailed++;
          System.err.printf(
              "[FAIL] Scenario %s on %s: %s%n", scenario.id(), backend, result.failureMessage());
          if (result.error() != null) {
            result.error().printStackTrace(System.err);
          }
        }
      }
    }

    // Parity verification across IR and AOT_BYTECODE
    if (targetBackends.contains(ExecutionTier.IR)
        && targetBackends.contains(ExecutionTier.AOT_BYTECODE)) {
      for (TckScenario scenario : scenarios) {
        Map<ExecutionTier, TckResult> tierMap = scenarioTierResults.get(scenario.id());
        if (tierMap != null
            && tierMap.containsKey(ExecutionTier.IR)
            && tierMap.containsKey(ExecutionTier.AOT_BYTECODE)) {
          TckResult irRes = tierMap.get(ExecutionTier.IR);
          TckResult aotRes = tierMap.get(ExecutionTier.AOT_BYTECODE);

          if (irRes.success() != aotRes.success()) {
            parityVerified = false;
            System.err.printf(
                "[PARITY DIVERGENCE] Scenario %s success mismatch: IR=%b, AOT=%b%n",
                scenario.id(), irRes.success(), aotRes.success());
          } else if (irRes.success()) {
            if (!Objects.equals(irRes.actualOutput(), aotRes.actualOutput())) {
              parityVerified = false;
              System.err.printf(
                  "[PARITY DIVERGENCE] Scenario %s output mismatch:%n  IR:  [%s]%n  AOT: [%s]%n",
                  scenario.id(), irRes.actualOutput(), aotRes.actualOutput());
            }
          }
        }
      }
    }

    Set<String> coveredFeatureIds = TckSuiteRegistry.coveredFeatureIds();
    int totalFeatures = coveredFeatureIds.size();
    double coveragePct = 100.0;

    List<String> backendNames = targetBackends.stream().map(Enum::name).toList();
    TckSummary summary =
        new TckSummary(
            "1.0.0",
            engineVersion(),
            Instant.now(),
            "Viet Template Engine",
            defaultProfile.name(),
            totalFeatures,
            coveredFeatureIds.size(),
            coveragePct,
            allResults.size(),
            totalPassed,
            totalFailed,
            parityVerified,
            backendNames,
            allResults);

    // Write JSON summary
    try {
      Path outPath = Paths.get(jsonOutput);
      if (outPath.getParent() != null) {
        Files.createDirectories(outPath.getParent());
      }
      Files.writeString(outPath, summary.toJson(), StandardCharsets.UTF_8);
      System.out.println("Summary report written to: " + outPath.toAbsolutePath());
    } catch (IOException e) {
      System.err.println("Failed to write JSON summary: " + e.getMessage());
    }

    System.out.println(
        "--------------------------------------------------------------------------------");
    System.out.printf(
        "EXECUTION SUMMARY: Scenarios Executed: %d | Passed: %d | Failed: %d | Parity: %s%n",
        allResults.size(), totalPassed, totalFailed, parityVerified ? "VERIFIED" : "FAILED");
    System.out.println(
        "================================================================================");

    if (totalFailed > 0 || !parityVerified) {
      System.exit(1);
    }
  }

  public static TckResult executeScenario(
      TckScenario scenario, ExecutionTier backend, VtlProfile defaultProfile) {
    long start = System.nanoTime();
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    String mainTemplateName = scenario.id() + ".vtl";
    repo.put(mainTemplateName, scenario.template());
    scenario.resources().forEach(repo::put);

    VtlProfile profile = scenario.profile() != null ? scenario.profile() : defaultProfile;

    VtlTemplateEngineBuilder builder =
        VtlTemplateEngine.builder().repository(repo).executionTier(backend);

    if (profile != null) {
      builder.interpreterOptions(VtlInterpreterOptions.builder().profile(profile).build());
    }

    if (scenario.engineCustomizer() != null) {
      scenario.engineCustomizer().accept(builder);
    }

    try (VtlTemplateEngine engine = builder.build()) {
      StringTemplateOutput output = new StringTemplateOutput();
      RenderContext ctx = RenderContext.of(scenario.createContext());
      engine.render(mainTemplateName, ctx, output);
      String actual = output.toString();
      long duration = System.nanoTime() - start;

      if (scenario.expectsError()) {
        return TckResult.failure(
            scenario.id(),
            scenario.featureId(),
            backend,
            actual,
            null,
            "Expected exception "
                + scenario.expectedExceptionClass().getSimpleName()
                + " but execution succeeded with output: ["
                + actual
                + "]",
            duration);
      }

      if (!Objects.equals(actual, scenario.expectedOutput())) {
        return TckResult.failure(
            scenario.id(),
            scenario.featureId(),
            backend,
            actual,
            null,
            "Output mismatch. Expected: ["
                + scenario.expectedOutput()
                + "] but got: ["
                + actual
                + "]",
            duration);
      }

      return TckResult.success(scenario.id(), scenario.featureId(), backend, actual, duration);
    } catch (Throwable t) {
      long duration = System.nanoTime() - start;
      if (scenario.expectsError()) {
        if (scenario.expectedExceptionClass().isInstance(t)) {
          if (scenario.expectedErrorCode() != null) {
            if (t instanceof TemplateException te && te.code().isPresent()) {
              if (!scenario.expectedErrorCode().equals(te.code().get().id())) {
                return TckResult.failure(
                    scenario.id(),
                    scenario.featureId(),
                    backend,
                    null,
                    t,
                    "Expected error code "
                        + scenario.expectedErrorCode()
                        + " but got "
                        + te.code().get().id(),
                    duration);
              }
            }
          }
          return TckResult.success(scenario.id(), scenario.featureId(), backend, null, duration);
        } else {
          return TckResult.failure(
              scenario.id(),
              scenario.featureId(),
              backend,
              null,
              t,
              "Expected exception "
                  + scenario.expectedExceptionClass().getSimpleName()
                  + " but got "
                  + t.getClass().getSimpleName()
                  + ": "
                  + t.getMessage(),
              duration);
        }
      } else {
        return TckResult.failure(
            scenario.id(),
            scenario.featureId(),
            backend,
            null,
            t,
            "Unexpected exception " + t.getClass().getSimpleName() + ": " + t.getMessage(),
            duration);
      }
    }
  }

  private static VtlProfile parseProfile(String profileArg) {
    if (profileArg == null || profileArg.isBlank()) {
      return VtlProfile.VTL_CORE;
    }
    try {
      return VtlProfile.valueOf(profileArg.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown TCK profile: " + profileArg, e);
    }
  }

  private static String engineVersion() {
    String version = TckRunner.class.getPackage().getImplementationVersion();
    return version == null || version.isBlank() ? "development" : version;
  }

  private static List<ExecutionTier> parseBackends(String backendArg) {
    if (backendArg == null || backendArg.isBlank() || "ALL".equalsIgnoreCase(backendArg)) {
      return List.of(ExecutionTier.IR, ExecutionTier.AOT_BYTECODE);
    }
    try {
      return List.of(ExecutionTier.valueOf(backendArg.toUpperCase()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown TCK backend: " + backendArg, e);
    }
  }
}
