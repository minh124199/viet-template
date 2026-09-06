package io.github.minh124199.viettemplate.tck.velocity.scenario;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Factory providing fresh, unshared context graphs for each engine invocation. */
@FunctionalInterface
public interface ContextFactory {

  /** Creates a fresh map of variable bindings. */
  Map<String, Object> create();

  /** Empty context factory singleton. */
  static ContextFactory empty() {
    return Collections::emptyMap;
  }

  /** Static single-variable context factory. */
  static ContextFactory of(String key, Object value) {
    return () -> {
      Map<String, Object> map = new HashMap<>();
      map.put(key, value);
      return map;
    };
  }

  /** Static two-variable context factory. */
  static ContextFactory of(String k1, Object v1, String k2, Object v2) {
    return () -> {
      Map<String, Object> map = new HashMap<>();
      map.put(k1, v1);
      map.put(k2, v2);
      return map;
    };
  }
}
