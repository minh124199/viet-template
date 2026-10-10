package io.github.minh124199.test.frontend.e2e;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record EmployeePageData(String departmentName, List<Employee> employees) {}
