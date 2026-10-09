package io.github.minh124199.viettemplate.assets;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Built-in, lightweight JSON serializer used as the default engine behind {@link
 * ClientData#defaultSerializer()} without third-party JSON dependencies.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>{@code null}
 *   <li>{@link Boolean}
 *   <li>{@link Number} (floating-point {@code NaN} and infinities emit JSON {@code null} per RFC
 *       8259)
 *   <li>{@link CharSequence}, {@link Character}
 *   <li>{@link Enum} (serialized by name)
 *   <li>{@link UUID}, {@link TemporalAccessor}
 *   <li>{@link Date} (formatted as ISO-8601 UTC timestamp string via epoch milliseconds)
 *   <li>{@link Map} (keys converted to string via {@code toString()})
 *   <li>{@link Iterable}, Java arrays
 *   <li>Java {@link Record} components (in declaration order) and JavaBeans with public zero-arg,
 *       non-void getters (properties emitted in deterministic alphabetical order, static/synthetic
 *       methods excluded)
 * </ul>
 *
 * <p><strong>Failure Semantics:</strong> Property and record accessor errors (e.g. getter
 * exceptions) throw {@link ClientDataSerializationException} with diagnostic code {@link
 * AssetDiagnosticCode#VT_CLIENT_002}. Circular object graphs throw on detection. Non-data system
 * types (e.g. {@link Class}, {@link ClassLoader}, {@link Thread}) throw on serialization.
 * Serialization depth is bounded by {@code 128}.
 *
 * <p><strong>Native Image Scope:</strong> Primitives, strings, numbers, booleans, enums, dates,
 * UUIDs, collections, arrays, and maps serialize in GraalVM Native Image without reflection
 * metadata. Reflective serialization of custom records and JavaBeans requires GraalVM reflection
 * registration (e.g. Quarkus {@code @RegisterForReflection} or Spring AOT
 * {@code @RegisterReflectionForBinding}).
 */
final class SimpleJsonSerializer implements ClientDataSerializer {

  static final SimpleJsonSerializer INSTANCE = new SimpleJsonSerializer();

  private static final int MAX_DEPTH = 128;

  SimpleJsonSerializer() {}

  @Override
  public void serialize(Object value, Appendable target) throws ClientDataSerializationException {
    try {
      writeValue(value, target, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    } catch (ClientDataSerializationException e) {
      throw e;
    } catch (IOException e) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          null,
          "JSON serialization I/O failure: " + e.getMessage(),
          e);
    }
  }

  private void writeValue(Object value, Appendable out, Set<Object> active, int depth)
      throws IOException {
    if (depth > MAX_DEPTH) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          null,
          "Exceeded maximum JSON serialization depth limit of " + MAX_DEPTH);
    }

    if (value == null) {
      out.append("null");
      return;
    }
    if (value instanceof Boolean b) {
      out.append(b.toString());
      return;
    }
    if (value instanceof Number n) {
      if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
        out.append("null");
        return;
      }
      if (value instanceof Float f && (f.isNaN() || f.isInfinite())) {
        out.append("null");
        return;
      }
      out.append(n.toString());
      return;
    }
    if (value instanceof CharSequence cs) {
      writeJsonString(cs, out);
      return;
    }
    if (value instanceof Character c) {
      writeJsonString(c.toString(), out);
      return;
    }
    if (value instanceof Enum<?> e) {
      writeJsonString(e.name(), out);
      return;
    }
    if (value instanceof Date d) {
      writeJsonString(java.time.Instant.ofEpochMilli(d.getTime()).toString(), out);
      return;
    }
    if (value instanceof UUID || value instanceof TemporalAccessor) {
      writeJsonString(value.toString(), out);
      return;
    }

    // Composite types require circular reference guard
    if (!active.add(value)) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          null,
          "Circular reference detected during JSON serialization for object of type: "
              + value.getClass().getName());
    }

    try {
      if (value instanceof Map<?, ?> map) {
        writeMap(map, out, active, depth);
        return;
      }
      if (value instanceof Iterable<?> iter) {
        writeIterable(iter, out, active, depth);
        return;
      }
      if (value.getClass().isArray()) {
        writeArray(value, out, active, depth);
        return;
      }
      if (value.getClass().isRecord()) {
        writeRecord(value, out, active, depth);
        return;
      }
      // Fallback: bean properties
      writeBean(value, out, active, depth);
    } finally {
      active.remove(value);
    }
  }

  private void writeJsonString(CharSequence cs, Appendable out) throws IOException {
    out.append('"');
    for (int i = 0; i < cs.length(); i++) {
      char c = cs.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  private void writeMap(Map<?, ?> map, Appendable out, Set<Object> active, int depth)
      throws IOException {
    out.append('{');
    boolean first = true;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      String key = entry.getKey() != null ? entry.getKey().toString() : "null";
      writeJsonString(key, out);
      out.append(':');
      writeValue(entry.getValue(), out, active, depth + 1);
    }
    out.append('}');
  }

  private void writeIterable(Iterable<?> iter, Appendable out, Set<Object> active, int depth)
      throws IOException {
    out.append('[');
    Iterator<?> it = iter.iterator();
    boolean first = true;
    while (it.hasNext()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      writeValue(it.next(), out, active, depth + 1);
    }
    out.append(']');
  }

  private void writeArray(Object array, Appendable out, Set<Object> active, int depth)
      throws IOException {
    out.append('[');
    int len = java.lang.reflect.Array.getLength(array);
    for (int i = 0; i < len; i++) {
      if (i > 0) {
        out.append(',');
      }
      writeValue(java.lang.reflect.Array.get(array, i), out, active, depth + 1);
    }
    out.append(']');
  }

  private void writeRecord(Object record, Appendable out, Set<Object> active, int depth)
      throws IOException {
    out.append('{');
    RecordComponent[] components = record.getClass().getRecordComponents();
    boolean first = true;
    for (RecordComponent comp : components) {
      try {
        Method accessor = comp.getAccessor();
        accessor.trySetAccessible();
        Object val = accessor.invoke(record);
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJsonString(comp.getName(), out);
        out.append(':');
        writeValue(val, out, active, depth + 1);
      } catch (ReflectiveOperationException e) {
        Throwable cause =
            (e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null)
                ? ite.getCause()
                : e;
        String causeMsg =
            cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        throw new ClientDataSerializationException(
            AssetDiagnosticCode.VT_CLIENT_002,
            null,
            "Failed reading record component '"
                + comp.getName()
                + "' on "
                + record.getClass().getName()
                + ": "
                + causeMsg,
            cause);
      }
    }
    out.append('}');
  }

  private void writeBean(Object bean, Appendable out, Set<Object> active, int depth)
      throws IOException {
    if (bean instanceof Class<?>) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          null,
          "Unsupported system type for client data serialization: java.lang.Class");
    }
    if (bean instanceof ClassLoader || bean instanceof Thread) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          null,
          "Unsupported system type for client data serialization: " + bean.getClass().getName());
    }

    out.append('{');
    Map<String, Method> properties = new TreeMap<>();
    for (Method method : bean.getClass().getMethods()) {
      if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
          || method.isSynthetic()
          || method.isBridge()
          || method.getParameterCount() != 0
          || method.getDeclaringClass() == Object.class
          || method.getReturnType() == void.class) {
        continue;
      }
      String name = method.getName();
      String propName = null;
      if (name.startsWith("get") && name.length() > 3) {
        propName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
      } else if (name.startsWith("is")
          && name.length() > 2
          && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
        propName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
      }
      if (propName != null) {
        properties.putIfAbsent(propName, method);
      }
    }
    boolean first = true;
    for (Map.Entry<String, Method> entry : properties.entrySet()) {
      try {
        Method method = entry.getValue();
        method.trySetAccessible();
        Object val = method.invoke(bean);
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJsonString(entry.getKey(), out);
        out.append(':');
        writeValue(val, out, active, depth + 1);
      } catch (ReflectiveOperationException e) {
        Throwable cause =
            (e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null)
                ? ite.getCause()
                : e;
        String causeMsg =
            cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        throw new ClientDataSerializationException(
            AssetDiagnosticCode.VT_CLIENT_002,
            null,
            "Failed reading property '"
                + entry.getKey()
                + "' on "
                + bean.getClass().getName()
                + ": "
                + causeMsg,
            cause);
      }
    }
    out.append('}');
  }
}
