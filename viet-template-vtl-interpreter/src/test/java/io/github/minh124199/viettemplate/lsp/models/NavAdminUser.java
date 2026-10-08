package io.github.minh124199.viettemplate.lsp.models;

public class NavAdminUser extends NavBaseUser {
  private String role;

  public NavAdminUser() {}

  public NavAdminUser(String email, String role) {
    super(email);
    this.role = role;
  }

  public String getRole() {
    return role;
  }
}
