package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceSymbolRankingTest {

  private WorkspaceSymbolIndex symbolIndex;

  @BeforeEach
  void setUp() {
    CanonicalSchemaResolver schemaResolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(schemaResolver);
    symbolIndex =
        new WorkspaceSymbolIndex(schemaResolver, schemaIndex, MemberAccessPolicy.standard());
  }

  private WorkspaceSymbolEntry createEntry(
      String name,
      SymbolKind kind,
      String containerName,
      String qualifiedName,
      String uri,
      Range range) {
    LocationInfo loc = LocationInfo.of(uri, range);
    return new WorkspaceSymbolEntry(
        name, kind, containerName, qualifiedName, loc, "TEST", qualifiedName);
  }

  @Test
  @DisplayName("Should classify all 9 match tiers deterministically")
  void testMatchTiersDirectCalculation() {
    Range dummyRange = Range.of(0, 0, 0, 5);
    String uri = "file:///test/models.vtl";

    // Tier 1: exact case-sensitive simple name
    WorkspaceSymbolEntry e1 =
        createEntry("User", SymbolKind.CLASS, "com.example", "com.example.User", uri, dummyRange);
    assertEquals(1, WorkspaceSymbolIndex.computeMatchTier(e1, "User", "user"));

    // Tier 2: exact case-insensitive simple name
    WorkspaceSymbolEntry e2 =
        createEntry("User", SymbolKind.CLASS, "com.example", "com.example.User", uri, dummyRange);
    assertEquals(2, WorkspaceSymbolIndex.computeMatchTier(e2, "user", "user"));
    assertEquals(2, WorkspaceSymbolIndex.computeMatchTier(e2, "USER", "user"));

    // Tier 3: qualified exact
    WorkspaceSymbolEntry e3 =
        createEntry(
            "Client", SymbolKind.CLASS, "com.example", "com.example.Client", uri, dummyRange);
    assertEquals(
        3, WorkspaceSymbolIndex.computeMatchTier(e3, "com.example.Client", "com.example.client"));
    assertEquals(
        3, WorkspaceSymbolIndex.computeMatchTier(e3, "COM.EXAMPLE.CLIENT", "com.example.client"));

    // Tier 4: prefix simple case-sensitive
    WorkspaceSymbolEntry e4 =
        createEntry(
            "UserProfile",
            SymbolKind.CLASS,
            "com.example",
            "com.example.UserProfile",
            uri,
            dummyRange);
    assertEquals(4, WorkspaceSymbolIndex.computeMatchTier(e4, "User", "user"));

    // Tier 5: prefix simple case-insensitive
    WorkspaceSymbolEntry e5 =
        createEntry(
            "UserProfile",
            SymbolKind.CLASS,
            "com.example",
            "com.example.UserProfile",
            uri,
            dummyRange);
    assertEquals(5, WorkspaceSymbolIndex.computeMatchTier(e5, "user", "user"));

    // Tier 6: prefix qualified
    WorkspaceSymbolEntry e6 =
        createEntry(
            "Profile", SymbolKind.CLASS, "com.example", "com.example.Profile", uri, dummyRange);
    assertEquals(6, WorkspaceSymbolIndex.computeMatchTier(e6, "com.ex", "com.ex"));
    assertEquals(6, WorkspaceSymbolIndex.computeMatchTier(e6, "COM.EX", "com.ex"));

    // Tier 7: substring simple
    WorkspaceSymbolEntry e7 =
        createEntry(
            "UserProfile",
            SymbolKind.CLASS,
            "com.example",
            "com.example.UserProfile",
            uri,
            dummyRange);
    assertEquals(7, WorkspaceSymbolIndex.computeMatchTier(e7, "Profile", "profile"));
    assertEquals(7, WorkspaceSymbolIndex.computeMatchTier(e7, "rofi", "rofi"));

    // Tier 8: substring qualified
    WorkspaceSymbolEntry e8 =
        createEntry(
            "Profile",
            SymbolKind.CLASS,
            "com.example.internal",
            "com.example.internal.Profile",
            uri,
            dummyRange);
    assertEquals(8, WorkspaceSymbolIndex.computeMatchTier(e8, "internal", "internal"));

    // Tier 9: camelCase
    WorkspaceSymbolEntry e9 =
        createEntry(
            "UserProfileManager",
            SymbolKind.CLASS,
            "com.example",
            "com.example.UserProfileManager",
            uri,
            dummyRange);
    assertEquals(9, WorkspaceSymbolIndex.computeMatchTier(e9, "UPM", "upm"));

    // No match -> 0
    WorkspaceSymbolEntry e0 =
        createEntry("User", SymbolKind.CLASS, "com.example", "com.example.User", uri, dummyRange);
    assertEquals(0, WorkspaceSymbolIndex.computeMatchTier(e0, "xyz", "xyz"));
  }

  @Test
  @DisplayName("Should validate camelCase matching with uppercase boundaries and separators")
  void testCamelCaseMatching() {
    // Upper case transitions
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("UserProfile", "UP"));
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("UserProfile", "up"));
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("UserProfileManager", "UPM"));
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("UserProfileManager", "uPm"));
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("FastOrderProcessor", "FOP"));

    // Word separators: underscore, hyphen, dot, dollar, hash
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("user_profile_dto", "upd"));
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("customer-order-item", "coi"));
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("app.config.service", "acs"));
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("my$special#macro", "msm"));
    assertTrue(WorkspaceSymbolIndex.matchesCamelCase("mixed_Case-Word.Item", "mcwi"));

    // Non-matches
    assertFalse(WorkspaceSymbolIndex.matchesCamelCase("UserProfile", "XYZ"));
    assertFalse(WorkspaceSymbolIndex.matchesCamelCase("UserProfile", "UPL"));
    assertFalse(WorkspaceSymbolIndex.matchesCamelCase("UserProfile", "UPMM"));
    assertFalse(WorkspaceSymbolIndex.matchesCamelCase(null, "UP"));
    assertFalse(WorkspaceSymbolIndex.matchesCamelCase("UserProfile", null));
    assertFalse(WorkspaceSymbolIndex.matchesCamelCase("UserProfile", ""));
    assertFalse(WorkspaceSymbolIndex.matchesCamelCase("", "UP"));
  }

  @Test
  @DisplayName("Should verify strict tie-breaking sequence in SYMBOL_COMPARATOR")
  void testSymbolComparatorTieBreakingSequence() {
    Range r0 = Range.of(0, 0, 0, 10);
    Range r1 = Range.of(1, 0, 1, 10);
    Range r2 = Range.of(1, 5, 1, 15);

    // 1. Tier difference
    WorkspaceSymbolEntry eTier1 =
        createEntry("User", SymbolKind.CLASS, "pkg", "pkg.User", "file:///a.vtl", r0);
    WorkspaceSymbolEntry eTier2 =
        createEntry("User", SymbolKind.CLASS, "pkg", "pkg.User", "file:///a.vtl", r0);
    WorkspaceSymbolIndex.ScoredSymbol sTier1 = new WorkspaceSymbolIndex.ScoredSymbol(eTier1, 1);
    WorkspaceSymbolIndex.ScoredSymbol sTier2 = new WorkspaceSymbolIndex.ScoredSymbol(eTier2, 2);
    assertTrue(
        WorkspaceSymbolIndex.SYMBOL_COMPARATOR.compare(sTier1, sTier2) < 0,
        "Lower match tier must come first");

    // 2. Kind priority difference (Tier equal)
    // CLASS (1) < STRUCT (3) < FUNCTION (5) < PROPERTY (6) < VARIABLE (9)
    WorkspaceSymbolEntry eClass =
        createEntry("Item", SymbolKind.CLASS, "pkg", "pkg.Item", "file:///a.vtl", r0);
    WorkspaceSymbolEntry eStruct =
        createEntry("Item", SymbolKind.STRUCT, "pkg", "pkg.Item", "file:///a.vtl", r0);
    WorkspaceSymbolEntry eFunc =
        createEntry("Item", SymbolKind.FUNCTION, "pkg", "pkg.Item", "file:///a.vtl", r0);
    WorkspaceSymbolEntry eProp =
        createEntry("Item", SymbolKind.PROPERTY, "pkg", "pkg.Item", "file:///a.vtl", r0);
    WorkspaceSymbolEntry eVar =
        createEntry("Item", SymbolKind.VARIABLE, "pkg", "pkg.Item", "file:///a.vtl", r0);

    List<WorkspaceSymbolIndex.ScoredSymbol> kindList =
        new ArrayList<>(
            List.of(
                new WorkspaceSymbolIndex.ScoredSymbol(eVar, 1),
                new WorkspaceSymbolIndex.ScoredSymbol(eProp, 1),
                new WorkspaceSymbolIndex.ScoredSymbol(eFunc, 1),
                new WorkspaceSymbolIndex.ScoredSymbol(eStruct, 1),
                new WorkspaceSymbolIndex.ScoredSymbol(eClass, 1)));
    kindList.sort(WorkspaceSymbolIndex.SYMBOL_COMPARATOR);
    assertEquals(SymbolKind.CLASS, kindList.get(0).entry().kind());
    assertEquals(SymbolKind.STRUCT, kindList.get(1).entry().kind());
    assertEquals(SymbolKind.FUNCTION, kindList.get(2).entry().kind());
    assertEquals(SymbolKind.PROPERTY, kindList.get(3).entry().kind());
    assertEquals(SymbolKind.VARIABLE, kindList.get(4).entry().kind());

    // 3. Simple name length difference (Tier and Kind equal)
    WorkspaceSymbolEntry eShort =
        createEntry("User", SymbolKind.CLASS, "pkg", "pkg.User", "file:///a.vtl", r0);
    WorkspaceSymbolEntry eLong =
        createEntry("UserData", SymbolKind.CLASS, "pkg", "pkg.UserData", "file:///a.vtl", r0);
    WorkspaceSymbolIndex.ScoredSymbol sShort = new WorkspaceSymbolIndex.ScoredSymbol(eShort, 4);
    WorkspaceSymbolIndex.ScoredSymbol sLong = new WorkspaceSymbolIndex.ScoredSymbol(eLong, 4);
    assertTrue(
        WorkspaceSymbolIndex.SYMBOL_COMPARATOR.compare(sShort, sLong) < 0,
        "Shorter simple name length must come first");

    // 4. Qualified name difference (Tier, Kind, Length equal)
    WorkspaceSymbolEntry eQNameA =
        createEntry("User", SymbolKind.CLASS, "a.pkg", "a.pkg.User", "file:///a.vtl", r0);
    WorkspaceSymbolEntry eQNameZ =
        createEntry("User", SymbolKind.CLASS, "z.pkg", "z.pkg.User", "file:///a.vtl", r0);
    WorkspaceSymbolIndex.ScoredSymbol sQA = new WorkspaceSymbolIndex.ScoredSymbol(eQNameA, 1);
    WorkspaceSymbolIndex.ScoredSymbol sQZ = new WorkspaceSymbolIndex.ScoredSymbol(eQNameZ, 1);
    assertTrue(
        WorkspaceSymbolIndex.SYMBOL_COMPARATOR.compare(sQA, sQZ) < 0,
        "Lexicographical qualified name must break tie");

    // 5. URI difference (Tier, Kind, Length, Qualified equal)
    WorkspaceSymbolEntry eUriA =
        createEntry("User", SymbolKind.CLASS, "pkg", "pkg.User", "file:///a/file.vtl", r0);
    WorkspaceSymbolEntry eUriB =
        createEntry("User", SymbolKind.CLASS, "pkg", "pkg.User", "file:///b/file.vtl", r0);
    WorkspaceSymbolIndex.ScoredSymbol sUriA = new WorkspaceSymbolIndex.ScoredSymbol(eUriA, 1);
    WorkspaceSymbolIndex.ScoredSymbol sUriB = new WorkspaceSymbolIndex.ScoredSymbol(eUriB, 1);
    assertTrue(
        WorkspaceSymbolIndex.SYMBOL_COMPARATOR.compare(sUriA, sUriB) < 0,
        "Lexicographical URI must break tie");

    // 6. Range difference (Tier, Kind, Length, Qualified, URI equal)
    WorkspaceSymbolEntry eRangeEarlier =
        createEntry("User", SymbolKind.CLASS, "pkg", "pkg.User", "file:///a.vtl", r1);
    WorkspaceSymbolEntry eRangeLater =
        createEntry("User", SymbolKind.CLASS, "pkg", "pkg.User", "file:///a.vtl", r2);
    WorkspaceSymbolIndex.ScoredSymbol sRangeEarly =
        new WorkspaceSymbolIndex.ScoredSymbol(eRangeEarlier, 1);
    WorkspaceSymbolIndex.ScoredSymbol sRangeLate =
        new WorkspaceSymbolIndex.ScoredSymbol(eRangeLater, 1);
    assertTrue(
        WorkspaceSymbolIndex.SYMBOL_COMPARATOR.compare(sRangeEarly, sRangeLate) < 0,
        "Earlier range start must break tie");
  }

  @Test
  @DisplayName("Should rank template macros across different tiers for query 'header'")
  void testSearchRankingEndToEnd() {
    String vtl =
        """
        #macro(header) <header/> #end
        #macro(HEADER) <header-caps/> #end
        #macro(headerSection) <section/> #end
        #macro(header_detail) <detail/> #end
        #macro(render_page_header) <page/> #end
        #macro(HugeExpensiveAppDailyEmailRenderer) <email/> #end
        """;
    symbolIndex.indexTemplateMacros(new TemplateDocument("file:///workspace/header.vtl", 1, vtl));

    // Search "header"
    List<SymbolInformation> results = symbolIndex.search("header");
    assertFalse(results.isEmpty());

    // Verify Tier 1: exact case-sensitive "header"
    assertEquals("header", results.get(0).name());
    // Verify Tier 2: exact case-insensitive "HEADER"
    assertEquals("HEADER", results.get(1).name());

    // Verify Tier 4: prefix case-sensitive ("headerSection", "header_detail")
    List<String> resultNames = results.stream().map(SymbolInformation::name).toList();
    assertTrue(resultNames.contains("headerSection"));
    assertTrue(resultNames.contains("header_detail"));
    assertTrue(resultNames.contains("render_page_header")); // substring simple (Tier 7)

    int idxExact = resultNames.indexOf("header");
    int idxCaps = resultNames.indexOf("HEADER");
    int idxPrefix = resultNames.indexOf("headerSection");
    int idxSub = resultNames.indexOf("render_page_header");

    assertTrue(idxExact < idxCaps, "Exact case-sensitive must precede case-insensitive");
    assertTrue(idxCaps < idxPrefix, "Exact match must precede prefix match");
    assertTrue(idxPrefix < idxSub, "Prefix match must precede substring match");
  }
}
