package io.github.minh124199.viettemplate.language.vtl.semantics.scope;

/** Lexical scope classification within the template semantic model. */
public enum ScopeKind {
  /** Root template model parameters provided by the caller. */
  ROOT_MODEL,

  /** Local variables introduced by #set or #define. */
  LOCAL,

  /** Loop scope introduced by #foreach, containing loop variable and $foreach metadata. */
  LOOP,

  /** Macro scope introduced by #macro, containing parameters and $bodyContent. */
  MACRO,

  /** Sub-template scope introduced by #include or #parse. */
  INCLUDE_PARSE,

  /** Built-in engine globals and intrinsic symbols. */
  BUILTIN
}
