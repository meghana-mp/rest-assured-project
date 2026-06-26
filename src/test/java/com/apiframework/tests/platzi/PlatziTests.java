package com.apiframework.tests.platzi;

import com.apiframework.constants.ApiConstants;
import com.apiframework.dataproviders.DataProviders;
import com.apiframework.models.platzi.CreateProductRequest;
import com.apiframework.models.platzi.LoginRequest;
import com.apiframework.models.platzi.LoginResponse;
import com.apiframework.models.platzi.PlatziUser;
import com.apiframework.models.platzi.Product;
import com.apiframework.models.platzi.RefreshTokenRequest;
import com.apiframework.specs.PlatziSpecs;
import com.apiframework.utils.TestUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.*;

/**
 * Platzi Fake Store API Tests — JWT Bearer Token Authentication
 *
 * TC_PF_001  User Login & Token    — serialization of LoginRequest + deserialization of LoginResponse
 * TC_PF_002  Get Profile           — deserialization of PlatziUser
 * TC_PF_003  Profile Without Token — DATA-DRIVEN (3 invalid/missing auth scenarios → 401)
 * TC_PF_004  Create Product        — serialization of CreateProductRequest + schema validation
 * TC_PF_005  Refresh Token         — serialization of RefreshTokenRequest + JWT structure check
 * TC_PF_006  Negative Price Rejected — POST /products price=-50, asserts 422 Unprocessable Entity
 */
@Epic("REST Assured Framework")
@Feature("Platzi Fake Store API — JWT Bearer Token")
public class PlatziTests {

    private static final Logger log = LoggerFactory.getLogger(PlatziTests.class);

    // Shared JWT state populated by TC_PF_001 and consumed by TC_PF_002, TC_PF_004, TC_PF_005
    private String accessToken;
    private String refreshToken;

    // ── Setup ───────────────────────────────────────────────────────────────

    @BeforeClass
    public void setUp() {
        log.info("Platzi Fake Store test session starting…");
    }

    // ── TC_PF_001 ───────────────────────────────────────────────────────────

    /*
     * TC_PF_001 — User Login & Token
     *
     * What it does:
     *   Sends a POST request to /auth/login with valid email and password credentials.
     *   The API returns a JWT access_token and a refresh_token. Both tokens are stored
     *   as instance variables and reused by TC_PF_002, TC_PF_004, and TC_PF_005.
     *
     * Steps:
     *   1. Build a LoginRequest POJO (email, password) using Lombok @Builder.
     *      Values are read from config.properties (platzi.email / platzi.password).
     *   2. POST /auth/login — RestAssured serializes LoginRequest to JSON automatically.
     *   3. Assert HTTP 200 or 201 (Platzi returns 201 on this endpoint) via authResponseSpec().
     *   4. Assert "access_token" and "refresh_token" fields are present and non-blank.
     *   5. Deserialize the response into a LoginResponse POJO and store both tokens.
     *
     * Serialization:   .body(body) triggers Jackson to convert LoginRequest →
     *                  { "email": "john@mail.com", "password": "changeme" }
     *
     * Deserialization: response.as(LoginResponse.class) — Jackson maps the JSON response
     *                  { "access_token": "eyJ...", "refresh_token": "eyJ..." } to the
     *                  LoginResponse POJO. @JsonProperty("access_token") and
     *                  @JsonProperty("refresh_token") handle the snake_case → camelCase mapping.
     *
     * Expected result:
     *   HTTP 200/201 — both access_token and refresh_token are non-blank JWT strings.
     */
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 1,
          groups = {"smoke", "regression", "full"},
          description = "TC_PF_001 — Verify login with valid credentials retrieves access/refresh tokens")
    public void TC_PF_001_userLoginAndToken() {
        com.apiframework.config.ConfigManager cfg =
                com.apiframework.config.ConfigManager.getInstance();

        // ── Serialization: LoginRequest POJO → JSON request body ─────────
        LoginRequest body = LoginRequest.builder()
                .email(cfg.getProperty("platzi.email",    "john@mail.com"))
                .password(cfg.getProperty("platzi.password", "changeme"))
                .build();

        Response response = given()
                .spec(PlatziSpecs.baseSpec())
                .body(body)
                .when()
                .post(ApiConstants.PLATZI_LOGIN)
                .then()
                .spec(PlatziSpecs.authResponseSpec())
                .body("access_token",  not(emptyOrNullString()))
                .body("refresh_token", not(emptyOrNullString()))
                .extract().response();

        // ── Deserialization: JSON → LoginResponse POJO ───────────────────
        LoginResponse loginResponse = response.as(LoginResponse.class);
        long responseTime = response.getTime();

        assertNotNull(loginResponse.getAccessToken(),  "access_token must not be null");
        assertNotNull(loginResponse.getRefreshToken(), "refresh_token must not be null");
        assertFalse(loginResponse.getAccessToken().isBlank(),  "access_token must not be blank");
        assertFalse(loginResponse.getRefreshToken().isBlank(), "refresh_token must not be blank");

        accessToken  = loginResponse.getAccessToken();
        refreshToken = loginResponse.getRefreshToken();

        TestUtils.logResult("TC_PF_001", true, responseTime,
                "access_token length=" + accessToken.length()
                        + ", refresh_token length=" + refreshToken.length());
    }

    // ── TC_PF_002 ───────────────────────────────────────────────────────────

    /*
     * TC_PF_002 — Get Profile
     *
     * What it does:
     *   Sends an authenticated GET request to /auth/profile using the JWT access_token
     *   obtained from TC_PF_001. Verifies that the API returns the logged-in user's
     *   profile entity with all expected identity fields.
     *
     * Steps:
     *   1. Use accessToken (stored from TC_PF_001) to build authorizedSpec() which
     *      injects: Authorization: Bearer <accessToken>
     *   2. GET /auth/profile — no request body needed.
     *   3. Assert HTTP 200 via successSpec() and verify id, email, name, role fields.
     *   4. Deserialize the response into a PlatziUser POJO and run TestNG assertions.
     *
     * Serialization:   NONE — GET request carries no body.
     * Deserialization: response.as(PlatziUser.class) — Jackson maps the JSON response
     *                  { "id": 1, "name": "Jhon", "email": "john@mail.com",
     *                    "role": "customer", "avatar": "https://..." }
     *                  to the PlatziUser POJO. All field names match directly
     *                  (no @JsonProperty needed), so Jackson uses default mapping.
     *
     * Dependency: dependsOnMethods = "TC_PF_001_userLoginAndToken" — requires a valid
     *             accessToken to be populated before this test runs.
     *
     * Expected result:
     *   HTTP 200 — PlatziUser with non-null id, non-blank email, name, and role.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 2,
          groups = {"regression", "full"},
          dependsOnMethods = "TC_PF_001_userLoginAndToken",
          description = "TC_PF_002 — Verify GET /auth/profile works when passing a valid JWT")
    public void TC_PF_002_getProfile() {
        Response response = given()
                .spec(PlatziSpecs.authorizedSpec(accessToken))
                .when()
                .get(ApiConstants.PLATZI_PROFILE)
                .then()
                .spec(PlatziSpecs.successSpec())
                .body("id",    notNullValue())
                .body("email", not(emptyOrNullString()))
                .body("name",  not(emptyOrNullString()))
                .body("role",  not(emptyOrNullString()))
                .extract().response();

        // ── Deserialization: JSON → PlatziUser POJO ──────────────────────
        PlatziUser user = response.as(PlatziUser.class);
        long responseTime = response.getTime();

        assertNotNull(user.getId(),    "User id must not be null");
        assertNotNull(user.getEmail(), "User email must not be null");
        assertNotNull(user.getRole(),  "User role must not be null");

        TestUtils.logResult("TC_PF_002", true, responseTime,
                "Profile: id=" + user.getId() + ", email=" + user.getEmail()
                        + ", role=" + user.getRole());
    }

    // ── TC_PF_003 ───────────────────────────────────────────────────────────

    /*
     * TC_PF_003 — Profile Without Token / Invalid Auth  [DATA-DRIVEN — runs 3 times]
     *
     * What it does:
     *   Verifies that the protected /auth/profile endpoint correctly rejects requests
     *   that carry no token or an invalid token, returning 401 Unauthorized in all cases.
     *   This is a negative/security test that runs three times with different invalid
     *   authentication scenarios.
     *
     * Data-Driven:
     *   Powered by @DataProvider("invalidPlatziTokens") in DataProviders.java.
     *   TestNG injects one row per iteration:
     *     Row 1 — null token → uses baseSpec() (no Authorization header sent at all)
     *     Row 2 — plain invalid string → uses authorizedSpec("random_invalid_string_token_xyz")
     *     Row 3 — malformed JWT  → uses authorizedSpec("eyJhbGciOiJIUzI1NiJ9.fake.bad_sig")
     *   All three scenarios must return HTTP 401.
     *
     * Steps:
     *   1. Receive invalidToken (String or null) and scenario label from DataProvider.
     *   2. Select the request spec:
     *        - null token   → PlatziSpecs.baseSpec()         (no Authorization header)
     *        - non-null     → PlatziSpecs.authorizedSpec(invalidToken) (invalid Bearer header)
     *   3. GET /auth/profile using the chosen spec.
     *   4. Assert HTTP 401 via unauthorizedSpec() for all 3 rows.
     *
     * Serialization:   NONE — GET request carries no body.
     * Deserialization: NONE — only the HTTP status code is asserted; the error response
     *                  body is not deserialized into a POJO.
     *
     * Expected result:
     *   HTTP 401 Unauthorized for all 3 token scenarios.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 3,
          groups = {"regression", "full"},
          dataProvider = "invalidPlatziTokens",
          dataProviderClass = DataProviders.class,
          description = "TC_PF_003 — Verify /auth/profile rejects missing/invalid tokens with 401")
    public void TC_PF_003_profileWithInvalidOrMissingAuth(String invalidToken, String scenario) {
        log.info("TC_PF_003 scenario: {}", scenario);

        // null  → no Authorization header; non-null → invalid Bearer value
        RequestSpecification reqSpec = (invalidToken == null)
                ? PlatziSpecs.baseSpec()
                : PlatziSpecs.authorizedSpec(invalidToken);

        Response response = given()
                .spec(reqSpec)
                .when()
                .get(ApiConstants.PLATZI_PROFILE)
                .then()
                .spec(PlatziSpecs.unauthorizedSpec())
                .extract().response();

        long responseTime = response.getTime();

        TestUtils.logResult("TC_PF_003", true, responseTime,
                "scenario=[" + scenario + "] correctly rejected with status="
                        + response.getStatusCode());
    }

    // ── TC_PF_004 ───────────────────────────────────────────────────────────

    /*
     * TC_PF_004 — Create Product & JSON Schema Validation
     *
     * What it does:
     *   Sends an authenticated POST request to /products with a CreateProductRequest body.
     *   The response is validated against the JSON schema (schemas/product-schema.json)
     *   which enforces that the price field is a non-negative number and the images field
     *   is an array containing at least one item.
     *
     * Steps:
     *   1. Build a CreateProductRequest POJO using Lombok @Builder with a unique title
     *      (using TestUtils.randomId() to avoid duplicates), price=149, description,
     *      categoryId=1, and a single image URL.
     *   2. POST /products using authorizedSpec(accessToken) — JWT token in Bearer header.
     *   3. Assert HTTP 200 or 201, validate against product-schema.json, and check:
     *        - price ≥ 0 (pricing data validation)
     *        - images.size() ≥ 1 (images array validation)
     *   4. Deserialize the response into a Product POJO and run TestNG assertions.
     *
     * Serialization:   .body(body) — Jackson serializes CreateProductRequest to JSON:
     *                  { "title": "...", "price": 149, "description": "...",
     *                    "categoryId": 1, "images": ["https://picsum.photos/200/300"] }
     *
     * Deserialization: response.as(Product.class) — Jackson maps the response JSON
     *                  { "id": 81, "title": "...", "price": 149, "images": [...],
     *                    "category": { "id": 1, "name": "...", "image": "..." } }
     *                  to the Product POJO. The nested "category" object is mapped to
     *                  Map<String, Object> to avoid coupling to a separate Category class.
     *
     * Dependency: dependsOnMethods = "TC_PF_001_userLoginAndToken" — requires accessToken.
     *
     * Expected result:
     *   HTTP 200/201 — response matches product-schema.json; price ≥ 0; images has ≥ 1 item.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 4,
          groups = {"regression", "full"},
          dependsOnMethods = "TC_PF_001_userLoginAndToken",
          dataProvider = "productCreateData",
          dataProviderClass = DataProviders.class,
          description = "TC_PF_004 — Verify POST /products validates pricing data and images array via JSON schema")
    public void TC_PF_004_createProductWithSchemaValidation(
            int price, int categoryId, String imageUrl, String description) {
        // ── Serialization: CreateProductRequest POJO → JSON request body ──
        CreateProductRequest body = CreateProductRequest.builder()
                .title("REST Assured Test Product — " + TestUtils.randomId())
                .price(price)
                .description(description)
                .categoryId(categoryId)
                .images(List.of(imageUrl))
                .build();

        Response response = given()
                .spec(PlatziSpecs.authorizedSpec(accessToken))
                .body(body)
                .when()
                .post(ApiConstants.PLATZI_PRODUCTS)
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(201)))
                // ── JSON schema assertion ─────────────────────────────────
                .body(matchesJsonSchemaInClasspath(ApiConstants.SCHEMA_PRODUCT))
                // ── Pricing data validation ───────────────────────────────
                .body("price", greaterThanOrEqualTo(0))
                .body("title", not(emptyOrNullString()))
                // ── Images array validation ───────────────────────────────
                .body("images",        notNullValue())
                .body("images.size()", greaterThanOrEqualTo(1))
                .extract().response();

        // ── Deserialization: JSON → Product POJO ─────────────────────────
        Product product = response.as(Product.class);
        long responseTime = response.getTime();

        assertNotNull(product.getId(),     "Product id must not be null");
        assertNotNull(product.getTitle(),  "Product title must not be null");
        assertTrue(product.getPrice() >= 0, "Price must be non-negative");
        assertNotNull(product.getImages(), "Images array must not be null");
        assertFalse(product.getImages().isEmpty(), "Images array must contain at least one entry");

        TestUtils.logResult("TC_PF_004", true, responseTime,
                "Product created — id=" + product.getId()
                        + ", price=" + product.getPrice()
                        + ", images=" + product.getImages().size());
    }

    // ── TC_PF_005 ───────────────────────────────────────────────────────────

    /*
     * TC_PF_005 — Refresh Token
     *
     * What it does:
     *   Sends a POST request to /auth/refresh-token with the refresh_token obtained from
     *   TC_PF_001. Simulates the real-world scenario where an access_token has expired
     *   and the client needs a new one without re-authenticating with email/password.
     *   The new token is validated to be a properly structured JWT (3 dot-separated segments).
     *
     * Steps:
     *   1. Build a RefreshTokenRequest POJO using Lombok @Builder with the stored refreshToken.
     *   2. POST /auth/refresh-token — no Authorization header needed (uses baseSpec).
     *   3. Assert HTTP 200 or 201 via authResponseSpec() and that "access_token" is non-blank.
     *   4. Extract the new access_token via jsonPath (partial deserialization).
     *   5. Split the token by "." and assert exactly 3 segments — proving it is a valid JWT
     *      (header.payload.signature structure).
     *
     * Serialization:   .body(body) — Jackson serializes RefreshTokenRequest to JSON:
     *                  { "refreshToken": "eyJ..." }
     *
     * Deserialization: response.jsonPath().getString("access_token") — targeted partial
     *                  extraction; only the access_token field is read from the JSON response
     *                  without mapping the full body to a POJO. This is appropriate when
     *                  only a single field is needed.
     *
     * Dependency: dependsOnMethods = "TC_PF_001_userLoginAndToken" — requires refreshToken.
     *
     * Expected result:
     *   HTTP 200/201 — new access_token is a non-blank string with exactly 3 JWT segments.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 5,
          groups = {"regression", "full"},
          dependsOnMethods = "TC_PF_001_userLoginAndToken",
          description = "TC_PF_005 — Verify an expired access token can be renewed using the refresh token")
    public void TC_PF_005_refreshToken() {
        // ── Serialization: RefreshTokenRequest POJO → JSON request body ──
        RefreshTokenRequest body = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        Response response = given()
                .spec(PlatziSpecs.baseSpec())
                .body(body)
                .when()
                .post(ApiConstants.PLATZI_REFRESH_TOKEN)
                .then()
                .spec(PlatziSpecs.authResponseSpec())
                .body("access_token", not(emptyOrNullString()))
                .extract().response();

        // ── Deserialization: targeted JSONPath extraction (not full POJO) ─
        String newAccessToken = response.jsonPath().getString("access_token");
        long responseTime = response.getTime();

        assertNotNull(newAccessToken, "New access_token must not be null");
        assertFalse(newAccessToken.isBlank(), "New access_token must not be blank");

        // Validate JWT structure: header.payload.signature → exactly 3 segments
        String[] segments = newAccessToken.split("\\.");
        assertEquals(segments.length, 3,
                "access_token must be a valid JWT with three Base64-encoded segments");

        TestUtils.logResult("TC_PF_005", true, responseTime,
                "Token refreshed — new access_token length=" + newAccessToken.length());
    }

    // ── TC_PF_006 — Negative Price Should Be Rejected ──────────────────────

    /*
     * TC_PF_006 — Negative Price Should Be Rejected  [groups: regression, full]
     *
     * What it does:
     *   Posts a product with price = -50 and asserts the API returns 422 Unprocessable
     *   Entity. Platzi Fake Store does not enforce the non-negative price constraint and
     *   accepts the invalid data, returning 200 or 201. The status code assertion fails.
     *
     * Why this test exists:
     *   Demonstrates a business rule gap — APIs should reject semantically invalid input
     *   even when it is syntactically valid JSON. The test captures the actual response
     *   body and a diagnostic note as Allure attachments so the gap is documented in the
     *   report alongside the failure message.
     *
     * Expected result:
     *   FAIL — assertEquals: expected [422] but found [200] or [201].
     *   Allure report shows: Actual Response Body + Failure Note attachments.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 6,
          groups = {"regression", "full"},
          dependsOnMethods = "TC_PF_001_userLoginAndToken",
          description = "TC_PF_006 — POST /products with price=-50 validates API enforces non-negative price constraint")
    public void TC_PF_006_negativeProductPriceShouldBeRejected() {
        log.info("TC_PF_006 — POST /products with invalid negative price (-50)");

        CreateProductRequest invalidProduct = CreateProductRequest.builder()
                .title("Test Product " + TestUtils.randomId())
                .price(-50)
                .description("Product with invalid negative price — API should reject with 422")
                .categoryId(1)
                .images(List.of("https://picsum.photos/200/300"))
                .build();

        Response response = given()
                .spec(PlatziSpecs.authorizedSpec(accessToken))
                .body(invalidProduct)
                .when()
                .post(ApiConstants.PLATZI_PRODUCTS)
                .then()
                .extract().response();

        Allure.addAttachment("Actual Response Body", "application/json",
                new ByteArrayInputStream(response.getBody().asString()
                        .getBytes(StandardCharsets.UTF_8)), "json");
        Allure.addAttachment("Failure Note", "text/plain",
                new ByteArrayInputStream(("Sent price: -50 (invalid negative value)\n"
                        + "Received status: " + response.getStatusCode() + "\n"
                        + "Expected status: 422 Unprocessable Entity\n"
                        + "Note: 422 is the semantically correct status for invalid business data.\n"
                        + "Platzi returns 400 Bad Request — a generic client error — instead of\n"
                        + "422 Unprocessable Entity which specifically signals a validation failure.")
                        .getBytes(StandardCharsets.UTF_8)), "txt");

        // FAILS: Platzi accepts negative prices — returns 200/201 instead of 422
        assertEquals(response.getStatusCode(), 422,
                "API must reject negative price with 422 Unprocessable Entity, but got: "
                        + response.getStatusCode());
    }
}
