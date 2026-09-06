package io.github.minh124199.viettemplate.language.vtl;

import java.util.Objects;
import java.util.Set;

/** Metadata and specification descriptor for the VTL frontend. */
public final class VtlFrontend {

  public static final String NAME = "VTL";
  public static final String SPEC_VERSION = "2.4";

  private static final Set<VtlProfile> SUPPORTED_PROFILES =
      Set.of(
          VtlProfile.VTL_CORE,
          VtlProfile.VTL_MIGRATION,
          VtlProfile.VTL_DYNAMIC,
          VtlProfile.VTL_SAFE);

  private VtlFrontend() {}

  public static String name() {
    return NAME;
  }

  public static String specVersion() {
    return SPEC_VERSION;
  }

  public static VtlProfile defaultProfile() {
    return VtlProfile.VTL_CORE;
  }

  public static boolean supportsProfile(VtlProfile profile) {
    Objects.requireNonNull(profile, "profile must not be null");
    return SUPPORTED_PROFILES.contains(profile);
  }
}
