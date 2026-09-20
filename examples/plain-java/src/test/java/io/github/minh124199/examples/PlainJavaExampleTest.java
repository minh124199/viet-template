package io.github.minh124199.examples;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.runtime.WriterTemplateOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Executable canonical example demonstrating plain Java usage of Viet Template.
 */
class PlainJavaExampleTest {

  public static record Product(String name, double price, int quantity) {
    public double total() {
      return price * quantity;
    }
  }

  @Test
  @DisplayName("1. Direct string rendering with TemplateEngine")
  void testDirectStringRendering() throws IOException {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    repository.put("hello.vm", "Hello, $name! Welcome to $project.");

    TemplateEngine engine =
        TemplateEngine.builder()
            .repository(repository)
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .build();

    RenderContext context =
        RenderContext.builder()
            .put("name", "Developer")
            .put("project", "Viet Template")
            .build();

    String rendered = engine.render("hello.vm", context);
    assertThat(rendered).isEqualTo("Hello, Developer! Welcome to Viet Template.");
  }

  @Test
  @DisplayName("2. Streaming rendering to java.io.Writer")
  void testWriterStreaming() throws IOException {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    repository.put(
        "table.vm",
        "Items: #foreach($item in $items)$item#if($foreach.hasNext), #end#end");

    TemplateEngine engine = TemplateEngine.builder().repository(repository).build();
    Template template = engine.get("table.vm");

    RenderContext context =
        RenderContext.builder().put("items", List.of("Apple", "Banana", "Cherry")).build();

    StringWriter writer = new StringWriter();
    template.render(context, new WriterTemplateOutput(writer));

    assertThat(writer.toString()).isEqualTo("Items: Apple, Banana, Cherry");
  }

  @Test
  @DisplayName("3. Zero-allocation UTF-8 binary streaming to OutputStream")
  void testUtf8BinaryStreaming() throws IOException {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    repository.put(
        "invoice.vm",
        "Invoice: $product.name() | Total: $$product.total()");

    TemplateEngine engine = TemplateEngine.builder().repository(repository).build();
    Template template = engine.get("invoice.vm");

    RenderContext context =
        RenderContext.builder()
            .put("product", new Product("Laptop", 1299.99, 2))
            .build();

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(outputStream)) {
      template.render(context, output);
    }

    assertThat(outputStream.toString(StandardCharsets.UTF_8))
        .isEqualTo("Invoice: Laptop | Total: $2599.98");
  }
}
