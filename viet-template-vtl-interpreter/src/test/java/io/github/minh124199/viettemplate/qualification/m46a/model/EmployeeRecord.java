package io.github.minh124199.viettemplate.qualification.m46a.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EmployeeRecord(
    long id,
    String name,
    DepartmentRecord department,
    StatusEnum status,
    List<String> skills,
    Map<String, String> attributes,
    Instant hireDate,
    Double salary) {}
