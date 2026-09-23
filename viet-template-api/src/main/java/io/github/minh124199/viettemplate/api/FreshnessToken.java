package io.github.minh124199.viettemplate.api;

/**
 * An opaque token representing the freshness state of a template resource.
 *
 * <p>Two tokens are {@link Object#equals(Object) equal} if and only if the underlying template
 * resource state has not changed.
 */
public interface FreshnessToken {

  /**
   * Returns a freshness token indicating that the template resource is immutable (e.g. packaged in
   * a static JAR on the classpath).
   *
   * @return the immutable freshness token singleton
   */
  static FreshnessToken immutable() {
    return ImmutableToken.INSTANCE;
  }

  /**
   * Returns a freshness token based on a monotonic version number.
   *
   * @param version the monotonic version number
   * @return a freshness token for the given version
   */
  static FreshnessToken ofVersion(long version) {
    return new VersionToken(version);
  }

  /**
   * Returns a freshness token based on file attributes (last modified timestamp and file size).
   *
   * @param lastModifiedMillis the last modified time in milliseconds
   * @param sizeBytes the file size in bytes
   * @return a freshness token for the given file attributes
   */
  static FreshnessToken ofFile(long lastModifiedMillis, long sizeBytes) {
    return new FileToken(lastModifiedMillis, sizeBytes);
  }
}

record ImmutableToken() implements FreshnessToken {
  static final ImmutableToken INSTANCE = new ImmutableToken();

  @Override
  public String toString() {
    return "FreshnessToken[immutable]";
  }
}

record VersionToken(long version) implements FreshnessToken {
  @Override
  public String toString() {
    return "FreshnessToken[version=" + version + "]";
  }
}

record FileToken(long lastModifiedMillis, long sizeBytes) implements FreshnessToken {
  @Override
  public String toString() {
    return "FreshnessToken[lastModifiedMillis=" + lastModifiedMillis + ", sizeBytes=" + sizeBytes + "]";
  }
}
