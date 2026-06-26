package com.apiframework.constants;

public final class ApiConstants {

    private ApiConstants() {}

    // ── GitHub API endpoints ────────────────────────────────────────────
    public static final String GITHUB_USER            = "/user";
    public static final String GITHUB_USER_REPOS      = "/user/repos";
    public static final String GITHUB_REPO_BY_OWNER   = "/repos/{owner}/{repo}";

    // ── Restful-Booker endpoints ────────────────────────────────────────
    public static final String BOOKER_AUTH            = "/auth";
    public static final String BOOKER_BOOKING         = "/booking";
    public static final String BOOKER_BOOKING_BY_ID   = "/booking/{id}";

    // ── Platzi Fake Store endpoints ─────────────────────────────────────
    public static final String PLATZI_LOGIN           = "/auth/login";
    public static final String PLATZI_PROFILE         = "/auth/profile";
    public static final String PLATZI_REFRESH_TOKEN   = "/auth/refresh-token";
    public static final String PLATZI_PRODUCTS        = "/products";

    // ── JSON Schema classpath locations ────────────────────────────────
    public static final String SCHEMA_BOOKING           = "schemas/booking-schema.json";
    public static final String SCHEMA_PRODUCT           = "schemas/product-schema.json";
    public static final String SCHEMA_GITHUB_USER       = "schemas/github-user-schema.json";
    public static final String SCHEMA_GITHUB_REPO       = "schemas/github-repo-schema.json";
    public static final String SCHEMA_GITHUB_REPOS_LIST  = "schemas/github-repos-array-schema.json";
    public static final String SCHEMA_BOOKING_STRICT     = "schemas/booking-strict-negative.json";

    // ── HTTP Headers ────────────────────────────────────────────────────
    public static final String HEADER_AUTHORIZATION   = "Authorization";
    public static final String HEADER_CONTENT_TYPE    = "Content-Type";
    public static final String HEADER_ACCEPT          = "Accept";
    public static final String BEARER_PREFIX          = "Bearer ";
    public static final String GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version";
    public static final String GITHUB_API_VERSION     = "2022-11-28";
    public static final String GITHUB_ACCEPT_HEADER   = "application/vnd.github+json";
    public static final String COOKIE_TOKEN           = "token";

    // ── Default response-time threshold (ms) ───────────────────────────
    public static final long DEFAULT_RESPONSE_TIME_MS = 5000L;

    // ── WireMock mock-server endpoints ─────────────────────────────────
    // These paths are served by an in-process WireMock server during tests.
    // They intentionally mirror a booking-style CRUD API so that WireMockTests
    // can demonstrate GET / POST / PUT / DELETE without hitting the live Booker API.
    public static final String MOCK_BOOKINGS      = "/mock/bookings";
    public static final String MOCK_BOOKING_BY_ID = "/mock/bookings/{id}";
}
