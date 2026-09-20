package io.github.minh124199.viettemplate.benchmarks.comparative;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/** Comparative benchmark adapter for Viet Template on the AOT bytecode tier (Track B). */
public class VietAotAdapter implements BenchmarkEngineAdapter {

  private VtlTemplateEngine engine;
  private final Map<String, Template> templates = new HashMap<>();

  @Override
  public String name() {
    return "Viet-AOT";
  }

  @Override
  public void setup() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    for (String workload : ComparativeWorkloads.ALL_WORKLOADS) {
      repo.put(workload + ".vtl", ComparativeWorkloads.getTemplate("viet", workload));
    }
    engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();
    for (String workload : ComparativeWorkloads.ALL_WORKLOADS) {
      templates.put(workload, engine.get(workload + ".vtl"));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public String render(String workload, Object model) {
    Template template = templates.get(workload);
    if (template == null) {
      throw new IllegalArgumentException("Unknown workload: " + workload);
    }
    RenderContext ctx =
        model instanceof Map<?, ?> map
            ? RenderContext.of((Map<String, Object>) map)
            : RenderContext.empty();
    StringTemplateOutput output = new StringTemplateOutput(512);
    try {
      template.render(ctx, output);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return output.toString();
  }

  @Override
  public void close() {
    if (engine != null) {
      engine.close();
    }
  }
}
