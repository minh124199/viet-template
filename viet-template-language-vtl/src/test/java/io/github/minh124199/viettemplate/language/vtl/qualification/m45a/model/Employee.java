package io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic JavaBean fixture for M45A qualification. */
@SuppressWarnings("serial")
public class Employee implements Serializable {

  private final long id;
  private final String name;
  private final boolean active;
  private final Address address;
  private final PayrollRecord payroll;
  private final Employee manager; // nullable
  private final List<String> skills;
  private final Map<String, String> attributes;
  private final String secretPin; // for security testing

  public Employee(
      long id,
      String name,
      boolean active,
      Address address,
      PayrollRecord payroll,
      Employee manager,
      List<String> skills,
      Map<String, String> attributes,
      String secretPin) {
    this.id = id;
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.active = active;
    this.address = address;
    this.payroll = payroll;
    this.manager = manager;
    this.skills = skills != null ? List.copyOf(skills) : List.of();
    this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    this.secretPin = secretPin;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }

  public Address getAddress() {
    return address;
  }

  public PayrollRecord getPayroll() {
    return payroll;
  }

  public Employee getManager() {
    return manager;
  }

  public List<String> getSkills() {
    return skills;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }

  public String getSecretPin() {
    return secretPin;
  }

  public String format(String prefix) {
    return prefix + ": " + name;
  }

  public String format(int code) {
    return "[" + code + "] " + name;
  }
}
