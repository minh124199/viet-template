package io.github.minh124199.viettemplate.schema.internal;

import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.PrimitiveKind;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ArrayTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ClassTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.DynamicTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.EnumTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.MapTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.NamedTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ParameterDef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ParameterizedTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.PrimitiveTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.PropertyDef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.TypeDef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.TypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.UnionTypeRef;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.WildcardTypeRef;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Internal bridge converting a canonical schema model into an internal VTL {@link ModelSchema}. */
public final class CanonicalModelSchemaConverter {

  private CanonicalModelSchemaConverter() {}

  public static ModelSchema toModelSchema(CanonicalSchema schema, ClassLoader classLoader) {
    Objects.requireNonNull(schema, "schema must not be null");
    ModelSchema.Builder builder = ModelSchema.builder();
    for (Map.Entry<String, ParameterDef> entry : schema.parameters().entrySet()) {
      ParameterDef param = entry.getValue();
      Nullability nullability = param.nullable() ? Nullability.NULLABLE : Nullability.NON_NULL;
      VType type = convertToVType(schema, param.type(), nullability, classLoader);
      builder.add(param.name(), type);
    }
    for (Map.Entry<String, TypeDef> entry : schema.types().entrySet()) {
      String typeName = entry.getKey();
      TypeDef typeDef = entry.getValue();
      Map<String, VType> propMap = new LinkedHashMap<>();
      for (Map.Entry<String, PropertyDef> propEntry : typeDef.properties().entrySet()) {
        PropertyDef prop = propEntry.getValue();
        Nullability nullability = prop.nullable() ? Nullability.NULLABLE : Nullability.NON_NULL;
        VType propType = convertToVType(schema, prop.type(), nullability, classLoader);
        propMap.put(prop.name(), propType);
      }
      builder.addType(typeName, propMap);
    }
    return builder.build();
  }

  private static VType convertToVType(
      CanonicalSchema schema, TypeRef typeRef, Nullability nullability, ClassLoader classLoader) {
    if (typeRef instanceof PrimitiveTypeRef ptr) {
      String name = ptr.name().toLowerCase(Locale.ROOT);
      return switch (name) {
        case "string" -> VType.ClassType.of("java.lang.String", List.of(), nullability);
        case "int", "integer" ->
            nullability == Nullability.NON_NULL
                ? new VType.PrimitiveType(PrimitiveKind.INT)
                : VType.ClassType.of("java.lang.Integer", List.of(), nullability);
        case "long" ->
            nullability == Nullability.NON_NULL
                ? new VType.PrimitiveType(PrimitiveKind.LONG)
                : VType.ClassType.of("java.lang.Long", List.of(), nullability);
        case "boolean" ->
            nullability == Nullability.NON_NULL
                ? new VType.PrimitiveType(PrimitiveKind.BOOLEAN)
                : VType.ClassType.of("java.lang.Boolean", List.of(), nullability);
        case "number" -> VType.ClassType.of("java.lang.Number", List.of(), nullability);
        case "double" ->
            nullability == Nullability.NON_NULL
                ? new VType.PrimitiveType(PrimitiveKind.DOUBLE)
                : VType.ClassType.of("java.lang.Double", List.of(), nullability);
        case "float" ->
            nullability == Nullability.NON_NULL
                ? new VType.PrimitiveType(PrimitiveKind.FLOAT)
                : VType.ClassType.of("java.lang.Float", List.of(), nullability);
        case "byte" ->
            nullability == Nullability.NON_NULL
                ? new VType.PrimitiveType(PrimitiveKind.BYTE)
                : VType.ClassType.of("java.lang.Byte", List.of(), nullability);
        case "short" ->
            nullability == Nullability.NON_NULL
                ? new VType.PrimitiveType(PrimitiveKind.SHORT)
                : VType.ClassType.of("java.lang.Short", List.of(), nullability);
        case "char", "character" ->
            nullability == Nullability.NON_NULL
                ? new VType.PrimitiveType(PrimitiveKind.CHAR)
                : VType.ClassType.of("java.lang.Character", List.of(), nullability);
        default -> VType.ClassType.of(ptr.name(), List.of(), nullability);
      };
    }
    if (typeRef instanceof ClassTypeRef ctr) {
      if ("java.lang.String".equals(ctr.name())) {
        return VType.ClassType.of("java.lang.String", List.of(), nullability);
      }
      if (schema.format() == SchemaFormat.JAVA || schema.format() == SchemaFormat.CONTRACT) {
        Optional<Class<?>> loaded = tryLoadClass(ctr.name(), classLoader);
        if (loaded.isPresent()) {
          return VType.ClassType.of(loaded.get(), nullability);
        }
      }
      return VType.ClassType.of(ctr.name(), List.of(), nullability);
    }
    if (typeRef instanceof NamedTypeRef ntr) {
      List<VType> args =
          ntr.arguments().stream()
              .map(a -> convertToVType(schema, a, Nullability.UNKNOWN, classLoader))
              .toList();
      if (schema.format() == SchemaFormat.JAVA || schema.format() == SchemaFormat.CONTRACT) {
        Optional<Class<?>> loaded = tryLoadClass(ntr.name(), classLoader);
        if (loaded.isPresent()) {
          return VType.ClassType.of(loaded.get(), args, nullability);
        }
      }
      return VType.ClassType.of(ntr.name(), args, nullability);
    }
    if (typeRef instanceof ArrayTypeRef atr) {
      return new VType.ArrayType(
          convertToVType(schema, atr.componentType(), Nullability.UNKNOWN, classLoader),
          nullability);
    }
    if (typeRef instanceof MapTypeRef mtr) {
      VType k = convertToVType(schema, mtr.keyType(), Nullability.UNKNOWN, classLoader);
      VType v = convertToVType(schema, mtr.valueType(), Nullability.UNKNOWN, classLoader);
      return VType.ClassType.of("java.util.Map", List.of(k, v), nullability);
    }
    if (typeRef instanceof EnumTypeRef etr) {
      if (schema.format() == SchemaFormat.JAVA || schema.format() == SchemaFormat.CONTRACT) {
        Optional<Class<?>> loaded = tryLoadClass(etr.name(), classLoader);
        if (loaded.isPresent()) {
          return VType.ClassType.of(loaded.get(), nullability);
        }
      }
      return VType.ClassType.of(etr.name(), List.of(), nullability);
    }
    if (typeRef instanceof ParameterizedTypeRef ptr) {
      List<VType> args =
          ptr.arguments().stream()
              .map(a -> convertToVType(schema, a, Nullability.UNKNOWN, classLoader))
              .toList();
      if (schema.format() == SchemaFormat.JAVA || schema.format() == SchemaFormat.CONTRACT) {
        Optional<Class<?>> loaded = tryLoadClass(ptr.rawType(), classLoader);
        if (loaded.isPresent()) {
          return VType.ClassType.of(loaded.get(), args, nullability);
        }
      }
      return VType.ClassType.of(ptr.rawType(), args, nullability);
    }
    if (typeRef instanceof UnionTypeRef utr) {
      List<VType> options =
          utr.options().stream()
              .map(o -> convertToVType(schema, o, nullability, classLoader))
              .toList();
      return new VType.UnionType(options, nullability);
    }
    if (typeRef instanceof DynamicTypeRef) {
      return VTypes.DYNAMIC;
    }
    if (typeRef instanceof WildcardTypeRef wtr) {
      return wtr.bound()
          .map(b -> convertToVType(schema, b, nullability, classLoader))
          .orElse(VTypes.DYNAMIC);
    }
    return VTypes.DYNAMIC;
  }

  private static Optional<Class<?>> tryLoadClass(String className, ClassLoader classLoader) {
    if (className == null || className.isBlank()) {
      return Optional.empty();
    }
    try {
      if (classLoader != null) {
        return Optional.of(Class.forName(className, false, classLoader));
      }
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl != null) {
        return Optional.of(Class.forName(className, false, cl));
      }
      return Optional.of(
          Class.forName(className, false, CanonicalModelSchemaConverter.class.getClassLoader()));
    } catch (ClassNotFoundException | LinkageError ignored) {
      return Optional.empty();
    }
  }
}
