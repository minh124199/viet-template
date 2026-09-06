package io.github.minh124199.viettemplate.language.vtl.semantics.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberResolverTest {

  record User(String name, int age, boolean active) {}

  public static class PersonBean {
    private String email;
    public boolean verified;

    public String getEmail() {
      return email;
    }

    public boolean isVerified() {
      return verified;
    }
  }

  @Test
  @DisplayName("Resolve record components by exact name")
  void recordComponents() {
    VType userType = VType.ClassType.of(User.class, Nullability.NON_NULL);

    MemberResolution nameRes = MemberResolver.resolveProperty(userType, "name");
    assertThat(nameRes.isFound()).isTrue();
    assertThat(nameRes.kind()).isEqualTo(MemberResolution.Kind.RECORD_COMPONENT);
    assertThat(nameRes.resultType())
        .isEqualTo(VType.ClassType.of(String.class, Nullability.NULLABLE));

    MemberResolution ageRes = MemberResolver.resolveProperty(userType, "age");
    assertThat(ageRes.isFound()).isTrue();
    assertThat(ageRes.resultType()).isEqualTo(VTypes.INT);

    MemberResolution activeRes = MemberResolver.resolveProperty(userType, "active");
    assertThat(activeRes.isFound()).isTrue();
    assertThat(activeRes.resultType()).isEqualTo(VTypes.BOOLEAN);
  }

  @Test
  @DisplayName("Resolve JavaBean getters and boolean getters")
  void javaBeanGetters() {
    VType personType = VType.ClassType.of(PersonBean.class, Nullability.NON_NULL);

    MemberResolution emailRes = MemberResolver.resolveProperty(personType, "email");
    assertThat(emailRes.isFound()).isTrue();
    assertThat(emailRes.kind()).isEqualTo(MemberResolution.Kind.GETTER);
    assertThat(emailRes.resultType())
        .isEqualTo(VType.ClassType.of(String.class, Nullability.NULLABLE));

    MemberResolution verifiedRes = MemberResolver.resolveProperty(personType, "verified");
    assertThat(verifiedRes.isFound()).isTrue();
    assertThat(verifiedRes.kind()).isEqualTo(MemberResolution.Kind.BOOLEAN_GETTER);
    assertThat(verifiedRes.resultType()).isEqualTo(VTypes.BOOLEAN);
  }

  @Test
  @DisplayName("Resolve Map key navigation")
  void mapNavigation() {
    VType mapType =
        VType.ClassType.of(
            Map.class, java.util.List.of(VTypes.STRING, VTypes.INT), Nullability.NON_NULL);

    MemberResolution mapRes = MemberResolver.resolveProperty(mapType, "someKey");
    assertThat(mapRes.isFound()).isTrue();
    assertThat(mapRes.kind()).isEqualTo(MemberResolution.Kind.MAP_ENTRY);
    assertThat(mapRes.resultType()).isEqualTo(VTypes.INT);
  }

  @Test
  @DisplayName("Missing property triggers typo suggestions using Levenshtein distance")
  void typoSuggestion() {
    VType userType = VType.ClassType.of(User.class, Nullability.NON_NULL);

    MemberResolution typoRes = MemberResolver.resolveProperty(userType, "nmae");
    assertThat(typoRes.isFound()).isFalse();
    assertThat(typoRes.kind()).isEqualTo(MemberResolution.Kind.NOT_FOUND);
    assertThat(typoRes.typoSuggestion()).contains("name");

    MemberResolution activeTypo = MemberResolver.resolveProperty(userType, "activ");
    assertThat(activeTypo.isFound()).isFalse();
    assertThat(activeTypo.typoSuggestion()).contains("active");
  }
}
