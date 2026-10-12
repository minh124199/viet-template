package io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model;

import java.io.Serializable;
import java.math.BigDecimal;

/** Deterministic record fixture for M45A qualification. */
public record PayrollRecord(long id, BigDecimal baseSalary, BigDecimal bonus)
    implements Serializable {}
