package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.linker.CallSiteRegistry;
import java.io.IOException;
import java.util.Objects;

/**
 * Internal execution bridge allowing engine components and conformance harnesses to invoke
 * interpreter execution targets without exposing internal AST or IR types on {@link
 * VtlInterpreter}.
 */
public final class EngineInterpreterBridge {

  private EngineInterpreterBridge() {}

  public static VtlInterpreter create(
      VtlInterpreterOptions options, ReferenceAccess referenceAccess) {
    return new VtlInterpreter(options, referenceAccess);
  }

  /**
   * Creates a {@link VtlInterpreter} backed by a high-performance inline-cached {@link
   * LinkedReferenceAccess} sharing the supplied {@link CallSiteRegistry}.
   */
  public static VtlInterpreter create(
      VtlInterpreterOptions options, CallSiteRegistry callSiteRegistry) {
    Objects.requireNonNull(options, "options must not be null");
    Objects.requireNonNull(callSiteRegistry, "callSiteRegistry must not be null");
    ReferenceAccess access = new LinkedReferenceAccess(options.securityPolicy(), callSiteRegistry);
    return new VtlInterpreter(options, access);
  }

  public static CompiledTemplate prepareIr(
      VtlInterpreter interpreter, IrTemplate template, SourceText source) {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    return interpreter.prepareIr(template, source);
  }

  public static void render(
      VtlInterpreter interpreter,
      SourceText source,
      VtlTemplate template,
      RenderContext renderContext,
      TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    interpreter.render(source, template, renderContext, output);
  }

  public static void render(
      VtlInterpreter interpreter,
      SourceText source,
      VtlTemplate template,
      ExecutionContext context,
      TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    interpreter.render(source, template, context, output);
  }

  public static void render(
      VtlInterpreter interpreter,
      IrTemplate template,
      SourceText source,
      RenderContext renderContext,
      TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    interpreter.render(template, source, renderContext, output);
  }

  public static void render(
      VtlInterpreter interpreter,
      IrTemplate template,
      SourceText source,
      ExecutionContext context,
      TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    interpreter.render(template, source, context, output);
  }

  public static void render(
      VtlInterpreter interpreter,
      VtlTemplate template,
      SourceText source,
      RenderContext renderContext,
      TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    interpreter.render(template, source, renderContext, output);
  }

  public static void interpret(
      VtlInterpreter interpreter,
      VtlTemplate template,
      SourceText source,
      RenderContext renderContext,
      TemplateOutput output)
      throws IOException {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    interpreter.interpret(template, source, renderContext, output);
  }
}
