package io.github.minh124199.test.frontend.dev;

import java.util.List;

public record EmployeePageData(
    String departmentName,
    List<Employee> employees
) {}
