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
 * Centralised RequestSpecification and ResponseSpecification factories
 * for the GitHub REST API (v3) authenticated via OAuth 2.0 Bearer token.
 */
public final class GitHubSpecs {

    private static final ConfigManager CFG = ConfigManager.getInstance();

    private GitHubSpecs() {}

    // ── Request Specifications ──────────────────────────────────────────

    /** Full auth spec — Bearer token + required GitHub media-type headers. */
    public static RequestSpecification authorizedSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(CFG.getProperty("github.base.url"))
                .addHeader(ApiConstants.HEADER_AUTHORIZATION,
                        ApiConstants.BEARER_PREFIX + CFG.getProperty("github.token"))
                .addHeader(ApiConstants.HEADER_ACCEPT, ApiConstants.GITHUB_ACCEPT_HEADER)
                .addHeader(ApiConstants.GITHUB_API_VERSION_HEADER, ApiConstants.GITHUB_API_VERSION)
                .setContentType(ContentType.JSON)
                .log(LogDetail.ALL)
                .build();
    }

    /**
     * Spec that accepts a caller-supplied token — used by data-driven tests
     * to inject different invalid token strings per test iteration.
     */
    public static RequestSpecification withTokenSpec(String token) {
        return new RequestSpecBuilder()
                .setBaseUri(CFG.getProperty("github.base.url"))
                .addHeader(ApiConstants.HEADER_AUTHORIZATION, ApiConstants.BEARER_PREFIX + token)
                .addHeader(ApiConstants.HEADER_ACCEPT, ApiConstants.GITHUB_ACCEPT_HEADER)
                .addHeader(ApiConstants.GITHUB_API_VERSION_HEADER, ApiConstants.GITHUB_API_VERSION)
                .setContentType(ContentType.JSON)
                .log(LogDetail.ALL)
                .build();
    }

    // ── Response Specifications ─────────────────────────────────────────

    /** Standard 200 OK — validates status, content-type, and response time. */
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

    /** 201 Created — for repository creation. */
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

    /** 401 Unauthorized — invalid/missing token. */
    public static ResponseSpecification unauthorizedSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(401)
                .expectContentType(ContentType.JSON)
                .log(LogDetail.ALL)
                .build();
    }

    /** 204 No Content — successful DELETE. */
    public static ResponseSpecification noContentSpec() {
        return new ResponseSpecBuilder()
                .expectStatusCode(204)
                .log(LogDetail.ALL)
                .build();
    }
}
