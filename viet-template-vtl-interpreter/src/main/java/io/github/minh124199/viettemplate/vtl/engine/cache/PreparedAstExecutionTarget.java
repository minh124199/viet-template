package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

record PreparedAstExecutionTarget(
    VtlInterpreter interpreter,
    SourceText sourceText,
    Optional<VtlTemplate> astNode)
    implements ExecutionTarget {

  PreparedAstExecutionTarget {
    Objects.requireNonNull(interpreter, "interpreter must not be null");
    Objects.requireNonNull(sourceText, "sourceText must not be null");
    Objects.requireNonNull(astNode, "astNode must not be null");
  }

  @Override
  public void render(RenderContext context, TemplateOutput output) throws IOException {
    VtlTemplate template =
        astNode.orElseGet(() -> {
          VtlParseResult parseResult = VtlParser.parse(sourceText);
          return parseResult.template();
        });
    EngineInterpreterBridge.render(interpreter, sourceText, template, context, output);
  }
}
