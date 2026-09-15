package io.github.minh124199.viettemplate.spring.web.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class VietTemplateViewTest {

  private TestTemplateEngine engine;
  private TemplateId templateId;

  static final class TrackingServletOutputStream extends ServletOutputStream {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int closeCount = 0;
    int flushCount = 0;

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {}

    @Override
    public void write(int b) {
      baos.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      baos.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      flushCount++;
      super.flush();
    }

    @Override
    public void close() throws IOException {
      closeCount++;
      super.close();
    }

    String getContentAsString() {
      return baos.toString(StandardCharsets.UTF_8);
    }
  }

  @BeforeEach
  void setUp() {
    engine = new TestTemplateEngine();
    templateId = TemplateId.of("pages/home.vtl");
  }

  @Test
  @DisplayName("Constructor enforces non-null invariants and UTF-8 encoding requirement")
  void constructorValidation() {
    assertThatNullPointerException().isThrownBy(() -> new VietTemplateView(null, templateId));
    assertThatNullPointerException().isThrownBy(() -> new VietTemplateView(engine, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new VietTemplateView(engine, templateId, "text/html", StandardCharsets.ISO_8859_1));
  }

  @Test
  @DisplayName("Stream ownership invariant: response stream is NEVER closed on successful render")
  void streamOwnershipInvariantOnSuccess() throws Exception {
    TrackingServletOutputStream trackingStream = new TrackingServletOutputStream();
    HttpServletResponse response =
        new MockHttpServletResponse() {
          @Override
          public ServletOutputStream getOutputStream() {
            return trackingStream;
          }
        };

    engine.registerTemplate(
        templateId,
        new Template() {
          @Override
          public TemplateDescriptor descriptor() {
            return null;
          }

          @Override
          public void render(RenderContext context, TemplateOutput output) throws IOException {
            output.write("Hello " + context.get("user") + "!");
          }
        });

    VietTemplateView view = new VietTemplateView(engine, templateId);
    view.render(Map.of("user", "Viet"), new MockHttpServletRequest(), response);

    assertThat(trackingStream.closeCount).isZero();
    assertThat(trackingStream.flushCount).isGreaterThanOrEqualTo(1);
    assertThat(trackingStream.getContentAsString()).isEqualTo("Hello Viet!");
  }

  @Test
  @DisplayName("Stream ownership invariant: response stream is NEVER closed on error")
  void streamOwnershipInvariantOnError() {
    TrackingServletOutputStream trackingStream = new TrackingServletOutputStream();
    HttpServletResponse response =
        new MockHttpServletResponse() {
          @Override
          public ServletOutputStream getOutputStream() {
            return trackingStream;
          }
        };

    engine.setRenderException(
        new TemplateRenderException(
            "Simulated render failure",
            templateId,
            io.github.minh124199.viettemplate.api.SourceSpan.UNKNOWN,
            io.github.minh124199.viettemplate.api.DiagnosticCode.of("RENDER", "FAILURE")));
    VietTemplateView view = new VietTemplateView(engine, templateId);

    assertThatThrownBy(() -> view.render(Map.of(), new MockHttpServletRequest(), response))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("Simulated render failure");

    assertThat(trackingStream.closeCount).isZero();
  }

  @Test
  @DisplayName("Sets response content-type and character encoding headers")
  void setsContentTypeAndCharsetHeaders() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    VietTemplateView view =
        new VietTemplateView(
            engine, templateId, "application/xhtml+xml;charset=UTF-8", StandardCharsets.UTF_8);

    view.render(Map.of(), new MockHttpServletRequest(), response);

    assertThat(response.getContentType()).contains("application/xhtml+xml");
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(view.getContentType()).isEqualTo("application/xhtml+xml;charset=UTF-8");
    assertThat(view.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(view.getEngine()).isSameAs(engine);
    assertThat(view.getTemplateId()).isEqualTo(templateId);
  }

  @Test
  @DisplayName(
      "Model propagation correctly propagates defined nulls, objects, and empty/null models")
  void modelPropagation() throws Exception {
    VietTemplateView view = new VietTemplateView(engine, templateId);

    // 1. Defined null variable
    Map<String, Object> modelWithNull = new HashMap<>();
    modelWithNull.put("explicitNull", null);
    modelWithNull.put("greeting", "Xin chao");

    MockHttpServletResponse response1 = new MockHttpServletResponse();
    view.render(modelWithNull, new MockHttpServletRequest(), response1);

    RenderContext ctx1 = engine.getLastRenderContext();
    assertThat(ctx1.contains("explicitNull")).isTrue();
    assertThat(ctx1.get("explicitNull")).isNull();
    assertThat(ctx1.contains("greeting")).isTrue();
    assertThat(ctx1.get("greeting")).isEqualTo("Xin chao");
    assertThat(ctx1.contains("nonExistent")).isFalse();

    // 2. Null model
    MockHttpServletResponse response2 = new MockHttpServletResponse();
    view.render(null, new MockHttpServletRequest(), response2);
    RenderContext ctx2 = engine.getLastRenderContext();
    assertThat(ctx2.keys()).isEmpty();

    // 3. Empty model
    MockHttpServletResponse response3 = new MockHttpServletResponse();
    view.render(Map.of(), new MockHttpServletRequest(), response3);
    RenderContext ctx3 = engine.getLastRenderContext();
    assertThat(ctx3.keys()).isEmpty();
  }

  @Test
  @DisplayName("Error propagation: exceptions during rendering propagate uncaught to caller")
  void errorPropagation() {
    VietTemplateView view = new VietTemplateView(engine, templateId);

    engine.setRenderIoException(new IOException("Simulated network stream disconnect"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> view.render(Map.of(), new MockHttpServletRequest(), response))
        .isInstanceOf(IOException.class)
        .hasMessage("Simulated network stream disconnect");
  }

  @Test
  @DisplayName("Thread safety: concurrent renders do not cross-contaminate models")
  void threadSafetyConcurrentRenders() throws Exception {
    engine.registerTemplate(
        templateId,
        new Template() {
          @Override
          public TemplateDescriptor descriptor() {
            return null;
          }

          @Override
          public void render(RenderContext context, TemplateOutput output) throws IOException {
            output.write("Result: " + context.get("val"));
          }
        });

    VietTemplateView view = new VietTemplateView(engine, templateId);
    int threads = 16;
    int iterations = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int t = 0; t < threads; t++) {
      final int threadId = t;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int i = 0; i < iterations; i++) {
                String expectedVal = "thread-" + threadId + "-iter-" + i;
                MockHttpServletResponse response = new MockHttpServletResponse();
                view.render(Map.of("val", expectedVal), new MockHttpServletRequest(), response);
                if (!response.getContentAsString().equals("Result: " + expectedVal)) {
                  failureCount.incrementAndGet();
                }
              }
            } catch (Exception e) {
              failureCount.incrementAndGet();
            }
          });
    }

    startLatch.countDown();
    executor.shutdown();
    boolean completed = executor.awaitTermination(10, TimeUnit.SECONDS);

    assertThat(completed).isTrue();
    assertThat(failureCount.get()).isZero();
  }

  @Test
  @DisplayName("equals, hashCode, and toString")
  void equalsAndHashCode() {
    VietTemplateView view1 = new VietTemplateView(engine, templateId);
    VietTemplateView view2 = new VietTemplateView(engine, templateId);
    VietTemplateView view3 = new VietTemplateView(engine, TemplateId.of("pages/other.vtl"));

    assertThat(view1).isEqualTo(view2);
    assertThat(view1.hashCode()).isEqualTo(view2.hashCode());
    assertThat(view1).isNotEqualTo(view3);
    assertThat(view1.toString()).contains("pages/home.vtl");
  }
}
