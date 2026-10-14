package io.github.minh124199.viettemplate.qualification.m46a;

import io.github.minh124199.viettemplate.aot.TypeScriptContractProjector;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.TypeRef;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Qualification wrapper delegating directly to {@link TypeScriptContractProjector}.
 *
 * <p>Preserved for backward test compatibility with M46A qualification test fixtures.
 */
public final class M46APrototypeTypeScriptProjector {

  private final TypeScriptContractProjector delegate;

  public M46APrototypeTypeScriptProjector() {
    this.delegate = new TypeScriptContractProjector();
  }

  public M46APrototypeTypeScriptProjector(MemberAccessPolicy memberAccessPolicy) {
    this.delegate = new TypeScriptContractProjector(memberAccessPolicy);
  }

  public String projectContract(TemplateContract contract) {
    return delegate.projectContract(contract);
  }

  public String projectClass(Class<?> clazz, String templateId) {
    return delegate.projectClass(clazz, templateId);
  }

  public String projectSchema(CanonicalSchema schema) {
    return delegate.projectSchema(schema);
  }

  public static String deriveTemplateParametersName(String templateId) {
    return TypeScriptContractProjector.deriveTemplateParametersName(templateId);
  }

  public static String projectCanonicalTypeRef(TypeRef type, Map<String, String> typeSymbolTable) {
    return TypeScriptContractProjector.projectCanonicalTypeRef(type, typeSymbolTable);
  }

  public static boolean writeIfChanged(Path file, String content) throws IOException {
    return TypeScriptContractProjector.writeIfChanged(file, content);
  }

  public static int cleanStaleOutputs(
      Path outputDir, Set<Path> currentOutputs, Set<Path> previousOwnedOutputs) throws IOException {
    return TypeScriptContractProjector.cleanStaleOutputs(
        outputDir, currentOutputs, previousOwnedOutputs);
  }

  public static Path deriveDeclarationFilePath(Path baseOutputDir, String templatePath) {
    return TypeScriptContractProjector.deriveDeclarationFilePath(baseOutputDir, templatePath);
  }

  public static Map<String, String> resolveTypeNames(Set<String> fqcns) {
    return TypeScriptContractProjector.resolveTypeNames(fqcns);
  }

  public static Map<String, String> resolveTypeNames(Set<String> fqcns, Set<String> reservedNames) {
    return TypeScriptContractProjector.resolveTypeNames(fqcns, reservedNames);
  }

  public static String extractSimpleName(String fqcn) {
    return TypeScriptContractProjector.extractSimpleName(fqcn);
  }

  public static String sanitizeTypeName(String raw) {
    return TypeScriptContractProjector.sanitizeTypeName(raw);
  }

  public static String formatPropertyName(String raw) {
    return TypeScriptContractProjector.formatPropertyName(raw);
  }
}
