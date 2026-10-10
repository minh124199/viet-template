package io.github.minh124199.test.frontend.e2e;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SecureEmployeeController {

  private static final int INITIAL_FOLLOWERS = 3;
  private final AtomicInteger followerCounter = new AtomicInteger(INITIAL_FOLLOWERS);

  @GetMapping("/health")
  @ResponseBody
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GetMapping("/login")
  public String login() {
    return "login";
  }

  @GetMapping({"/secure/employees", "/secure/employees/{id}"})
  public String employeePage(
      @PathVariable(name = "id", required = false) String id, Model model) {
    String employeeId = (id != null && !id.isBlank()) ? id : "42";
    Employee employee =
        new Employee(
            employeeId,
            "Jane Doe",
            "Staff Engineer",
            "Engineering",
            135000L,
            "2022-03-15",
            followerCounter.get(),
            "</script><script>window.__vtInjected = true; window.__vt_xss = true;</script>");

    model.addAttribute("departmentName", "Engineering");
    model.addAttribute("employee", employee);
    model.addAttribute("employees", List.of(employee));
    model.addAttribute("pageData", new EmployeePageData("Engineering", List.of(employee)));

    return "employees";
  }

  @GetMapping(value = "/secure/admin", produces = MediaType.TEXT_PLAIN_VALUE)
  @ResponseBody
  public String admin() {
    return "ADMIN ACCESS GRANTED";
  }

  @PostMapping(
      value = "/secure/api/employees/{id}/follow",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public FollowResponse follow(@PathVariable("id") String id) {
    int updated = followerCounter.incrementAndGet();
    return new FollowResponse(id, updated);
  }

  @PostMapping("/secure/employees/{id}/follow")
  public ResponseEntity<Void> followFallback(@PathVariable("id") String id) {
    followerCounter.incrementAndGet();
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, "/secure/employees/" + id)
        .build();
  }

  @PostMapping("/api/test/reset")
  @ResponseBody
  public Map<String, Object> reset() {
    followerCounter.set(INITIAL_FOLLOWERS);
    return Map.of("status", "RESET", "followers", INITIAL_FOLLOWERS);
  }
}
