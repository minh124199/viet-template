package io.github.minh124199.test.spring;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HelloController {

  @GetMapping("/hello")
  public String hello(
      @RequestParam(name = "name", defaultValue = "World") String name,
      @RequestParam(name = "location", defaultValue = "Vietnam") String location,
      Model model) {
    model.addAttribute("name", name);
    model.addAttribute("location", location);
    return "hello";
  }

  @GetMapping("/users")
  public String users(Model model) {
    model.addAttribute(
        "users",
        List.of(
            Map.of("name", "Alice", "role", "Admin"),
            Map.of("name", "Bob", "role", "Engineer")));
    return "users";
  }

  @GetMapping("/malicious-traversal")
  public String maliciousTraversal() {
    return "../secret";
  }

  @GetMapping("/missing-view")
  public String missingView() {
    return "non-existent-template";
  }
}
