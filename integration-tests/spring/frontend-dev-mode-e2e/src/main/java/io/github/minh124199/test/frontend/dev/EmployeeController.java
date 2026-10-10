package io.github.minh124199.test.frontend.dev;

import io.github.minh124199.viettemplate.api.TemplateEngine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
public class EmployeeController {

  private static final int INITIAL_FOLLOWERS = 3;
  private final AtomicInteger followerCounter = new AtomicInteger(INITIAL_FOLLOWERS);

  @Autowired
  private ApplicationContext context;

  @Autowired
  private TemplateEngine engine;

  public static String getJavaVersion() {
    return "JAVA-A";
  }

  @GetMapping("/health")
  @ResponseBody
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GetMapping(value = "/api/version", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Map<String, String> version() {
    return Map.of("version", getJavaVersion());
  }

  @GetMapping(value = "/__test/restart-generation", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Map<String, Object> restartGeneration() {
    ClassLoader cl = getClass().getClassLoader();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("classLoaderId", Integer.toHexString(System.identityHashCode(cl)));
    map.put("classLoaderName", cl.getClass().getName());
    map.put("engineId", Integer.toHexString(System.identityHashCode(engine)));
    map.put("javaVersion", getJavaVersion());
    return map;
  }

  @GetMapping({"/", "/employees", "/employees/{id}"})
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
            "</script><script>window.__vtInjected = true;</script>");

    model.addAttribute("departmentName", "Engineering");
    model.addAttribute("employee", employee);
    model.addAttribute("employees", List.of(employee));
    model.addAttribute("pageData", new EmployeePageData("Engineering", List.of(employee)));
    model.addAttribute("javaVersion", getJavaVersion());

    return "employees";
  }

  @PostMapping(
      value = "/api/employees/{id}/follow",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public FollowResponse follow(@PathVariable("id") String id) {
    int updated = followerCounter.incrementAndGet();
    return new FollowResponse(id, updated);
  }

  @PostMapping("/employees/{id}/follow")
  public ResponseEntity<Void> followFallback(@PathVariable("id") String id) {
    followerCounter.incrementAndGet();
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(org.springframework.http.HttpHeaders.LOCATION, "/employees/" + id)
        .build();
  }

  @PostMapping("/api/test/reset")
  @ResponseBody
  public Map<String, Object> reset() {
    followerCounter.set(INITIAL_FOLLOWERS);
    return Map.of("status", "RESET", "followers", INITIAL_FOLLOWERS);
  }
}
