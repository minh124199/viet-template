package io.github.minh124199.test.nativeapp;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RegisterReflectionForBinding({Account.class, Profile.class, HostilePayload.class, User.class})
public class NativeTestApp {

  public static void main(String[] args) {
    SpringApplication.run(NativeTestApp.class, args);
  }
}
