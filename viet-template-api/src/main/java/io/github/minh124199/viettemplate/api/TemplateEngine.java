package io.github.minh124199.viettemplate.api;

/** Primary entrypoint contract for loading and managing templates. */
public interface TemplateEngine {

  Template get(TemplateId id);

  default Template get(String name) {
    return get(TemplateId.of(name));
  }
}
