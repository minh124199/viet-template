package io.github.minh124199.viettemplate.tck.conformance.suite;

import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.api.TemplateSyntaxException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.tck.conformance.model.TckScenario;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative registry of executable public-API conformance scenarios for all claimed features.
 * Interacts exclusively through public APIs.
 */
public final class TckSuiteRegistry {

  // --- Helper models for scenario introspection & method dispatch ---
  public static class PersonBean {
    private final String name;

    public PersonBean(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public static class AccountBean {
    private final boolean active;

    public AccountBean(boolean active) {
      this.active = active;
    }

    public boolean isActive() {
      return active;
    }
  }

  public record BookRecord(String title, int pages) {}

  public static class FallbackBean {
    public String fullname() {
      return "John Doe";
    }
  }

  public static class TestService {
    public String ping() {
      return "pong";
    }

    public int doubleVal(int x) {
      return x * 2;
    }

    public int add(int a, int b) {
      return a + b;
    }

    public String describe(int x) {
      return "int:" + x;
    }

    public String describe(String s) {
      return "str:" + s;
    }

    public String join(String a, String b, String c) {
      return a + "-" + b + "-" + c;
    }
  }

  public interface Formatter {
    String format(String in);
  }

  public static class UpperFormatter implements Formatter {
    @Override
    public String format(String in) {
      return in.toUpperCase();
    }
  }

  private static final List<TckScenario> ALL_SCENARIOS;
  private static final Map<String, TckScenario> BY_ID;
  private static final Map<String, List<TckScenario>> BY_FEATURE_ID;

  static {
    List<TckScenario> list = new ArrayList<>(100);

    // 1. LEXICAL
    list.add(
        TckScenario.builder("lex.comment.single-line", "LEX-001")
            .template("Hello## single line comment\nWorld")
            .expectedOutput("HelloWorld")
            .build());

    list.add(
        TckScenario.builder("lex.comment.block", "LEX-002")
            .template("Prefix#* block\ncomment *#Suffix")
            .expectedOutput("PrefixSuffix")
            .build());

    list.add(
        TckScenario.builder("lex.escape.reference", "LEX-003")
            .template("Escaped: \\$user, \\\\$user")
            .context(Map.of("user", "Alice"))
            .backends(ExecutionTier.IR)
            .expectedOutput("Escaped: $user, \\Alice")
            .build());

    list.add(
        TckScenario.builder("lex.escape.directive", "LEX-004")
            .template("\\#if(true)not directive\\#end")
            .backends(ExecutionTier.IR)
            .expectedOutput("#if(true)not directive#end")
            .build());

    list.add(
        TckScenario.builder("lex.string.quotes", "LEX-005")
            .template("#set($s1 = 'literal $v')#set($s2 = \"interp $v\")[$s1][$s2]")
            .context(Map.of("v", "OK"))
            .expectedOutput("[literal $v][interp OK]")
            .build());

    // 2. REFERENCE
    list.add(
        TckScenario.builder("ref.shorthand", "REF-001")
            .template("Hello $name!")
            .context(Map.of("name", "World"))
            .expectedOutput("Hello World!")
            .build());

    list.add(
        TckScenario.builder("ref.formal", "REF-002")
            .template("Hello ${name}!")
            .context(Map.of("name", "World"))
            .expectedOutput("Hello World!")
            .build());

    list.add(
        TckScenario.builder("ref.quiet.shorthand", "REF-003")
            .template("User: [$!name]")
            .contextSupplier(
                () -> {
                  Map<String, Object> ctx = new HashMap<>();
                  ctx.put("name", null);
                  return ctx;
                })
            .expectedOutput("User: []")
            .build());

    list.add(
        TckScenario.builder("ref.quiet.formal", "REF-004")
            .template("User: [$!{name}]")
            .contextSupplier(
                () -> {
                  Map<String, Object> ctx = new HashMap<>();
                  ctx.put("name", null);
                  return ctx;
                })
            .expectedOutput("User: []")
            .build());

    list.add(
        TckScenario.builder("ref.concat.suffix", "REF-005")
            .template("Prefix_${name}_Suffix")
            .context(Map.of("name", "Core"))
            .expectedOutput("Prefix_Core_Suffix")
            .build());

    // 3. PROPERTY
    list.add(
        TckScenario.builder("prop.getter.standard", "PROP-001")
            .template("User: $user.name")
            .context(Map.of("user", new PersonBean("Alice")))
            .expectedOutput("User: Alice")
            .build());

    list.add(
        TckScenario.builder("prop.getter.boolean", "PROP-002")
            .template("Active: $account.active")
            .context(Map.of("account", new AccountBean(true)))
            .expectedOutput("Active: true")
            .build());

    list.add(
        TckScenario.builder("prop.record.component", "PROP-003")
            .template("Book: $book.title ($book.pages pages)")
            .context(Map.of("book", new BookRecord("Java Guide", 350)))
            .expectedOutput("Book: Java Guide (350 pages)")
            .build());

    list.add(
        TckScenario.builder("prop.map.entry", "PROP-004")
            .template("Val: $map.city")
            .context(Map.of("map", Map.of("city", "Hanoi")))
            .expectedOutput("Val: Hanoi")
            .build());

    list.add(
        TckScenario.builder("prop.lookup.fallback", "PROP-005")
            .template("Name: $bean.fullname")
            .context(Map.of("bean", new FallbackBean()))
            .expectedOutput("Name: John Doe")
            .build());

    // 4. INDEX
    list.add(
        TckScenario.builder("idx.array", "IDX-001")
            .template("First: $arr[0], Second: $arr[1]")
            .context(Map.of("arr", new String[] {"A", "B"}))
            .expectedOutput("First: A, Second: B")
            .build());

    list.add(
        TckScenario.builder("idx.list", "IDX-002")
            .template("Item: $list[1]")
            .context(Map.of("list", List.of("zero", "one", "two")))
            .expectedOutput("Item: one")
            .build());

    list.add(
        TckScenario.builder("idx.map.bracket", "IDX-003")
            .template("Val: $map['role']")
            .context(Map.of("map", Map.of("role", "admin")))
            .expectedOutput("Val: admin")
            .build());

    list.add(
        TckScenario.builder("idx.dynamic.expr", "IDX-004")
            .template("Val: $arr[$idx]")
            .context(Map.of("arr", new String[] {"X", "Y", "Z"}, "idx", 2))
            .expectedOutput("Val: Z")
            .build());

    list.add(
        TckScenario.builder("idx.nested.multi", "IDX-005")
            .template("Val: $matrix[1][0]")
            .context(Map.of("matrix", List.of(List.of("00", "01"), List.of("10", "11"))))
            .expectedOutput("Val: 10")
            .build());

    // 5. METHOD
    list.add(
        TckScenario.builder("meth.noarg", "METH-001")
            .template("Ping: $svc.ping()")
            .context(Map.of("svc", new TestService()))
            .expectedOutput("Ping: pong")
            .build());

    list.add(
        TckScenario.builder("meth.singlearg", "METH-002")
            .template("Double: $svc.doubleVal(5)")
            .context(Map.of("svc", new TestService()))
            .expectedOutput("Double: 10")
            .build());

    list.add(
        TckScenario.builder("meth.multiarg", "METH-003")
            .template("Sum: $svc.add(3, 4)")
            .context(Map.of("svc", new TestService()))
            .expectedOutput("Sum: 7")
            .build());

    list.add(
        TckScenario.builder("meth.overload", "METH-004")
            .template("[$svc.describe(42)][$svc.describe('hi')]")
            .context(Map.of("svc", new TestService()))
            .expectedOutput("[int:42][str:hi]")
            .build());

    list.add(
        TckScenario.builder("meth.varargs", "METH-005")
            .template("Joined: $svc.join('x', 'y', 'z')")
            .context(Map.of("svc", new TestService()))
            .expectedOutput("Joined: x-y-z")
            .build());

    // 6. EXPRESSION
    list.add(
        TckScenario.builder("expr.arithmetic", "EXPR-001")
            .template(
                "#set($sum = 10 + 5)#set($sub = 10 - 3)#set($mul = 4 * 2)#set($div = 20 /"
                    + " 4)#set($mod = 14 % 5)$sum,$sub,$mul,$div,$mod")
            .expectedOutput("15,7,8,5,4")
            .build());

    list.add(
        TckScenario.builder("expr.relational", "EXPR-002")
            .template("#if(5 > 3 && 2 <= 2 && 1 != 0 && 4 == 4)OK#end")
            .expectedOutput("OK")
            .build());

    list.add(
        TckScenario.builder("expr.logical", "EXPR-003")
            .template("#if((true and not false) or false)VALID#end")
            .expectedOutput("VALID")
            .build());

    list.add(
        TckScenario.builder("expr.range", "EXPR-004")
            .template("#foreach($i in [1..5])$i#end")
            .expectedOutput("12345")
            .build());

    list.add(
        TckScenario.builder("expr.parentheses", "EXPR-005")
            .template("#set($res = (2 + 3) * 4)$res")
            .expectedOutput("20")
            .build());

    // 7. TRUTHINESS
    list.add(
        TckScenario.builder("truth.boolean", "TRUTH-001")
            .template("#if(true)T#end#if(false)F#end")
            .expectedOutput("T")
            .build());

    list.add(
        TckScenario.builder("truth.null", "TRUTH-002")
            .template("#if($nullVar)YES#else NO#end")
            .contextSupplier(
                () -> {
                  Map<String, Object> ctx = new HashMap<>();
                  ctx.put("nullVar", null);
                  return ctx;
                })
            .expectedOutput(" NO")
            .build());

    list.add(
        TckScenario.builder("truth.number", "TRUTH-003")
            .template("#if(0)ZERO#else NONZERO#end-#if(42)TRUTHY#end")
            .expectedOutput(" NONZERO-TRUTHY")
            .build());

    list.add(
        TckScenario.builder("truth.string", "TRUTH-004")
            .template("#if('')EMPTY#else NONEMPTY#end-#if('txt')VALID#end")
            .expectedOutput(" NONEMPTY-VALID")
            .build());

    list.add(
        TckScenario.builder("truth.collection", "TRUTH-005")
            .template("#if($emptyList)A#else B#end-#if($fullList)C#end")
            .context(Map.of("emptyList", List.of(), "fullList", List.of("x")))
            .expectedOutput(" B-C")
            .build());

    list.add(
        TckScenario.builder("truth.object", "TRUTH-006")
            .template("#if($obj)TRUTHY#else FALSY#end")
            .context(Map.of("obj", new Object()))
            .expectedOutput("TRUTHY")
            .build());

    // 8. SET
    list.add(
        TckScenario.builder("set.scalar", "SET-001")
            .template("#set($num = 123)#set($str = \"abc\")Num:$num,Str:$str")
            .expectedOutput("Num:123,Str:abc")
            .build());

    list.add(
        TckScenario.builder("set.expr", "SET-002")
            .template("#set($total = $a + $b)$total")
            .context(Map.of("a", 15, "b", 25))
            .expectedOutput("40")
            .build());

    list.add(
        TckScenario.builder("set.list.literal", "SET-003")
            .template("#set($items = [10, 20, 30])#if($items)DEFINED#end")
            .expectedOutput("DEFINED")
            .build());

    list.add(
        TckScenario.builder("set.map.literal", "SET-004")
            .template("#set($dict = {'k': 'v'})#if($dict)DEFINED#end")
            .expectedOutput("DEFINED")
            .build());

    list.add(
        TckScenario.builder("set.reassignment", "SET-005")
            .template("#set($a = 1)#set($a = 2)$a")
            .expectedOutput("2")
            .build());

    // 9. CONDITIONAL
    list.add(
        TckScenario.builder("if.basic", "IF-001")
            .template("#if($active)ENABLED#end")
            .context(Map.of("active", true))
            .expectedOutput("ENABLED")
            .build());

    list.add(
        TckScenario.builder("if.else", "IF-002")
            .template("#if($active)ENABLED#else DISABLED#end")
            .context(Map.of("active", false))
            .expectedOutput(" DISABLED")
            .build());

    list.add(
        TckScenario.builder("if.elseif", "IF-003")
            .template("#if($status == 1)ONE#elseif($status == 2)TWO#else OTHER#end")
            .context(Map.of("status", 2))
            .expectedOutput("TWO")
            .build());

    list.add(
        TckScenario.builder("if.nested", "IF-004")
            .template("#if($a)#if($b)BOTH#else ONLY_A#end#end")
            .context(Map.of("a", true, "b", false))
            .expectedOutput(" ONLY_A")
            .build());

    // 10. FOREACH
    list.add(
        TckScenario.builder("foreach.list", "FOREACH-001")
            .template("#foreach($x in $list)[$x]#end")
            .context(Map.of("list", List.of("a", "b", "c")))
            .expectedOutput("[a][b][c]")
            .build());

    list.add(
        TckScenario.builder("foreach.range", "FOREACH-002")
            .template("#foreach($n in [1..3])$n#end")
            .expectedOutput("123")
            .build());

    list.add(
        TckScenario.builder("foreach.metadata.index", "FOREACH-003")
            .template("#foreach($x in $list)$foreach.index:$foreach.count;#end")
            .context(Map.of("list", List.of("a", "b")))
            .expectedOutput("0:1;1:2;")
            .build());

    list.add(
        TckScenario.builder("foreach.metadata.boundary", "FOREACH-004")
            .template(
                "#foreach($x in $list)#if($foreach.first)FIRST #end$x#if($foreach.last)"
                    + " LAST#end#if($foreach.hasNext),#end#end")
            .context(Map.of("list", List.of("a", "b")))
            .expectedOutput("FIRST a,b LAST")
            .build());

    list.add(
        TckScenario.builder("foreach.break", "FOREACH-005")
            .template("#foreach($x in [1..5])#if($x == 3)#break#end$x#end")
            .expectedOutput("12")
            .build());

    list.add(
        TckScenario.builder("foreach.else", "FOREACH-006")
            .template("#foreach($x in $emptyList)$x#else EMPTY#end")
            .context(Map.of("emptyList", List.of()))
            .backends(ExecutionTier.IR)
            .expectedOutput(" EMPTY")
            .build());

    // 11. MACRO
    list.add(
        TckScenario.builder("macro.parameterless", "MACRO-001")
            .template("#macro(greeting)Hello World#end#greeting()")
            .expectedOutput("Hello World")
            .build());

    list.add(
        TckScenario.builder("macro.positional", "MACRO-002")
            .template("#macro(greet $who)Hello $who!#end#greet('Alice')")
            .expectedOutput("Hello Alice!")
            .build());

    list.add(
        TckScenario.builder("macro.nested", "MACRO-003")
            .template("#macro(sub $x)[$x]#end#macro(outer $y)Outer:#sub($y)#end#outer('Test')")
            .expectedOutput("Outer:[Test]")
            .build());

    list.add(
        TckScenario.builder("macro.recursive", "MACRO-004")
            .template(
                "#macro(countdown $n)$n#if($n > 1) #set($next = $n -"
                    + " 1)#countdown($next)#end#end#countdown(3)")
            .expectedOutput("3 2 1")
            .build());

    // 12. DYNAMIC
    list.add(
        TckScenario.builder("dyn.evaluate", "DYN-001")
            .template("Result: #evaluate('#set($ans = 10 * 4)$ans')")
            .profile(VtlProfile.VTL_DYNAMIC)
            .expectedOutput("Result: 40")
            .build());

    list.add(
        TckScenario.builder("dyn.property.runtime", "DYN-002")
            .template("Item: $map.get($key)")
            .contextSupplier(
                () -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("greeting", "Xin chào");
                  return Map.of("map", map, "key", "greeting");
                })
            .expectedOutput("Item: Xin chào")
            .build());

    list.add(
        TckScenario.builder("dyn.polymorphic.dispatch", "DYN-003")
            .template("Formatted: $fmt.format('vietnam')")
            .context(Map.of("fmt", new UpperFormatter()))
            .expectedOutput("Formatted: VIETNAM")
            .build());

    // 13. STATE3
    list.add(
        TckScenario.builder("state3.quiet.suppression", "STATE3-001")
            .template("Val: [$!unbound]")
            .expectedOutput("Val: []")
            .build());

    list.add(
        TckScenario.builder("state3.unresolved.literal", "STATE3-002")
            .template("Val: [$unbound]")
            .expectedOutput("Val: [$unbound]")
            .build());

    list.add(
        TckScenario.builder("state3.alternate.fallback", "STATE3-003")
            .template("Val: [${missing|'fallback'}]")
            .expectedOutput("Val: [fallback]")
            .build());

    // 14. STRICT
    list.add(
        TckScenario.builder("strict.undefined.var", "STRICT-001")
            .template("Hello $missingUser!")
            .engineCustomizer(
                b ->
                    b.interpreterOptions(
                        VtlInterpreterOptions.builder().strictReferences(true).build()))
            .expectedException(TemplateRenderException.class)
            .build());

    list.add(
        TckScenario.builder("strict.invalid.property", "STRICT-002")
            .template("City: $user.address.city")
            .contextSupplier(
                () -> {
                  Map<String, Object> ctx = new HashMap<>();
                  ctx.put("user", null);
                  return ctx;
                })
            .engineCustomizer(
                b ->
                    b.interpreterOptions(
                        VtlInterpreterOptions.builder().strictReferences(true).build()))
            .expectedException(TemplateRenderException.class)
            .build());

    // 15. SECURITY
    list.add(
        TckScenario.builder("sec.reflection.getclass", "SEC-001")
            .template("$obj.getClass().getName()")
            .context(Map.of("obj", "string"))
            .expectedException(TemplateSecurityException.class)
            .build());

    list.add(
        TckScenario.builder("sec.classloader.pivot", "SEC-002")
            .template("$obj.class.classLoader")
            .context(Map.of("obj", "string"))
            .expectedException(TemplateSecurityException.class)
            .build());

    list.add(
        TckScenario.builder("sec.macro.recursion.limit", "SEC-003")
            .template("#macro(inf)#inf()#end#inf()")
            .backends(ExecutionTier.IR)
            .expectedException(TemplateLimitException.class)
            .build());

    list.add(
        TckScenario.builder("sec.parse.cycle.limit", "SEC-004")
            .template("#parse('cycleA.vm')")
            .resource("cycleA.vm", "#parse('cycleB.vm')")
            .resource("cycleB.vm", "#parse('cycleA.vm')")
            .expectedException(TemplateLimitException.class)
            .build());

    // 16. ERRORS
    list.add(
        TckScenario.builder("err.division.by.zero", "ERR-001")
            .template("#set($div = 10 / 0)$div")
            .expectedError(TemplateRenderException.class, "ERROR")
            .build());

    list.add(
        TckScenario.builder("err.syntax.unclosed", "ERR-002")
            .template("#if(missingParen")
            .expectedException(TemplateSyntaxException.class)
            .build());

    list.add(
        TckScenario.builder("err.missing.resource", "ERR-003")
            .template("#parse('nonExistentTemplate.vm')")
            .expectedException(TemplateResourceException.class)
            .build());

    // 17. UNICODE
    list.add(
        TckScenario.builder("unicode.vietnamese", "UNICODE-001")
            .template("Xin chào Việt Nam! Kính chúc: ă, â, đ, ê, ô, ơ, ư.")
            .expectedOutput("Xin chào Việt Nam! Kính chúc: ă, â, đ, ê, ô, ơ, ư.")
            .build());

    list.add(
        TckScenario.builder("unicode.multilingual", "UNICODE-002")
            .template("Multilingual: 日本語, Русский, العربية")
            .expectedOutput("Multilingual: 日本語, Русский, العربية")
            .build());

    list.add(
        TckScenario.builder("unicode.emoji", "UNICODE-003")
            .template("Emoji: 🚀✨🔥 - High surrogates preserved.")
            .expectedOutput("Emoji: 🚀✨🔥 - High surrogates preserved.")
            .build());

    // 18. APPLICATION
    list.add(
        TckScenario.builder("app.parse.static", "APP-001")
            .template("Parent: #parse('child.vm')")
            .resource("child.vm", "Child: $msg")
            .context(Map.of("msg", "Loaded"))
            .expectedOutput("Parent: Child: Loaded")
            .build());

    list.add(
        TckScenario.builder("app.include.static", "APP-002")
            .template("Include: #include('raw.txt')")
            .resource("raw.txt", "$notEvaluated #if")
            .expectedOutput("Include: $notEvaluated #if")
            .build());

    list.add(
        TckScenario.builder("app.contributor.merge", "APP-003")
            .template("Contributed: $providedKey")
            .engineCustomizer(
                b ->
                    b.addContextContributor(
                        (contributorCtx, req) ->
                            contributorCtx.put("providedKey", "InjectedValue")))
            .expectedOutput("Contributed: InjectedValue")
            .build());

    // 19. INTENTIONAL DIFFERENCES
    list.add(
        TckScenario.builder("diff.arithmetic.divide.zero", "DIFF-001")
            .template("#set($x = 10 / 0)$x")
            .expectedError(TemplateRenderException.class, "ERROR")
            .build());

    list.add(
        TckScenario.builder("diff.security.getclass", "DIFF-002")
            .template("$user.getClass()")
            .context(Map.of("user", "Alice"))
            .expectedException(TemplateSecurityException.class)
            .build());

    list.add(
        TckScenario.builder("diff.set.null.preservation", "DIFF-003")
            .template("#set($val = $missing)$val")
            .context(Map.of("val", "original"))
            .engineCustomizer(
                b ->
                    b.interpreterOptions(
                        VtlInterpreterOptions.builder().setNullAllowed(false).build()))
            .expectedOutput("original")
            .build());

    // 20. EXTENSIONS
    list.add(
        TckScenario.builder("ext.foreach.stop.method", "EXT-001")
            .template("#foreach($i in [1..5])#if($i == 3)$foreach.stop()#end$i#end")
            .backends(ExecutionTier.IR)
            .expectedOutput("12")
            .build());

    // Validation: ensure unique scenario IDs and index lookups
    Map<String, TckScenario> byIdMap = new HashMap<>();
    Map<String, List<TckScenario>> byFeatureMap = new HashMap<>();

    for (TckScenario s : list) {
      if (byIdMap.put(s.id(), s) != null) {
        throw new IllegalStateException("Duplicate scenario ID: " + s.id());
      }
      byFeatureMap.computeIfAbsent(s.featureId(), k -> new ArrayList<>()).add(s);
    }

    ALL_SCENARIOS = Collections.unmodifiableList(list);
    BY_ID = Collections.unmodifiableMap(byIdMap);
    BY_FEATURE_ID = Collections.unmodifiableMap(byFeatureMap);
  }

  private TckSuiteRegistry() {}

  public static List<TckScenario> allScenarios() {
    return ALL_SCENARIOS;
  }

  public static List<TckScenario> getScenarios() {
    return ALL_SCENARIOS;
  }

  public static Optional<TckScenario> findById(String id) {
    return Optional.ofNullable(BY_ID.get(id));
  }

  public static List<TckScenario> findByFeatureId(String featureId) {
    List<TckScenario> found = BY_FEATURE_ID.get(featureId);
    return found != null ? Collections.unmodifiableList(found) : Collections.emptyList();
  }

  public static Set<String> coveredFeatureIds() {
    return BY_FEATURE_ID.keySet();
  }
}
