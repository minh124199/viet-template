package io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model;

import java.io.Serializable;
import java.util.List;

/** Department record fixture for M45A qualification. */
public record Department(String code, String name, Manager manager, List<Employee> staff)
    implements Serializable {}
