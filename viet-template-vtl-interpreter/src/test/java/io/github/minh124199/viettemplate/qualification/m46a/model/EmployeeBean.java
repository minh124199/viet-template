package io.github.minh124199.viettemplate.qualification.m46a.model;

public class EmployeeBean {
  private String username;
  private int badgeNumber;
  private boolean active;

  public EmployeeBean() {}

  public EmployeeBean(String username, int badgeNumber, boolean active) {
    this.username = username;
    this.badgeNumber = badgeNumber;
    this.active = active;
  }

  public String getUsername() {
    return username;
  }

  public int getBadgeNumber() {
    return badgeNumber;
  }

  public boolean isActive() {
    return active;
  }
}
