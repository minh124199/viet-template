package io.github.minh124199.viettemplate.benchmarks.comparative;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl;

/** Comparative benchmark adapter for Apache Velocity 2.4.1 (Track A). */
public class VelocityAdapter implements BenchmarkEngineAdapter {

  private VelocityEngine engine;
  private final Map<String, Template> templates = new HashMap<>();

  @Override
  public String name() {
    return "Velocity";
  }

  @Override
  public void setup() {
    engine = new VelocityEngine();
    engine.setProperty("resource.loaders", "string");
    engine.setProperty(
        "resource.loader.string.class",
        "org.apache.velocity.runtime.resource.loader.StringResourceLoader");
    engine.setProperty("resource.loader.string.repository.name", "velocityBenchmarkRepo");
    engine.setProperty("resource.loader.string.repository.static", "false");

    StringResourceRepository repo = new StringResourceRepositoryImpl();
    engine.setApplicationAttribute("velocityBenchmarkRepo", repo);
    engine.init();

    for (String workload : ComparativeWorkloads.ALL_WORKLOADS) {
      String templateName = workload + ".vm";
      repo.putStringResource(templateName, ComparativeWorkloads.getTemplate("velocity", workload));
      templates.put(workload, engine.getTemplate(templateName));
    }
  }

  @Override
  public String render(String workload, Object model) {
    Template template = templates.get(workload);
    if (template == null) {
      throw new IllegalArgumentException("Unknown workload: " + workload);
    }
    VelocityContext context = new VelocityContext();
    if (model instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        context.put(entry.getKey().toString(), entry.getValue());
      }
    }
    StringWriter writer = new StringWriter(512);
    template.merge(context, writer);
    return writer.toString();
  }

  @Override
  public void close() {
    templates.clear();
  }
}
