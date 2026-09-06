package io.github.minh124199.viettemplate.tck.velocity.model;

import java.util.HashMap;
import java.util.Map;

/** Model supporting index and keyed access. */
public class IndexedBean {
  private final Object[] array = new Object[10];
  private final Map<String, Object> map = new HashMap<>();

  public IndexedBean() {
    array[0] = "zero";
    array[1] = "one";
    map.put("key", "value");
  }

  public Object get(int index) {
    return array[index];
  }

  public void set(int index, Object value) {
    array[index] = value;
  }

  public Object get(String key) {
    return map.get(key);
  }

  public void set(String key, Object value) {
    map.put(key, value);
  }
}
