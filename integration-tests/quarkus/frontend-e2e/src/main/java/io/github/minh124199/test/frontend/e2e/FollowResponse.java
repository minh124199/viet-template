package io.github.minh124199.test.frontend.e2e;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record FollowResponse(String id, int followers) {}
