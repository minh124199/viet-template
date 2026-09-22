package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.vtl.compiler.TemplateClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mechanical runtime ABI audit test for generated AOT templates.
 *
 * <p>Compiles a representative suite of templates covering: static text, scalar variables, property
 * chains, method calls, #if conditionals, #foreach loops (array, list, range), arithmetic and
 * formatting, HTML/VTL escaping, macros, security facades, and map/list indexing.
 *
 * <p>Emits compiled classfiles to {@code build/reports/generated-template-abi-classes} and {@code
 * target/generated-template-abi-classes} so downstream verification tools and Python constant-pool
 * auditors can analyze the constant pools.
 */
class GeneratedTemplateAbiAuditTest {

  @Test
  @DisplayName("Compiles representative template suite for runtime ABI audit")
  void testCompileRepresentativeSuiteForAbiAudit(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("templates");
    Files.createDirectories(srcDir);

    // 1. Static text
    Files.writeString(
        srcDir.resolve("01_static_text.vtl"),
        """
        <!DOCTYPE html>
        <html>
        <head><title>Static Page</title></head>
        <body>
          <h1>Welcome to Viet Template</h1>
          <p>High performance compiler-driven Java template engine.</p>
        </body>
        </html>
        """,
        StandardCharsets.UTF_8);

    // 2. Scalar variables and quiet references
    Files.writeString(
        srcDir.resolve("02_scalars.vtl"),
        """
        Hello, $name!
        Quiet: $!missing
        Escaped: \\$name and \\\\$name
        Enclosed: ${title} in the system.
        """,
        StandardCharsets.UTF_8);

    // 3. Property chains
    Files.writeString(
        srcDir.resolve("03_properties.vtl"),
        """
        User: $user.name
        City: $user.address.city
        Zip: $user.address.zipCode
        """,
        StandardCharsets.UTF_8);

    // 4. Method calls
    Files.writeString(
        srcDir.resolve("04_methods.vtl"),
        """
        Upper: $user.getName().toUpperCase()
        Sum: $calc.add(10, 20)
        Custom: $service.format("PREFIX", $user.name)
        """,
        StandardCharsets.UTF_8);

    // 5. Conditionals (#if, #elseif, #else, logical operators)
    Files.writeString(
        srcDir.resolve("05_conditionals.vtl"),
        """
        #if($user.admin && $user.active)
          Admin Access Granted
        #elseif($user.active || !$user.banned)
          User Access Granted
        #else
          Access Denied
        #end
        #if($score > 90) Grade: A #elseif($score >= 75) Grade: B #else Grade: C #end
        """,
        StandardCharsets.UTF_8);

    // 6. Foreach loops (arrays, lists, ranges, loop metadata)
    Files.writeString(
        srcDir.resolve("06_foreach.vtl"),
        """
        #foreach($item in $items)
          Item $foreach.index ($foreach.count): $item.first=$foreach.first, last=$foreach.last
        #end
        #foreach($n in [1..5])
          Range step: $n
        #end
        """,
        StandardCharsets.UTF_8);

    // 7. Formatting and arithmetic
    Files.writeString(
        srcDir.resolve("07_formatting.vtl"),
        """
        Sum: #set($total = $a + $b * 2)$total
        Division: #set($half = $b / 2)$half
        Comparison: #if($total >= 100) Century! #end
        """,
        StandardCharsets.UTF_8);

    // 8. Escaping
    Files.writeString(
        srcDir.resolve("08_escaping.vtl"),
        """
        Raw text with directives:
        \\#if(true) Escaped Directive \\#end
        \\#foreach($x in $y) Escaped Loop \\#end
        $!unsafeHtml
        """,
        StandardCharsets.UTF_8);

    // 9. Macros
    Files.writeString(
        srcDir.resolve("09_macros.vtl"),
        """
        #macro(renderCard $title $content)
          <div class="card">
            <h3>$title</h3>
            <p>$content</p>
          </div>
        #end
        #renderCard("Title 1", "Content 1")
        #renderCard("Title 2", $user.name)
        """,
        StandardCharsets.UTF_8);

    // 10. Security facade
    Files.writeString(
        srcDir.resolve("10_security.vtl"),
        """
        #if($security.authenticated)
          Authenticated as: $security.name
          #if($security.hasRole("ADMIN"))
            Superuser Section
          #end
        #else
          Anonymous Guest
        #end
        """,
        StandardCharsets.UTF_8);

    // 11. Map and array/list indexing
    Files.writeString(
        srcDir.resolve("11_collections.vtl"),
        """
        Map lookup: $map["key1"] and $map[$dynKey]
        List lookup: $list[0] and $list[1]
        """,
        StandardCharsets.UTF_8);

    // Compile using TemplateAotCompiler
    Path outDir = tempDir.resolve("classes");
    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.compiledCount()).isEqualTo(11);
    assertThat(result.artifacts()).hasSize(11);

    // Also copy generated .class files into build/reports/generated-template-abi-classes
    // and target/generated-template-abi-classes for tooling inspection
    Path[] reportDirs =
        new Path[] {
          Paths.get("build/reports/generated-template-abi-classes"),
          Paths.get("target/generated-template-abi-classes"),
          Paths.get("../build/reports/generated-template-abi-classes")
        };

    for (Path rDir : reportDirs) {
      try {
        Files.createDirectories(rDir);
        for (TemplateAotArtifact art : result.artifacts()) {
          Files.copy(
              art.outputFile(),
              rDir.resolve(art.outputFile().getFileName()),
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (Exception ignored) {
        // Tolerant of different working directories
      }
    }

    // Verify all 11 compiled template classes load and render cleanly
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    for (TemplateAotArtifact art : result.artifacts()) {
      byte[] classBytes = Files.readAllBytes(art.outputFile());
      Class<? extends CompiledTemplate> clazz =
          loader.defineTemplateClass(art.className(), classBytes);
      CompiledTemplate template = clazz.getDeclaredConstructor().newInstance();

      Map<String, Object> ctx = new HashMap<>();
      ctx.put("name", "Viet");
      ctx.put("title", "Engine");
      ctx.put("user", new User("Alice", new Address("Hanoi", "10000"), true, false));
      ctx.put("calc", new Calc());
      ctx.put("service", new Service());
      ctx.put("score", 85);
      ctx.put("items", List.of("One", "Two", "Three"));
      ctx.put("a", 10);
      ctx.put("b", 20);
      ctx.put("unsafeHtml", "<script>alert(1)</script>");
      ctx.put("security", new SecurityModel("Alice", true, List.of("ADMIN")));
      ctx.put("map", Map.of("key1", "val1", "dynKey", "val2"));
      ctx.put("dynKey", "dynKey");
      ctx.put("list", List.of("First", "Second"));

      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos);
      template.render(RenderContext.of(ctx), out);
      out.flush();
      assertThat(baos.toString(StandardCharsets.UTF_8)).isNotEmpty();
    }
  }

  public record Address(String city, String zipCode) {}

  public record User(String name, Address address, boolean active, boolean banned) {
    public String getName() {
      return name;
    }

    public boolean isAdmin() {
      return true;
    }
  }

  public static class Calc {
    public int add(int a, int b) {
      return a + b;
    }
  }

  public static class Service {
    public String format(String prefix, String value) {
      return prefix + ":" + value;
    }
  }

  public record SecurityModel(String name, boolean authenticated, List<String> roles) {
    public boolean hasRole(String role) {
      return roles.contains(role);
    }
  }
}
