package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;

/**
 * Canonical semantic symbol identity used across the workspace reference graph and find references
 * provider.
 *
 * <p>Differentiates semantic declarations from string names, ensuring precise cross-language
 * resolution and zero false positives between distinct types, overloads, and scopes.
 */
sealed interface WorkspaceSymbolKey extends Serializable
    permits JvmMemberSymbolKey,
        SchemaMemberSymbolKey,
        TemplateLocalSymbolKey,
        RootParameterSymbolKey {

  static JvmMemberSymbolKey jvmMember(
      String declaringClassName,
      JvmMemberSymbolKey.Kind memberKind,
      String memberName,
      String descriptor,
      int parameterCount) {
    return new JvmMemberSymbolKey(
        declaringClassName, memberKind, memberName, descriptor, parameterCount);
  }

  static JvmMemberSymbolKey jvmMember(
      String declaringClassName, JvmMemberSymbolKey.Kind memberKind, String memberName) {
    return new JvmMemberSymbolKey(declaringClassName, memberKind, memberName, "", 0);
  }

  static SchemaMemberSymbolKey schemaMember(
      String schemaSource, String typeName, String propertyName) {
    return new SchemaMemberSymbolKey(schemaSource, typeName, propertyName);
  }

  static TemplateLocalSymbolKey templateLocal(
      String templateUri, String variableName, int definitionStartOffset, int definitionEndOffset) {
    return new TemplateLocalSymbolKey(
        templateUri, variableName, definitionStartOffset, definitionEndOffset);
  }

  static RootParameterSymbolKey rootParameter(String schemaSourceOrTemplateUri, String rootName) {
    return new RootParameterSymbolKey(schemaSourceOrTemplateUri, rootName);
  }
}
