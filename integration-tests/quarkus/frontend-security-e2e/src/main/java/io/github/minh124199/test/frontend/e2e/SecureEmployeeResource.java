package io.github.minh124199.test.frontend.e2e;

import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
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
public class SecureEmployeeResource {

  private static final int INITIAL_FOLLOWERS = 3;
  private final AtomicInteger followerCounter = new AtomicInteger(INITIAL_FOLLOWERS);

  @Inject VietTemplateRenderer renderer;

  @GET
  @Path("/health")
  @PermitAll
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GET
  @Path("/login")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public String login() {
    return renderer.render("login.vtl", Map.of());
  }

  @GET
  @Path("/secure/employees/{id}")
  @RolesAllowed("USER")
  @Produces(MediaType.TEXT_HTML)
  public String employeePage(@PathParam("id") String id) {
    return renderPage(id);
  }

  @GET
  @Path("/secure/employees")
  @RolesAllowed("USER")
  @Produces(MediaType.TEXT_HTML)
  public String employeePageDefault() {
    return renderPage("42");
  }

  @GET
  @Path("/secure/admin")
  @RolesAllowed("ADMIN")
  @Produces(MediaType.TEXT_PLAIN)
  public String admin() {
    return "ADMIN ACCESS GRANTED";
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
  @Path("/secure/api/employees/{id}/follow")
  @RolesAllowed("USER")
  @Produces(MediaType.APPLICATION_JSON)
  public Response follow(@PathParam("id") String id) {
    int updated = followerCounter.incrementAndGet();
    return Response.ok(new FollowResponse(id, updated)).build();
  }

  @POST
  @Path("/secure/employees/{id}/follow")
  @RolesAllowed("USER")
  @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.WILDCARD})
  public Response followFallback(@PathParam("id") String id) {
    followerCounter.incrementAndGet();
    return Response.status(Response.Status.FOUND)
        .location(URI.create("/secure/employees/" + id))
        .build();
  }

  void registerResetRoute(@jakarta.enterprise.event.Observes io.vertx.ext.web.Router router) {
    router
        .post("/api/test/reset")
        .order(-100)
        .handler(
            rc -> {
              followerCounter.set(INITIAL_FOLLOWERS);
              rc.response()
                  .putHeader("Content-Type", "application/json")
                  .end("{\"status\":\"RESET\",\"followers\":" + INITIAL_FOLLOWERS + "}");
            });
  }

  @POST
  @Path("/api/test/reset")
  @PermitAll
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Object> reset() {
    followerCounter.set(INITIAL_FOLLOWERS);
    return Map.of("status", "RESET", "followers", INITIAL_FOLLOWERS);
  }
}

