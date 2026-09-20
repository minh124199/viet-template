package io.github.minh124199.viettemplate.benchmarks.comparative;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import java.util.HashMap;
import java.util.Map;

/** Comparative benchmark adapter for Quarkus Qute 3.39.4 (Track B). */
public class QuteAdapter implements BenchmarkEngineAdapter {

  private Engine engine;
  private final Map<String, Template> templates = new HashMap<>();

  @Override
  public String name() {
    return "Qute";
  }

  @Override
  public void setup() {
    engine =
        Engine.builder()
            .addDefaults()
            .addValueResolver(new io.quarkus.qute.ReflectionValueResolver())
            .build();
    for (String workload : ComparativeWorkloads.ALL_WORKLOADS) {
      String content = ComparativeWorkloads.getTemplate("qute", workload);
      Template tpl = engine.parse(content);
      templates.put(workload, tpl);
    }
  }

  @Override
  public String render(String workload, Object model) {
    Template template = templates.get(workload);
    if (template == null) {
      throw new IllegalArgumentException("Unknown workload: " + workload);
    }
    TemplateInstance instance = template.instance();
    if (model instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        instance.data(entry.getKey().toString(), entry.getValue());
      }
    }
    return instance.render();
  }

  @Override
  public void close() {
    if (engine != null) {
      engine.clearTemplates();
    }
    templates.clear();
  }
}
