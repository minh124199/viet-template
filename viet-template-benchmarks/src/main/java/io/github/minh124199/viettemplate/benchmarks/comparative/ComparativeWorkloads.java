package io.github.minh124199.viettemplate.benchmarks.comparative;

import io.github.minh124199.viettemplate.runtime.StandardEscapers;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Authoritative workload definitions, models, and template sources for cross-engine comparative
 * benchmarks (Workloads C01 to C08).
 */
public final class ComparativeWorkloads {

  public static final String C01 = "C01";
  public static final String C02 = "C02";
  public static final String C03 = "C03";
  public static final String C04 = "C04";
  public static final String C05 = "C05";
  public static final String C06 = "C06";
  public static final String C07 = "C07";
  public static final String C08 = "C08";

  public static final List<String> ALL_WORKLOADS = List.of(C01, C02, C03, C04, C05, C06, C07, C08);

  // --- Domain Model Records & Classes ---

  public record City(String name, String code) {
    public String getName() {
      return name;
    }

    public String getCode() {
      return code;
    }
  }

  public record Address(String street, City city) {
    public String getStreet() {
      return street;
    }

    public City getCity() {
      return city;
    }
  }

  public record Customer(String name, Address address) {
    public String getName() {
      return name;
    }

    public Address getAddress() {
      return address;
    }
  }

  public record Billing(String id, String postalCode) {
    public String getId() {
      return id;
    }

    public String getPostalCode() {
      return postalCode;
    }
  }

  public record Payment(String method, Billing billing) {
    public String getMethod() {
      return method;
    }

    public Billing getBilling() {
      return billing;
    }
  }

  public record Order(String id, Customer customer, Payment payment) {
    public String getId() {
      return id;
    }

    public Customer getCustomer() {
      return customer;
    }

    public Payment getPayment() {
      return payment;
    }
  }

  public record TableItem(String id, String name, double price) {
    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public double getPrice() {
      return price;
    }
  }

  public record Member(String name, String role) {
    public String getName() {
      return name;
    }

    public String getRole() {
      return role;
    }
  }

  public record Department(String name, List<Member> members) {
    public String getName() {
      return name;
    }

    public List<Member> getMembers() {
      return members;
    }
  }

  public static class EscaperTool {
    public String escape(String input) {
      if (input == null) {
        return "";
      }
      StringTemplateOutput out = new StringTemplateOutput(input.length() + 32);
      try {
        StandardEscapers.htmlText().escape(input, out);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return out.toString();
    }
  }

  // --- Static Cached Models ---

  private static final Map<String, Object> C01_MODEL = Collections.emptyMap();

  private static final Map<String, Object> C02_MODEL =
      Map.of(
          "title",
          "Benchmark Report",
          "author",
          "Viet Template Team",
          "count",
          42,
          "verified",
          true);

  private static final Map<String, Object> C03_MODEL;

  static {
    City city = new City("San Francisco", "94105");
    Address address = new Address("500 Howard Street", city);
    Customer customer = new Customer("Alice Smith", address);
    Billing billing = new Billing("BILL-100", "94105-0001");
    Payment payment = new Payment("CREDIT", billing);
    Order order = new Order("ORD-999", customer, payment);
    C03_MODEL = Map.of("order", order);
  }

  private static final Map<String, Object> C04_MODEL =
      Map.of("enabled", true, "inStock", false, "discount", true);

  private static final Map<String, Object> C05_MODEL;

  static {
    List<TableItem> smallItems =
        List.of(
            new TableItem("ITM-1", "Product Alpha", 19.99),
            new TableItem("ITM-2", "Product Beta", 29.99),
            new TableItem("ITM-3", "Product Gamma", 39.99),
            new TableItem("ITM-4", "Product Delta", 49.99),
            new TableItem("ITM-5", "Product Epsilon", 59.99));
    C05_MODEL = Map.of("items", smallItems);
  }

  private static final Map<String, Object> C06_MODEL;

  static {
    List<TableItem> largeItems = new ArrayList<>(100);
    for (int i = 0; i < 100; i++) {
      largeItems.add(new TableItem("ITM-" + i, "Product " + i, 10.0 + (i * 0.5)));
    }
    C06_MODEL = Map.of("items", Collections.unmodifiableList(largeItems));
  }

  private static final Map<String, Object> C07_MODEL;

  static {
    List<Department> depts =
        List.of(
            new Department(
                "Engineering", List.of(new Member("Alice", "Lead"), new Member("Bob", "Dev"))),
            new Department(
                "Marketing",
                List.of(new Member("Charlie", "Director"), new Member("Diana", "Manager"))));
    C07_MODEL = Map.of("departments", depts);
  }

  private static final Map<String, Object> C08_MODEL =
      Map.of(
          "userInput", "<script>alert('XSS');</script> & \"safe\"",
          "url", "https://example.com/search?q=test&category=books&sort=asc",
          "esc", new EscaperTool());

  private ComparativeWorkloads() {}

  /** Returns the standard data model for the specified workload. */
  public static Object getModel(String workload) {
    return switch (workload) {
      case C01 -> C01_MODEL;
      case C02 -> C02_MODEL;
      case C03 -> C03_MODEL;
      case C04 -> C04_MODEL;
      case C05 -> C05_MODEL;
      case C06 -> C06_MODEL;
      case C07 -> C07_MODEL;
      case C08 -> C08_MODEL;
      default -> throw new IllegalArgumentException("Unknown workload: " + workload);
    };
  }

  /** Returns the expected canonical output string for the specified workload. */
  public static String getExpectedOutput(String workload) {
    return switch (workload) {
      case C01 ->
          "<div><h1>Static Title</h1><p>Static paragraph content for benchmarking raw text"
              + " streaming throughput without variables.</p></div>";
      case C02 ->
          "<div><h1>Benchmark Report</h1><p>Author: Viet Template Team, Count: 42, Verified:"
              + " true</p></div>";
      case C03 ->
          "<div><h2>Order: ORD-999</h2><p>Customer: Alice Smith</p><p>City: San Francisco"
              + " (94105)</p><p>Postal: 94105-0001</p></div>";
      case C04 ->
          "<div><span>Active</span><span>Out of Stock</span><span>Discount Applied</span></div>";
      case C05 ->
          "<table><thead><tr><th>ID</th><th>Name</th><th>Price</th></tr></thead><tbody>"
              + "<tr><td>ITM-1</td><td>Product Alpha</td><td>19.99</td></tr>"
              + "<tr><td>ITM-2</td><td>Product Beta</td><td>29.99</td></tr>"
              + "<tr><td>ITM-3</td><td>Product Gamma</td><td>39.99</td></tr>"
              + "<tr><td>ITM-4</td><td>Product Delta</td><td>49.99</td></tr>"
              + "<tr><td>ITM-5</td><td>Product Epsilon</td><td>59.99</td></tr>"
              + "</tbody></table>";
      case C06 -> buildLargeTableExpectedOutput();
      case C07 ->
          "<div class=\"departments\"><div class=\"dept\"><h3>Engineering</h3><ul><li>Alice -"
              + " Lead</li><li>Bob - Dev</li></ul></div><div"
              + " class=\"dept\"><h3>Marketing</h3><ul><li>Charlie - Director</li><li>Diana -"
              + " Manager</li></ul></div></div>";
      case C08 ->
          "<div class=\"escaped\">"
              + "<p>&lt;script&gt;alert(&#39;XSS&#39;);&lt;/script&gt; &amp; &quot;safe&quot;</p>"
              + "<p>https://example.com/search?q=test&amp;category=books&amp;sort=asc</p>"
              + "</div>";
      default -> throw new IllegalArgumentException("Unknown workload: " + workload);
    };
  }

  private static String buildLargeTableExpectedOutput() {
    StringBuilder sb =
        new StringBuilder(
            "<table><thead><tr><th>ID</th><th>Name</th><th>Price</th></tr></thead><tbody>");
    for (int i = 0; i < 100; i++) {
      sb.append("<tr><td>ITM-")
          .append(i)
          .append("</td><td>Product ")
          .append(i)
          .append("</td><td>")
          .append(10.0 + (i * 0.5))
          .append("</td></tr>");
    }
    sb.append("</tbody></table>");
    return sb.toString();
  }

  /**
   * Returns the template source string for the specified engine and workload.
   *
   * @param engine engine identifier ("viet", "velocity", "qute", "jte", "thymeleaf")
   * @param workload workload identifier (C01 to C08)
   * @return template string
   */
  public static String getTemplate(String engine, String workload) {
    String eng = engine.toLowerCase();
    return switch (workload) {
      case C01 ->
          "<div><h1>Static Title</h1><p>Static paragraph content for benchmarking raw text"
              + " streaming throughput without variables.</p></div>";
      case C02 -> getC02Template(eng);
      case C03 -> getC03Template(eng);
      case C04 -> getC04Template(eng);
      case C05, C06 -> getTableTemplate(eng);
      case C07 -> getC07Template(eng);
      case C08 -> getC08Template(eng);
      default -> throw new IllegalArgumentException("Unknown workload: " + workload);
    };
  }

  private static String getC02Template(String engine) {
    return switch (engine) {
      case "viet", "velocity" ->
          "<div><h1>$title</h1><p>Author: $author, Count: $count, Verified: $verified</p></div>";
      case "qute" ->
          "<div><h1>{title}</h1><p>Author: {author}, Count: {count}, Verified:"
              + " {verified}</p></div>";
      case "jte" ->
          "@param String title\n"
              + "@param String author\n"
              + "@param int count\n"
              + "@param boolean verified\n"
              + "<div><h1>${title}</h1><p>Author: ${author}, Count: ${count}, Verified:"
              + " ${verified}</p></div>";
      case "thymeleaf" ->
          "<div><h1>[(${title})]</h1><p>Author: [(${author})], Count: [(${count})], Verified:"
              + " [(${verified})]</p></div>";
      default -> throw new IllegalArgumentException("Unknown engine: " + engine);
    };
  }

  private static String getC03Template(String engine) {
    return switch (engine) {
      case "viet", "velocity" ->
          "<div><h2>Order: $order.id</h2><p>Customer: $order.customer.name</p><p>City:"
              + " $order.customer.address.city.name"
              + " ($order.customer.address.city.code)</p><p>Postal:"
              + " $order.payment.billing.postalCode</p></div>";
      case "qute" ->
          "<div><h2>Order: {order.id}</h2><p>Customer: {order.customer.name}</p><p>City:"
              + " {order.customer.address.city.name}"
              + " ({order.customer.address.city.code})</p><p>Postal:"
              + " {order.payment.billing.postalCode}</p></div>";
      case "jte" ->
          "@param"
              + " io.github.minh124199.viettemplate.benchmarks.comparative.ComparativeWorkloads.Order"
              + " order\n"
              + "<div><h2>Order: ${order.id()}</h2><p>Customer:"
              + " ${order.customer().name()}</p><p>City:"
              + " ${order.customer().address().city().name()}"
              + " (${order.customer().address().city().code()})</p><p>Postal:"
              + " ${order.payment().billing().postalCode()}</p></div>";
      case "thymeleaf" ->
          "<div><h2>Order: [(${order.id})]</h2><p>Customer: [(${order.customer.name})]</p><p>City:"
              + " [(${order.customer.address.city.name})]"
              + " ([(${order.customer.address.city.code})])</p><p>Postal:"
              + " [(${order.payment.billing.postalCode})]</p></div>";
      default -> throw new IllegalArgumentException("Unknown engine: " + engine);
    };
  }

  private static String getC04Template(String engine) {
    return switch (engine) {
      case "viet", "velocity" ->
          "<div>#if($enabled)<span>Active</span>#else<span>Inactive</span>#end#if($inStock)<span>In"
              + " Stock</span>#else<span>Out of Stock</span>#end#if($discount)<span>Discount"
              + " Applied</span>#else<span>Standard Price</span>#end</div>";
      case "qute" ->
          "<div>{#if enabled}<span>Active</span>{#else}<span>Inactive</span>{/if}{#if"
              + " inStock}<span>In Stock</span>{#else}<span>Out of Stock</span>{/if}{#if"
              + " discount}<span>Discount Applied</span>{#else}<span>Standard"
              + " Price</span>{/if}</div>";
      case "jte" ->
          "@param boolean enabled\n"
              + "@param boolean inStock\n"
              + "@param boolean discount\n"
              + "<div>@if(enabled)<span>Active</span>@else<span>Inactive</span>@endif@if(inStock)<span>In"
              + " Stock</span>@else<span>Out of Stock</span>@endif@if(discount)<span>Discount"
              + " Applied</span>@else<span>Standard Price</span>@endif</div>";
      case "thymeleaf" ->
          "<div><th:block th:if=\"${enabled}\"><span>Active</span></th:block><th:block"
              + " th:unless=\"${enabled}\"><span>Inactive</span></th:block><th:block"
              + " th:if=\"${inStock}\"><span>In Stock</span></th:block><th:block"
              + " th:unless=\"${inStock}\"><span>Out of Stock</span></th:block><th:block"
              + " th:if=\"${discount}\"><span>Discount Applied</span></th:block><th:block"
              + " th:unless=\"${discount}\"><span>Standard Price</span></th:block></div>";
      default -> throw new IllegalArgumentException("Unknown engine: " + engine);
    };
  }

  private static String getTableTemplate(String engine) {
    return switch (engine) {
      case "viet", "velocity" ->
          "<table><thead><tr><th>ID</th><th>Name</th><th>Price</th></tr></thead><tbody>#foreach($item"
              + " in $items)<tr><td>$item.id</td><td>$item.name</td><td>$item.price</td></tr>#end</tbody></table>";
      case "qute" ->
          "<table><thead><tr><th>ID</th><th>Name</th><th>Price</th></tr></thead><tbody>{#for item"
              + " in items}<tr><td>{item.id}</td><td>{item.name}</td><td>{item.price}</td></tr>{/for}</tbody></table>";
      case "jte" ->
          "@param"
              + " java.util.List<io.github.minh124199.viettemplate.benchmarks.comparative.ComparativeWorkloads.TableItem>"
              + " items\n"
              + "<table><thead><tr><th>ID</th><th>Name</th><th>Price</th></tr></thead><tbody>@for(var"
              + " item :"
              + " items)<tr><td>${item.id()}</td><td>${item.name()}</td><td>${item.price()}</td></tr>@endfor</tbody></table>";
      case "thymeleaf" ->
          "<table><thead><tr><th>ID</th><th>Name</th><th>Price</th></tr></thead><tbody><th:block"
              + " th:each=\"item :"
              + " ${items}\"><tr><td>[(${item.id})]</td><td>[(${item.name})]</td><td>[(${item.price})]</td></tr></th:block></tbody></table>";
      default -> throw new IllegalArgumentException("Unknown engine: " + engine);
    };
  }

  private static String getC07Template(String engine) {
    return switch (engine) {
      case "viet", "velocity" ->
          "<div class=\"departments\">#foreach($dept in $departments)<div"
              + " class=\"dept\"><h3>$dept.name</h3><ul>#foreach($member in"
              + " $dept.members)<li>$member.name - $member.role</li>#end</ul></div>#end</div>";
      case "qute" ->
          "<div class=\"departments\">{#for dept in departments}<div"
              + " class=\"dept\"><h3>{dept.name}</h3><ul>{#for member in"
              + " dept.members}<li>{member.name} - {member.role}</li>{/for}</ul></div>{/for}</div>";
      case "jte" ->
          "@param"
              + " java.util.List<io.github.minh124199.viettemplate.benchmarks.comparative.ComparativeWorkloads.Department>"
              + " departments\n"
              + "<div class=\"departments\">@for(var dept : departments)<div"
              + " class=\"dept\"><h3>${dept.name()}</h3><ul>@for(var member :"
              + " dept.members())<li>${member.name()} -"
              + " ${member.role()}</li>@endfor</ul></div>@endfor</div>";
      case "thymeleaf" ->
          "<div class=\"departments\"><th:block th:each=\"dept : ${departments}\"><div"
              + " class=\"dept\"><h3>[(${dept.name})]</h3><ul><th:block th:each=\"member :"
              + " ${dept.members}\"><li>[(${member.name})] -"
              + " [(${member.role})]</li></th:block></ul></div></th:block></div>";
      default -> throw new IllegalArgumentException("Unknown engine: " + engine);
    };
  }

  private static String getC08Template(String engine) {
    return switch (engine) {
      case "viet", "velocity" ->
          "<div class=\"escaped\"><p>$esc.escape($userInput)</p><p>$esc.escape($url)</p></div>";
      case "qute" ->
          "<div class=\"escaped\"><p>{esc.escape(userInput)}</p><p>{esc.escape(url)}</p></div>";
      case "jte" ->
          "@param String userInput\n"
              + "@param String url\n"
              + "@param"
              + " io.github.minh124199.viettemplate.benchmarks.comparative.ComparativeWorkloads.EscaperTool"
              + " esc\n"
              + "<div class=\"escaped\"><p>${esc.escape(userInput)}</p><p>${esc.escape(url)}</p></div>";
      case "thymeleaf" ->
          "<div class=\"escaped\"><p>[(${esc.escape(userInput)})]</p><p>[(${esc.escape(url)})]</p></div>";
      default -> throw new IllegalArgumentException("Unknown engine: " + engine);
    };
  }
}
