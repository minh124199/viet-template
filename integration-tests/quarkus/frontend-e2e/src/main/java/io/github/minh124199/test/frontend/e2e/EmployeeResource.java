package io.github.minh124199.test.frontend.e2e;

import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Path("/")
public class EmployeeResource {

  private static final int INITIAL_FOLLOWERS = 3;
  private final AtomicInteger followerCounter = new AtomicInteger(INITIAL_FOLLOWERS);

  @Inject VietTemplateRenderer renderer;

  @GET
  @Path("/health")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GET
  @Path("/employees/{id}")
  @Produces(MediaType.TEXT_HTML)
  public String employeePage(@PathParam("id") String id) {
    return renderPage(id);
  }

  @GET
  @Path("/employees")
  @Produces(MediaType.TEXT_HTML)
  public String employeePageDefault() {
    return renderPage("42");
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public String rootPage() {
    return renderPage("42");
  }

  private String renderPage(String id) {
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

    Map<String, Object> model = new HashMap<>();
    model.put("departmentName", "Engineering");
    model.put("employee", employee);
    model.put("employees", List.of(employee));
    model.put("pageData", new EmployeePageData("Engineering", List.of(employee)));

    return renderer.render("employees.vtl", model);
  }

  @POST
  @Path("/api/employees/{id}/follow")
  @Produces(MediaType.APPLICATION_JSON)
  public FollowResponse follow(@PathParam("id") String id) {
    int updated = followerCounter.incrementAndGet();
    return new FollowResponse(id, updated);
  }

  @POST
  @Path("/employees/{id}/follow")
  @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.WILDCARD})
  public Response followFallback(@PathParam("id") String id) {
    followerCounter.incrementAndGet();
    return Response.status(Response.Status.FOUND)
        .location(URI.create("/employees/" + id))
        .build();
  }

  @POST
  @Path("/api/test/reset")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Object> reset() {
    followerCounter.set(INITIAL_FOLLOWERS);
    return Map.of("status", "RESET", "followers", INITIAL_FOLLOWERS);
  }
}
