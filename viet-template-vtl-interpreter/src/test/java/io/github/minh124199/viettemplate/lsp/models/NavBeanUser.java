package io.github.minh124199.viettemplate.lsp.models;

public class NavBeanUser {
  private String name;
  private boolean active;

  public NavBeanUser() {}

  public NavBeanUser(String name, boolean active) {
    this.name = name;
    this.active = active;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }
}
