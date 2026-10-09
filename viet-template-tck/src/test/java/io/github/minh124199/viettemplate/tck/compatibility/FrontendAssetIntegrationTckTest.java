package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.assets.ClientData;
import io.github.minh124199.viettemplate.assets.FrontendAssets;
import io.github.minh124199.viettemplate.assets.FrontendAssetsRenderContextContributor;
import io.github.minh124199.viettemplate.assets.vite.ViteAssetResolver;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FrontendAssetIntegrationTckTest {

  private static final String MANIFEST_JSON =
      """
      {
        "src/main.ts": {
          "file": "assets/main-1111.js",
          "name": "main",
          "src": "src/main.ts",
          "isEntry": true,
          "css": ["assets/main-2222.css"]
        },
        "src/pages/employees/index.ts": {
          "file": "assets/employees-3333.js",
          "name": "employees",
          "src": "src/pages/employees/index.ts",
          "isEntry": true,
          "css": ["assets/employees-4444.css"],
          "imports": ["_vendor-5555.js"]
        },
        "_vendor-5555.js": {
          "file": "assets/vendor-5555.js",
          "css": ["assets/vendor-6666.css"]
        },
        "src/pages/payroll.svelte": {
          "file": "assets/payroll-7777.js",
          "src": "src/pages/payroll.svelte",
          "isEntry": true,
          "css": ["assets/payroll-8888.css"]
        },
        "src/images/logo.svg": {
          "file": "assets/logo-9999.svg",
          "src": "src/images/logo.svg"
        }
      }
      """;

  @Test
  @DisplayName(
      "VTL template evaluates $assets.head, $assets.body, and $assets.url without new grammar")
  void evaluateAssetsInVtlTemplate() throws IOException {
    ViteAssetResolver resolver =
        ViteAssetResolver.productionBuilder().publicBase("/").manifest(MANIFEST_JSON).build();

    FrontendAssets assets = new FrontendAssets(resolver);
    ClientData clientData = new ClientData();
    FrontendAssetsRenderContextContributor contributor =
        new FrontendAssetsRenderContextContributor(assets, clientData);

    InMemoryTemplateRepository repository =
        InMemoryTemplateRepository.create()
            .put(
                "page.vtl",
                """
                <!DOCTYPE html>
                <html>
                <head>
                $assets.head("src/main.ts")
                </head>
                <body>
                  <img src="$assets.url('src/images/logo.svg')" alt="Logo">
                  <h1>Welcome, $user.name</h1>
                  $assets.body("src/main.ts")
                </body>
                </html>
                """);

    TemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repository)
            .addContextContributor(contributor)
            .build();

    StringTemplateOutput output = new StringTemplateOutput();
    engine.render(
        TemplateId.of("page.vtl"), RenderContext.of("user", Map.of("name", "Nguyen")), output);

    String html = output.toString();
    assertThat(html).contains("<link rel=\"stylesheet\" href=\"/assets/main-2222.css\">");
    assertThat(html).contains("<script type=\"module\" src=\"/assets/main-1111.js\"></script>");
    assertThat(html).contains("<img src=\"/assets/logo-9999.svg\" alt=\"Logo\">");
    assertThat(html).contains("<h1>Welcome, Nguyen</h1>");
  }

  @Test
  @DisplayName("Svelte island template pattern with TS bootstrap and script-safe client data")
  void svelteIslandArchitectureTemplate() throws IOException {
    ViteAssetResolver resolver =
        ViteAssetResolver.productionBuilder().publicBase("/").manifest(MANIFEST_JSON).build();

    FrontendAssets assets = new FrontendAssets(resolver);
    ClientData clientData = new ClientData();
    FrontendAssetsRenderContextContributor contributor =
        new FrontendAssetsRenderContextContributor(assets, clientData);

    InMemoryTemplateRepository repository =
        InMemoryTemplateRepository.create()
            .put(
                "employees/view.vtl",
                """
                <!DOCTYPE html>
                <html>
                <head>
                $assets.head("src/pages/employees/index.ts")
                </head>
                <body>
                  <header>
                    <h1>$employee.name</h1>
                    <p>Department: $employee.department</p>
                  </header>
                  <main>
                    <!-- Progressive enhancement: semantic SSR fallback -->
                    <section class="employee-summary">
                      <p>Status: Active</p>
                    </section>

                    <!-- Interactive Svelte island container -->
                    <div data-employees-app></div>
                  </main>

                  $clientData.script("employees", $pageData)
                  $assets.body("src/pages/employees/index.ts")
                  $assets.body("src/pages/payroll.svelte")
                </body>
                </html>
                """);

    TemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repository)
            .addContextContributor(contributor)
            .build();

    Map<String, Object> employeeData =
        Map.of(
            "name", "Jane Doe",
            "department", "Engineering",
            "hostileNotes", "</script><script>alert('xss')</script>");

    StringTemplateOutput output = new StringTemplateOutput();
    engine.render(
        TemplateId.of("employees/view.vtl"),
        RenderContext.of(
            Map.of(
                "employee", employeeData, "pageData", Map.of("id", 42, "details", employeeData))),
        output);

    String html = output.toString();
    // Verify CSS from both entry and imported chunk collected
    assertThat(html).contains("href=\"/assets/employees-4444.css\"");
    assertThat(html).contains("href=\"/assets/vendor-6666.css\"");
    // Verify modulepreload
    assertThat(html).contains("<link rel=\"modulepreload\" href=\"/assets/vendor-5555.js\">");
    // Verify scripts
    assertThat(html)
        .contains("<script type=\"module\" src=\"/assets/employees-3333.js\"></script>");
    // Verify direct .svelte entry resolution
    assertThat(html).contains("<script type=\"module\" src=\"/assets/payroll-7777.js\"></script>");
    // Verify script-safe client data encoding
    assertThat(html)
        .contains("<script type=\"application/json\" data-vt-client-data=\"employees\">");
    assertThat(html).doesNotContain("</script><script>alert");
    assertThat(html).contains("\\u003C/script\\u003E\\u003Cscript\\u003Ealert");
  }

  @Test
  @DisplayName("Development mode HMR tags in VTL template")
  void devModeHmrInVtlTemplate() throws IOException {
    ViteAssetResolver resolver = ViteAssetResolver.development("http://localhost:5173");
    FrontendAssets assets = new FrontendAssets(resolver);
    ClientData clientData = new ClientData();
    FrontendAssetsRenderContextContributor contributor =
        new FrontendAssetsRenderContextContributor(assets, clientData);

    InMemoryTemplateRepository repository =
        InMemoryTemplateRepository.create()
            .put(
                "dev.vtl",
                """
                <head>
                $assets.head("src/main.ts")
                </head>
                <body>
                $assets.body("src/main.ts")
                </body>
                """);

    TemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repository)
            .addContextContributor(contributor)
            .build();

    StringTemplateOutput output = new StringTemplateOutput();
    engine.render(TemplateId.of("dev.vtl"), RenderContext.empty(), output);

    String html = output.toString();
    assertThat(html)
        .contains("<script type=\"module\" src=\"http://localhost:5173/@vite/client\"></script>");
    assertThat(html)
        .contains("<script type=\"module\" src=\"http://localhost:5173/src/main.ts\"></script>");
  }
}
