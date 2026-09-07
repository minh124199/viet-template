package io.github.minh124199.viettemplate.runtime;

/** Escaping contexts for template output interpolation. */
public enum EscapeMode {
  HTML_TEXT,
  HTML_ATTRIBUTE_QUOTED,
  URL_COMPONENT,
  JS_STRING,
  CSS_STRING,
  RAW
}
