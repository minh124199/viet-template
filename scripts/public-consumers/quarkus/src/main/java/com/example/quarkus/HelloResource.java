package com.example.quarkus;

import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/hello")
public class HelloResource {

    @Inject
    VietTemplateRenderer renderer;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String hello(@QueryParam("name") @DefaultValue("QuarkusUser") String name) {
        return renderer.render("hello.vtl", Map.of("name", name));
    }
}
