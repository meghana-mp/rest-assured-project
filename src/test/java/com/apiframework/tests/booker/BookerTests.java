package com.apiframework.tests.booker;

import com.apiframework.constants.ApiConstants;
import com.apiframework.dataproviders.DataProviders;
import com.apiframework.models.booker.BookingDates;
import com.apiframework.models.booker.BookingRequest;
import com.apiframework.models.booker.BookingWrapper;
import com.apiframework.models.booker.TokenRequest;
import com.apiframework.models.booker.TokenResponse;
import com.apiframework.specs.BookerSpecs;
import com.apiframework.utils.TestUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.*;

/**
 * Restful-Booker API Tests — Basic Auth / Session Token
 *
 * TC_RB_001  Generate Token (POST /auth)
 * TC_RB_002  Create Booking & Schema Validation (POST /booking)
 * TC_RB_003  Retrieve Booking (GET /booking/{id})
 * TC_RB_004  Full Update — PUT (PUT /booking/{id} with cookie token)
 * TC_RB_005  Update Without Token (PUT /booking/{id} → 403 Forbidden)
 * TC_RB_006  Data-Driven Booking Creation (POST /booking × 3 guests)
 * TC_RB_007  Strict Schema Validation Failure (GET /booking/{id} — missing booking_reference)
 */
@Epic("REST Assured Framework")
@Feature("Restful-Booker API — Cookie Token Auth")
public class BookerTests {

    private static final Logger log = LoggerFactory.getLogger(BookerTests.class);

    // Shared state across test methods (populated in earlier tests)
    private String authToken;
    private Integer bookingId;
    private BookingRequest originalBooking;

    // ── Setup ───────────────────────────────────────────────────────────

    @BeforeClass
    public void setUp() {
        log.info("Restful-Booker test session starting…");
    }

    // ── TC_RB_001 — Generate Token ──────────────────────────────────────

    /*
     * TC_RB_001 — Generate Token
     *
     * What it does:
     *   Sends a POST request to /auth with valid credentials (username: admin, password: password123).
     *   The API responds with a session token string that must be passed as a Cookie on all
     *   subsequent write operations (PUT, PATCH, DELETE).
     *
     * Steps:
     *   1. Build a TokenRequest body with username and password from config.properties.
     *   2. POST to /auth using the base (unauthenticated) request spec.
     *   3. Assert the response status is 200 and the "token" field is present and non-blank.
     *   4. Deserialize the response into TokenResponse and store the token in the class-level
     *      variable `authToken` so TC_RB_004 can inject it as a Cookie.
     *
     * Expected result:
     *   HTTP 200 — response body contains a non-empty string token (e.g. "abc123def456").
     */
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 1,
          groups = {"smoke", "regression", "full"},
          description = "TC_RB_001 — Verify POST /auth with valid credentials returns a session token")
    public void TC_RB_001_generateToken() {
        String username = com.apiframework.config.ConfigManager.getInstance()
                .getProperty("booker.username", "admin");
        String password = com.apiframework.config.ConfigManager.getInstance()
                .getProperty("booker.password", "password123");

        TokenRequest body = TokenRequest.builder()
                .username(username)
                .password(password)
                .build();

        Response response = given()
                .spec(BookerSpecs.baseSpec())
                .body(body)
                .when()
                .post(ApiConstants.BOOKER_AUTH)
                .then()
                .spec(BookerSpecs.successSpec())
                .body("token", not(emptyOrNullString()))
                .extract().response();

        TokenResponse tokenResponse = response.as(TokenResponse.class);
        long responseTime = response.getTime();

        assertNotNull(tokenResponse.getToken(), "Token must not be null");
        assertFalse(tokenResponse.getToken().isBlank(), "Token must not be blank");

        authToken = tokenResponse.getToken();

        TestUtils.logResult("TC_RB_001", true, responseTime,
                "Token received (length=" + authToken.length() + ")");
    }

    // ── TC_RB_002 — Create Booking & Schema Validation ─────────────────

    /*
     * TC_RB_002 — Create Booking & Schema Validation
     *
     * What it does:
     *   Sends a POST request to /booking with a complete booking payload and validates that
     *   the response body matches the predefined JSON schema (schemas/booking-schema.json).
     *   This confirms the API returns the correct structure for every booking field.
     *
     * Steps:
     *   1. Build a BookingRequest with firstname, lastname, totalprice, depositpaid,
     *      checkin/checkout dates (5 and 10 days from today), and additionalneeds.
     *   2. POST to /booking using the base spec (no auth required for creation).
     *   3. Assert HTTP 200 and validate the full response against the JSON schema.
     *   4. Assert individual fields (bookingid > 0, firstname, totalprice, etc.) match the request.
     *   5. Deserialize into BookingWrapper and store `bookingId` for TC_RB_003, TC_RB_004, TC_RB_005.
     *
     * Expected result:
     *   HTTP 200 — response matches booking-schema.json; bookingid is a positive integer;
     *   all booking fields are echoed back correctly in the nested "booking" object.
     */
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 2,
          groups = {"smoke", "regression", "full"},
          dependsOnMethods = "TC_RB_001_generateToken",
          dataProvider = "bookingCreateData",
          dataProviderClass = DataProviders.class,
          description = "TC_RB_002 — Verify creating a booking matches the expected JSON schema")
    public void TC_RB_002_createBookingWithSchemaValidation(
            String firstname, String lastname, int totalprice, boolean depositpaid, String additionalneeds) {
        originalBooking = BookingRequest.builder()
                .firstname(firstname)
                .lastname(lastname)
                .totalprice(totalprice)
                .depositpaid(depositpaid)
                .bookingdates(BookingDates.builder()
                        .checkin(TestUtils.dateOffset(5))
                        .checkout(TestUtils.dateOffset(10))
                        .build())
                .additionalneeds(additionalneeds)
                .build();

        Response response = given()
                .spec(BookerSpecs.baseSpec())
                .body(originalBooking)
                .when()
                .post(ApiConstants.BOOKER_BOOKING)
                .then()
                .spec(BookerSpecs.successSpec())
                // ── JSON schema assertion ──────────────────────────────────
                .body(matchesJsonSchemaInClasspath(ApiConstants.SCHEMA_BOOKING))
                // ── Field-level assertions ────────────────────────────────
                .body("bookingid",                       notNullValue())
                .body("booking.firstname",               equalTo(firstname))
                .body("booking.lastname",                equalTo(lastname))
                .body("booking.totalprice",              equalTo(totalprice))
                .body("booking.depositpaid",             equalTo(depositpaid))
                .body("booking.bookingdates.checkin",    not(emptyOrNullString()))
                .body("booking.bookingdates.checkout",   not(emptyOrNullString()))
                .body("booking.additionalneeds",         equalTo(additionalneeds))
                .extract().response();

        BookingWrapper wrapper = response.as(BookingWrapper.class);
        long responseTime = response.getTime();

        assertNotNull(wrapper.getBookingid(), "bookingid must not be null");
        assertTrue(wrapper.getBookingid() > 0, "bookingid must be a positive integer");
        assertNotNull(wrapper.getBooking(),   "booking object must not be null");

        bookingId = wrapper.getBookingid();

        TestUtils.logResult("TC_RB_002", true, responseTime,
                "Schema validated — bookingid=" + bookingId);
    }

    // ── TC_RB_003 — Retrieve Booking ────────────────────────────────────

    /*
     * TC_RB_003 — Retrieve Booking
     *
     * What it does:
     *   Sends a GET request to /booking/{id} using the bookingId created in TC_RB_002 and
     *   verifies that every field in the response exactly matches what was sent during creation.
     *   This confirms the API persists and returns all booking data accurately.
     *
     * Steps:
     *   1. Use `bookingId` stored from TC_RB_002 as the path parameter.
     *   2. GET /booking/{id} using the base spec (no auth required for retrieval).
     *   3. Assert HTTP 200 and verify each response field equals the corresponding field
     *      in `originalBooking` (firstname, lastname, totalprice, depositpaid, dates, additionalneeds).
     *   4. Deserialize the flat response body into BookingRequest and run TestNG assertEquals checks.
     *
     * Expected result:
     *   HTTP 200 — all returned fields match the original booking payload exactly.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 3,
          groups = {"regression", "full"},
          dependsOnMethods = "TC_RB_002_createBookingWithSchemaValidation",
          description = "TC_RB_003 — Verify GET /booking/{id} fetches the exact booking details")
    public void TC_RB_003_retrieveBooking() {
        Response response = given()
                .spec(BookerSpecs.baseSpec())
                .pathParam("id", bookingId)
                .when()
                .get(ApiConstants.BOOKER_BOOKING_BY_ID)
                .then()
                .spec(BookerSpecs.successSpec())
                .body("firstname",               equalTo(originalBooking.getFirstname()))
                .body("lastname",                equalTo(originalBooking.getLastname()))
                .body("totalprice",              equalTo(originalBooking.getTotalprice()))
                .body("depositpaid",             equalTo(originalBooking.getDepositpaid()))
                .body("bookingdates.checkin",    equalTo(originalBooking.getBookingdates().getCheckin()))
                .body("bookingdates.checkout",   equalTo(originalBooking.getBookingdates().getCheckout()))
                .body("additionalneeds",         equalTo(originalBooking.getAdditionalneeds()))
                .extract().response();

        BookingRequest fetched = response.as(BookingRequest.class);
        long responseTime = response.getTime();

        assertEquals(fetched.getFirstname(),  originalBooking.getFirstname(),  "firstname must match");
        assertEquals(fetched.getLastname(),   originalBooking.getLastname(),   "lastname must match");
        assertEquals(fetched.getTotalprice(), originalBooking.getTotalprice(), "totalprice must match");
        assertEquals(fetched.getDepositpaid(),originalBooking.getDepositpaid(),"depositpaid must match");

        TestUtils.logResult("TC_RB_003", true, responseTime,
                "Retrieved booking id=" + bookingId + " — all fields match");
    }

    // ── TC_RB_004 — Full Update (PUT) ───────────────────────────────────

    /*
     * TC_RB_004 — Full Update (PUT)
     *
     * What it does:
     *   Sends a PUT request to /booking/{id} to replace all fields of an existing booking.
     *   The request is authenticated using the session token from TC_RB_001, passed as a
     *   Cookie header (Cookie: token=<value>). A full PUT replaces every field — unlike
     *   PATCH which only updates specific fields.
     *
     * Steps:
     *   1. Build a new BookingRequest with updated values: firstname "Jane", totalprice 399,
     *      depositpaid false, new check-in/checkout dates, and updated additionalneeds.
     *   2. PUT /booking/{id} using `authenticatedSpec(authToken)` which injects the Cookie.
     *   3. Assert HTTP 200 and verify all updated fields are reflected in the response body.
     *   4. Deserialize the response into BookingRequest and run TestNG assertions on changed fields.
     *
     * Expected result:
     *   HTTP 200 — response body contains the fully updated booking object with all new values.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 4,
          groups = {"regression", "full"},
          dependsOnMethods = {"TC_RB_001_generateToken", "TC_RB_002_createBookingWithSchemaValidation"},
          dataProvider = "bookingUpdateData",
          dataProviderClass = DataProviders.class,
          description = "TC_RB_004 — Verify PUT /booking/{id} updates all elements of an existing booking")
    public void TC_RB_004_fullUpdateBooking(
            String firstname, String lastname, int totalprice, boolean depositpaid, String additionalneeds) {
        BookingRequest updated = BookingRequest.builder()
                .firstname(firstname)
                .lastname(lastname)
                .totalprice(totalprice)
                .depositpaid(depositpaid)
                .bookingdates(BookingDates.builder()
                        .checkin(TestUtils.dateOffset(7))
                        .checkout(TestUtils.dateOffset(14))
                        .build())
                .additionalneeds(additionalneeds)
                .build();

        Response response = given()
                .spec(BookerSpecs.authenticatedSpec(authToken))
                .pathParam("id", bookingId)
                .body(updated)
                .when()
                .put(ApiConstants.BOOKER_BOOKING_BY_ID)
                .then()
                .spec(BookerSpecs.successSpec())
                .body("firstname",   equalTo(firstname))
                .body("lastname",    equalTo(lastname))
                .body("totalprice",  equalTo(totalprice))
                .body("depositpaid", equalTo(depositpaid))
                .extract().response();

        BookingRequest result = response.as(BookingRequest.class);
        long responseTime = response.getTime();

        assertEquals(result.getFirstname(),   firstname,                   "firstname must be updated");
        assertEquals(result.getTotalprice(),  Integer.valueOf(totalprice), "totalprice must be updated");
        assertEquals(result.getDepositpaid(), depositpaid,                 "depositpaid must match update value");

        TestUtils.logResult("TC_RB_004", true, responseTime,
                "Full PUT update applied to bookingid=" + bookingId);
    }

    // ── TC_RB_005 — Update Without Token (403 Forbidden) ────────────────

    /*
     * TC_RB_005 — Update Without Token (Negative Test)
     *
     * What it does:
     *   Attempts a PUT request to /booking/{id} without providing any authentication token or
     *   cookie. This is a negative/security test that confirms the API correctly rejects
     *   unauthenticated write operations with a 403 Forbidden response.
     *
     * Steps:
     *   1. Build a BookingRequest payload with arbitrary values (not intended to succeed).
     *   2. PUT /booking/{id} using `baseSpec()` which carries NO Cookie and NO Authorization header.
     *   3. Assert the response status is exactly 403 Forbidden using `forbiddenSpec()`.
     *   4. Log the result — no deserialization needed since the body content is irrelevant here.
     *
     * Expected result:
     *   HTTP 403 Forbidden — the API rejects the write attempt because no valid token or
     *   cookie was provided. The booking data remains unchanged on the server.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 5,
          groups = {"regression", "full"},
          dependsOnMethods = "TC_RB_002_createBookingWithSchemaValidation",
          dataProvider = "bookingNegativeData",
          dataProviderClass = DataProviders.class,
          description = "TC_RB_005 — Verify PUT without auth token/cookie returns 403 Forbidden")
    public void TC_RB_005_updateWithoutToken(
            String firstname, String lastname, int totalprice, boolean depositpaid) {
        BookingRequest payload = BookingRequest.builder()
                .firstname(firstname)
                .lastname(lastname)
                .totalprice(totalprice)
                .depositpaid(depositpaid)
                .bookingdates(BookingDates.builder()
                        .checkin(TestUtils.dateOffset(1))
                        .checkout(TestUtils.dateOffset(2))
                        .build())
                .build();

        // baseSpec carries NO token cookie — write must be rejected
        Response response = given()
                .spec(BookerSpecs.baseSpec())
                .pathParam("id", bookingId)
                .body(payload)
                .when()
                .put(ApiConstants.BOOKER_BOOKING_BY_ID)
                .then()
                .spec(BookerSpecs.forbiddenSpec())
                .extract().response();

        long responseTime = response.getTime();

        TestUtils.logResult("TC_RB_005", true, responseTime,
                "PUT without token correctly returned 403 for bookingid=" + bookingId);
    }

    // ── TC_RB_006 — Data-Driven Booking Creation ─────────────────────────

    /*
     * TC_RB_006 — Data-Driven Booking Creation  [DATA-DRIVEN — runs 3 times]
     *
     * What it does:
     *   Creates a new booking three times using three different guest datasets supplied
     *   by a DataProvider. Each iteration is fully independent — it creates its own
     *   booking, verifies all returned fields match what was sent, and does not share
     *   state with TC_RB_002 through TC_RB_005. This demonstrates the data-driven
     *   pattern for the same API flow executed with varied input data.
     *
     * Data-Driven:
     *   Powered by @DataProvider("bookingGuestData") in DataProviders.java.
     *   TestNG injects one row per iteration:
     *     Row 1 — Alice Johnson,   £150, deposit paid,     "Breakfast included"
     *     Row 2 — Bob Smith,       £320, no deposit,       "High floor room"
     *     Row 3 — Charlie Brown,   £500, deposit paid,     "Airport transfer, Late checkout"
     *   The method signature accepts all five columns as parameters.
     *
     * Steps:
     *   1. Receive firstname, lastname, totalprice, depositpaid, additionalneeds from DataProvider.
     *   2. Build a BookingRequest POJO with the injected values and dynamic check-in (+1 day)
     *      / check-out (+3 days) dates via TestUtils.dateOffset().
     *   3. POST /booking — RestAssured serializes BookingRequest to JSON automatically.
     *   4. Assert HTTP 200 via successSpec() and validate key fields match the request data.
     *   5. Deserialize the response into BookingWrapper and assert bookingid > 0.
     *   6. Verify each field in the nested booking object matches the DataProvider values.
     *
     * Serialization:   .body(booking) — Jackson serializes BookingRequest to JSON:
     *                  { "firstname": "Alice", "lastname": "Johnson", "totalprice": 150,
     *                    "depositpaid": true, "bookingdates": { "checkin": "...",
     *                    "checkout": "..." }, "additionalneeds": "Breakfast included" }
     *
     * Deserialization: response.as(BookingWrapper.class) — Jackson maps the response
     *                  { "bookingid": N, "booking": { ...all fields... } }
     *                  to BookingWrapper. The nested "booking" object is further
     *                  deserialized into a BookingRequest via the bookingdates field
     *                  which is itself a nested BookingDates POJO.
     *
     * Expected result:
     *   HTTP 200 for all 3 iterations — bookingid is a positive integer; all booking
     *   fields echoed back match the DataProvider input exactly.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 6,
          groups = {"regression", "full"},
          dataProvider = "bookingGuestData",
          dataProviderClass = DataProviders.class,
          description = "TC_RB_006 — Data-driven booking creation with multiple guest datasets")
    public void TC_RB_006_createBookingDataDriven(String firstname, String lastname,
                                                   int totalprice, boolean depositpaid,
                                                   String additionalneeds) {
        log.info("TC_RB_006 — guest: {} {}, price: {}", firstname, lastname, totalprice);

        // ── Serialization: BookingRequest POJO → JSON request body ────────
        BookingRequest booking = BookingRequest.builder()
                .firstname(firstname)
                .lastname(lastname)
                .totalprice(totalprice)
                .depositpaid(depositpaid)
                .bookingdates(BookingDates.builder()
                        .checkin(TestUtils.dateOffset(1))
                        .checkout(TestUtils.dateOffset(3))
                        .build())
                .additionalneeds(additionalneeds)
                .build();

        Response response = given()
                .spec(BookerSpecs.baseSpec())
                .body(booking)
                .when()
                .post(ApiConstants.BOOKER_BOOKING)
                .then()
                .spec(BookerSpecs.successSpec())
                .body("bookingid",               notNullValue())
                .body("booking.firstname",        equalTo(firstname))
                .body("booking.lastname",         equalTo(lastname))
                .body("booking.totalprice",       equalTo(totalprice))
                .body("booking.depositpaid",      equalTo(depositpaid))
                .body("booking.additionalneeds",  equalTo(additionalneeds))
                .extract().response();

        // ── Deserialization: JSON → BookingWrapper (contains nested BookingRequest) ──
        BookingWrapper wrapper = response.as(BookingWrapper.class);
        long responseTime = response.getTime();

        assertNotNull(wrapper.getBookingid(), "bookingid must not be null");
        assertTrue(wrapper.getBookingid() > 0, "bookingid must be a positive integer");
        assertEquals(wrapper.getBooking().getFirstname(),  firstname,  "firstname must match");
        assertEquals(wrapper.getBooking().getLastname(),   lastname,   "lastname must match");
        assertEquals(wrapper.getBooking().getTotalprice(), Integer.valueOf(totalprice), "totalprice must match");

        TestUtils.logResult("TC_RB_006", true, responseTime,
                "guest=" + firstname + " " + lastname
                        + ", bookingid=" + wrapper.getBookingid()
                        + ", price=" + totalprice);
    }

    // ── TC_RB_007 — Strict Schema Validation Failure ─────────────────────

    /*
     * TC_RB_007 — Strict Schema Validation Failure  [groups: regression, full]
     *
     * What it does:
     *   Fetches the booking created in TC_RB_002 and validates the response against
     *   booking-strict-negative.json — a schema that requires a 'booking_reference'
     *   field that the Booker API never returns. The schema validator detects the
     *   missing required field and fails the test.
     *
     * Why this test exists:
     *   Demonstrates what a JSON Schema validation failure looks like in the Allure
     *   report. The actual response body and a diagnostic note are attached as Allure
     *   artifacts before the failing assertion so the cause is immediately visible.
     *
     * Expected result:
     *   FAIL — schema validation error: required key [booking_reference] not found.
     *   Allure report shows: Actual Response Body + Failure Note attachments.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 7,
          groups = {"regression", "full"},
          dependsOnMethods = "TC_RB_002_createBookingWithSchemaValidation",
          description = "TC_RB_007 — GET /booking/{id}: validates strict schema requiring booking_reference field")
    public void TC_RB_007_strictSchemaValidationFailure() {
        log.info("TC_RB_007 — GET booking/{} validating against strict schema (expects booking_reference)", bookingId);

        Response response = given()
                .spec(BookerSpecs.baseSpec())
                .pathParam("id", bookingId)
                .when()
                .get(ApiConstants.BOOKER_BOOKING_BY_ID)
                .then()
                .statusCode(200)
                .extract().response();

        Allure.addAttachment("Actual Response Body", "application/json",
                new ByteArrayInputStream(response.getBody().asString().getBytes(StandardCharsets.UTF_8)), "json");
        Allure.addAttachment("Failure Note", "text/plain",
                new ByteArrayInputStream(("Missing required field: 'booking_reference'\n"
                        + "Schema: " + ApiConstants.SCHEMA_BOOKING_STRICT + "\n"
                        + "Received: " + response.getBody().asString())
                        .getBytes(StandardCharsets.UTF_8)), "txt");

        // FAILS: schema requires 'booking_reference'; Booker API never returns this field
        response.then().body(matchesJsonSchemaInClasspath(ApiConstants.SCHEMA_BOOKING_STRICT));
    }
}
