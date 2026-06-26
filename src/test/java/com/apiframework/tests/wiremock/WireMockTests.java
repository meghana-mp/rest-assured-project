package com.apiframework.tests.wiremock;

import com.apiframework.constants.ApiConstants;
import com.apiframework.models.wiremock.MockBooking;
import com.apiframework.utils.TestUtils;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

/*
 * WHY specific WireMock imports instead of a wildcard?
 *   WireMock has its own equalTo(), containing(), and matching() factory methods that
 *   return StringValuePattern (a WireMock type), NOT Hamcrest Matchers. If we used
 *   "import static com.github.tomakehurst.wiremock.client.WireMock.*", those names
 *   would shadow Hamcrest's equalTo() everywhere in this file — causing compile errors
 *   on REST Assured's .body() calls which expect Hamcrest Matchers.
 *   By importing only what WireMock needs by name, Hamcrest's wildcard import wins
 *   for all .body() assertions.
 */
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

// Hamcrest wildcard is safe here because the WireMock names that conflict
// (equalTo, containing, matching) are already covered by the specific imports above.
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.*;

/**
 * WireMock Tests — In-Process HTTP Mocking and Stubbing
 *
 * WHAT this class demonstrates:
 *   A full booking-style CRUD API is mocked using WireMock's embedded server.
 *   No real network calls are made — WireMock intercepts every request and returns
 *   pre-configured stub responses, making these tests completely deterministic,
 *   instant, and credential-free.
 *
 * WHY WireMock?
 *   Real APIs are slow, rate-limited, require credentials, and can return unexpected
 *   data. WireMock lets you:
 *     - Test error scenarios impossible to trigger on the real API (503, timeouts)
 *     - Run tests in CI without any credentials or network access
 *     - Verify the exact request your code sends (headers, body, path params)
 *     - Keep tests fast and deterministic regardless of external API state
 *
 * HOW the two stub approaches work:
 *   a) FILE-BASED stubs — JSON files in src/test/resources/wiremock/mappings/ are
 *      loaded automatically at server start via usingFilesUnderClasspath("wiremock").
 *      Body response files live in src/test/resources/wiremock/__files/.
 *      Used for: GET /mock/bookings, GET /mock/bookings/{id}, DELETE.
 *
 *   b) PROGRAMMATIC stubs — stubFor() calls in @BeforeClass register stubs with
 *      dynamic request-body and header matching — impossible with static files.
 *      Used for: POST (body contains "Alice"), PUT (requires Authorization header).
 *
 * Test inventory:
 *   TC_WM_001  GET    /mock/bookings      — list all bookings (file-based stub)  [smoke, full]
 *   TC_WM_002  POST   /mock/bookings      — create booking (stubbing demo)        [regression, full]
 *   TC_WM_003  PUT    /mock/bookings/{id} — update booking (auth header required) [full]
 *   TC_WM_004  DELETE /mock/bookings/{id} — delete booking (204 No Content)       [full]
 */
@Epic("REST Assured Framework")
@Feature("WireMock — Embedded Mocking and Stubbing")
public class WireMockTests {

    private static final Logger log = LoggerFactory.getLogger(WireMockTests.class);

    /** The embedded HTTP server — started once per class, shared across all test methods. */
    private WireMockServer wireMockServer;

    /** Base URL resolved after server start; injected into every REST Assured call. */
    private String mockBaseUrl;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * WHY @BeforeClass (not @BeforeMethod)?
     *   Starting a WireMock server is expensive (~200 ms). One server instance shared
     *   across all test methods is faster and mirrors real-world usage where a mock
     *   server runs for the lifetime of a test suite, not per-test.
     *
     * HOW the server is configured:
     *   dynamicPort()              → OS assigns a free port; prevents port collisions in CI.
     *   usingFilesUnderClasspath() → auto-loads wiremock/mappings/*.json and __files/.
     */
    @BeforeClass(alwaysRun = true)
    public void startWireMockServer() {
        wireMockServer = new WireMockServer(
                WireMockConfiguration.options()
                        .dynamicPort()
                        .usingFilesUnderClasspath("wiremock")
        );

        wireMockServer.start();

        // Point the WireMock static client at our server so stubFor() / verify() calls
        // target the correct port dynamically chosen above.
        WireMock.configureFor("localhost", wireMockServer.port());

        mockBaseUrl = "http://localhost:" + wireMockServer.port();
        log.info("WireMock server started on {}", mockBaseUrl);

        // File-based stubs for ALL 4 HTTP verbs are auto-loaded above:
        //   GET  /mock/bookings        → mappings/get-all-bookings.json   → __files/bookings-list.json
        //   GET  /mock/bookings/{id}   → mappings/get-booking-by-id.json  → __files/booking-single.json
        //   POST /mock/bookings        → mappings/post-create-booking.json → __files/booking-created.json
        //   PUT  /mock/bookings/{id}   → mappings/put-update-booking.json  → __files/booking-updated.json
        //   DELETE /mock/bookings/{id} → mappings/delete-booking.json      → 204 (no body)
        //
        // WHY add programmatic stubs ON TOP of the file-based POST and PUT?
        //   File-based stubs match on METHOD + URL only — they cannot inspect request bodies
        //   or headers. The programmatic stubs below add stricter conditions:
        //     POST → only fires when body contains "Alice"  (body-matching stub)
        //     PUT  → only fires when Authorization header is present (header-matching stub)
        //   WireMock uses LIFO order: programmatic stubs (registered last) are tried FIRST.
        //   If their conditions match → they win. If not, the file-based stub acts as fallback.
        registerCreateBookingStub();
        registerUpdateBookingStub();
    }

    /**
     * Programmatic stub for POST /mock/bookings.
     *
     * WHY programmatic?
     *   The stub matches on REQUEST BODY content — it only fires when the body contains
     *   "Alice". A JSON mapping file can only match URLs; to inspect the body you need
     *   the Java API. This is the core "stubbing" concept: pre-programme the server to
     *   give a specific response only for a specific request shape.
     */
    private void registerCreateBookingStub() {
        stubFor(post(urlEqualTo(ApiConstants.MOCK_BOOKINGS))
                .withRequestBody(containing("Alice"))             // body-matching stub condition
                .withHeader("Content-Type", containing("application/json"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("booking-created.json")));
    }

    /**
     * Programmatic stub for PUT /mock/bookings/1.
     *
     * WHY require the Authorization header in the stub?
     *   By gating the 200 response on "Authorization: Bearer <anything>", the test
     *   proves that the framework code actually attaches auth credentials to PUT calls.
     *   If the header is missing, WireMock returns 404 — the test fails immediately.
     */
    private void registerUpdateBookingStub() {
        stubFor(put(urlEqualTo(ApiConstants.MOCK_BOOKINGS + "/1"))
                .withHeader("Authorization", matching("Bearer .+"))   // header-matching condition
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("booking-updated.json")));
    }

    /** Stop the server and log any unmatched requests for diagnostics. */
    @AfterClass(alwaysRun = true)
    public void stopWireMockServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            List<LoggedRequest> unmatched = wireMockServer.findAllUnmatchedRequests();
            if (!unmatched.isEmpty()) {
                unmatched.forEach(r ->
                        log.warn("WireMock — unmatched request: {} {}", r.getMethod(), r.getUrl()));
            }
            wireMockServer.stop();
            log.info("WireMock server stopped.");
        }
    }

    // ── TC_WM_001 — GET all bookings ───────────────────────────────────────────

    /*
     * TC_WM_001 — GET All Bookings  [groups: smoke, full]
     *
     * WHAT it does:
     *   Sends GET /mock/bookings to the WireMock server and verifies the response
     *   matches the file-based stub loaded from wiremock/mappings/get-all-bookings.json.
     *   The response body is served from wiremock/__files/bookings-list.json.
     *
     * WHY this is the "WireMock demo" test:
     *   It demonstrates the FILE-BASED stub approach — the stub definition and response
     *   body live in JSON files on the classpath, not in Java code. This is useful for
     *   large response payloads or when non-developers need to edit stub data without
     *   touching Java source.
     *
     * Deserialization:
     *   response.jsonPath().getList(".", MockBooking.class) — Jackson maps each element
     *   of the JSON array to a MockBooking POJO. Root path "." = the top-level array.
     *
     * Expected result:
     *   HTTP 200 — JSON array of exactly 2 MockBooking objects (John Doe, Jane Smith).
     */
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 1,
          groups = {"smoke", "full"},
          description = "TC_WM_001 — WireMock: GET /mock/bookings returns stubbed booking list")
    public void TC_WM_001_mockGetAllBookings() {
        log.info("TC_WM_001 — GET all bookings from WireMock at {}", mockBaseUrl);

        Response response = given()
                .baseUri(mockBaseUrl)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .when()
                .get(ApiConstants.MOCK_BOOKINGS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$",          instanceOf(List.class))
                .body("size()",     equalTo(2))
                .body("[0].id",        equalTo(1))
                .body("[0].firstname", equalTo("John"))
                .body("[0].lastname",  equalTo("Doe"))
                .body("[1].id",        equalTo(2))
                .body("[1].firstname", equalTo("Jane"))
                .extract().response();

        // Deserialize JSON array → List<MockBooking>
        List<MockBooking> bookings = response.jsonPath().getList(".", MockBooking.class);
        long responseTime = response.getTime();

        assertNotNull(bookings, "Bookings list must not be null");
        assertEquals(bookings.size(),                2,      "Stub must return exactly 2 bookings");
        assertEquals(bookings.get(0).getFirstname(), "John", "First booking must be John");
        assertEquals(bookings.get(1).getFirstname(), "Jane", "Second booking must be Jane");

        // Verify WireMock actually received the GET — proves the test hit the mock server
        List<LoggedRequest> received = wireMockServer
                .findAll(getRequestedFor(urlEqualTo(ApiConstants.MOCK_BOOKINGS)));
        assertFalse(received.isEmpty(),
                "WireMock must have received at least one GET /mock/bookings request");

        TestUtils.logResult("TC_WM_001", true, responseTime,
                "WireMock returned " + bookings.size() + " bookings via file-based stub");
    }

    // ── TC_WM_002 — POST create booking (stubbing demo) ───────────────────────

    /*
     * TC_WM_002 — POST Create Booking — Stubbing Demonstration  [groups: regression, full]
     *
     * WHAT it does:
     *   Sends POST /mock/bookings with body containing "Alice". The programmatic stub
     *   (registerCreateBookingStub) matches on body content and returns HTTP 201 with
     *   a booking that has server-assigned id=3.
     *
     * WHY this is the "stubbing" test:
     *   Stubbing = pre-programming a server to return a SPECIFIC response for a SPECIFIC
     *   request. The key difference from TC_WM_001 is REQUEST BODY MATCHING:
     *     - If the body contains "Alice"  → 201 Created  (stub fires)
     *     - If the body contains anything else → 404     (no matching stub)
     *   This proves the framework sends exactly the right payload.
     *
     * HOW mocking ≠ stubbing (conceptually):
     *   MOCK  = the entire fake server (WireMock itself).
     *   STUB  = one pre-programmed rule on that server for a specific request pattern.
     *   Every stubFor() call creates one stub. This test specifically exercises body-based
     *   stub matching, which is the most common real-world stubbing use case.
     *
     * Serialization:
     *   .body(newBooking) → Jackson converts MockBooking →
     *   {"firstname":"Alice","lastname":"Johnson","totalprice":200,"depositpaid":true}
     *
     * Deserialization:
     *   response.as(MockBooking.class) → JSON 201 body mapped back to MockBooking.
     *   The id field (3) was not in the request — it comes from the stub response body.
     *
     * Expected result:
     *   HTTP 201 Created — id=3 in body proves the stub matched correctly.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 2,
          groups = {"regression", "full"},
          description = "TC_WM_002 — Stubbing: POST body matching returns 201 with server-assigned id")
    public void TC_WM_002_stubbedCreateBooking() {
        log.info("TC_WM_002 — POST create booking (stubbing demo) to WireMock at {}", mockBaseUrl);

        // Serialization: MockBooking POJO → JSON request body via Jackson
        MockBooking newBooking = MockBooking.builder()
                .firstname("Alice")
                .lastname("Johnson")
                .totalprice(200)
                .depositpaid(true)
                .build();

        Response response = given()
                .baseUri(mockBaseUrl)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(newBooking)
                .when()
                .post(ApiConstants.MOCK_BOOKINGS)
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id",         equalTo(3))
                .body("firstname",  equalTo("Alice"))
                .body("lastname",   equalTo("Johnson"))
                .body("totalprice", equalTo(200))
                .extract().response();

        // Deserialization: JSON 201 response body → MockBooking POJO
        MockBooking created = response.as(MockBooking.class);
        long responseTime   = response.getTime();

        assertNotNull(created.getId(),       "Created booking must have a server-assigned id");
        assertEquals(created.getId(),        Integer.valueOf(3),  "Stub must return id=3");
        assertEquals(created.getFirstname(), "Alice",             "Firstname must match request");
        assertEquals(created.getLastname(),  "Johnson",           "Lastname must match request");
        assertTrue(created.getDepositpaid(), "Depositpaid must be true");

        // WireMock call verification: confirm exactly one POST with "Alice" in the body was received
        wireMockServer.verify(1,
                postRequestedFor(urlEqualTo(ApiConstants.MOCK_BOOKINGS))
                        .withRequestBody(containing("Alice")));

        TestUtils.logResult("TC_WM_002", true, responseTime,
                "Stub body-match succeeded; created booking id=" + created.getId());
    }

    // ── TC_WM_003 — PUT update booking ────────────────────────────────────────

    /*
     * TC_WM_003 — PUT Update Booking  [groups: full]
     *
     * WHAT it does:
     *   Sends PUT /mock/bookings/1 with "Authorization: Bearer mock-session-token".
     *   The programmatic stub (registerUpdateBookingStub) gates the 200 response on
     *   the Authorization header being present and matching "Bearer .+".
     *
     * WHY enforce the auth header in the stub?
     *   It proves the framework correctly attaches auth credentials to mutating requests.
     *   If the header is missing, WireMock returns 404 and the test fails — immediately
     *   surfacing the missing auth logic.
     *
     * Serialization:   .body(updatedBooking) → Jackson → JSON request body.
     * Deserialization: response.as(MockBooking.class) → updated booking with price=175.
     *
     * Expected result:
     *   HTTP 200 — totalprice=175 in body confirms the PUT stub matched.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 3,
          groups = {"full"},
          description = "TC_WM_003 — WireMock: PUT /mock/bookings/{id} with auth header returns updated booking")
    public void TC_WM_003_mockUpdateBooking() {
        log.info("TC_WM_003 — PUT update booking/1 on WireMock at {}", mockBaseUrl);

        MockBooking updatedBooking = MockBooking.builder()
                .firstname("John")
                .lastname("Doe")
                .totalprice(175)      // price bumped from 150 → 175
                .depositpaid(true)
                .build();

        Response response = given()
                .baseUri(mockBaseUrl)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                // WHY: stub requires this header — omitting it causes WireMock to return 404
                .header("Authorization", "Bearer mock-session-token")
                .body(updatedBooking)
                .when()
                .put(ApiConstants.MOCK_BOOKINGS + "/1")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id",         equalTo(1))
                .body("totalprice", equalTo(175))
                .extract().response();

        MockBooking result = response.as(MockBooking.class);
        long responseTime  = response.getTime();

        assertEquals(result.getId(),         Integer.valueOf(1),   "Booking id must be 1");
        assertEquals(result.getTotalprice(), Integer.valueOf(175), "Price must reflect the update");
        assertTrue(result.getDepositpaid(),  "Depositpaid must still be true");

        // Verify WireMock received the PUT with the Authorization header present
        wireMockServer.verify(1,
                putRequestedFor(urlEqualTo(ApiConstants.MOCK_BOOKINGS + "/1"))
                        .withHeader("Authorization", matching("Bearer .+")));

        TestUtils.logResult("TC_WM_003", true, responseTime,
                "PUT update verified — id=1, new totalprice=" + result.getTotalprice());
    }

    // ── TC_WM_004 — DELETE booking ────────────────────────────────────────────

    /*
     * TC_WM_004 — DELETE Booking  [groups: full]
     *
     * WHAT it does:
     *   Sends DELETE /mock/bookings/1. The file-based stub (delete-booking.json) matches
     *   any DELETE to /mock/bookings/<number> and returns 204 No Content.
     *
     * WHY 204 and not 200?
     *   REST convention: successful DELETE returns 204 with no body. Returning a body
     *   would imply the resource still exists. The test asserts both the status code
     *   and that WireMock logged exactly one DELETE at the expected URL.
     *
     * HOW deletion is confirmed in a stateless mock:
     *   WireMock doesn't track state — a follow-up GET would still return 200 because
     *   the GET stub is still registered. Instead, we use WireMock's call-verification
     *   API (verify()) to assert the DELETE request reached the server with the right URL.
     *
     * Serialization:   NONE — DELETE carries no body.
     * Deserialization: NONE — 204 has no response body.
     *
     * Expected result:
     *   HTTP 204 No Content — WireMock confirms one DELETE request was received.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 4,
          groups = {"full"},
          description = "TC_WM_004 — WireMock: DELETE /mock/bookings/{id} returns 204 No Content")
    public void TC_WM_004_mockDeleteBooking() {
        log.info("TC_WM_004 — DELETE booking/1 on WireMock at {}", mockBaseUrl);

        given()
                .baseUri(mockBaseUrl)
                .accept(ContentType.JSON)
                .when()
                .delete(ApiConstants.MOCK_BOOKINGS + "/1")
                .then()
                .statusCode(204);

        // WHY verify() instead of a follow-up GET?
        //   WireMock is stateless — the GET stub always returns 200. Call verification
        //   is the correct way to assert the right request was made.
        wireMockServer.verify(1,
                deleteRequestedFor(urlMatching(ApiConstants.MOCK_BOOKINGS + "/[0-9]+")));

        log.info("[TC_WM_004] PASS | DELETE /mock/bookings/1 → 204 confirmed by WireMock");
    }
}
