package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Generates compile-time safe typed Java facade classes for templates with contracts. */
final class TypedTemplateFacadeGenerator {

  private TypedTemplateFacadeGenerator() {}

  public static String deriveFacadeClassName(TemplateId templateId) {
    String val = templateId.value();
    int dot = val.lastIndexOf('.');
    if (dot > 0) {
      val = val.substring(0, dot);
    }
    String[] parts = val.split("[/\\\\_\\-\\.]+");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (!part.isEmpty()) {
        sb.append(Character.toUpperCase(part.charAt(0)));
        if (part.length() > 1) {
          sb.append(part.substring(1));
        }
      }
    }
    if (sb.isEmpty() || Character.isDigit(sb.charAt(0))) {
      sb.insert(0, "T");
    }
    sb.append("View");
    return sb.toString();
  }

  public static Path generate(
      TemplateContract contract, String compiledFqcn, String packagePrefix, Path sourceOutputDir)
      throws IOException {
    Objects.requireNonNull(contract, "contract must not be null");
    Objects.requireNonNull(compiledFqcn, "compiledFqcn must not be null");
    Objects.requireNonNull(packagePrefix, "packagePrefix must not be null");
    Objects.requireNonNull(sourceOutputDir, "sourceOutputDir must not be null");

    String className = deriveFacadeClassName(contract.templateId());
    String pkg =
        packagePrefix.isBlank() ? "io.github.minh124199.viettemplate.generated" : packagePrefix;
    Path pkgDir = pkg.isEmpty() ? sourceOutputDir : sourceOutputDir.resolve(pkg.replace('.', '/'));
    Files.createDirectories(pkgDir);
    Path javaFile = pkgDir.resolve(className + ".java");

    String source = generateSource(contract, compiledFqcn, pkg, className);
    Files.writeString(javaFile, source, StandardCharsets.UTF_8);
    return javaFile;
  }

  public static String generateSource(
      TemplateContract contract, String compiledFqcn, String pkg, String className) {
    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(pkg).append(";\n\n");
    sb.append("import io.github.minh124199.viettemplate.api.CompiledTemplate;\n");
    sb.append("import io.github.minh124199.viettemplate.api.RenderContext;\n");
    sb.append("import io.github.minh124199.viettemplate.api.TemplateOutput;\n");
    sb.append("import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;\n");
    sb.append("import java.io.IOException;\n");
    sb.append("import java.io.UncheckedIOException;\n\n");
    sb.append("/**\n");
    sb.append(" * Generated typed Java facade for template {@code ")
        .append(contract.templateId().value())
        .append("}.\n");
    sb.append(" */\n");
    sb.append("public final class ").append(className).append(" {\n\n");
    sb.append("  private static final CompiledTemplate COMPILED = new ")
        .append(compiledFqcn)
        .append("();\n\n");
    sb.append("  private ").append(className).append("() {}\n\n");

    List<TemplateParameter> params = contract.parameters();

    // 1. render(TemplateOutput output, ...)
    sb.append("  public static void render(TemplateOutput output");
    for (TemplateParameter p : params) {
      sb.append(", ").append(formatType(p)).append(" ").append(p.name());
    }
    sb.append(") throws IOException {\n");
    sb.append("    RenderContext context = ").append(renderContextExpression(params)).append(";\n");
    sb.append("    COMPILED.render(context, output);\n");
    sb.append("  }\n\n");

    // 2. render(...) returning String
    sb.append("  public static String render(");
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) sb.append(", ");
      TemplateParameter p = params.get(i);
      sb.append(formatType(p)).append(" ").append(p.name());
    }
    sb.append(") {\n");
    sb.append("    RenderContext context = ").append(renderContextExpression(params)).append(";\n");
    sb.append("    StringTemplateOutput out = new StringTemplateOutput();\n");
    sb.append("    try {\n");
    sb.append("      COMPILED.render(context, out);\n");
    sb.append("    } catch (IOException e) {\n");
    sb.append("      throw new UncheckedIOException(e);\n");
    sb.append("    }\n");
    sb.append("    return out.toString();\n");
    sb.append("  }\n");

    sb.append("}\n");
    return sb.toString();
  }

  private static String renderContextExpression(List<TemplateParameter> params) {
    if (params.isEmpty()) {
      return "RenderContext.empty()";
    }
    if (params.size() <= 4) {
      StringBuilder sb = new StringBuilder("RenderContext.of(");
      for (int i = 0; i < params.size(); i++) {
        if (i > 0) sb.append(", ");
        TemplateParameter p = params.get(i);
        sb.append('"').append(p.name()).append("\", ").append(p.name());
      }
      sb.append(")");
      return sb.toString();
    }
    // More than 4: use parallel array overload
    StringBuilder keys = new StringBuilder("new String[] {");
    StringBuilder vals = new StringBuilder("new Object[] {");
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) {
        keys.append(", ");
        vals.append(", ");
      }
      keys.append('"').append(params.get(i).name()).append('"');
      vals.append(params.get(i).name());
    }
    keys.append("}");
    vals.append("}");
    return "RenderContext.of(" + keys + ", " + vals + ")";
  }

  private static String formatType(TemplateParameter param) {
    String raw = param.rawType().getCanonicalName();
    if (raw == null) {
      raw = param.rawType().getName();
    }
    if (param.typeArguments().isEmpty()) {
      return raw;
    }
    StringBuilder sb = new StringBuilder(raw).append('<');
    for (int i = 0; i < param.typeArguments().size(); i++) {
      if (i > 0) sb.append(", ");
      Class<?> arg = param.typeArguments().get(i);
      String argName = arg.getCanonicalName();
      sb.append(argName != null ? argName : arg.getName());
    }
    sb.append('>');
    return sb.toString();
  }
}
