package io.github.minh124199.viettemplate.lsp.models;

public record NavExplicitAccessorRecord(String name) {
  @Override
  public String name() {
    return this.name != null ? this.name.trim() : "";
  }
}
