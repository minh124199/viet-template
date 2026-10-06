package io.github.minh124199.viettemplate.migration;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateParameter;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Package-private reader for companion template contract files in migration analysis. */
final class MigrationContractReader {

  private MigrationContractReader() {}

  public static Optional<TemplateContract> findCompanion(
      Path templateFile, TemplateId templateId, ClassLoader classLoader) {
    Objects.requireNonNull(templateFile, "templateFile must not be null");
    Path directCompanion =
        templateFile.resolveSibling(templateFile.getFileName().toString() + ".contract");
    if (Files.isRegularFile(directCompanion)) {
      return Optional.of(read(directCompanion, templateId, classLoader));
    }
    String name = templateFile.getFileName().toString();
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      String base = name.substring(0, dot);
      Path baseCompanion = templateFile.resolveSibling(base + ".contract");
      if (Files.isRegularFile(baseCompanion)) {
        return Optional.of(read(baseCompanion, templateId, classLoader));
      }
    }
    return Optional.empty();
  }

  public static TemplateContract read(
      Path contractFile, TemplateId templateId, ClassLoader classLoader) {
    try (BufferedReader reader = Files.newBufferedReader(contractFile)) {
      return read(reader, templateId, classLoader);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to read contract file: " + contractFile, e);
    }
  }

  public static TemplateContract read(
      BufferedReader reader, TemplateId templateId, ClassLoader classLoader) throws IOException {
    ClassLoader loader =
        classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = MigrationContractReader.class.getClassLoader();
    }
    TemplateContract.Builder builder = TemplateContract.builder(templateId);
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
        continue;
      }
      int eq = line.indexOf('=');
      if (eq <= 0) {
        throw new IllegalArgumentException("Invalid contract line: " + line);
      }
      String key = line.substring(0, eq).trim();
      String val = line.substring(eq + 1).trim();

      if ("class".equalsIgnoreCase(key) || "model".equalsIgnoreCase(key)) {
        try {
          Class<?> clazz = Class.forName(val, false, loader);
          return TemplateContract.fromClass(templateId, clazz);
        } catch (ClassNotFoundException e) {
          throw new IllegalArgumentException("Cannot resolve model class: " + val, e);
        }
      } else if ("record".equalsIgnoreCase(key)) {
        try {
          Class<?> clazz = Class.forName(val, false, loader);
          return TemplateContract.fromRecord(templateId, clazz);
        } catch (ClassNotFoundException e) {
          throw new IllegalArgumentException("Cannot resolve model record: " + val, e);
        }
      } else {
        boolean nullable = false;
        String paramName = key;
        if (paramName.startsWith("nullable ")) {
          nullable = true;
          paramName = paramName.substring("nullable ".length()).trim();
        }
        TemplateParameter param = parseParameter(paramName, val, nullable, loader);
        builder.parameter(param);
      }
    }
    return builder.build();
  }

  private static List<String> splitTypeArguments(String argsPart) {
    List<String> result = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < argsPart.length(); i++) {
      char c = argsPart.charAt(i);
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
      } else if (c == ',' && depth == 0) {
        result.add(argsPart.substring(start, i).trim());
        start = i + 1;
      }
    }
    if (start < argsPart.length()) {
      result.add(argsPart.substring(start).trim());
    }
    return result;
  }

  private static TemplateParameter parseParameter(
      String name, String typeDesc, boolean nullable, ClassLoader loader) {
    String typeStr = typeDesc.trim();
    int angle = typeStr.indexOf('<');
    if (angle > 0 && typeStr.endsWith(">")) {
      String rawTypeName = typeStr.substring(0, angle).trim();
      String typeArgsPart = typeStr.substring(angle + 1, typeStr.length() - 1).trim();
      Class<?> rawClass = resolvePrimitiveOrClass(rawTypeName, loader);
      List<String> argStrings = splitTypeArguments(typeArgsPart);
      List<Class<?>> typeArgs = new ArrayList<>();
      for (String argStr : argStrings) {
        typeArgs.add(resolvePrimitiveOrClass(argStr, loader));
      }
      return TemplateParameter.of(name, rawClass, typeArgs, nullable);
    } else {
      Class<?> clazz = resolvePrimitiveOrClass(typeStr, loader);
      return TemplateParameter.of(name, clazz, nullable);
    }
  }

  private static Class<?> resolvePrimitiveOrClass(String name, ClassLoader loader) {
    switch (name) {
      case "int" -> {
        return int.class;
      }
      case "long" -> {
        return long.class;
      }
      case "double" -> {
        return double.class;
      }
      case "float" -> {
        return float.class;
      }
      case "boolean" -> {
        return boolean.class;
      }
      case "byte" -> {
        return byte.class;
      }
      case "short" -> {
        return short.class;
      }
      case "char" -> {
        return char.class;
      }
      case "String", "java.lang.String" -> {
        return String.class;
      }
      case "Integer", "java.lang.Integer" -> {
        return Integer.class;
      }
      case "Long", "java.lang.Long" -> {
        return Long.class;
      }
      case "Double", "java.lang.Double" -> {
        return Double.class;
      }
      case "Boolean", "java.lang.Boolean" -> {
        return Boolean.class;
      }
      case "Object", "java.lang.Object" -> {
        return Object.class;
      }
      case "List", "java.util.List" -> {
        return List.class;
      }
      case "Map", "java.util.Map" -> {
        return java.util.Map.class;
      }
      default -> {
        try {
          return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
          try {
            return Class.forName("java.lang." + name, false, loader);
          } catch (ClassNotFoundException e2) {
            try {
              return Class.forName("java.util." + name, false, loader);
            } catch (ClassNotFoundException e3) {
              throw new IllegalArgumentException("Unknown type in contract: " + name, e);
            }
          }
        }
      }
    }
  }
}
