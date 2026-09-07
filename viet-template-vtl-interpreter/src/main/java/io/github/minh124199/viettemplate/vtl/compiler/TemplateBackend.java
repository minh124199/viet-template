package io.github.minh124199.viettemplate.vtl.compiler;

import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;

/** Service Provider Interface (SPI) for template execution and compilation backends. */
public interface TemplateBackend {

  BackendId id();

  BackendCapabilities capabilities();

  BackendResult compile(IrTemplate template, BackendOptions options);
}
