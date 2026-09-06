package io.github.minh124199.viettemplate.tck.probe;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/**
 * Test-only compatibility probe helper executing templates directly against Apache Velocity Engine
 * 2.4.1.
 */
public final class Velocity241Probe {

  private Velocity241Probe() {}

  public record Result(String output, Throwable exception, Map<String, Object> finalContext) {

    public boolean isSuccess() {
      return exception == null;
    }

    public String outputOrThrow() {
      if (exception != null) {
        if (exception instanceof RuntimeException re) {
          throw re;
        }
        throw new RuntimeException("Velocity execution failed", exception);
      }
      return output;
    }
  }

  public static Result render(String template) {
    return render(template, Map.of(), new Properties());
  }

  public static Result render(String template, Map<String, Object> context) {
    return render(template, context, new Properties());
  }

  public static Result render(
      String template, Map<String, Object> context, Properties customProperties) {
    try {
      VelocityEngine engine = new VelocityEngine();
      // Default configurations
      engine.setProperty(
          "runtime.log.instance", org.slf4j.LoggerFactory.getLogger("Velocity241Probe"));
      if (customProperties != null) {
        for (String name : customProperties.stringPropertyNames()) {
          engine.setProperty(name, customProperties.getProperty(name));
        }
      }
      engine.init();

      VelocityContext vCtx = new VelocityContext();
      if (context != null) {
        context.forEach(vCtx::put);
      }

      StringWriter out = new StringWriter();
      engine.evaluate(vCtx, out, "Velocity241Probe", template);

      Map<String, Object> finalCtx = new HashMap<>();
      for (String key : vCtx.getKeys()) {
        finalCtx.put(key, vCtx.get(key));
      }

      return new Result(out.toString(), null, finalCtx);
    } catch (Throwable t) {
      return new Result(null, t, Map.of());
    }
  }
}
