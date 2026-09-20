package io.github.minh124199.viettemplate.benchmarks.comparative;

import java.util.HashMap;
import java.util.Map;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/** Comparative benchmark adapter for Thymeleaf 3.1.5.RELEASE (Track A). */
public class ThymeleafAdapter implements BenchmarkEngineAdapter {

  private TemplateEngine engine;
  private final Map<String, String> templateSources = new HashMap<>();

  @Override
  public String name() {
    return "Thymeleaf";
  }

  @Override
  public void setup() {
    engine = new TemplateEngine();
    StringTemplateResolver resolver = new StringTemplateResolver();
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCacheable(true);
    engine.setTemplateResolver(resolver);

    for (String workload : ComparativeWorkloads.ALL_WORKLOADS) {
      String source = ComparativeWorkloads.getTemplate("thymeleaf", workload);
      templateSources.put(workload, source);

      // Pre-warm template cache
      Context ctx = new Context();
      Object model = ComparativeWorkloads.getModel(workload);
      if (model instanceof Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          ctx.setVariable(entry.getKey().toString(), entry.getValue());
        }
      }
      engine.process(source, ctx);
    }
  }

  @Override
  public String render(String workload, Object model) {
    String source = templateSources.get(workload);
    if (source == null) {
      throw new IllegalArgumentException("Unknown workload: " + workload);
    }
    Context context = new Context();
    if (model instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        context.setVariable(entry.getKey().toString(), entry.getValue());
      }
    }
    return engine.process(source, context);
  }

  @Override
  public void close() {
    if (engine != null) {
      engine.clearTemplateCache();
    }
    templateSources.clear();
  }
}
