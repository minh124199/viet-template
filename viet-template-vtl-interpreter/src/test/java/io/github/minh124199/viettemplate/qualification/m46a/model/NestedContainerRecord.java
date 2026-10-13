package io.github.minh124199.viettemplate.qualification.m46a.model;

public record NestedContainerRecord(String id, NestedItem item) {
  public record NestedItem(String label, int value) {}
}
