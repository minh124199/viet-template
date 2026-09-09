package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable cryptographic fingerprint capturing the exact configuration of a security policy.
 *
 * <p>Used to partition compilation caches and dynamic linker call-site caches so differing security
 * policies never alias cached bytecode or linkage decisions.
 */
public record SecurityPolicyFingerprint(String value) implements Serializable {

  public SecurityPolicyFingerprint {
    Objects.requireNonNull(value, "value must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }

  public static SecurityPolicyFingerprint of(String value) {
    return new SecurityPolicyFingerprint(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
