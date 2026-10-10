package io.github.minh124199.test.frontend.e2e;

public record Employee(
    String id,
    String name,
    String role,
    String department,
    long salary,
    String startDate,
    int followers,
    String bio) {}
