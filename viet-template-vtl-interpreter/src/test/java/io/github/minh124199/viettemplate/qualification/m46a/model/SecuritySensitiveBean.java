package io.github.minh124199.viettemplate.qualification.m46a.model;

public class SecuritySensitiveBean {
  private String publicData = "safe";

  public String getPublicData() {
    return publicData;
  }

  public ClassLoader getClassLoader() {
    return getClass().getClassLoader();
  }

  public Thread getCurrentThread() {
    return Thread.currentThread();
  }
}
