package io.github.minh124199.viettemplate.language.vtl.ir.plan;

/** Escaping strategy to be applied when rendering a dynamic value. */
public enum IrEscapeMode {
  RAW,
  HTML_TEXT,
  HTML_ATTRIBUTE_QUOTED,
  URL_COMPONENT
}
