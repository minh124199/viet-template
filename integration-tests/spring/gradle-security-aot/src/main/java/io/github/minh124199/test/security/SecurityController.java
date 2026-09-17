package io.github.minh124199.test.security;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SecurityController {

  @GetMapping("/public")
  public String publicPage() {
    return "public";
  }

  @GetMapping("/dashboard")
  public String dashboard() {
    return "dashboard";
  }

  @GetMapping("/admin")
  public String admin() {
    return "dashboard";
  }

  @GetMapping("/user")
  public String user() {
    return "dashboard";
  }
}
