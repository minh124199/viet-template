package io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model;

import java.util.List;
import java.util.Map;

/** Manager subclass demonstrating class inheritance and covariant specialization. */
@SuppressWarnings("serial")
public class Manager extends Employee {

  private final int directReportsCount;
  private final String departmentName;

  public Manager(
      long id,
      String name,
      boolean active,
      Address address,
      PayrollRecord payroll,
      Employee manager,
      List<String> skills,
      Map<String, String> attributes,
      String secretPin,
      int directReportsCount,
      String departmentName) {
    super(id, name, active, address, payroll, manager, skills, attributes, secretPin);
    this.directReportsCount = directReportsCount;
    this.departmentName = departmentName;
  }

  public int getDirectReportsCount() {
    return directReportsCount;
  }

  public String getDepartmentName() {
    return departmentName;
  }
}
