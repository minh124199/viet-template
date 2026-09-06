package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.util.Map;

public abstract class AbstractInterpreterTest {

  protected String render(String template) {
    return render(template, Map.of(), new VtlInterpreter());
  }

  protected String render(String template, Map<String, Object> context) {
    return render(template, context, new VtlInterpreter());
  }

  protected String render(String template, VtlInterpreter interpreter) {
    return render(template, Map.of(), interpreter);
  }

  protected String render(
      String template, Map<String, Object> context, VtlInterpreter interpreter) {
    SourceText source = SourceText.of("test.vm", template);
    VtlParserOptions parserOptions =
        VtlParserOptions.ofDefaults()
            .withAllowBareNullLiteral(interpreter.options().allowBareNullLiteral());
    VtlParseResult parseResult = VtlParser.parse(source, parserOptions);
    if (parseResult.hasErrors()) {
      throw new AssertionError("Parse errors encountered: " + parseResult.diagnostics());
    }
    VtlTemplate ast = parseResult.template();
    StringTemplateOutput output = new StringTemplateOutput();
    RenderContext renderContext = MapRenderContext.of(context);
    try {
      interpreter.interpret(ast, source, renderContext, output);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
    return output.toString();
  }
}
