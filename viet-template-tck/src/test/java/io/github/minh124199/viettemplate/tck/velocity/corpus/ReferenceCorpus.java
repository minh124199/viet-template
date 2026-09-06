package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.AddressBean;
import io.github.minh124199.viettemplate.tck.velocity.model.AddressRecord;
import io.github.minh124199.viettemplate.tck.velocity.model.NullReturningBean;
import io.github.minh124199.viettemplate.tck.velocity.model.PersonBean;
import io.github.minh124199.viettemplate.tck.velocity.model.PersonRecord;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering reference syntax forms and member navigation. */
public final class ReferenceCorpus {

  private ReferenceCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Root variable references
    list.add(
        CompatibilityScenario.of(
            "reference.root.defined-simple",
            ScenarioCategory.REFERENCE,
            "Value: $foo",
            ContextFactory.of("foo", "bar")));

    list.add(
        CompatibilityScenario.of(
            "reference.root.defined-formal",
            ScenarioCategory.REFERENCE,
            "Value: ${foo}",
            ContextFactory.of("foo", "bar")));

    list.add(
        CompatibilityScenario.simple(
            "reference.root.undefined-simple", ScenarioCategory.REFERENCE, "Value: $missing"));

    list.add(
        CompatibilityScenario.simple(
            "reference.root.undefined-formal", ScenarioCategory.REFERENCE, "Value: ${missing}"));

    list.add(
        CompatibilityScenario.simple(
            "reference.root.undefined-quiet-simple",
            ScenarioCategory.REFERENCE,
            "Value: [$!missing]"));

    list.add(
        CompatibilityScenario.simple(
            "reference.root.undefined-quiet-formal",
            ScenarioCategory.REFERENCE,
            "Value: [$!{missing}]"));

    list.add(
        CompatibilityScenario.of(
            "reference.root.defined-null-simple",
            ScenarioCategory.REFERENCE,
            "Value: [$nullVal]",
            () -> {
              Map<String, Object> ctx = new HashMap<>();
              ctx.put("nullVal", null);
              return ctx;
            }));

    list.add(
        CompatibilityScenario.of(
            "reference.root.defined-null-quiet",
            ScenarioCategory.REFERENCE,
            "Value: [$!nullVal]",
            () -> {
              Map<String, Object> ctx = new HashMap<>();
              ctx.put("nullVal", null);
              return ctx;
            }));

    // 2. JavaBean property navigation
    list.add(
        CompatibilityScenario.of(
            "reference.property.bean.getter",
            ScenarioCategory.REFERENCE,
            "Name: $person.name, Age: $person.age, Active: $person.active",
            () ->
                Map.of(
                    "person",
                    new PersonBean("Alice", 30, true, new AddressBean("Hanoi", "10000")))));

    list.add(
        CompatibilityScenario.of(
            "reference.property.bean.nested",
            ScenarioCategory.REFERENCE,
            "City: $person.address.city, Zip: $person.address.zip",
            () ->
                Map.of(
                    "person",
                    new PersonBean("Alice", 30, true, new AddressBean("Hanoi", "10000")))));

    list.add(
        CompatibilityScenario.of(
            "reference.property.bean.method-call",
            ScenarioCategory.REFERENCE,
            "Greeting: $person.sayHello('Bob')",
            () ->
                Map.of(
                    "person",
                    new PersonBean("Alice", 30, true, new AddressBean("Hanoi", "10000")))));

    // 3. Java 17 Record component navigation
    list.add(
        CompatibilityScenario.of(
            "reference.property.record.simple",
            ScenarioCategory.REFERENCE,
            "Name: $person.name, Age: $person.age, Active: $person.active",
            () ->
                Map.of(
                    "person",
                    new PersonRecord("Charlie", 25, true, new AddressRecord("Saigon", "70000")))));

    list.add(
        CompatibilityScenario.of(
            "reference.property.record.nested",
            ScenarioCategory.REFERENCE,
            "City: $person.address.city, Zip: $person.address.zip",
            () ->
                Map.of(
                    "person",
                    new PersonRecord("Charlie", 25, true, new AddressRecord("Saigon", "70000")))));

    // 4. Map and Index access
    list.add(
        CompatibilityScenario.of(
            "reference.property.map.dot-and-bracket",
            ScenarioCategory.REFERENCE,
            "Dot: $map.city, Bracket: $map['city']",
            () -> Map.of("map", Map.of("city", "Da Nang"))));

    list.add(
        CompatibilityScenario.of(
            "reference.index.list",
            ScenarioCategory.REFERENCE,
            "First: $list[0], Second: $list[1]",
            () -> Map.of("list", List.of("alpha", "beta"))));

    list.add(
        CompatibilityScenario.of(
            "reference.index.array",
            ScenarioCategory.REFERENCE,
            "First: $arr[0], Second: $arr[1]",
            () -> Map.of("arr", new String[] {"first", "second"})));

    list.add(
        CompatibilityScenario.of(
            "reference.index.nested-object",
            ScenarioCategory.REFERENCE,
            "Nested: $list[0].name",
            () -> Map.of("list", List.of(new PersonBean("Eve", 28, false, null)))));

    // 5. Null intermediate and null returns
    list.add(
        CompatibilityScenario.of(
            "reference.null.intermediate-navigation",
            ScenarioCategory.REFERENCE,
            "Result: [$person.address.city]",
            () -> Map.of("person", new PersonBean("Dave", 40, true, null))));

    list.add(
        CompatibilityScenario.of(
            "reference.null.method-returning-null",
            ScenarioCategory.REFERENCE,
            "Result: [$probe.returnNull()]",
            () -> Map.of("probe", new NullReturningBean())));

    list.add(
        CompatibilityScenario.of(
            "reference.null.property-returning-null",
            ScenarioCategory.REFERENCE,
            "Result: [$probe.nullProperty]",
            () -> Map.of("probe", new NullReturningBean())));

    return list;
  }
}
