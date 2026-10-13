package io.github.minh124199.viettemplate.qualification.m46a.model;

import java.util.List;

public record RecursiveNode(
    String id, String label, RecursiveNode parent, List<RecursiveNode> children) {}
