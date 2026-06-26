package com.apiframework.tests.stub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/*
 * WHY specific WireMock imports instead of a wildcard?
 *   WireMock.equalTo() returns StringValuePattern — not the Hamcrest Matcher that
 *   REST Assured's .body() chain expects. Specific named imports let Hamcrest's
 *   equalTo() win for all assertion calls while WireMock's stub DSL is still available.
 */
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.*;

/**
 * Stub Demonstration Tests — Pure Programmatic WireMock Stubbing
 *
 * WHAT this class demonstrates:
 *   All 4 HTTP verbs (GET, POST, PUT, DELETE) using only programmatic stubs.
 *   Every stub is defined in Java via stubFor(), with no JSON mapping files on disk.
 *   This is the counterpart to WireMockTests.java — that class mixes file-based and
 *   programmatic stubs; this class shows the pure-Java approach end to end.
 *
 * WHY a separate class from WireMockTests?
 *   WireMockTests demonstrates the MOCKING concept (an embedded fake server) and shows
 *   how file-based and programmatic stubs can coexist. StubDemoTests focuses entirely on
 *   STUBBING: pre-programming precise request → response mappings in Java code, making
 *   the full stub lifecycle visible in one place.
 *
 * DATA-DRIVEN STUBS:
 *   All stub request bodies, response bodies, and expected assertion values are loaded from:
 *     src/test/resources/testdata/stub-demo-data.json
 *   Changing that JSON file updates stub behaviour and test expectations simultaneously —
 *   no Java source changes needed.
 *
 * HOW the Allure reporting integration works here:
 *   @Epic / @Feature / @Story / @Severity → organise this class in the Allure report tree.
 *   @Description          → adds extended test documentation visible in the report.
 *   @Step on private methods → Allure's AspectJ weaver captures each call as a named step
 *                              inside the test timeline, so the report shows exactly what
 *                              happened during each test method.
 *
 * Stub inventory (driven by stub-demo-data.json):
 *   /api/items        GET    → 200  JSON array of items
 *   /api/items        POST   → 201  newly created item
 *   /api/items/1      PUT    → 200  updated item
 *   /api/items/1      DELETE → 204  No Content
 *
 * Test inventory:
 *   TC_SD_001  GET    — stubbed list retrieval                [smoke, regression, full]
 *   TC_SD_002  POST   — stubbed resource creation             [regression, full]
 *   TC_SD_003  PUT    — stubbed resource update               [regression, full]
 *   TC_SD_004  DELETE — stubbed resource deletion             [regression, full]
 */
@Epic("REST Assured Framework")
@Feature("Pure Programmatic Stubbing")
public class StubDemoTests {

    private static final Logger log        = LoggerFactory.getLogger(StubDemoTests.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Endpoint paths shared across stubs and test methods
    private static final String ITEMS_PATH      = "/api/items";
    private static final String ITEM_BY_ID_PATH = "/api/items/1";

    // Static fields: @Step annotations trigger AspectJ load-time weaving which can cause
    // @BeforeClass to run on a different instance than the test methods. Static fields are
    // class-level, so they are visible across all instances AspectJ may create.
    private static WireMockServer wireMockServer;
    private static String         baseUrl;

    /** Loaded from testdata/stub-demo-data.json; drives both stub registration and assertions. */
    private static JsonNode stubData;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * WHY @BeforeClass?
     *   One server instance is cheaper than starting/stopping per test.
     *   All stubs are registered once and remain available for the full class run.
     *   Stub data is also loaded once here and reused by every test and step method.
     */
    @BeforeClass(alwaysRun = true)
    public static void startServer() throws IOException {
        try (InputStream is = StubDemoTests.class.getClassLoader()
                .getResourceAsStream("testdata/stub-demo-data.json")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Stub data file not found on classpath: testdata/stub-demo-data.json");
            }
            stubData = MAPPER.readTree(is);
        }

        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        baseUrl = "http://localhost:" + wireMockServer.port();
        log.info("StubDemo server started on {}", baseUrl);
        registerAllStubs();
    }

    @AfterClass(alwaysRun = true)
    public static void stopServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            log.info("StubDemo server stopped.");
        }
    }

    // ── Stub Registration ──────────────────────────────────────────────────────

    /**
     * Registers all 4 programmatic stubs using response bodies from stub-demo-data.json.
     *
     * WHY read bodies from JSON instead of inline strings?
     *   The response body drives both what the stub returns AND what the assertions expect.
     *   Keeping both in one place (stub-demo-data.json) means a single edit updates the
     *   stub contract and the test assertions simultaneously.
     */
    private static void registerAllStubs() {
        stubFor(get(urlEqualTo(ITEMS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(stubData.get("get").get("response").toString())));

        stubFor(post(urlEqualTo(ITEMS_PATH))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(stubData.get("post").get("response").toString())));

        stubFor(put(urlEqualTo(ITEM_BY_ID_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(stubData.get("put").get("response").toString())));

        stubFor(delete(urlEqualTo(ITEM_BY_ID_PATH))
                .willReturn(aResponse()
                        .withStatus(204)));

        log.info("Registered 4 programmatic stubs from stub-demo-data.json.");
    }

    // ── TC_SD_001 — GET ────────────────────────────────────────────────────────

    /**
     * TC_SD_001 — GET stub: retrieve all items.
     *
     * Demonstrates the simplest stub pattern: URL + method → fixed response.
     * No request body or header matching needed for a GET.
     */
    @Test(priority = 1,
          groups = {"smoke", "regression", "full"},
          description = "TC_SD_001 — Stubbing: GET /api/items returns a stubbed JSON array of items")
    @Story("TC_SD_001 — GET stub returns list")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Sends GET /api/items to a programmatic stub. "
            + "Verifies the response is a JSON array with item count and field values from stub-demo-data.json. "
            + "Confirms via WireMock call verification that exactly one GET was received.")
    public void TC_SD_001_stubbedGet() {
        Response response = executeGet();
        assertGetResponse(response);
        verifyGetCallMade();
        log.info("[TC_SD_001] PASS | Stubbed GET returned {} items",
                response.jsonPath().getList("$").size());
    }

    @Step("Execute GET /api/items")
    private Response executeGet() {
        JsonNode items = stubData.get("get").get("response");
        return given()
                .baseUri(baseUrl)
                .accept(ContentType.JSON)
                .when()
                .get(ITEMS_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$",         instanceOf(List.class))
                .body("size()",    equalTo(items.size()))
                .body("[0].id",    equalTo(items.get(0).get("id").asInt()))
                .body("[0].name",  equalTo(items.get(0).get("name").asText()))
                .body("[0].price", equalTo((float) items.get(0).get("price").asDouble()))
                .body("[1].id",    equalTo(items.get(1).get("id").asInt()))
                .body("[1].name",  equalTo(items.get(1).get("name").asText()))
                .extract().response();
    }

    @Step("Assert GET response: correct item count, ids and names from data file")
    private void assertGetResponse(Response response) {
        int expectedCount = stubData.get("get").get("response").size();
        List<?> items     = response.jsonPath().getList("$");
        assertNotNull(items, "Response array must not be null");
        assertEquals(items.size(), expectedCount,
                "Stub must return exactly " + expectedCount + " items");
    }

    @Step("Verify WireMock received exactly 1 GET /api/items request")
    private void verifyGetCallMade() {
        wireMockServer.verify(1, getRequestedFor(urlEqualTo(ITEMS_PATH)));
    }

    // ── TC_SD_002 — POST ───────────────────────────────────────────────────────

    /**
     * TC_SD_002 — POST stub: create a new item.
     *
     * Request body comes from stub-demo-data.json post.request.
     * Expected response values come from stub-demo-data.json post.response.
     * The id in the response simulates a server-assigned auto-increment.
     */
    @Test(priority = 2,
          groups = {"regression", "full"},
          description = "TC_SD_002 — Stubbing: POST /api/items returns 201 with server-assigned id")
    @Story("TC_SD_002 — POST stub creates resource")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Sends POST /api/items with a JSON body from stub-demo-data.json. "
            + "The stub returns 201 Created with an id not present in the request. "
            + "Demonstrates that programmatic stubs decouple the test from real server state.")
    public void TC_SD_002_stubbedPost() {
        String requestBody = stubData.get("post").get("request").toString();
        Response response  = executePost(requestBody);
        assertPostResponse(response);
        verifyPostCallMade();
        log.info("[TC_SD_002] PASS | Stubbed POST returned created item id={}",
                response.jsonPath().getInt("id"));
    }

    @Step("Execute POST /api/items with JSON body from data file")
    private Response executePost(String body) {
        JsonNode expected = stubData.get("post").get("response");
        return given()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(body)
                .when()
                .post(ITEMS_PATH)
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id",    equalTo(expected.get("id").asInt()))
                .body("name",  equalTo(expected.get("name").asText()))
                .body("price", equalTo((float) expected.get("price").asDouble()))
                .extract().response();
    }

    @Step("Assert POST response: 201, id and name match stub response from data file")
    private void assertPostResponse(Response response) {
        int expectedId = stubData.get("post").get("response").get("id").asInt();
        assertNotNull(response.jsonPath().get("id"),   "Created item must have an id");
        assertEquals((int) response.jsonPath().get("id"), expectedId,
                "Stub must return id=" + expectedId);
        assertNotNull(response.jsonPath().get("name"), "Created item must have a name");
    }

    @Step("Verify WireMock received exactly 1 POST /api/items request")
    private void verifyPostCallMade() {
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(ITEMS_PATH)));
    }

    // ── TC_SD_003 — PUT ────────────────────────────────────────────────────────

    /**
     * TC_SD_003 — PUT stub: update an existing item.
     *
     * Request body comes from stub-demo-data.json put.request.
     * Expected response values come from stub-demo-data.json put.response.
     * Original price for the higher-than assertion comes from get.response[0].price.
     */
    @Test(priority = 3,
          groups = {"regression", "full"},
          description = "TC_SD_003 — Stubbing: PUT /api/items/{id} returns 200 with updated resource")
    @Story("TC_SD_003 — PUT stub updates resource")
    @Severity(SeverityLevel.NORMAL)
    @Description("Sends PUT /api/items/1 with an updated JSON body from stub-demo-data.json. "
            + "The stub returns 200 OK with an updated name and raised price. "
            + "Demonstrates a full-replace PUT pattern and how stubs can simulate server-side update logic.")
    public void TC_SD_003_stubbedPut() {
        String requestBody = stubData.get("put").get("request").toString();
        Response response  = executePut(requestBody);
        assertPutResponse(response);
        verifyPutCallMade();
        log.info("[TC_SD_003] PASS | Stubbed PUT returned updated item id={}",
                response.jsonPath().getInt("id"));
    }

    @Step("Execute PUT /api/items/1 with updated JSON body from data file")
    private Response executePut(String body) {
        JsonNode expected = stubData.get("put").get("response");
        return given()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(body)
                .when()
                .put(ITEM_BY_ID_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id",    equalTo(expected.get("id").asInt()))
                .body("name",  equalTo(expected.get("name").asText()))
                .body("price", equalTo((float) expected.get("price").asDouble()))
                .extract().response();
    }

    @Step("Assert PUT response: id unchanged, name and price match stub response from data file")
    private void assertPutResponse(Response response) {
        int    expectedId    = stubData.get("put").get("response").get("id").asInt();
        String expectedName  = stubData.get("put").get("response").get("name").asText();
        double originalPrice = stubData.get("get").get("response").get(0).get("price").asDouble();
        double updatedPrice  = stubData.get("put").get("response").get("price").asDouble();

        assertEquals((int) response.jsonPath().get("id"), expectedId,
                "Item id must remain " + expectedId + " after update");
        assertEquals(response.jsonPath().getString("name"), expectedName,
                "Name must match stub response");
        assertTrue(updatedPrice > originalPrice,
                "Updated price " + updatedPrice + " must exceed original " + originalPrice);
    }

    @Step("Verify WireMock received exactly 1 PUT /api/items/1 request")
    private void verifyPutCallMade() {
        wireMockServer.verify(1, putRequestedFor(urlEqualTo(ITEM_BY_ID_PATH)));
    }

    // ── TC_SD_004 — DELETE ─────────────────────────────────────────────────────

    /**
     * TC_SD_004 — DELETE stub: remove an item.
     *
     * Verifies that DELETE /api/items/1 returns 204 No Content.
     * WireMock is stateless — the GET stub still returns data after this call,
     * which is why call verification (not a follow-up GET) confirms the deletion.
     */
    @Test(priority = 4,
          groups = {"regression", "full"},
          description = "TC_SD_004 — Stubbing: DELETE /api/items/{id} returns 204 No Content")
    @Story("TC_SD_004 — DELETE stub removes resource")
    @Severity(SeverityLevel.NORMAL)
    @Description("Sends DELETE /api/items/1. "
            + "The stub returns 204 No Content with no response body — the correct REST convention for deletion. "
            + "Because WireMock is stateless, deletion is confirmed via verify() rather than a follow-up GET.")
    public void TC_SD_004_stubbedDelete() {
        executeDelete();
        verifyDeleteCallMade();
        log.info("[TC_SD_004] PASS | Stubbed DELETE /api/items/1 returned 204");
    }

    @Step("Execute DELETE /api/items/1 — expect 204 No Content")
    private void executeDelete() {
        given()
                .baseUri(baseUrl)
                .when()
                .delete(ITEM_BY_ID_PATH)
                .then()
                .statusCode(204);
    }

    @Step("Verify WireMock received exactly 1 DELETE /api/items/1 request")
    private void verifyDeleteCallMade() {
        wireMockServer.verify(1, deleteRequestedFor(urlEqualTo(ITEM_BY_ID_PATH)));
    }
}
