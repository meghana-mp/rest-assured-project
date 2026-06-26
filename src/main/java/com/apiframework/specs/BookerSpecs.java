package com.apiframework.specs;

import com.apiframework.config.ConfigManager;
import com.apiframework.constants.ApiConstants;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.hamcrest.Matchers;

import java.util.concurrent.TimeUnit;

/**
 * RequestSpecification and ResponseSpecification factories for the
 * Restful-Booker API. Authentication uses a session token obtained via
 * POST /auth, passed as a Cookie on subsequent write requests.
 */
public final class BookerSpecs {

    private static final ConfigManager CFG = ConfigManager.getInstance();

    private BookerSpecs() {}

    // ── Request Specifications ──────────────────────────────────────────

    /** Base spec — no authentication headers. Used for GET and POST /auth. */
    public static RequestSpecification baseSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(CFG.getProperty("booker.base.url"))
                .setContentType(ContentType.JSON)
                .addHeader(ApiConstants.HEADER_ACCEPT, "application/json")
                .log(LogDetail.ALL)
                .build();
    }

    /**
     * Authenticated spec — appends the session token as a Cookie.
     * Required for PUT, PATCH, DELETE /booking/{id}.
     */
    public static RequestSpecification authenticatedSpec(String token) {
        return new RequestSpecBuilder()
                .setBaseUri(CFG.getProperty("booker.base.url"))
                .setContentType(ContentType.JSON)
                .addHeader(ApiConstants.HEADER_ACCEPT, "application/json")
                .addCookie(ApiConstants.COOKIE_TOKEN, token)
                .log(LogDetail.ALL)
                .build();
    }

    // ── Response Specifications ─────────────────────────────────────────

    /** 200 OK with JSON body and response-time guard. */
    public static ResponseSpecification successSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(200)
                .expectContentType(ContentType.JSON)
                .expectResponseTime(Matchers.lessThan(
                        CFG.getLongProperty("response.time.threshold.ms", ApiConstants.DEFAULT_RESPONSE_TIME_MS)),
                        TimeUnit.MILLISECONDS)
                .log(LogDetail.ALL)
                .build();
    }

    /** 403 Forbidden — write attempt without a valid token/cookie. */
    public static ResponseSpecification forbiddenSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(403)
                .log(LogDetail.ALL)
                .build();
    }
}
