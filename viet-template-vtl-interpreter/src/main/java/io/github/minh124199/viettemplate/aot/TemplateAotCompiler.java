package io.github.minh124199.viettemplate.aot;

/**
 * Public Ahead-Of-Time (AOT) compiler facade for compiling VietTemplate templates into JVM bytecode
 * during build time.
 */
public interface TemplateAotCompiler {

  /**
   * Creates a default instance of the template AOT compiler.
   *
   * @return a new {@link TemplateAotCompiler} instance
   */
  static TemplateAotCompiler create() {
    return new DefaultTemplateAotCompiler();
  }

  /**
   * Compiles the requested templates according to the provided request configuration.
   *
   * @param request the compilation request specifying source directories, output locations, and
   *     options
   * @return the compilation result containing artifacts, diagnostics, and metrics
   */
  TemplateAotResult compile(TemplateAotRequest request);
}
