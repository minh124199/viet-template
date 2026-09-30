package io.github.minh124199.viettemplate.lsp;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Domain model representing parsed canonical contract schema (*.vt-schema.json) for tooling. */
final class CanonicalSchemaModel {

  private CanonicalSchemaModel() {}

  sealed interface TypeRef extends Serializable
      permits PrimitiveTypeRef,
          ClassTypeRef,
          ParameterizedTypeRef,
          ArrayTypeRef,
          WildcardTypeRef,
          NamedTypeRef {

    String displayName();
  }

  record PrimitiveTypeRef(String name) implements TypeRef {
    public PrimitiveTypeRef {
      Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public String displayName() {
      return name;
    }
  }

  record ClassTypeRef(String name) implements TypeRef {
    public ClassTypeRef {
      Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public String displayName() {
      return simpleName(name);
    }
  }

  record ParameterizedTypeRef(String rawType, List<TypeRef> arguments) implements TypeRef {
    public ParameterizedTypeRef {
      Objects.requireNonNull(rawType, "rawType must not be null");
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    @Override
    public String displayName() {
      String raw = simpleName(rawType);
      if (arguments.isEmpty()) {
        return raw;
      }
      StringBuilder sb = new StringBuilder(raw).append("<");
      for (int i = 0; i < arguments.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(arguments.get(i).displayName());
      }
      return sb.append(">").toString();
    }
  }

  record ArrayTypeRef(TypeRef componentType) implements TypeRef {
    public ArrayTypeRef {
      Objects.requireNonNull(componentType, "componentType must not be null");
    }

    @Override
    public String displayName() {
      return componentType.displayName() + "[]";
    }
  }

  record WildcardTypeRef(String boundKind, Optional<TypeRef> bound) implements TypeRef {
    public WildcardTypeRef {
      Objects.requireNonNull(boundKind, "boundKind must not be null");
      Objects.requireNonNull(bound, "bound must not be null");
    }

    @Override
    public String displayName() {
      if (bound.isEmpty()) {
        return "?";
      }
      return "? " + boundKind + " " + bound.get().displayName();
    }
  }

  record NamedTypeRef(String name, List<TypeRef> arguments) implements TypeRef {
    public NamedTypeRef {
      Objects.requireNonNull(name, "name must not be null");
      arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    @Override
    public String displayName() {
      String simple = simpleName(name);
      if (arguments.isEmpty()) {
        return simple;
      }
      StringBuilder sb = new StringBuilder(simple).append("<");
      for (int i = 0; i < arguments.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(arguments.get(i).displayName());
      }
      return sb.append(">").toString();
    }
  }

  record PropertyDef(String name, TypeRef type, boolean nullable) implements Serializable {
    public PropertyDef {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
    }
  }

  record TypeDef(String kind, Map<String, PropertyDef> properties) implements Serializable {
    public TypeDef {
      Objects.requireNonNull(kind, "kind must not be null");
      properties =
          properties == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(properties));
    }
  }

  record ParameterDef(
      String name, TypeRef type, boolean nullable, boolean optional, String documentation)
      implements Serializable {
    public ParameterDef {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
      documentation = documentation == null ? "" : documentation;
    }

    public ParameterDef(String name, TypeRef type, boolean nullable, boolean optional) {
      this(name, type, nullable, optional, "");
    }

    public boolean required() {
      return !optional;
    }
  }

  record SchemaEnvelope(
      String schema,
      String format,
      int schemaVersion,
      String templateId,
      String contractFingerprint,
      Map<String, ParameterDef> parameters,
      Map<String, TypeDef> types,
      String rawJson)
      implements Serializable {
    public SchemaEnvelope {
      parameters =
          parameters == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(parameters));
      types = types == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(types));
    }
  }

  static String simpleName(String fqcn) {
    if (fqcn == null || fqcn.isEmpty()) {
      return "";
    }
    int lastDot = fqcn.lastIndexOf('.');
    int lastDollar = fqcn.lastIndexOf('$');
    int sep = Math.max(lastDot, lastDollar);
    return sep >= 0 && sep + 1 < fqcn.length() ? fqcn.substring(sep + 1) : fqcn;
  }
}
