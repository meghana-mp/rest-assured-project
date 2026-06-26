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

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

/**
 * RequestSpecification and ResponseSpecification factories for the
 * Platzi Fake Store API, which uses JWT Bearer token authentication.
 */
public final class PlatziSpecs {

    private static final ConfigManager CFG = ConfigManager.getInstance();

    private PlatziSpecs() {}

    // ── Request Specifications ──────────────────────────────────────────

    /** Unauthenticated base spec — used for login and negative tests. */
    public static RequestSpecification baseSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(CFG.getProperty("platzi.base.url"))
                .setContentType(ContentType.JSON)
                .addHeader(ApiConstants.HEADER_ACCEPT, ContentType.JSON.toString())
                .log(LogDetail.ALL)
                .build();
    }

    /** Authenticated spec — attaches a JWT access token as a Bearer header. */
    public static RequestSpecification authorizedSpec(String accessToken) {
        return new RequestSpecBuilder()
                .setBaseUri(CFG.getProperty("platzi.base.url"))
                .setContentType(ContentType.JSON)
                .addHeader(ApiConstants.HEADER_ACCEPT, ContentType.JSON.toString())
                .addHeader(ApiConstants.HEADER_AUTHORIZATION,
                        ApiConstants.BEARER_PREFIX + accessToken)
                .log(LogDetail.ALL)
                .build();
    }

    // ── Response Specifications ─────────────────────────────────────────

    /** 200 OK with response-time guard. */
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

    /** 201 Created — for resource creation endpoints. */
    public static ResponseSpecification createdSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(201)
                .expectContentType(ContentType.JSON)
                .expectResponseTime(Matchers.lessThan(
                        CFG.getLongProperty("response.time.threshold.ms", ApiConstants.DEFAULT_RESPONSE_TIME_MS)),
                        TimeUnit.MILLISECONDS)
                .log(LogDetail.ALL)
                .build();
    }

    /**
     * Accepts 200 OR 201 — used for auth POST endpoints (login, refresh-token)
     * where the Platzi API inconsistently returns 201 instead of 200.
     */
    public static ResponseSpecification authResponseSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(anyOf(equalTo(200), equalTo(201)))
                .expectContentType(ContentType.JSON)
                .expectResponseTime(Matchers.lessThan(
                        CFG.getLongProperty("response.time.threshold.ms", ApiConstants.DEFAULT_RESPONSE_TIME_MS)),
                        TimeUnit.MILLISECONDS)
                .log(LogDetail.ALL)
                .build();
    }

    /** 401 Unauthorized — missing or expired JWT token. */
    public static ResponseSpecification unauthorizedSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(401)
                .log(LogDetail.ALL)
                .build();
    }
}
