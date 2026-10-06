package io.github.minh124199.viettemplate.explanation;

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

/** Package-private reader for companion template contract files in explanation. */
final class ExplanationContractReader {

  private ExplanationContractReader() {}

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
      loader = ExplanationContractReader.class.getClassLoader();
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
    int angle = typeDesc.indexOf('<');
    if (angle < 0) {
      try {
        Class<?> raw = resolveClass(typeDesc.trim(), loader);
        return TemplateParameter.of(name, raw, nullable);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException(
            "Cannot resolve parameter type '" + typeDesc + "' for '" + name + "'", e);
      }
    }
    String rawName = typeDesc.substring(0, angle).trim();
    String argsPart = typeDesc.substring(angle + 1, typeDesc.lastIndexOf('>')).trim();
    try {
      Class<?> raw = resolveClass(rawName, loader);
      List<Class<?>> typeArgs = new ArrayList<>();
      for (String arg : splitTypeArguments(argsPart)) {
        typeArgs.add(resolveTypeArgument(arg, loader));
      }
      return TemplateParameter.of(name, raw, typeArgs, nullable);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "Cannot resolve parameter type '" + typeDesc + "' for '" + name + "'", e);
    }
  }

  private static Class<?> resolveTypeArgument(String arg, ClassLoader loader)
      throws ClassNotFoundException {
    String trimmed = arg.trim();
    if (trimmed.equals("?")) {
      return Object.class;
    }
    if (trimmed.startsWith("? extends ")) {
      trimmed = trimmed.substring("? extends ".length()).trim();
    } else if (trimmed.startsWith("? super ")) {
      trimmed = trimmed.substring("? super ".length()).trim();
    }
    int subAngle = trimmed.indexOf('<');
    if (subAngle > 0) {
      trimmed = trimmed.substring(0, subAngle).trim();
    }
    return resolveClass(trimmed, loader);
  }

  private static Class<?> resolveClass(String name, ClassLoader loader)
      throws ClassNotFoundException {
    return switch (name) {
      case "int" -> int.class;
      case "long" -> long.class;
      case "boolean" -> boolean.class;
      case "double" -> double.class;
      case "float" -> float.class;
      case "short" -> short.class;
      case "byte" -> byte.class;
      case "char" -> char.class;
      case "String" -> String.class;
      case "Integer" -> Integer.class;
      case "Long" -> Long.class;
      case "Boolean" -> Boolean.class;
      case "Double" -> Double.class;
      case "Float" -> Float.class;
      case "Short" -> Short.class;
      case "Byte" -> Byte.class;
      case "Character" -> Character.class;
      case "Object" -> Object.class;
      case "List" -> java.util.List.class;
      case "Map" -> java.util.Map.class;
      case "Set" -> java.util.Set.class;
      case "Collection" -> java.util.Collection.class;
      case "Iterable" -> java.lang.Iterable.class;
      case "CharSequence" -> java.lang.CharSequence.class;
      case "BigDecimal" -> java.math.BigDecimal.class;
      case "BigInteger" -> java.math.BigInteger.class;
      case "UUID" -> java.util.UUID.class;
      case "Instant" -> java.time.Instant.class;
      case "LocalDate" -> java.time.LocalDate.class;
      case "LocalDateTime" -> java.time.LocalDateTime.class;
      default -> {
        try {
          yield Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
          if (!name.contains(".")) {
            try {
              yield Class.forName("java.lang." + name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
            try {
              yield Class.forName("java.util." + name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
            try {
              yield Class.forName("java.math." + name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
          }
          throw e;
        }
      }
    };
  }
}
