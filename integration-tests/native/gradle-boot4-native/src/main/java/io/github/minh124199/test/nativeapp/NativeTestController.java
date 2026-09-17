package io.github.minh124199.test.nativeapp;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NativeTestController {

  @GetMapping("/public")
  public String publicPage(Model model) {
    model.addAttribute("user", new User("Alice"));
    model.addAttribute("account", new Account("AliceAccount", 10));
    model.addAttribute("profile", new Profile("Alice Wonderland"));
    model.addAttribute("settings", Map.of("theme", "dark"));
    model.addAttribute("tags", List.of("java", "native", "viet-template"));
    model.addAttribute("hostile", new HostilePayload("<script>alert('x')</script>"));
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
}
