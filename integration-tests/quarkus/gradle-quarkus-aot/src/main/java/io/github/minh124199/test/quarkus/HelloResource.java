package io.github.minh124199.test.quarkus;

import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Path("/hello")
public class HelloResource {

  @Inject VietTemplateRenderer renderer;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public String hello(@QueryParam("name") @DefaultValue("World") String name) {
    return renderer.render("hello.vtl", Map.of("name", name));
  }

  @GET
  @Path("/page")
  @Produces(MediaType.TEXT_HTML)
  public String page(@QueryParam("name") @DefaultValue("QuarkusPage") String name) {
    return renderer.render("page", Map.of("name", name));
  }

  @GET
  @Path("/stream")
  @Produces(MediaType.TEXT_HTML)
  public Response stream(@QueryParam("name") @DefaultValue("StreamQuarkus") String name) {
    StreamingOutput stream =
        output -> {
          renderer.render("hello.vtl", Map.of("name", name), output);
          // Write additional bytes to verify container stream was NOT closed
          output.write("<!-- streamed footer -->".getBytes(StandardCharsets.UTF_8));
          output.flush();
        };
    return Response.ok(stream).build();
  }

  @GET
  @Path("/undefined")
  @Produces(MediaType.TEXT_HTML)
  public String undefined() {
    return renderer.render("undefined-check.vtl", Map.of());
  }

  @GET
  @Path("/secured")
  @RolesAllowed({"ADMIN", "USER"})
  @Produces(MediaType.TEXT_PLAIN)
  public String secured() {
    return renderer.render("security.vtl", Map.of());
  }

  @GET
  @Path("/admin")
  @RolesAllowed("ADMIN")
  @Produces(MediaType.TEXT_PLAIN)
  public String admin() {
    return renderer.render("security.vtl", Map.of());
  }

  @GET
  @Path("/csrf")
  @Produces(MediaType.TEXT_PLAIN)
  public String csrf() {
    return renderer.render("csrf.vtl", Map.of());
  }

  @POST
  @Path("/csrf-submit")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_PLAIN)
  public String csrfSubmit(@FormParam("message") String message) {
    return "Received: " + message;
  }

  @io.quarkus.runtime.annotations.RegisterForReflection
  public static class NativeUserPojo {
    private final String username;
    private final int roleLevel;

    public NativeUserPojo(String username, int roleLevel) {
      this.username = username;
      this.roleLevel = roleLevel;
    }

    public String getUsername() {
      return username;
    }

    public int getRoleLevel() {
      return roleLevel;
    }
  }

  @io.quarkus.runtime.annotations.RegisterForReflection
  public record NativeUserRecord(String team, boolean lead) {}

  @GET
  @Path("/client-data")
  @Produces(MediaType.APPLICATION_JSON)
  public String clientData() {
    StringBuilder sb = new StringBuilder();
    io.github.minh124199.viettemplate.assets.ClientData.defaultSerializer()
        .serialize(
            Map.of(
                "user", new NativeUserPojo("native-user", 99),
                "team", new NativeUserRecord("core", true)),
            sb);
    return sb.toString();
  }
}
