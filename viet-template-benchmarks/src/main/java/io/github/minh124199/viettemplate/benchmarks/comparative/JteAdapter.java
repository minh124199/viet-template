package io.github.minh124199.viettemplate.benchmarks.comparative;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Comparative benchmark adapter for jte 3.2.4 (Track B). */
public class JteAdapter implements BenchmarkEngineAdapter {

  private TemplateEngine engine;
  private Path tempDir;

  @Override
  public String name() {
    return "jte";
  }

  @Override
  public void setup() {
    Map<String, String> templateMap = new HashMap<>();
    for (String workload : ComparativeWorkloads.ALL_WORKLOADS) {
      templateMap.put(workload + ".jte", ComparativeWorkloads.getTemplate("jte", workload));
    }

    CodeResolver codeResolver =
        new CodeResolver() {
          @Override
          public String resolve(String name) {
            return templateMap.get(name);
          }

          @Override
          public long getLastModified(String name) {
            return 1L;
          }

          @Override
          public boolean exists(String name) {
            return templateMap.containsKey(name);
          }

          @Override
          public List<String> resolveAllTemplateNames() {
            return new ArrayList<>(templateMap.keySet());
          }
        };

    try {
      tempDir = Files.createTempDirectory("jte-bench-classes");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    engine =
        TemplateEngine.create(
            codeResolver,
            tempDir,
            ContentType.Plain,
            Thread.currentThread().getContextClassLoader());
    engine.precompileAll();
  }

  @Override
  @SuppressWarnings("unchecked")
  public String render(String workload, Object model) {
    String templateName = workload + ".jte";
    StringOutput output = new StringOutput(512);
    Map<String, Object> params =
        model instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    engine.render(templateName, params, output);
    return output.toString();
  }

  @Override
  public void close() {
    if (tempDir != null && Files.exists(tempDir)) {
      try (var stream = Files.walk(tempDir)) {
        stream
            .sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException ignored) {
                  }
                });
      } catch (IOException ignored) {
      }
    }
  }
}
