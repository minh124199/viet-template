package io.github.minh124199.test.quarkus;

import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
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
}
