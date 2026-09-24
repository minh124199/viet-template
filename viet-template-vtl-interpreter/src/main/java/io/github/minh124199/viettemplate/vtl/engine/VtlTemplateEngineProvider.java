package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateEngineProvider;
import io.github.minh124199.viettemplate.vtl.internal.compiler.*;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.*;
import io.github.minh124199.viettemplate.vtl.internal.engine.context.*;
import io.github.minh124199.viettemplate.vtl.internal.engine.dependency.*;
import io.github.minh124199.viettemplate.vtl.internal.engine.layout.*;
import io.github.minh124199.viettemplate.vtl.internal.engine.macro.*;
import io.github.minh124199.viettemplate.vtl.internal.engine.watcher.*;
import io.github.minh124199.viettemplate.vtl.internal.interpreter.*;

/** Reference implementation of {@link TemplateEngineProvider}. */
public final class VtlTemplateEngineProvider implements TemplateEngineProvider {

  @Override
  public TemplateEngine.Builder createBuilder() {
    return new VtlTemplateEngineBuilder();
  }
}
