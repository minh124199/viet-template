package io.github.minh124199.viettemplate.language.vtl.ast;

/** Common sealed interface for all directive AST nodes in VTL. */
public sealed interface VtlDirectiveNode extends VtlNode
    permits VtlSetDirectiveNode,
        VtlIfDirectiveNode,
        VtlForeachDirectiveNode,
        VtlIncludeDirectiveNode,
        VtlParseDirectiveNode,
        VtlBreakDirectiveNode,
        VtlStopDirectiveNode,
        VtlEvaluateDirectiveNode,
        VtlDefineDirectiveNode,
        VtlMacroDefinitionNode,
        VtlDirectiveCallNode,
        VtlBlockDirectiveCallNode {}
