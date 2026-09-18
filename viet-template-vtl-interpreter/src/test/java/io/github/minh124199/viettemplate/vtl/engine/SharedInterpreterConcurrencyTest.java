package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SharedInterpreterConcurrencyTest {

  public record Item(String name, int val) {}

  public record User(String name) {}

  public record Nested(String detail) {}

  public record Req(
      String id,
      boolean active,
      List<Item> items,
      User user,
      Map<String, Object> map,
      Nested nested,
      String nullableProp) {
    public String computeSignature() {
      return "SIG-" + id;
    }
  }

  private static final String TEMPLATE_SOURCE =
      "#macro(formatReq $id $val)[macro:$id=>$val]#end\n"
          + "#set($localReqId = $req.id)\n"
          + "#if($req.active)STATUS:ACTIVE#{else}STATUS:INACTIVE#end\n"
          + "#foreach($item in $req.items)[$foreach.count:$item.name=$item.val]#end\n"
          + "$req.user.name\n"
          + "$req.map.get('key')\n"
          + "$req.items.get(0).name\n"
          + "#formatReq($req.id, $req.nested.detail)\n"
          + "$!req.nullableProp\n"
          + "${localReqId}-done\n"
          + "$req.computeSignature()";

  private static final TemplateId TEMPLATE_ID = TemplateId.of("rich_concurrent.vtl");
  private static InMemoryTemplateRepository repository;
  private static VtlTemplateEngine engine;

  @BeforeAll
  static void setUp() {
    repository = InMemoryTemplateRepository.create();
    repository.put(TEMPLATE_ID, TEMPLATE_SOURCE);
    engine =
        VtlTemplateEngine.builder().repository(repository).executionTier(ExecutionTier.IR).build();
  }

  @AfterAll
  static void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  @Test
  @DisplayName("Stress test 1,000 concurrent renders on shared engine with virtual threads")
  void testSharedInterpreterConcurrency1000() throws Exception {
    runConcurrencyStress(1_000);
  }

  @Test
  @DisplayName("Stress test 10,000 concurrent renders on shared engine with virtual threads")
  void testSharedInterpreterConcurrency10000() throws Exception {
    runConcurrencyStress(10_000);
  }

  private void runConcurrencyStress(int count) throws Exception {
    List<Callable<RenderResult>> tasks = new ArrayList<>(count);
    List<Req> expectedRequests = new ArrayList<>(count);

    for (int i = 0; i < count; i++) {
      Req req = createReq(i);
      expectedRequests.add(req);
      tasks.add(
          () -> {
            RenderContext ctx = RenderContext.builder().put("req", req).build();
            RenderRequest request = RenderRequest.of(TEMPLATE_ID, ctx);
            StringTemplateOutput out = new StringTemplateOutput();
            engine.render(request, out);
            return new RenderResult(req.id(), out.toString());
          });
    }

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<RenderResult>> futures = executor.invokeAll(tasks);
      assertThat(futures).hasSize(count);

      for (int i = 0; i < count; i++) {
        RenderResult result = futures.get(i).get();
        Req expectedReq = expectedRequests.get(i);
        String expectedText = expectedOutput(expectedReq);

        // Verify exact output equality per render
        assertThat(result.renderedOutput())
            .as("Render output for task %d (%s) must exactly match expected", i, expectedReq.id())
            .isEqualTo(expectedText);

        // Confirm thread received its own unique ID and state, not another thread's
        assertThat(result.requestId()).isEqualTo(expectedReq.id());
        assertThat(result.renderedOutput()).contains(expectedReq.id() + "-done");
        assertThat(result.renderedOutput()).contains("SIG-" + expectedReq.id());
        assertThat(result.renderedOutput()).contains(expectedReq.user().name());
        assertThat(result.renderedOutput()).contains(expectedReq.nested().detail());
      }
    }
  }

  private record RenderResult(String requestId, String renderedOutput) {}

  static Req createReq(int i) {
    String id = String.format("REQ-%06d", i);
    boolean active = (i % 2 == 0);
    List<Item> items =
        new ArrayList<>(List.of(new Item("itemA-" + i, i * 2), new Item("itemB-" + i, i * 2 + 1)));
    User user = new User("user-" + i);
    Map<String, Object> map = new HashMap<>();
    map.put("key", "mapVal-" + i);
    Nested nested = new Nested("detail-" + i);
    String nullableProp = (i % 3 == 0) ? null : ("prop-" + i);
    return new Req(id, active, items, user, map, nested, nullableProp);
  }

  static String expectedOutput(Req req) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    sb.append(req.active() ? "STATUS:ACTIVE\n" : "STATUS:INACTIVE\n");
    for (int i = 0; i < req.items().size(); i++) {
      Item item = req.items().get(i);
      sb.append("[")
          .append(i + 1)
          .append(":")
          .append(item.name())
          .append("=")
          .append(item.val())
          .append("]");
    }
    sb.append("\n");
    sb.append(req.user().name()).append("\n");
    sb.append(req.map().get("key")).append("\n");
    sb.append(req.items().get(0).name()).append("\n");
    sb.append("[macro:").append(req.id()).append("=>").append(req.nested().detail()).append("]\n");
    if (req.nullableProp() != null) {
      sb.append(req.nullableProp());
    }
    sb.append("\n");
    sb.append(req.id()).append("-done\n");
    sb.append(req.computeSignature());
    return sb.toString();
  }
}
