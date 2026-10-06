package io.github.minh124199.viettemplate.schema;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Domain model representing canonical template schemas and rich type definitions for tooling and
 * verification.
 */
public final class CanonicalSchemaModel {

  private CanonicalSchemaModel() {}

  /** Sealed representation of all schema-compatible types. */
  public sealed interface TypeRef extends Serializable
      permits PrimitiveTypeRef,
          ClassTypeRef,
          NamedTypeRef,
          ArrayTypeRef,
          MapTypeRef,
          EnumTypeRef,
          UnionTypeRef,
          WildcardTypeRef,
          ParameterizedTypeRef,
          DynamicTypeRef {

    String displayName();
  }

  /** Primitive scalar type reference (e.g. string, integer, boolean). */
  public record PrimitiveTypeRef(String name) implements TypeRef {
    public PrimitiveTypeRef {
      Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public String displayName() {
      return name;
    }
  }

  /** Java class type reference (e.g. java.lang.String, com.example.User). */
  public record ClassTypeRef(String name) implements TypeRef {
    public ClassTypeRef {
      Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public String displayName() {
      return simpleName(name);
    }
  }

  /** Named user-defined or schema type reference with optional type arguments. */
  public record NamedTypeRef(String name, List<TypeRef> arguments) implements TypeRef {
    public NamedTypeRef {
      Objects.requireNonNull(name, "name must not be null");
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public NamedTypeRef(String name) {
      this(name, List.of());
    }

    @Override
    public String displayName() {
      String simple = simpleName(name);
      if (arguments.isEmpty()) {
        return simple;
      }
      return simple
          + "<"
          + arguments.stream().map(TypeRef::displayName).collect(Collectors.joining(", "))
          + ">";
    }
  }

  /** Array type reference. */
  public record ArrayTypeRef(TypeRef componentType) implements TypeRef {
    public ArrayTypeRef {
      Objects.requireNonNull(componentType, "componentType must not be null");
    }

    @Override
    public String displayName() {
      return componentType.displayName() + "[]";
    }
  }

  /** Key-value map type reference. */
  public record MapTypeRef(TypeRef keyType, TypeRef valueType) implements TypeRef {
    public MapTypeRef {
      Objects.requireNonNull(keyType, "keyType must not be null");
      Objects.requireNonNull(valueType, "valueType must not be null");
    }

    @Override
    public String displayName() {
      return "Map<" + keyType.displayName() + ", " + valueType.displayName() + ">";
    }
  }

  /** Enumeration type reference with allowed symbol values. */
  public record EnumTypeRef(String name, List<String> symbols) implements TypeRef {
    public EnumTypeRef {
      Objects.requireNonNull(name, "name must not be null");
      symbols = symbols == null ? List.of() : List.copyOf(symbols);
    }

    public EnumTypeRef(String name) {
      this(name, List.of());
    }

    @Override
    public String displayName() {
      String simple = simpleName(name);
      return simple.isEmpty() ? "enum" : simple;
    }
  }

  /** Union type reference representing one of multiple possible types. */
  public record UnionTypeRef(List<TypeRef> options) implements TypeRef {
    public UnionTypeRef {
      options = options == null ? List.of() : List.copyOf(options);
    }

    @Override
    public String displayName() {
      if (options.isEmpty()) {
        return "never";
      }
      return options.stream().map(TypeRef::displayName).collect(Collectors.joining(" | "));
    }
  }

  /** Wildcard type reference with optional upper or lower bound. */
  public record WildcardTypeRef(String boundKind, Optional<TypeRef> bound) implements TypeRef {
    public WildcardTypeRef {
      Objects.requireNonNull(boundKind, "boundKind must not be null");
      bound = bound == null ? Optional.empty() : bound;
    }

    public WildcardTypeRef() {
      this("extends", Optional.empty());
    }

    public WildcardTypeRef(String boundKind, TypeRef bound) {
      this(boundKind, Optional.ofNullable(bound));
    }

    @Override
    public String displayName() {
      if (bound.isEmpty()) {
        return "?";
      }
      return "? " + boundKind + " " + bound.get().displayName();
    }
  }

  /** Parameterized generic type reference (e.g. List<String>). */
  public record ParameterizedTypeRef(String rawType, List<TypeRef> arguments) implements TypeRef {
    public ParameterizedTypeRef {
      Objects.requireNonNull(rawType, "rawType must not be null");
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public ParameterizedTypeRef(String rawType) {
      this(rawType, List.of());
    }

    @Override
    public String displayName() {
      String raw = simpleName(rawType);
      if (arguments.isEmpty()) {
        return raw;
      }
      return raw
          + "<"
          + arguments.stream().map(TypeRef::displayName).collect(Collectors.joining(", "))
          + ">";
    }
  }

  /** Dynamic / unconstrained type reference. */
  public record DynamicTypeRef() implements TypeRef {
    @Override
    public String displayName() {
      return "dynamic";
    }
  }

  /** Property definition on a composite type or record. */
  public record PropertyDef(
      String name, TypeRef type, boolean nullable, boolean optional, String documentation)
      implements Serializable {

    public PropertyDef {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
      documentation = documentation == null ? "" : documentation;
    }

    public PropertyDef(String name, TypeRef type, boolean nullable) {
      this(name, type, nullable, false, "");
    }

    public PropertyDef(String name, TypeRef type, boolean nullable, boolean optional) {
      this(name, type, nullable, optional, "");
    }

    public boolean required() {
      return !optional;
    }
  }

  /** Type definition describing an object, enum, or map type in the canonical schema. */
  public record TypeDef(
      String name,
      String kind,
      Map<String, PropertyDef> properties,
      List<String> enumConstants,
      Optional<TypeRef> mapValueType,
      String documentation)
      implements Serializable {

    public TypeDef {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(kind, "kind must not be null");
      properties =
          properties == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(properties));
      enumConstants = enumConstants == null ? List.of() : List.copyOf(enumConstants);
      mapValueType = mapValueType == null ? Optional.empty() : mapValueType;
      documentation = documentation == null ? "" : documentation;
    }

    public TypeDef(String name, String kind, Map<String, PropertyDef> properties) {
      this(name, kind, properties, List.of(), Optional.empty(), "");
    }

    public TypeDef(
        String name, String kind, Map<String, PropertyDef> properties, List<String> enumConstants) {
      this(name, kind, properties, enumConstants, Optional.empty(), "");
    }

    public TypeDef(
        String name, String kind, Map<String, PropertyDef> properties, String documentation) {
      this(name, kind, properties, List.of(), Optional.empty(), documentation);
    }
  }

  /** Template parameter definition. */
  public record ParameterDef(
      String name, TypeRef type, boolean nullable, boolean optional, String documentation)
      implements Serializable {

    public ParameterDef {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
      documentation = documentation == null ? "" : documentation;
    }

    public ParameterDef(String name, TypeRef type, boolean nullable) {
      this(name, type, nullable, false, "");
    }

    public ParameterDef(String name, TypeRef type, boolean nullable, boolean optional) {
      this(name, type, nullable, optional, "");
    }

    public boolean required() {
      return !optional;
    }
  }

  /** Immutable canonical schema representation containing parameters and registered types. */
  public record CanonicalSchema(
      String templateId,
      SchemaFormat format,
      String contractFingerprint,
      Map<String, ParameterDef> parameters,
      Map<String, TypeDef> types,
      String rawSource)
      implements Serializable {

    public CanonicalSchema {
      Objects.requireNonNull(templateId, "templateId must not be null");
      Objects.requireNonNull(format, "format must not be null");
      Objects.requireNonNull(contractFingerprint, "contractFingerprint must not be null");
      parameters =
          parameters == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(parameters));
      types = types == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(types));
      rawSource = rawSource == null ? "" : rawSource;
    }
  }

  /** Utility helper to extract simple class name from fully-qualified name. */
  public static String simpleName(String fqcn) {
    if (fqcn == null || fqcn.isEmpty()) {
      return "";
    }
    int lastDot = fqcn.lastIndexOf('.');
    int lastDollar = fqcn.lastIndexOf('$');
    int sep = Math.max(lastDot, lastDollar);
    return sep >= 0 && sep + 1 < fqcn.length() ? fqcn.substring(sep + 1) : fqcn;
  }
}
