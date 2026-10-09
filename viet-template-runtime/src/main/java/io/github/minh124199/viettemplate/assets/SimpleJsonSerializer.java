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
 * Built-in, lightweight JSON serializer for standalone usage and tests without third-party JSON
 * libraries.
 *
 * <p>Supports {@code null}, {@link Boolean}, {@link Number}, {@link CharSequence}, {@link Enum},
 * {@link Character}, {@link UUID}, {@link TemporalAccessor}, {@link Date}, {@link Map}, {@link
 * Iterable}, Java arrays, records, and basic JavaBeans with public getters.
 */
public final class SimpleJsonSerializer implements ClientDataSerializer {

  public static final SimpleJsonSerializer INSTANCE = new SimpleJsonSerializer();

  private static final int MAX_DEPTH = 128;

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
    if (value instanceof UUID || value instanceof TemporalAccessor || value instanceof Date) {
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
        Object val = comp.getAccessor().invoke(record);
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJsonString(comp.getName(), out);
        out.append(':');
        writeValue(val, out, active, depth + 1);
      } catch (ReflectiveOperationException e) {
        throw new IOException("Failed reading record component " + comp.getName(), e);
      }
    }
    out.append('}');
  }

  private void writeBean(Object bean, Appendable out, Set<Object> active, int depth)
      throws IOException {
    out.append('{');
    Map<String, Method> properties = new TreeMap<>();
    for (Method method : bean.getClass().getMethods()) {
      if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
        continue;
      }
      String name = method.getName();
      String propName = null;
      if (name.startsWith("get") && name.length() > 3) {
        propName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
      } else if (name.startsWith("is") && name.length() > 2) {
        propName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
      }
      if (propName != null) {
        properties.putIfAbsent(propName, method);
      }
    }
    boolean first = true;
    for (Map.Entry<String, Method> entry : properties.entrySet()) {
      try {
        Object val = entry.getValue().invoke(bean);
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJsonString(entry.getKey(), out);
        out.append(':');
        writeValue(val, out, active, depth + 1);
      } catch (ReflectiveOperationException ignored) {
        // Skip unreadable property
      }
    }
    out.append('}');
  }
}
