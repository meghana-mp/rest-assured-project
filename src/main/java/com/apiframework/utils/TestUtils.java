package com.apiframework.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class TestUtils {

    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TestUtils() {}

    /**
     * Returns a unique, timestamp-suffixed string safe for use as a
     * repository or resource name in test runs.
     */
    public static String uniqueName(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }

    /** Returns a random UUID string without hyphens. */
    public static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns a date string (yyyy-MM-dd) offset by {@code daysFromToday}.
     * Positive values → future, negative → past.
     */
    public static String dateOffset(int daysFromToday) {
        return LocalDate.now().plusDays(daysFromToday).format(DATE_FMT);
    }

    /** Logs a structured pass/fail line for a given test case ID. */
    public static void logResult(String testCaseId, boolean passed, long responseTimeMs, String details) {
        String status = passed ? "PASS" : "FAIL";
        log.info("[{}] {} | Response time: {}ms | {}", testCaseId, status, responseTimeMs, details);
    }

    /** Logs just the response time captured after a call. */
    public static void logResponseTime(String testCaseId, long responseTimeMs) {
        log.info("[{}] Response time captured: {}ms", testCaseId, responseTimeMs);
    }
}
