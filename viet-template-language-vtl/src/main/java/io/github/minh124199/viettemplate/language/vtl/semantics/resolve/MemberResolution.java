package io.github.minh124199.viettemplate.language.vtl.semantics.resolve;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.lang.reflect.Member;
import java.util.Objects;
import java.util.Optional;

/** Result of resolving a property access step on a receiver type. */
public record MemberResolution(
    Kind kind, VType resultType, Optional<Member> targetMember, Optional<String> typoSuggestion) {

  public enum Kind {
    RECORD_COMPONENT,
    GETTER,
    BOOLEAN_GETTER,
    FIELD,
    EXTENSION,
    MAP_ENTRY,
    DYNAMIC,
    NOT_FOUND
  }

  public MemberResolution {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(resultType, "resultType must not be null");
    Objects.requireNonNull(targetMember, "targetMember must not be null");
    Objects.requireNonNull(typoSuggestion, "typoSuggestion must not be null");
  }

  public static MemberResolution of(Kind kind, VType resultType, Member targetMember) {
    return new MemberResolution(
        kind, resultType, Optional.ofNullable(targetMember), Optional.empty());
  }

  public static MemberResolution dynamic(VType resultType) {
    return new MemberResolution(Kind.DYNAMIC, resultType, Optional.empty(), Optional.empty());
  }

  public static MemberResolution notFound(VType errorType, Optional<String> suggestion) {
    return new MemberResolution(Kind.NOT_FOUND, errorType, Optional.empty(), suggestion);
  }

  public boolean isFound() {
    return kind != Kind.NOT_FOUND;
  }
}
